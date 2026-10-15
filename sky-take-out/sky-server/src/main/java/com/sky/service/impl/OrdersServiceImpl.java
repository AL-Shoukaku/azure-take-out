package com.sky.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.exception.UserNotLoginException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrdersMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserMapper;
import com.sky.result.PageResult;
import com.sky.service.AddressBookService;
import com.sky.service.OrdersService;
import com.sky.service.ShoppingCartService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrdersServiceImpl implements OrdersService {

    private static final BigDecimal DELIVERY_FEE = new BigDecimal("6.00");

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Autowired
    private AddressBookService addressBookService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private WebSocketServer webSocketServer;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = getCurrentUserId();
        // 检查传入的数据是否合理，地址簿，购物车是否为空
        AddressBook addressBook = addressBookService.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null || !userId.equals(addressBook.getUserId())) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_FOUND);
        }
        List<ShoppingCart> shoppingCartList = shoppingCartService.showShoppingCart();
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        checkShoppingCartProductStatus(shoppingCartList);
        // 向订单表 orders 中插入一条数据
        Orders orders = new Orders();
        // 直接复制 DTO 中的数据，手动计算 amount 和 packAmount，防止来自非前端的恶意数据
        BeanUtils.copyProperties(ordersSubmitDTO, orders, "amount", "packAmount");
        int packAmount = shoppingCartList.stream().mapToInt(ShoppingCart::getNumber).sum();
        BigDecimal dishesAmount = shoppingCartList.stream()
                .map(item -> item.getAmount().multiply(BigDecimal.valueOf(item.getNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        orders.setPackAmount(packAmount);
        orders.setAmount(dishesAmount.add(DELIVERY_FEE).add(BigDecimal.valueOf(packAmount)));
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(UUID.randomUUID().toString().replace("-", ""));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getDetail());
        orders.setUserName(userMapper.getById(userId).getName());

        ordersMapper.submitOrder(orders);
        List<OrderDetail> orderDetailList = new ArrayList<>();
        // 向明细表 order_detail 中插入多条数据
        for (ShoppingCart shoppingCart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(shoppingCart, orderDetail);
            orderDetail.setOrderId(orders.getId()); // 设置该订单明细关联的订单 id
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.batchInsert(orderDetailList);

        // 清空购物车数据
        shoppingCartService.cleanShoppingCart();

        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
    }

    private void checkShoppingCartProductStatus(List<ShoppingCart> shoppingCartList) {
        for (ShoppingCart item : shoppingCartList) {
            Integer status = null;
            if (item.getDishId() != null && item.getSetmealId() == null) {
                Dish dish = dishMapper.getById(item.getDishId());
                status = dish == null ? null : dish.getStatus();
            } else if (item.getSetmealId() != null && item.getDishId() == null) {
                // 套餐只检查自身售卖状态，允许包含停售菜品。
                Setmeal setmeal = setmealMapper.getById(item.getSetmealId());
                status = setmeal == null ? null : setmeal.getStatus();
            }
            if (!StatusConstant.ENABLE.equals(status)) {
                throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_PRODUCT_UNAVAILABLE);
            }
        }
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = getCurrentUserId();
        Orders paymentOrder = checkOrderOwnership(
                ordersMapper.getByNumberAndUserId(ordersPaymentDTO.getOrderNumber(), userId), userId);
        if (!Orders.PENDING_PAYMENT.equals(paymentOrder.getStatus())
                || !Orders.UN_PAID.equals(paymentOrder.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 本地模拟支付：复用支付成功逻辑，并在状态更新时限定当前用户。
        processPaymentSuccess(paymentOrder, userId);

        // 返回一组假参数（小程序端不会再调用 wx.requestPayment，仅为了保持接口结构不变）
        return OrderPaymentVO.builder()
                .nonceStr("fake_nonce_str")
                .paySign("fake_pay_sign")
                .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                .signType("RSA")
                .packageStr("prepay_id=fake_prepay_id")
                .build();
    }

    private void processPaymentSuccess(Orders ordersDB, Long userId) {
        // 先把订单号、订单id和订单号封装成json字符串，这样如果序列化失败就不会更改数据库
        String jsonStr = serializeNotification(1, ordersDB.getId(), ordersDB.getNumber());

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        int updated = ordersMapper.updateForUser(orders, Orders.PENDING_PAYMENT, userId);
        if (updated == 0) {
            // 与取消订单并发时，重新确认最终状态；仅已支付/已退款视为重复通知。
            Orders latest = ordersMapper.getByNumberAndUserId(ordersDB.getNumber(), userId);
            if (latest != null && (Orders.PAID.equals(latest.getPayStatus())
                    || Orders.REFUND.equals(latest.getPayStatus()))) {
                return;
            }
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 通过 websocket 把来单消息推送给商家端，商家端收到消息后可以播放提示音
        // 封装 type, orderId 和 content 三个字段
        webSocketServer.sendToAllClient(jsonStr);
    }

    /**
     * 用户查询历史订单，可支持根据订单状态查询
     */
    public PageResult historyOrdersPageQuery(Integer pageNum, Integer pageSize, Integer status) {
        Long userId = getCurrentUserId();
        PageHelper.startPage(pageNum, pageSize);

        Page<Orders> page = ordersMapper.pageQuery(userId, status);
        List<Orders> ordersList = page.getResult();
        List<OrderVO> orderVOList = new ArrayList<>();
        for (Orders orders : ordersList) {
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
            orderVO.setOrderDetailList(orderDetailList);
            orderVOList.add(orderVO);
        }
        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 用户查询本人订单详情
     */
    public OrderVO queryUserOrderDetail(Long id) {
        return getOrderDetail(getCurrentUserOrder(id));
    }

    /**
     * 管理端查询订单详情，不比较雇员 ID 与订单用户 ID
     */
    public OrderVO queryOrderDetail(Long id) {
        // 获取订单order，然后根据orderId查询订单明细，最后封装成orderVO返回
        Orders orders = ordersMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return getOrderDetail(orders);
    }

    private OrderVO getOrderDetail(Orders orders) {
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 用户取消订单
     */
    public void userCancelOrder(Long id) {
        Orders orders = getCurrentUserOrder(id);

        // 对于已经接单的订单，用户不能取消
        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (!Orders.PENDING_PAYMENT.equals(orders.getStatus())
                && !Orders.TO_BE_CONFIRMED.equals(orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 修改订单状态为已经取消
        Orders newOrders = new Orders();
        newOrders.setId(id);
        newOrders.setStatus(Orders.CANCELLED);
        newOrders.setCancelTime(LocalDateTime.now());

        // 已支付订单在本地模拟退款。
        if (orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            //支付状态修改为 退款
            newOrders.setPayStatus(Orders.REFUND);
        }

        if (ordersMapper.updateForUser(newOrders, orders.getStatus(), getCurrentUserId()) == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 用户再来一单
     */
    public void repetition(Long id) {
        Orders orders = getCurrentUserOrder(id);
        // 获取所有订单明细，然后将它们插入到购物车中
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        Long userId = getCurrentUserId();
        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart);
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartList.add(shoppingCart);
        }
        shoppingCartMapper.batchInsert(shoppingCartList);
    }

    /**
     * 商家取消订单，需要传入取消原因
     */
    public void cancelOrder(OrdersCancelDTO ordersCancelDTO) {
        Orders orders = ordersMapper.getById(ordersCancelDTO.getId());

        // 检查订单是否存在
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 对于已经接单的订单，商家不能取消
        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (!Orders.PENDING_PAYMENT.equals(orders.getStatus())
                && !Orders.TO_BE_CONFIRMED.equals(orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 修改订单状态为已经取消
        Orders newOrders = new Orders();
        newOrders.setId(ordersCancelDTO.getId());
        newOrders.setStatus(Orders.CANCELLED);
        newOrders.setCancelTime(LocalDateTime.now());
        newOrders.setCancelReason(ordersCancelDTO.getCancelReason());

        // 已支付订单在本地模拟退款。
        if (orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            //支付状态修改为 退款
            newOrders.setPayStatus(Orders.REFUND);
        }

        updateOrderIfStatus(newOrders, orders.getStatus());
    }

    /**
     * 商家分页查询订单
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult adminPageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = ordersMapper.adminPageSearch(ordersPageQueryDTO);
        // 需要额外风中一些信息
        List<OrderVO> orderVOList = getOrderVOList(page);
        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 将订单列表转换为OrderVO列表，包含订单菜品信息
     *
     * @param page 分页查询结果
     * @return OrderVO列表
     */
    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        // 需要返回订单菜品信息，自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if (ordersList != null && !ordersList.isEmpty()) {
            for (Orders orders : ordersList) {
                // 将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes = getOrderDishesStr(orders);

                // 将订单菜品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    /**
     * 根据订单id获取菜品信息字符串
     *
     * @param orders
     * @return
     */
    private String getOrderDishesStr(Orders orders) {
        // 查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        // 将该订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }

    /**
     * 商家统计各个类别的订单
     * @return
     */
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        int toBeConfirmedCount = ordersMapper.countByStatus(Orders.TO_BE_CONFIRMED);
        int confirmedCount = ordersMapper.countByStatus(Orders.CONFIRMED);
        int deliverInProgressCount = ordersMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS);
        orderStatisticsVO.setConfirmed(confirmedCount);
        orderStatisticsVO.setToBeConfirmed(toBeConfirmedCount);
        orderStatisticsVO.setDeliveryInProgress(deliverInProgressCount);
        return orderStatisticsVO;
    }

    /**
     * 商家完成订单
     */
    public void completeOrder(Long id) {
        Orders orders = ordersMapper.getById(id);

        // 检查订单是否存在
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 商家只能完成派送中的订单，其他状态的订单不能完成
        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (orders.getStatus() != Orders.DELIVERY_IN_PROGRESS) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 修改订单状态为已完成
        Orders newOrders = new Orders();
        newOrders.setId(id);
        newOrders.setStatus(Orders.COMPLETED);
        newOrders.setDeliveryTime(LocalDateTime.now());
        updateOrderIfStatus(newOrders, Orders.DELIVERY_IN_PROGRESS);
    }

    /**
     * 商家接单
     */
    public void confirmOrder(OrdersConfirmDTO ordersConfirmDTO) {
        Long id = ordersConfirmDTO.getId();
        Orders orders = ordersMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 商家只能接收待接单的订单，其他状态的订单不能接单
        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (orders.getStatus() != Orders.TO_BE_CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 修改订单状态为已接单
        Orders newOrders = new Orders();
        newOrders.setId(id);
        newOrders.setStatus(Orders.CONFIRMED);
        updateOrderIfStatus(newOrders, Orders.TO_BE_CONFIRMED);
    }


    /**
     * 商家拒单
     */
    public void rejectOrder(OrdersRejectionDTO ordersRejectionDTO) {
        Long id = ordersRejectionDTO.getId();
        Orders orders = ordersMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 商家只能拒绝待接单的订单，其他状态的订单不能拒单
        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (orders.getStatus() != Orders.TO_BE_CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 修改订单状态为已取消，并设置拒单原因，支付状态为退款
        Orders newOrders = new Orders();
        newOrders.setId(id);
        newOrders.setStatus(Orders.CANCELLED);
        newOrders.setCancelTime(LocalDateTime.now());
        newOrders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        newOrders.setPayStatus(Orders.REFUND);
        updateOrderIfStatus(newOrders, Orders.TO_BE_CONFIRMED);
    }

    /**
     * 商家派送订单
     * @param id 订单id
     */
    public void deliveryOrder(Long id) {
        Orders orders = ordersMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 商家只能派送已接单的订单，其他状态的订单不能派送
        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (orders.getStatus() != Orders.CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 修改订单状态为派送中
        Orders newOrders = new Orders();
        newOrders.setId(id);
        newOrders.setStatus(Orders.DELIVERY_IN_PROGRESS);
        updateOrderIfStatus(newOrders, Orders.CONFIRMED);
    }


    /**
     * 客户催单
     */
    public void remindOrder(Long id) {
        Orders orders = getCurrentUserOrder(id);
        // 客户只能催单待接单的订单，其他状态的订单不能催单
        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (orders.getStatus() != Orders.TO_BE_CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 通过 websocket 把催单消息推送给商家端，商家端收到消息后可以播放提示音
        // 封装 type, orderId 和 content 三个字段
        String jsonStr = serializeNotification(2, id, orders.getNumber());
        webSocketServer.sendToAllClient(jsonStr);
    }

    private void updateOrderIfStatus(Orders order, Integer expectedStatus) {
        if (ordersMapper.update(order, expectedStatus) == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 根据发送消息的类型，订单 id 和订单号序列化为 JSON 字符串
     * @param type          1.表示来单提醒，2.表示客户催单
     * @param orderId       订单 id
     * @param orderNumber   订单号
     * @return
     */
    private String serializeNotification(int type, Long orderId, String orderNumber) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("orderId", orderId);
        message.put("content", "订单号 " + orderNumber);
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("订单通知 JSON 序列化失败", e);
        }
    }

    private Long getCurrentUserId() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
        }
        return userId;
    }

    private Orders getCurrentUserOrder(Long id) {
        Long userId = getCurrentUserId();
        return checkOrderOwnership(ordersMapper.getByIdAndUserId(id, userId), userId);
    }

    private Orders checkOrderOwnership(Orders order, Long userId) {
        if (order == null || !userId.equals(order.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return order;
    }
}

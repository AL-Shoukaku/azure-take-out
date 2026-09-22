package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrdersMapper;
import com.sky.mapper.UserMapper;
import com.sky.result.PageResult;
import com.sky.service.AddressBookService;
import com.sky.service.OrdersService;
import com.sky.service.ShoppingCartService;
import com.sky.service.UserService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrdersServiceImpl implements OrdersService {

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
    private WeChatPayUtil weChatPayUtil;

    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        // 检查传入的数据是否合理，地址簿，购物车是否为空
        AddressBook addressBook = addressBookService.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        List<ShoppingCart> shoppingCartList = shoppingCartService.showShoppingCart();
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        // 向订单表 orders 中插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setUserId(BaseContext.getCurrentId());
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getDetail());
        orders.setUserName(userMapper.getById(BaseContext.getCurrentId()).getName());

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

        // 封装VO对象返回
        OrderSubmitVO orderSubmitVO = new OrderSubmitVO();

        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        /*
        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
        */

        //===== 本地开发：没有微信支付商户号，跳过真实支付，直接调用 paySuccess 把订单置为已支付 =====
        // 说明：paySuccess 就是微信支付回调 PayNotifyController 里用的那个方法，逻辑与真实支付成功完全一致
        paySuccess(ordersPaymentDTO.getOrderNumber());

        // 返回一组假参数（小程序端不会再调用 wx.requestPayment，仅为了保持接口结构不变）
        return OrderPaymentVO.builder()
                .nonceStr("fake_nonce_str")
                .paySign("fake_pay_sign")
                .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                .signType("RSA")
                .packageStr("prepay_id=fake_prepay_id")
                .build();
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = ordersMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        ordersMapper.update(orders);
    }

    /**
     * 用户查询历史订单，可支持根据订单状态查询
     */
    public PageResult historyOrdersPageQuery(Integer pageNum, Integer pageSize, Integer status) {
        PageHelper.startPage(pageNum, pageSize);

        Page<Orders> page = ordersMapper.pageQuery(BaseContext.getCurrentId().intValue(), status);
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
     * 用户查询订单详情
     */
    public OrderVO queryOrderDetail(Integer id) {
        // 获取订单order，然后根据orderId查询订单明细，最后封装成orderVO返回
        OrderVO orderVO = new OrderVO();
        Orders orders = ordersMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        BeanUtils.copyProperties(orders, orderVO);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }
}

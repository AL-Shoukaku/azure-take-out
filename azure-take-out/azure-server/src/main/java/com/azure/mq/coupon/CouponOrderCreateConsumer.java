package com.azure.mq.coupon;

import com.azure.entity.Coupon;
import com.azure.entity.CouponOrder;
import com.azure.entity.User;
import com.azure.exception.BaseException;
import com.azure.mapper.CouponMapper;
import com.azure.mapper.CouponOrderMapper;
import com.azure.mapper.UserMapper;
import com.azure.message.CouponOrderCreateMessage;
import com.azure.mq.RabbitConstants;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class CouponOrderCreateConsumer {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CouponOrderMapper couponOrderMapper;

    @Autowired
    private CouponMapper couponMapper;

    /**
     * 从优惠券订单创建的消息队列中取出消息并创建订单
     * @param message   消息
     */
    @RabbitListener(queues = RabbitConstants.COUPON_ORDER_CREATE_QUEUE)
    @Transactional
    public void receiveCouponOrderCreateMessage(CouponOrderCreateMessage message) {
        Coupon coupon = message.getCoupon();
        Long userId = message.getUserId();
        String couponNumber = message.getCouponNumber();
        String orderNumber = message.getOrderNumber();
        if (coupon == null || userId == null || coupon.getId() == null) {
            throw new RuntimeException("异常的优惠券订单消息！");
        }
        CouponOrder couponOrder = createCouponOrder(coupon, userId, couponNumber, orderNumber);
        if (couponMapper.decreaseStockByOne(coupon.getId()) == 0) {
            throw new RuntimeException("创建优惠券订单时，库存不足");
        }
        couponOrderMapper.insertCouponOrder(couponOrder);
    }

    /**
     * 创建购券订单
     * @param coupon    优惠券模板
     * @param userId    用户ID
     * @return          购券订单
     */
    private CouponOrder createCouponOrder(Coupon coupon, Long userId, String couponNumber, String orderNumber) {
        if (userId == null) {
            throw new BaseException("用户未登录");
        }
        User user = userMapper.getById(userId);
        if (user == null) {
            throw new BaseException("用户不存在");
        }

        CouponOrder couponOrder = new CouponOrder();
        LocalDateTime now = LocalDateTime.now();
        couponOrder.setUserId(userId);
        couponOrder.setCouponId(coupon.getId());
        couponOrder.setNumber(orderNumber);
        couponOrder.setCouponNumber(couponNumber);
        couponOrder.setUsername(user.getUsername());
        couponOrder.setCouponName(coupon.getName());
        couponOrder.setType(coupon.getType());
        couponOrder.setPrice(coupon.getPrice());
        couponOrder.setDiscountAmount(coupon.getDiscountAmount());
        couponOrder.setThresholdAmount(coupon.getThresholdAmount());
        couponOrder.setStatus(CouponOrder.STATUS_UNPAID);
        couponOrder.setCreateTime(now);
        couponOrder.setPayDeadline(now.plusMinutes(15));
        return couponOrder;
    }
}

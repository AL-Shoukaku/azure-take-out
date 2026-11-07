package com.azure.service.impl;

import com.azure.constant.StatusConstant;
import com.azure.context.BaseContext;
import com.azure.dto.coupon.CouponDTO;
import com.azure.dto.coupon.CouponPageQueryDTO;
import com.azure.dto.coupon.CouponUpdateDTO;
import com.azure.entity.Coupon;
import com.azure.entity.CouponOrder;
import com.azure.entity.User;
import com.azure.entity.UserCoupon;
import com.azure.exception.BaseException;
import com.azure.mapper.CouponMapper;
import com.azure.mapper.CouponOrderMapper;
import com.azure.mapper.UserMapper;
import com.azure.mapper.UserCouponMapper;
import com.azure.result.PageResult;
import com.azure.service.CouponService;
import com.azure.vo.coupon.*;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CouponServiceImpl implements CouponService {

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CouponOrderMapper couponOrderMapper;

    /**
     * 商家发布优惠券
     * @param couponDTO 优惠券信息
     */
    public void publishCoupon(CouponDTO couponDTO) {
        if (couponDTO.getStartTime() == null || couponDTO.getEndTime() == null
                || !couponDTO.getStartTime().isBefore(couponDTO.getEndTime())) {
            throw new BaseException("优惠券的开始时间必须早于结束时间");
        }
        Coupon coupon = new Coupon();
        BeanUtils.copyProperties(couponDTO, coupon);
        LocalDateTime now = LocalDateTime.now();
        coupon.setStock(couponDTO.getTotalStock());
        coupon.setStatus(StatusConstant.DISABLE);   // 默认停售，商家可以在后台手动启用
        coupon.setCreateTime(now);
        coupon.setUpdateTime(now);
        couponMapper.insertCoupon(coupon);
    }

    /**
     * 商家修改优惠券信息
     * @param couponUpdateDTO 优惠券信息
     */
    @Override
    public void updateCoupon(CouponUpdateDTO couponUpdateDTO) {
        Coupon coupon = new Coupon();
        BeanUtils.copyProperties(couponUpdateDTO, coupon);
        LocalDateTime now = LocalDateTime.now();
        coupon.setUpdateTime(now);
        int row = couponMapper.updateCoupon(coupon);
        if (row == 0) {
            throw new BaseException("优惠券不存在");
        }
    }

    /**
     * 商家起售/停售优惠券
     * @param couponId 优惠券ID
     * @param status   优惠券状态，1启用，2禁用
     */
    @Override
    public void startOrStop(Long couponId, Integer status) {
        if (couponId == null || status == null) {
            throw new BaseException("优惠券ID和状态不能为空");
        }
        if (couponId < 0 || (!status.equals(StatusConstant.ENABLE) && !status.equals(StatusConstant.DISABLE))) {
            throw new BaseException("优惠券ID或状态不合法");
        }
        int row = couponMapper.updateStatus(couponId, status);
        if (row == 0) {
            throw new BaseException("优惠券不存");
        }
    }

    /**
     * 根据优惠券ID查询优惠券详情
     * @param couponId 优惠券ID
     * @return 优惠券详情
     */
    @Override
    public CouponVO getById(Long couponId) {
        if (couponId == null || couponId < 0) {
            throw new BaseException("优惠券ID不合法");
        }
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BaseException("优惠券不存在");
        }
        CouponVO couponVO = new CouponVO();
        BeanUtils.copyProperties(coupon, couponVO);
        return couponVO;
    }

    /**
     * 分页查询优惠券列表
     * @param couponPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery(CouponPageQueryDTO couponPageQueryDTO) {
        PageHelper.startPage(couponPageQueryDTO.getPage(), couponPageQueryDTO.getPageSize());
        Page<CouponVO> page = couponMapper.pageQuery(couponPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 用户查询可领取的优惠券列表
     * 包括未开始的优惠券和正在进行中的优惠券，且库存大于0
     * @return  可领取/购买的优惠券列表
     */
    @Override
    public List<AvailableCouponVO> queryAvailableCoupons() {
        return couponMapper.getAvailableCoupons();
    }

    /**
     * 用户领取满减券
     * @param couponId  优惠券ID
     */
    @Override
    @Transactional
    public CouponClaimVO userGetCoupon(Long couponId) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = BaseContext.getCurrentId();
        Coupon coupon = checkCouponAvailable(couponId, userId, now, Coupon.TYPE_FULL_REDUCTION);
        // 扣减库存，并将优惠券发放给用户
        coupon.setStock(coupon.getStock() - 1);
        couponMapper.updateCoupon(coupon);
        UserCoupon userCoupon = createUserCoupon(coupon, userId);
        userCouponMapper.insertUserCoupon(userCoupon);

        return CouponClaimVO.builder()
                .number(userCoupon.getNumber())
                .expireTime(userCoupon.getExpireTime())
                .build();
    }

    /**
     * 用户购买代金券
     * @param couponId  优惠券ID
     */
    @Override
    @Transactional
    public CouponOrderSubmitVO userBuyCoupon(Long couponId) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = BaseContext.getCurrentId();
        Coupon coupon = checkCouponAvailable(couponId, userId, now, Coupon.TYPE_VOUCHER);

        // 扣减库存，并提交优惠券订单
        coupon.setStock(coupon.getStock() - 1);
        couponMapper.updateCoupon(coupon);
        CouponOrder couponOrder = createCouponOrder(coupon, userId);
        couponOrderMapper.insertCouponOrder(couponOrder);

        return CouponOrderSubmitVO.builder()
                .number(couponOrder.getNumber())
                .price(couponOrder.getPrice())
                .payDeadline(couponOrder.getPayDeadline())
                .build();
    }

    /**
     * 用户支付代金券订单
     * @param couponOrderNumber 代金券订单号
     */
    @Override
    @Transactional
    public CouponOrderPayVO payCouponOrder(String couponOrderNumber) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BaseException("用户未登录");
        }
        if (couponOrderNumber == null || couponOrderNumber.isBlank()) {
            throw new BaseException("代金券订单号不能为空");
        }

        // 根据订单号与用户ID查询订单，确保订单存在且属于当前用户
        CouponOrder couponOrder = couponOrderMapper.selectByNumberAndUserId(couponOrderNumber, userId);
        if (couponOrder == null) {
            throw new BaseException("代金券订单不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(couponOrder.getPayDeadline())) {
            throw new BaseException("代金券订单已超过支付截止时间");
        }

        // 更新订单状态为已支付，并设置支付时间
        couponOrder.setStatus(CouponOrder.STATUS_PAID);
        couponOrder.setPayTime(now);
        int updateRows = couponOrderMapper.updateStatusByIdWithExpectedStatus(
                couponOrder.getId(),
                CouponOrder.STATUS_UNPAID,
                CouponOrder.STATUS_PAID,
                now
        );
        if (updateRows == 0) {
            throw new BaseException("订单状态更新失败，可能是订单已支付或已关闭");
        }

        // 发放代金券给用户
        UserCoupon userCoupon = createUserCouponWithOrder(couponOrder);
        userCouponMapper.insertUserCoupon(userCoupon);

        return CouponOrderPayVO.builder()
                .number(couponOrder.getNumber())
                .expireTime(userCoupon.getExpireTime())
                .build();
    }


    /**
     * 检查优惠券是否可领取/购买
     * @param couponId      优惠券ID
     * @param userId        用户ID
     * @param now           当前时间
     * @param expectType    预期的优惠券类型
     * @return              优惠券信息
     */
    private Coupon checkCouponAvailable(Long couponId, Long userId, LocalDateTime now, Integer expectType) {
        if (couponId == null || couponId < 0) {
            throw new BaseException("优惠券ID不合法");
        }
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BaseException("优惠券不存在");
        }
        // 检查优惠券类型是否符合预期
        if (!coupon.getType().equals(expectType)) {
            throw new BaseException("优惠券类型不符合预期");
        }
        // 检查优惠券是否启售
        if (!coupon.getStatus().equals(StatusConstant.ENABLE)) {
            throw new BaseException("优惠券未启售");
        }
        // 检查优惠券是否在活动时间内
        if (now.isBefore(coupon.getStartTime()) || coupon.getEndTime().isBefore(now)) {
            throw new BaseException("优惠券不在活动时间内");
        }
        // 检查库存是否大于0
        if (coupon.getStock() <= 0) {
            throw new BaseException("优惠券库存不足");
        }
        // 检查用户是否已经领取过该优惠券，满减券从 user_coupon 表中查询，代金券从 coupon_order 表中查询
        if (coupon.getType().equals(Coupon.TYPE_FULL_REDUCTION) && userCouponMapper.userHasGetCoupon(userId, couponId) > 0) {
            throw new BaseException("用户已领取过该满减券");
        }
        if (coupon.getType().equals(Coupon.TYPE_VOUCHER) && couponOrderMapper.userHasBuyCoupon(userId, couponId) > 0) {
            throw new BaseException("用户已购买过该代金券");
        }
        return coupon;
    }

    /**
     * 创建用户优惠券
     * @param coupon    优惠券模板
     * @param userId    用户ID
     * @return          用户优惠券
     */
    private UserCoupon createUserCoupon(Coupon coupon, Long userId) {
        if (userId == null) {
            throw new BaseException("用户未登录");
        }

        UserCoupon userCoupon = new UserCoupon();
        LocalDateTime now = LocalDateTime.now();
        BeanUtils.copyProperties(coupon, userCoupon, "id");
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        userCoupon.setNumber(UUID.randomUUID().toString().replace("-", ""));
        userCoupon.setStatus(UserCoupon.STATUS_UNUSED);
        userCoupon.setCreateTime(now);
        userCoupon.setExpireTime(now.plusDays(coupon.getValidTime()));
        return userCoupon;
    }

    /**
     * 根据已支付的购券订单创建用户优惠券
     * @param couponOrder 已支付的购券订单
     * @return 用户优惠券
     */
    private UserCoupon createUserCouponWithOrder(CouponOrder couponOrder) {
        Coupon coupon = couponMapper.selectById(couponOrder.getCouponId());
        if (coupon == null) {
            throw new BaseException("优惠券不存在");
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(couponOrder.getUserId());
        userCoupon.setCouponId(couponOrder.getCouponId());
        userCoupon.setNumber(couponOrder.getCouponNumber());
        userCoupon.setType(couponOrder.getType());
        userCoupon.setName(couponOrder.getCouponName());
        userCoupon.setDiscountAmount(couponOrder.getDiscountAmount());
        userCoupon.setThresholdAmount(couponOrder.getThresholdAmount());
        userCoupon.setStatus(UserCoupon.STATUS_UNUSED);
        userCoupon.setCreateTime(couponOrder.getPayTime());
        userCoupon.setExpireTime(couponOrder.getPayTime().plusDays(coupon.getValidTime()));
        return userCoupon;
    }

    /**
     * 创建购券订单
     * @param coupon    优惠券模板
     * @param userId    用户ID
     * @return          购券订单
     */
    private CouponOrder createCouponOrder(Coupon coupon, Long userId) {
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
        couponOrder.setNumber(UUID.randomUUID().toString().replace("-", ""));
        couponOrder.setCouponNumber(UUID.randomUUID().toString().replace("-", ""));
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

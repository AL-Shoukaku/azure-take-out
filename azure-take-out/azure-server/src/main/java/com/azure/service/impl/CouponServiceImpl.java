package com.azure.service.impl;

import com.azure.constant.StatusConstant;
import com.azure.dto.coupon.CouponDTO;
import com.azure.entity.Coupon;
import com.azure.exception.BaseException;
import com.azure.mapper.CouponMapper;
import com.azure.service.CouponService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CouponServiceImpl implements CouponService {

    @Autowired
    private CouponMapper couponMapper;

    /**
     * 商家发布优惠券
     * @param couponDTO 优惠券信息
     */
    public void publishCoupon(CouponDTO couponDTO) {
        if (couponDTO.getStartTime() == null || couponDTO.getEndTime() == null
                || couponDTO.getStartTime().isAfter(couponDTO.getEndTime())) {
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


}

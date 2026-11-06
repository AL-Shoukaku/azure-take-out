package com.azure.service.impl;

import com.azure.constant.StatusConstant;
import com.azure.dto.coupon.CouponDTO;
import com.azure.dto.coupon.CouponPageQueryDTO;
import com.azure.dto.coupon.CouponUpdateDTO;
import com.azure.entity.Coupon;
import com.azure.exception.BaseException;
import com.azure.mapper.CouponMapper;
import com.azure.result.PageResult;
import com.azure.service.CouponService;
import com.azure.vo.coupon.CouponVO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
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
        if (couponId < 0 || (status != StatusConstant.ENABLE && status != StatusConstant.DISABLE)) {
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
}

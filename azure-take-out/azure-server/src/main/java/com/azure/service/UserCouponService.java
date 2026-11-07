package com.azure.service;

import com.azure.dto.coupon.UserCouponPageQueryDTO;
import com.azure.result.PageResult;
import com.azure.vo.coupon.UserCouponVO;

public interface UserCouponService {

    /**
     * 用户分页查询自己持有的优惠券，支持按状态筛选
     */
    PageResult pageQuery(UserCouponPageQueryDTO userCouponPageQueryDTO);

    /**
     * 用户查询本人持有的优惠券详情
     */
    UserCouponVO queryUserCouponDetail(Long id);
}

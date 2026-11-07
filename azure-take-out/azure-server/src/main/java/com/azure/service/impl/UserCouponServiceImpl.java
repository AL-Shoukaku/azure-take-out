package com.azure.service.impl;

import com.azure.constant.MessageConstant;
import com.azure.context.BaseContext;
import com.azure.dto.coupon.UserCouponPageQueryDTO;
import com.azure.exception.BaseException;
import com.azure.exception.UserNotLoginException;
import com.azure.mapper.UserCouponMapper;
import com.azure.result.PageResult;
import com.azure.service.UserCouponService;
import com.azure.vo.coupon.UserCouponVO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserCouponServiceImpl implements UserCouponService {

    @Autowired
    private UserCouponMapper userCouponMapper;

    /**
     * 用户分页查询自己持有的优惠券，支持按状态筛选
     */
    public PageResult pageQuery(UserCouponPageQueryDTO userCouponPageQueryDTO) {
        Long userId = getCurrentUserId();
        PageHelper.startPage(userCouponPageQueryDTO.getPage(), userCouponPageQueryDTO.getPageSize());
        Page<UserCouponVO> page = userCouponMapper.userPageQuery(userId, userCouponPageQueryDTO.getStatus());
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 用户查询本人持有的优惠券详情
     */
    public UserCouponVO queryUserCouponDetail(Long id) {
        Long userId = getCurrentUserId();
        UserCouponVO userCoupon = userCouponMapper.selectByIdAndUserId(id, userId);
        if (userCoupon == null) {
            throw new BaseException("优惠券不存在");
        }
        return userCoupon;
    }

    private Long getCurrentUserId() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
        }
        return userId;
    }
}

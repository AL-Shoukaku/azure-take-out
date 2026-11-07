package com.azure.service.impl;

import com.azure.constant.MessageConstant;
import com.azure.context.BaseContext;
import com.azure.dto.coupon.CouponOrderPageQueryDTO;
import com.azure.exception.BaseException;
import com.azure.exception.UserNotLoginException;
import com.azure.mapper.CouponOrderMapper;
import com.azure.result.PageResult;
import com.azure.service.CouponOrderService;
import com.azure.vo.coupon.CouponOrderPageQueryVO;
import com.azure.vo.coupon.CouponOrderVO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CouponOrderServiceImpl implements CouponOrderService {

    @Autowired
    private CouponOrderMapper couponOrderMapper;

    /**
     * 商家分页查询优惠券订单，支持按订单号、用户名、下单时间、订单状态筛选
     */
    public PageResult adminPageQuery(CouponOrderPageQueryDTO couponOrderPageQueryDTO) {
        PageHelper.startPage(couponOrderPageQueryDTO.getPage(), couponOrderPageQueryDTO.getPageSize());
        Page<CouponOrderPageQueryVO> page = couponOrderMapper.adminPageSearch(couponOrderPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 商家查询优惠券订单详情
     */
    public CouponOrderVO queryOrderDetail(Long id) {
        CouponOrderVO couponOrder = couponOrderMapper.selectById(id);
        if (couponOrder == null) {
            throw new BaseException("代金券订单不存在");
        }
        return couponOrder;
    }

    /**
     * 用户分页查询自己的优惠券订单，支持按订单状态筛选
     */
    public PageResult historyOrdersPageQuery(Integer page, Integer pageSize, Integer status) {
        Long userId = getCurrentUserId();
        PageHelper.startPage(page, pageSize);
        Page<CouponOrderPageQueryVO> pageResult = couponOrderMapper.userPageQuery(userId, status);
        return new PageResult(pageResult.getTotal(), pageResult.getResult());
    }

    /**
     * 用户查询本人的优惠券订单详情
     */
    public CouponOrderVO queryUserOrderDetail(Long id) {
        Long userId = getCurrentUserId();
        CouponOrderVO couponOrder = couponOrderMapper.selectByIdAndUserId(id, userId);
        if (couponOrder == null) {
            throw new BaseException("代金券订单不存在");
        }
        return couponOrder;
    }

    private Long getCurrentUserId() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
        }
        return userId;
    }
}

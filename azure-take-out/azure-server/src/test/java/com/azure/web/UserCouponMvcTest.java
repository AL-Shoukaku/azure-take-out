package com.azure.web;

import com.azure.result.PageResult;
import com.azure.support.WebTestSupport;
import com.azure.vo.coupon.UserCouponVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class UserCouponMvcTest extends WebTestSupport {

    @Test
    void pageBindsPaginationAndOptionalStatusIncludingZero() throws Exception {
        UserCouponVO row = new UserCouponVO();
        row.setId(7L);
        when(userCouponService.pageQuery(any())).thenReturn(new PageResult(3, List.of(row)));

        successful(user(get("/user/userCoupon/page").param("page", "2").param("pageSize", "6").param("status", "1")))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].id").value(7));
        verify(userCouponService).pageQuery(argThat(dto -> dto.getPage() == 2 && dto.getPageSize() == 6
                && dto.getStatus() != null && dto.getStatus() == 1));

        successful(user(get("/user/userCoupon/page").param("page", "1").param("pageSize", "10")))
                .andExpect(jsonPath("$.data.total").value(3));
        verify(userCouponService).pageQuery(argThat(dto -> dto.getPage() == 1 && dto.getPageSize() == 10
                && dto.getStatus() == null));
    }

    @Test
    void detailDelegatesTargetId() throws Exception {
        UserCouponVO row = new UserCouponVO();
        row.setId(9L);
        row.setNumber("COUPON-9");
        when(userCouponService.queryUserCouponDetail(9L)).thenReturn(row);

        successful(user(get("/user/userCoupon/details/9")))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.number").value("COUPON-9"));
        verify(userCouponService).queryUserCouponDetail(9L);
    }
}

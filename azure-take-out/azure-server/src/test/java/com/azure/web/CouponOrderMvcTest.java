package com.azure.web;

import com.azure.result.PageResult;
import com.azure.support.WebTestSupport;
import com.azure.vo.coupon.CouponOrderVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CouponOrderMvcTest extends WebTestSupport {

    @Test
    void adminSearchBindsNumberUsernameTimeRangeAndStatus() throws Exception {
        when(couponOrderService.adminPageQuery(any())).thenReturn(new PageResult(0, Collections.emptyList()));
        successful(admin(get("/admin/couponOrder/conditionSearch")
                .param("page", "1").param("pageSize", "10").param("number", "ORDER")
                .param("username", "张三").param("status", "1")
                .param("beginTime", "2026-11-01 00:00:00").param("endTime", "2026-11-07 23:59:59")));
        verify(couponOrderService).adminPageQuery(argThat(dto -> dto.getPage() == 1 && dto.getPageSize() == 10
                && dto.getNumber().equals("ORDER") && dto.getUsername().equals("张三") && dto.getStatus() == 1
                && dto.getBeginTime().equals(LocalDateTime.of(2026, 11, 1, 0, 0))
                && dto.getEndTime().equals(LocalDateTime.of(2026, 11, 7, 23, 59, 59))));
    }

    @Test
    void userHistoryBindsPaginationAndOptionalStatus() throws Exception {
        when(couponOrderService.historyOrdersPageQuery(2, 5, 1)).thenReturn(new PageResult(3, Collections.emptyList()));
        successful(user(get("/user/couponOrder/historyOrders").param("page", "2").param("pageSize", "5").param("status", "1")))
                .andExpect(jsonPath("$.data.total").value(3));
        when(couponOrderService.historyOrdersPageQuery(1, 10, null)).thenReturn(new PageResult(0, Collections.emptyList()));
        successful(user(get("/user/couponOrder/historyOrders").param("page", "1").param("pageSize", "10")));
        verify(couponOrderService).historyOrdersPageQuery(1, 10, null);
    }

    @Test
    void bothEndsExposeCouponOrderDetail() throws Exception {
        CouponOrderVO vo = new CouponOrderVO();
        vo.setId(9L);
        vo.setNumber("order-9");
        vo.setPrice(new BigDecimal("9.90"));
        when(couponOrderService.queryUserOrderDetail(9L)).thenReturn(vo);
        when(couponOrderService.queryOrderDetail(9L)).thenReturn(vo);

        successful(user(get("/user/couponOrder/orderDetail/9"))).andExpect(jsonPath("$.data.number").value("order-9"));
        successful(admin(get("/admin/couponOrder/details/9"))).andExpect(jsonPath("$.data.id").value(9));
        verify(couponOrderService).queryUserOrderDetail(9L);
        verify(couponOrderService).queryOrderDetail(9L);
    }
}

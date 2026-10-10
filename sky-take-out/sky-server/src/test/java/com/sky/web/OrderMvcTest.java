package com.sky.web;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.support.WebTestSupport;
import com.sky.vo.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderMvcTest extends WebTestSupport {
    @Test void submitBindsDeliverySecondsAndSerializesOrderTimeToMinutes() throws Exception {
        when(ordersService.submitOrder(any())).thenReturn(OrderSubmitVO.builder().id(9L).orderNumber("uuid-order").orderAmount(new BigDecimal("42.00"))
                .orderTime(LocalDateTime.of(2026,10,8,12,30,45)).build());
        successful(user(post("/user/order/submit").contentType("application/json").content("{\"addressBookId\":5,\"amount\":0.01,\"packAmount\":0,\"payMethod\":1,\"remark\":\"少盐\",\"estimatedDeliveryTime\":\"2026-10-08 12:30:45\",\"tablewareNumber\":2}")))
                .andExpect(jsonPath("$.data.orderAmount").value(42.0)).andExpect(jsonPath("$.data.orderTime").value("2026-10-08 12:30"));
        verify(ordersService).submitOrder(argThat(dto -> dto.getAddressBookId()==5L && dto.getRemark().equals("少盐")
                && dto.getEstimatedDeliveryTime().equals(LocalDateTime.of(2026,10,8,12,30,45)) && dto.getTablewareNumber()==2));
    }
    @Test void paymentPreservesSimulationResponseContract() throws Exception {
        when(ordersService.payment(any())).thenReturn(OrderPaymentVO.builder().nonceStr("fake_nonce_str").paySign("fake_pay_sign").packageStr("prepay_id=fake_prepay_id").signType("RSA").timeStamp("123456").build());
        successful(user(put("/user/order/payment").contentType("application/json").content("{\"orderNumber\":\"order-9\",\"payMethod\":1}")))
                .andExpect(jsonPath("$.data.packageStr").value("prepay_id=fake_prepay_id")).andExpect(jsonPath("$.data.paySign").value("fake_pay_sign"));
        verify(ordersService).payment(argThat(dto -> dto.getOrderNumber().equals("order-9") && dto.getPayMethod()==1));
    }
    @Test void historyBindsPaginationAndOptionalStatus() throws Exception {
        when(ordersService.historyOrdersPageQuery(2,5,5)).thenReturn(new PageResult(11,Collections.emptyList()));
        successful(user(get("/user/order/historyOrders").param("page","2").param("pageSize","5").param("status","5"))).andExpect(jsonPath("$.data.total").value(11));
        when(ordersService.historyOrdersPageQuery(1,10,null)).thenReturn(new PageResult(0,Collections.emptyList()));
        successful(user(get("/user/order/historyOrders").param("page","1").param("pageSize","10"))).andExpect(jsonPath("$.data.records").isEmpty());
        verify(ordersService).historyOrdersPageQuery(1,10,null);
    }
    @Test void bothEndsExposeOrderDetail() throws Exception {
        OrderVO vo = new OrderVO(); vo.setId(9L); vo.setNumber("order-9"); when(ordersService.queryUserOrderDetail(9L)).thenReturn(vo); when(ordersService.queryOrderDetail(9L)).thenReturn(vo);
        successful(user(get("/user/order/orderDetail/9"))).andExpect(jsonPath("$.data.number").value("order-9"));
        successful(admin(get("/admin/order/details/9"))).andExpect(jsonPath("$.data.id").value(9)); verify(ordersService).queryUserOrderDetail(9L); verify(ordersService).queryOrderDetail(9L);
    }
    @Test void userCancelRepetitionAndReminderDelegateTargetId() throws Exception {
        successful(user(put("/user/order/cancel/9"))); verify(ordersService).userCancelOrder(9L);
        successful(user(post("/user/order/repetition/9"))); verify(ordersService).repetition(9L);
        successful(user(get("/user/order/reminder/9"))); verify(ordersService).remindOrder(9L);
    }
    @Test void adminSearchBindsDateRangeAndFilters() throws Exception {
        when(ordersService.adminPageQuery(any())).thenReturn(new PageResult(0,Collections.emptyList()));
        successful(admin(get("/admin/order/conditionSearch").param("page","1").param("pageSize","10").param("number","order").param("phone","138")
                .param("status","2").param("beginTime","2026-10-08 00:00:00").param("endTime","2026-10-08 23:59:59")));
        verify(ordersService).adminPageQuery(argThat(dto -> dto.getPage()==1 && dto.getPageSize()==10 && dto.getStatus()==2
                && dto.getNumber().equals("order") && dto.getPhone().equals("138") && dto.getBeginTime().equals(LocalDateTime.of(2026,10,8,0,0))
                && dto.getEndTime().equals(LocalDateTime.of(2026,10,8,23,59,59))));
    }
    @Test void adminStatisticsReturnsThreeCounters() throws Exception {
        OrderStatisticsVO statistics = new OrderStatisticsVO();
        statistics.setToBeConfirmed(1); statistics.setConfirmed(2); statistics.setDeliveryInProgress(3);
        when(ordersService.statistics()).thenReturn(statistics);
        successful(admin(get("/admin/order/statistics"))).andExpect(jsonPath("$.data.toBeConfirmed").value(1))
                .andExpect(jsonPath("$.data.confirmed").value(2)).andExpect(jsonPath("$.data.deliveryInProgress").value(3));
    }
    @Test void adminConfirmCancelAndRejectBindBodies() throws Exception {
        successful(admin(put("/admin/order/confirm").contentType("application/json").content("{\"id\":9}")));
        verify(ordersService).confirmOrder(argThat(dto -> dto.getId()==9L));
        successful(admin(put("/admin/order/cancel").contentType("application/json").content("{\"id\":9,\"cancelReason\":\"停业\"}")));
        verify(ordersService).cancelOrder(argThat(dto -> dto.getId()==9L && dto.getCancelReason().equals("停业")));
        successful(admin(put("/admin/order/rejection").contentType("application/json").content("{\"id\":9,\"rejectionReason\":\"售罄\"}")));
        verify(ordersService).rejectOrder(argThat(dto -> dto.getId()==9L && dto.getRejectionReason().equals("售罄")));
    }
    @Test void adminDeliveryAndCompletionBindPathId() throws Exception {
        successful(admin(put("/admin/order/delivery/9"))); verify(ordersService).deliveryOrder(9L);
        successful(admin(put("/admin/order/complete/9"))); verify(ordersService).completeOrder(9L);
    }
}

package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrdersMapper;
import com.sky.support.ServiceTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderTaskTest extends ServiceTestSupport {
    @InjectMocks private OrderTask task;
    @Mock private OrdersMapper mapper;
    @Captor private ArgumentCaptor<LocalDateTime> cutoff;
    @Captor private ArgumentCaptor<Orders> update;

    @Test void timeoutUsesFifteenMinuteCutoffAndConditionalCancellation() {
        when(mapper.getByStatusAndOrderTime(eq(1),any())).thenReturn(Arrays.asList(Orders.builder().id(7L).build(),Orders.builder().id(8L).build()));
        LocalDateTime before = LocalDateTime.now(); task.processTimeOutOrders(); LocalDateTime after = LocalDateTime.now();
        verify(mapper).getByStatusAndOrderTime(eq(1),cutoff.capture()); assertThat(cutoff.getValue()).isBetween(before.minusMinutes(15),after.minusMinutes(15));
        verify(mapper,times(2)).update(update.capture(),eq(1));
        assertThat(update.getAllValues()).extracting(Orders::getId).containsExactly(7L,8L);
        for (Orders row : update.getAllValues()) {
            assertThat(row.getStatus()).isEqualTo(Orders.CANCELLED); assertThat(row.getCancelReason()).isEqualTo("订单超时未支付，系统自动取消");
            assertThat(row.getCancelTime()).isBetween(before,after); assertThat(row.getPayStatus()).isNull();
        }
    }
    @Test void deliveryCompletionUsesOneHourCutoffAndConditionalUpdate() {
        when(mapper.getByStatusAndOrderTime(eq(4),any())).thenReturn(Collections.singletonList(Orders.builder().id(7L).build()));
        LocalDateTime before = LocalDateTime.now(); task.processDeliveryOrders(); LocalDateTime after = LocalDateTime.now();
        verify(mapper).getByStatusAndOrderTime(eq(4),cutoff.capture()); assertThat(cutoff.getValue()).isBetween(before.minusHours(1),after.minusHours(1));
        verify(mapper).update(update.capture(),eq(4)); assertThat(update.getValue().getStatus()).isEqualTo(Orders.COMPLETED);
        assertThat(update.getValue().getDeliveryTime()).isBetween(before,after);
    }
    @ParameterizedTest @NullAndEmptySource
    void timeoutDoesNothingWithoutCandidates(List<Orders> rows) {
        when(mapper.getByStatusAndOrderTime(eq(1),any())).thenReturn(rows); task.processTimeOutOrders(); verify(mapper,never()).update(any(),anyInt());
    }
    @ParameterizedTest @NullAndEmptySource
    void deliveryDoesNothingWithoutCandidates(List<Orders> rows) {
        when(mapper.getByStatusAndOrderTime(eq(4),any())).thenReturn(rows); task.processDeliveryOrders(); verify(mapper,never()).update(any(),anyInt());
    }
    @Test void concurrentPaymentDoesNotStopProcessingRemainingTimeoutCandidates() {
        when(mapper.getByStatusAndOrderTime(eq(1),any())).thenReturn(Arrays.asList(Orders.builder().id(7L).build(),Orders.builder().id(8L).build()));
        when(mapper.update(any(),eq(1))).thenReturn(0,1); task.processTimeOutOrders(); verify(mapper,times(2)).update(any(),eq(1));
    }
}

package com.azure.service.impl;

import com.azure.entity.Orders;
import com.azure.mapper.OrdersMapper;
import com.azure.service.impl.OrdersServiceImpl;
import com.azure.support.ServiceTestSupport;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import org.mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCompletionTimeTest extends ServiceTestSupport {
    @InjectMocks private OrdersServiceImpl service;
    @Mock private OrdersMapper mapper;
    @Captor private ArgumentCaptor<Orders> update;
    @Test void completedOrderMustPersistDeliveryTime() {
        Orders row=Orders.builder().id(9L).userId(USER_ID).status(Orders.DELIVERY_IN_PROGRESS).payStatus(1).build();
        when(mapper.getById(9L)).thenReturn(row);
        when(mapper.update(any(),eq(Orders.DELIVERY_IN_PROGRESS))).thenReturn(1);
        LocalDateTime before=LocalDateTime.now();
        service.completeOrder(9L);
        verify(mapper).update(update.capture(),eq(Orders.DELIVERY_IN_PROGRESS));
        assertThat(update.getValue().getDeliveryTime()).isBetween(before,LocalDateTime.now());
    }
}

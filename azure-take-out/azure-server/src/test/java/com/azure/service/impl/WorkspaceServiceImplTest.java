package com.azure.service.impl;

import com.azure.entity.Orders;
import com.azure.mapper.*;
import com.azure.support.ServiceTestSupport;
import com.azure.vo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes","unchecked"})
class WorkspaceServiceImplTest extends ServiceTestSupport {
    @InjectMocks private WorkspaceServiceImpl service;
    @Mock private OrdersMapper ordersMapper;
    @Mock private UserMapper userMapper;
    @Mock private DishMapper dishMapper;
    @Mock private SetmealMapper setmealMapper;

    @Test void businessDataComputesTurnoverRateAverageAndNewUsers() {
        List<Map> filters = new ArrayList<>();
        when(ordersMapper.countByMap(any())).thenAnswer(i -> {
            Map filter = new HashMap((Map)i.getArgument(0)); filters.add(filter); return filter.get("status") == null ? 8 : 2;
        });
        when(ordersMapper.sumByMap(any())).thenReturn(100.0); when(userMapper.countByMap(any())).thenReturn(3);
        LocalDateTime begin = LocalDate.of(2026,10,8).atStartOfDay(); LocalDateTime end = begin.with(LocalTime.MAX);
        BusinessDataVO result = service.getBusinessData(begin,end);
        assertThat(result).extracting(BusinessDataVO::getTurnover,BusinessDataVO::getValidOrderCount,BusinessDataVO::getOrderCompletionRate,
                BusinessDataVO::getUnitPrice,BusinessDataVO::getNewUsers).containsExactly(100.0,2,0.25,50.0,3);
        assertThat(filters.get(0)).containsEntry("begin",begin).containsEntry("end",end).doesNotContainKey("status");
        assertThat(filters.get(1)).containsEntry("status",Orders.COMPLETED);
    }
    @ParameterizedTest @CsvSource({"0,0","8,0"})
    void emptyOrUncompletedPeriodsAvoidDivisionByZero(int total,int valid) {
        when(ordersMapper.countByMap(any())).thenReturn(total,valid);
        when(ordersMapper.sumByMap(any())).thenReturn(null); when(userMapper.countByMap(any())).thenReturn(0);
        BusinessDataVO result = service.getBusinessData(LocalDateTime.now().minusDays(1),LocalDateTime.now());
        assertThat(result.getTurnover()).isZero(); assertThat(result.getOrderCompletionRate()).isZero(); assertThat(result.getUnitPrice()).isZero();
    }
    @Test void orderOverviewUsesTodayAndEachStatusSeparately() {
        List<Map> filters = new ArrayList<>();
        when(ordersMapper.countByMap(any())).thenAnswer(i -> { Map filter = new HashMap((Map)i.getArgument(0)); filters.add(filter); return filters.size(); });
        OrderOverViewVO result = service.getOrderOverView();
        assertThat(result).extracting(OrderOverViewVO::getWaitingOrders,OrderOverViewVO::getDeliveredOrders,OrderOverViewVO::getCompletedOrders,
                OrderOverViewVO::getCancelledOrders,OrderOverViewVO::getAllOrders).containsExactly(1,2,3,4,5);
        assertThat(filters).extracting(f -> f.get("status")).containsExactly(2,3,5,6,null);
        for (Map filter : filters) assertThat(((LocalDateTime)filter.get("begin")).toLocalTime()).isEqualTo(LocalTime.MIN);
    }
    @Test void dishOverviewSeparatesSellingAndStopped() {
        List<Integer> statuses = new ArrayList<>();
        when(dishMapper.countByMap(any())).thenAnswer(i -> { statuses.add((Integer)((Map)i.getArgument(0)).get("status")); return statuses.size()+2; });
        DishOverViewVO result = service.getDishOverView(); assertThat(result.getSold()).isEqualTo(3); assertThat(result.getDiscontinued()).isEqualTo(4);
        assertThat(statuses).containsExactly(1,0);
    }
    @Test void setmealOverviewSeparatesSellingAndStopped() {
        List<Integer> statuses = new ArrayList<>();
        when(setmealMapper.countByMap(any())).thenAnswer(i -> { statuses.add((Integer)((Map)i.getArgument(0)).get("status")); return statuses.size()+4; });
        SetmealOverViewVO result = service.getSetmealOverView(); assertThat(result.getSold()).isEqualTo(5); assertThat(result.getDiscontinued()).isEqualTo(6);
        assertThat(statuses).containsExactly(1,0);
    }
}

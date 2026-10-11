package com.sky.web;

import com.sky.support.WebTestSupport;
import com.sky.vo.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportWorkspaceMvcTest extends WebTestSupport {
    private static final LocalDate BEGIN=LocalDate.of(2026,10,1),END=LocalDate.of(2026,10,8);
    @Test void allChartEndpointsBindIsoDateRangesAndReturnData() throws Exception {
        when(reportService.getTurnoverStatistic(BEGIN,END)).thenReturn(TurnoverReportVO.builder().dateList("2026-10-01").turnoverList("12.0").build());
        successful(admin(get("/admin/report/turnoverStatistics").param("begin",BEGIN.toString()).param("end",END.toString()))).andExpect(jsonPath("$.data.turnoverList").value("12.0"));
        when(reportService.getUserStatistic(BEGIN,END)).thenReturn(UserReportVO.builder().newUserList("1").totalUserList("2").build());
        successful(admin(get("/admin/report/userStatistics").param("begin",BEGIN.toString()).param("end",END.toString()))).andExpect(jsonPath("$.data.newUserList").value("1"));
        when(reportService.getOrderStatistic(BEGIN,END)).thenReturn(OrderReportVO.builder().orderCompletionRate(0.5).build());
        successful(admin(get("/admin/report/ordersStatistics").param("begin",BEGIN.toString()).param("end",END.toString()))).andExpect(jsonPath("$.data.orderCompletionRate").value(0.5));
        when(reportService.getSalesTop10(BEGIN,END)).thenReturn(SalesTop10ReportVO.builder().nameList("鸡丁").numberList("10").build());
        successful(admin(get("/admin/report/top10").param("begin",BEGIN.toString()).param("end",END.toString()))).andExpect(jsonPath("$.data.nameList").value("鸡丁"));
        verify(reportService).getTurnoverStatistic(BEGIN,END); verify(reportService).getUserStatistic(BEGIN,END);
        verify(reportService).getOrderStatistic(BEGIN,END); verify(reportService).getSalesTop10(BEGIN,END);
    }
    @Test void exportWritesServiceBytesDirectlyToResponse() throws Exception {
        doAnswer(i -> { jakarta.servlet.http.HttpServletResponse response=i.getArgument(0); response.getOutputStream().write(new byte[]{80,75,3,4}); return null; }).when(reportService).exportBusinessData(any());
        mvc.perform(admin(get("/admin/report/export"))).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{80,75,3,4}));
        verify(reportService).exportBusinessData(any());
    }
    @Test void workspaceUsesCurrentDayBoundaries() throws Exception {
        when(workspaceService.getBusinessData(any(),any())).thenAnswer(i -> {
            LocalDateTime begin=i.getArgument(0),end=i.getArgument(1);
            assertThat(begin.toLocalTime()).isEqualTo(LocalTime.MIN); assertThat(end.toLocalTime()).isEqualTo(LocalTime.MAX);
            assertThat(begin.toLocalDate()).isEqualTo(end.toLocalDate());
            return BusinessDataVO.builder().turnover(42.0).newUsers(2).build();
        });
        successful(admin(get("/admin/workspace/businessData"))).andExpect(jsonPath("$.data.turnover").value(42.0));
    }
    @Test void workspaceOverviewEndpointsReturnTheirOwnCounters() throws Exception {
        when(workspaceService.getOrderOverView()).thenReturn(OrderOverViewVO.builder().allOrders(8).build());
        when(workspaceService.getDishOverView()).thenReturn(DishOverViewVO.builder().sold(4).discontinued(2).build());
        when(workspaceService.getSetmealOverView()).thenReturn(SetmealOverViewVO.builder().sold(3).discontinued(1).build());
        successful(admin(get("/admin/workspace/overviewOrders"))).andExpect(jsonPath("$.data.allOrders").value(8));
        successful(admin(get("/admin/workspace/overviewDishes"))).andExpect(jsonPath("$.data.sold").value(4));
        successful(admin(get("/admin/workspace/overviewSetmeals"))).andExpect(jsonPath("$.data.discontinued").value(1));
    }
}

package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.exception.BaseException;
import com.sky.entity.Orders;
import com.sky.mapper.*;
import com.sky.service.WorkspaceService;
import com.sky.support.ServiceTestSupport;
import com.sky.vo.*;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportServiceImplTest extends ServiceTestSupport {
    @InjectMocks private ReportServiceImpl service;
    @Mock private OrdersMapper ordersMapper;
    @Mock private UserMapper userMapper;
    @Mock private OrderDetailMapper orderDetailMapper;
    @Mock private WorkspaceService workspaceService;
    private static final LocalDate BEGIN = LocalDate.of(2026,9,30);
    private static final LocalDate END = BEGIN.plusDays(2);

    @Test void turnoverIncludesEveryDateAcrossMonthBoundaryAndNormalizesNull() {
        when(ordersMapper.getTurnoverByTime(any(),any())).thenReturn(10.5,null,20.0);
        TurnoverReportVO result = service.getTurnoverStatistic(BEGIN,END);
        assertThat(result.getDateList()).isEqualTo("2026-09-30,2026-10-01,2026-10-02");
        assertThat(result.getTurnoverList()).isEqualTo("10.5,0.0,20.0");
        for (int i=0;i<3;i++) verify(ordersMapper).getTurnoverByTime(BEGIN.plusDays(i).atStartOfDay(),BEGIN.plusDays(i).atTime(LocalTime.MAX));
    }
    @Test void oneDayTurnoverHasNoTrailingComma() {
        when(ordersMapper.getTurnoverByTime(any(),any())).thenReturn(12.0);
        TurnoverReportVO result = service.getTurnoverStatistic(BEGIN,BEGIN);
        assertThat(result.getDateList()).isEqualTo("2026-09-30"); assertThat(result.getTurnoverList()).isEqualTo("12.0");
    }
    @Test void userStatisticsSeparateCumulativeAndNewUsers() {
        when(userMapper.getTotalUserByTime(any())).thenReturn(10,12,null);
        when(userMapper.getNewUserByTime(any(),any())).thenReturn(1,2,null);
        UserReportVO result = service.getUserStatistic(BEGIN,END);
        assertThat(result.getDateList()).isEqualTo("2026-09-30,2026-10-01,2026-10-02");
        assertThat(result.getTotalUserList()).isEqualTo("10,12,0"); assertThat(result.getNewUserList()).isEqualTo("1,2,0");
        verify(userMapper).getNewUserByTime(BEGIN.atStartOfDay(),BEGIN.atTime(LocalTime.MAX));
        verify(userMapper).getTotalUserByTime(END.atTime(LocalTime.MAX));
    }
    @Test void orderStatisticsUseCompletedOrdersAndWeightedOverallRate() {
        when(ordersMapper.getTotalOrderCountByTimeAndStatus(any(),any(),isNull())).thenReturn(10,2,null);
        when(ordersMapper.getTotalOrderCountByTimeAndStatus(any(),any(),eq(Orders.COMPLETED))).thenReturn(3,2,null);
        OrderReportVO result = service.getOrderStatistic(BEGIN,END);
        assertThat(result.getDateList()).isEqualTo("2026-09-30,2026-10-01,2026-10-02");
        assertThat(result.getOrderCountList()).isEqualTo("10,2,0"); assertThat(result.getValidOrderCountList()).isEqualTo("3,2,0");
        assertThat(result.getTotalOrderCount()).isEqualTo(12); assertThat(result.getValidOrderCount()).isEqualTo(5);
        assertThat(result.getOrderCompletionRate()).isCloseTo(5.0/12,within(1e-12));
    }
    @Test void nonemptyPeriodWithoutCompletedOrdersHasZeroCompletionRate() {
        when(ordersMapper.getTotalOrderCountByTimeAndStatus(any(),any(),isNull())).thenReturn(5);
        when(ordersMapper.getTotalOrderCountByTimeAndStatus(any(),any(),eq(Orders.COMPLETED))).thenReturn(0);
        assertThat(service.getOrderStatistic(BEGIN,BEGIN).getOrderCompletionRate()).isZero();
    }
    @Test void zeroOrdersHaveFullCompletionRateUnderCurrentBusinessRule() {
        when(ordersMapper.getTotalOrderCountByTimeAndStatus(any(),any(),any())).thenReturn(0);
        OrderReportVO result=service.getOrderStatistic(BEGIN,BEGIN);
        assertThat(result.getTotalOrderCount()).isZero();
        assertThat(result.getValidOrderCount()).isZero();
        assertThat(result.getOrderCompletionRate()).isEqualTo(1.0);
    }
    @ParameterizedTest @ValueSource(strings={"turnover","users","orders"})
    void reversedDateRangeIsRejectedBeforeDatabaseAccess(String kind) {
        assertThatThrownBy(() -> {
            switch(kind) {
                case "turnover": service.getTurnoverStatistic(END,BEGIN); break;
                case "users": service.getUserStatistic(END,BEGIN); break;
                case "orders": service.getOrderStatistic(END,BEGIN); break;
                default: throw new IllegalArgumentException(kind);
            }
        }).isInstanceOf(BaseException.class);
        verifyNoInteractions(ordersMapper,userMapper,orderDetailMapper);
    }
    @Test void salesTopTenPreservesDatabaseRankingAndUsesWholeDateRange() {
        GoodsSalesDTO first = new GoodsSalesDTO(); first.setName("鸡丁"); first.setNumber(30);
        GoodsSalesDTO second = new GoodsSalesDTO(); second.setName("米饭"); second.setNumber(20);
        when(orderDetailMapper.getSalesTop10(BEGIN.atStartOfDay(),END.atTime(LocalTime.MAX))).thenReturn(Arrays.asList(first,second));
        SalesTop10ReportVO result = service.getSalesTop10(BEGIN,END);
        assertThat(result.getNameList()).isEqualTo("鸡丁,米饭"); assertThat(result.getNumberList()).isEqualTo("30,20");
    }
    @Test void emptySalesReturnsEmptyLists() {
        when(orderDetailMapper.getSalesTop10(any(),any())).thenReturn(Collections.emptyList());
        SalesTop10ReportVO result = service.getSalesTop10(BEGIN,END);
        assertThat(result.getNameList()).isEmpty(); assertThat(result.getNumberList()).isEmpty();
    }
    @Test void excelExportCanBeReadAndContainsSummaryAndAllThirtyDailyRows() throws Exception {
        List<LocalDateTime> starts = new ArrayList<>(); List<LocalDateTime> ends = new ArrayList<>();
        when(workspaceService.getBusinessData(any(),any())).thenAnswer(i -> {
            starts.add(i.getArgument(0)); ends.add(i.getArgument(1));
            return BusinessDataVO.builder().turnover(123.5).validOrderCount(4).orderCompletionRate(0.5).unitPrice(30.875).newUsers(2).build();
        });
        MockHttpServletResponse response = new MockHttpServletResponse(); service.exportBusinessData(response);
        assertThat(response.getContentAsByteArray()).isNotEmpty(); assertThat(starts).hasSize(31);
        LocalDateTime first = starts.get(0); LocalDateTime last = ends.get(0);
        assertThat(first.toLocalTime()).isEqualTo(LocalTime.MIN); assertThat(last.toLocalTime()).isEqualTo(LocalTime.MAX);
        assertThat(last.toLocalDate()).isEqualTo(first.toLocalDate().plusDays(29));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("时间:"+first+"至"+last);
            assertThat(sheet.getRow(3).getCell(2).getNumericCellValue()).isEqualTo(123.5);
            assertThat(sheet.getRow(3).getCell(4).getNumericCellValue()).isEqualTo(0.5);
            assertThat(sheet.getRow(3).getCell(6).getNumericCellValue()).isEqualTo(2);
            assertThat(sheet.getRow(4).getCell(2).getNumericCellValue()).isEqualTo(4);
            assertThat(sheet.getRow(4).getCell(4).getNumericCellValue()).isEqualTo(30.875);
            for (int i=0;i<30;i++) {
                assertThat(starts.get(i+1)).isEqualTo(first.plusDays(i));
                assertThat(ends.get(i+1)).isEqualTo(first.plusDays(i).with(LocalTime.MAX));
                assertThat(sheet.getRow(i+7).getCell(1).getStringCellValue()).isEqualTo(first.plusDays(i).toString());
                assertThat(sheet.getRow(i+7).getCell(2).getNumericCellValue()).isEqualTo(123.5);
                assertThat(sheet.getRow(i+7).getCell(3).getNumericCellValue()).isEqualTo(4);
                assertThat(sheet.getRow(i+7).getCell(4).getNumericCellValue()).isEqualTo(0.5);
                assertThat(sheet.getRow(i+7).getCell(5).getNumericCellValue()).isEqualTo(30.875);
                assertThat(sheet.getRow(i+7).getCell(6).getNumericCellValue()).isEqualTo(2);
            }
        }
    }
    @Test void exportPropagatesOutputFailure() throws Exception {
        when(workspaceService.getBusinessData(any(),any())).thenReturn(BusinessDataVO.builder().turnover(0.0).validOrderCount(0).orderCompletionRate(0.0).unitPrice(0.0).newUsers(0).build());
        HttpServletResponse response = mock(HttpServletResponse.class); when(response.getOutputStream()).thenThrow(new IOException("output failed"));
        assertThatThrownBy(() -> service.exportBusinessData(response)).isInstanceOf(RuntimeException.class).hasCauseInstanceOf(IOException.class);
    }
}

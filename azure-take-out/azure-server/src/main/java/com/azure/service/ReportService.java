package com.azure.service;

import com.azure.vo.OrderReportVO;
import com.azure.vo.SalesTop10ReportVO;
import com.azure.vo.TurnoverReportVO;
import com.azure.vo.UserReportVO;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;

public interface ReportService {

    /**
     * 统计指定时间段内的营业额数据
     */
    TurnoverReportVO getTurnoverStatistic(LocalDate begin, LocalDate end);

    /**
     * 统计指定时间段内的用户数据
     * @param begin 起始时间
     * @param end   结束时间
     * @return      统计结果
     */
    UserReportVO getUserStatistic(LocalDate begin, LocalDate end);

    /**
     * 统计指定时间段内的订单数据
     * @param begin 起始时间
     * @param end   结束时间
     * @return      统计结果
     */
    OrderReportVO getOrderStatistic(LocalDate begin, LocalDate end);

    /**
     * 统计指定时间段内销量排名前十的菜品及其销量
     * @param begin 起始时间
     * @param end   结束时间
     * @return    统计结果
     */
    SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end);

    /**
     * 导出运营数据报表
     */
    void exportBusinessData(HttpServletResponse response);
}

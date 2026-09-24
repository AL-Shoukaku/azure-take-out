package com.sky.service;

import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

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
}

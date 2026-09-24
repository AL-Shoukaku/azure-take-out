package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrdersMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public TurnoverReportVO getTurnoverStatistic(LocalDate begin, LocalDate end) {
        // 参数准备，包括日期列表和营业额列表
        String dateList = "";
        String turnoverList = "";
        // 遍历指定时间段内的每一天
        LocalDate currentDate = begin;
        while (true) {
            // 将日期转换为字符串并添加到日期列表中
            dateList += currentDate.toString() + ",";

            // 获取当天的时间数据
            LocalDateTime beginTime = LocalDateTime.of(currentDate, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(currentDate, LocalTime.MAX);

            // 获取当天的营业额数据并插入到营业额列表
            Double turnover = ordersMapper.getTurnoverByTime(beginTime, endTime);
            if (turnover == null) {
                turnover = 0.0;
            }
            turnoverList += turnover + ",";
            if (currentDate.equals(end)) {
                break;
            }
            currentDate = currentDate.plusDays(1);
        }
        dateList = dateList.substring(0, dateList.length() - 1);
        turnoverList = turnoverList.substring(0, turnoverList.length() - 1);// 去掉最后一个逗号


        return TurnoverReportVO.builder()
                .dateList(dateList)
                .turnoverList(turnoverList)
                .build();
    }

    public UserReportVO getUserStatistic(LocalDate begin, LocalDate end) {
        // 参数准备，包括日期列表，总用户和新增用户列表
        String dateList = "";
        String newUserList = "";
        String totalUserList = "";
        // 遍历指定时间段内的每一天
        LocalDate currentDate = begin;
        while (true) {
            // 将日期转换为字符串并添加到日期列表中
            dateList += currentDate.toString() + ",";

            // 获取当天的时间数据
            LocalDateTime beginTime = LocalDateTime.of(currentDate, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(currentDate, LocalTime.MAX);

            // 获取当天的用户总量
            Integer totalUser = userMapper.getTotalUserByTime(endTime);
            if (totalUser == null) {
                totalUser = 0;
            }
            totalUserList += totalUser + ",";

            // 获取当天的新增用户量
            Integer newUser = userMapper.getNewUserByTime(beginTime, endTime);
            if (newUser == null) {
                newUser = 0;
            }
            newUserList += newUser + ",";

            if (currentDate.equals(end)) {
                break;
            }
            currentDate = currentDate.plusDays(1);
        }
        dateList = dateList.substring(0, dateList.length() - 1);
        newUserList = newUserList.substring(0, newUserList.length() - 1);
        totalUserList = totalUserList.substring(0, totalUserList.length() - 1);// 去掉最后一个逗号

        return UserReportVO.builder()
                .dateList(dateList)
                .newUserList(newUserList)
                .totalUserList(totalUserList)
                .build();
    }
}

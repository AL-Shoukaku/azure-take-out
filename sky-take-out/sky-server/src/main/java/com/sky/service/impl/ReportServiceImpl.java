package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.exception.BaseException;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrdersMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
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

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 统计指定时间段内的营业额数据
     */
    @Override
    public TurnoverReportVO getTurnoverStatistic(LocalDate begin, LocalDate end) {
        // 检验 begin 和 end 是否为 null 以及 begin 是否在 end 之前
        if (begin == null || end == null || begin.isAfter(end)) {
            throw new BaseException("日期不能为空，且开始日期不能晚于结束日期");
        }
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

    /**
     * 统计指定时间段内的用户数据
     * @param begin 起始时间
     * @param end   结束时间
     * @return      统计结果
     */
    @Override
    public UserReportVO getUserStatistic(LocalDate begin, LocalDate end) {
        if (begin == null || end == null || begin.isAfter(end)) {
            throw new BaseException("日期不能为空，且开始日期不能晚于结束日期");
        }
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

    /**
     * 统计指定时间段内的订单数据
     * @param begin 起始时间
     * @param end   结束时间
     * @return      统计结果
     */
    @Override
    public OrderReportVO getOrderStatistic(LocalDate begin, LocalDate end) {
        if (begin == null || end == null || begin.isAfter(end)) {
            throw new BaseException("日期不能为空，且开始日期不能晚于结束日期");
        }
        // 参数准备，包括日期列表，总用户和新增用户列表
        String dateList = "";
        String orderCountList = "";
        String validOrderCountList = "";
        Integer totalOrderCount = 0;
        Integer validOrderCount = 0;
        Double orderCompletionRate = 0.0;
        // 遍历指定时间段内的每一天
        LocalDate currentDate = begin;
        while (true) {
            // 将日期转换为字符串并添加到日期列表中
            dateList += currentDate.toString() + ",";

            // 获取当天的时间数据
            LocalDateTime beginTime = LocalDateTime.of(currentDate, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(currentDate, LocalTime.MAX);

            // 获取当天的订单总量,更新总订单数和对应列表
            Integer totalOrderCountForDay = ordersMapper.getTotalOrderCountByTimeAndStatus(beginTime, endTime, null);
            if (totalOrderCountForDay == null) {
                totalOrderCountForDay = 0;
            }
            orderCountList += totalOrderCountForDay + ",";
            totalOrderCount += totalOrderCountForDay;

            // 获取当天的有效订单量,更新有效订单数和对应列表
            Integer validOrderCountForDay = ordersMapper.getTotalOrderCountByTimeAndStatus(beginTime, endTime, Orders.COMPLETED);
            if (validOrderCountForDay == null) {
                validOrderCountForDay = 0;
            }
            validOrderCountList += validOrderCountForDay + ",";
            validOrderCount += validOrderCountForDay;

            if (currentDate.equals(end)) {
                break;
            }
            currentDate = currentDate.plusDays(1);
        }
        // 去掉最后一个逗号，计算订单完成率
        dateList = dateList.substring(0, dateList.length() - 1);
        orderCountList = orderCountList.substring(0, orderCountList.length() - 1);
        validOrderCountList = validOrderCountList.substring(0, validOrderCountList.length() - 1);
        orderCompletionRate = (totalOrderCount == 0) ? 1.0 : (double) validOrderCount / totalOrderCount;


        return OrderReportVO.builder()
                .dateList(dateList)
                .orderCountList(orderCountList)
                .validOrderCountList(validOrderCountList)
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 统计指定时间段内销量排名前十的菜品及其销量
     * @param begin 起始时间
     * @param end   结束时间
     * @return    统计结果
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        if (begin == null || end == null || begin.isAfter(end)) {
            throw new BaseException("日期不能为空，且开始日期不能晚于结束日期");
        }
        // top10商品的名称列表和销量列表
        String nameList = "";
        String numberList = "";

        // 获取指定时间段内销量排名前十的商品列表
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> salesTop10 = orderDetailMapper.getSalesTop10(beginTime, endTime);

        for (GoodsSalesDTO goodsSalesDTO : salesTop10) {
            nameList += goodsSalesDTO.getName() + ",";
            numberList += goodsSalesDTO.getNumber() + ",";
        }
        // 去掉最后一个逗号
        if (!nameList.isEmpty()) {
            nameList = nameList.substring(0, nameList.length() - 1);
        }
        if (!numberList.isEmpty()) {
            numberList = numberList.substring(0, numberList.length() - 1);
        }

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    /**
     * 导出运营数据报表
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) {
        // 查询数据库获取营业数据
        LocalDateTime begin = LocalDateTime.of(LocalDate.now().minusDays(30), LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.MAX);

        BusinessDataVO businessData = workspaceService.getBusinessData(begin, end);

        // 将数据写入Excel文件
        // 先建立一个Excel文件对象，然后将数据写入到Excel文件中
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try {
            XSSFWorkbook excel = new XSSFWorkbook(in);
            // 填充数据
            Sheet sheet = excel.getSheetAt(0);
            sheet.getRow(1).getCell(1).setCellValue("时间:" + begin + "至" + end);
            sheet.getRow(3).getCell(2).setCellValue(businessData.getTurnover());
            sheet.getRow(3).getCell(4).setCellValue(businessData.getOrderCompletionRate());
            sheet.getRow(3).getCell(6).setCellValue(businessData.getNewUsers());
            sheet.getRow(4).getCell(2).setCellValue(businessData.getValidOrderCount());
            sheet.getRow(4).getCell(4).setCellValue(businessData.getUnitPrice());

            for (int i = 7; i < 37; i++) {
                sheet.getRow(i).getCell(1).setCellValue(begin.plusDays(i - 7).toString());
                LocalDateTime currentBegin = begin.plusDays(i - 7);
                LocalDateTime currentEnd = currentBegin.with(LocalTime.MAX);
                BusinessDataVO currentBusinessData = workspaceService.getBusinessData(currentBegin, currentEnd);
                sheet.getRow(i).getCell(2).setCellValue(currentBusinessData.getTurnover());
                sheet.getRow(i).getCell(3).setCellValue(currentBusinessData.getValidOrderCount());
                sheet.getRow(i).getCell(4).setCellValue(currentBusinessData.getOrderCompletionRate());
                sheet.getRow(i).getCell(5).setCellValue(currentBusinessData.getUnitPrice());
                sheet.getRow(i).getCell(6).setCellValue(currentBusinessData.getNewUsers());
            }


            // 通过输出流将 Excel 文件下载到浏览器
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);
            excel.close();
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }
}

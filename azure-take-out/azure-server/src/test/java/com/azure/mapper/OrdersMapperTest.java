package com.azure.mapper;

import com.github.pagehelper.*;
import com.azure.dto.OrdersPageQueryDTO;
import com.azure.entity.Orders;
import com.azure.support.SqlTestSupport;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OrdersMapperTest extends SqlTestSupport {
    @Test void ownedQueriesCannotReturnAnotherUsersOrderOrOrdersWithoutIdentity() {
        OrdersMapper mapper = mapper(OrdersMapper.class);
        assertThat(mapper.getByIdAndUserId(1L,USER_ID).getId()).isEqualTo(1L);
        assertThat(mapper.getByIdAndUserId(3L,USER_ID)).isNull();
        assertThat(mapper.getByNumberAndUserId("order-1",USER_ID).getId()).isEqualTo(1L);
        assertThat(mapper.getByNumberAndUserId("order-3",USER_ID)).isNull();
        assertThat(mapper.getByIdAndUserId(1L,null)).isNull();
        PageHelper.startPage(1,10); assertThat(mapper.pageQuery(null,null)).isEmpty();
    }
    @Test void userUpdateRequiresBothOwnerAndExpectedStatus() {
        OrdersMapper mapper = mapper(OrdersMapper.class);
        Orders change = Orders.builder().id(1L).status(6).build();
        assertThat(mapper.updateForUser(change,1,43L)).isZero();
        assertThat(mapper.updateForUser(change,2,USER_ID)).isZero();
        assertThat(mapper.updateForUser(change,1,null)).isZero();
        assertThat(mapper.getById(1L).getStatus()).isEqualTo(1);
        assertThat(mapper.updateForUser(change,1,USER_ID)).isEqualTo(1);
        assertThat(mapper.updateForUser(change,1,USER_ID)).isZero();
        assertThat(mapper.getById(1L).getStatus()).isEqualTo(6);
    }
    @Test void insertBackfillsIdAndRoundTripsOrderFields() {
        Orders row = Orders.builder().number("unique-new-order").userId(USER_ID).addressBookId(1L).status(1).payStatus(0).payMethod(1)
                .orderTime(LocalDateTime.of(2026,10,8,12,30)).amount(new BigDecimal("42.00")).phone("13800000000")
                .consignee("张三").address("测试路1号").remark("少盐").deliveryStatus(1).tablewareStatus(1).packAmount(3).tablewareNumber(2).build();
        OrdersMapper mapper = mapper(OrdersMapper.class); mapper.submitOrder(row); assertThat(row.getId()).isNotNull();
        Orders saved = mapper.getByNumber("unique-new-order"); assertThat(saved.getId()).isEqualTo(row.getId());
        assertThat(saved.getAmount()).isEqualByComparingTo("42.00"); assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getRemark()).isEqualTo("少盐"); assertThat(saved.getPackAmount()).isEqualTo(3);
        assertThat(saved.getTablewareNumber()).isEqualTo(2); assertThat(saved.getOrderTime()).isEqualTo(row.getOrderTime());
    }
    @Test void duplicateOrderNumberIsRejectedByDatabaseConstraint() {
        Orders row = mapper(OrdersMapper.class).getById(1L); row.setId(null);
        assertThatThrownBy(() -> mapper(OrdersMapper.class).submitOrder(row)).hasRootCauseInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
    }
    @Test void conditionalUpdateWinsOnlyOnceAndPreservesUnspecifiedColumns() {
        OrdersMapper mapper = mapper(OrdersMapper.class);
        Orders paid = Orders.builder().id(1L).status(2).payStatus(1).checkoutTime(LocalDateTime.of(2026,10,8,12,30)).build();
        assertThat(mapper.update(paid,1)).isEqualTo(1);
        assertThat(mapper.update(Orders.builder().id(1L).status(6).build(),1)).isZero();
        Orders saved = mapper.getById(1L); assertThat(saved.getStatus()).isEqualTo(2); assertThat(saved.getPayStatus()).isEqualTo(1);
        assertThat(saved.getAmount()).isEqualByComparingTo("42.00"); assertThat(saved.getCheckoutTime()).isEqualTo(paid.getCheckoutTime());
    }
    @Test void cancellationPersistsReasonTimeAndRefund() {
        OrdersMapper mapper = mapper(OrdersMapper.class); Orders update = Orders.builder().id(2L).status(6).payStatus(2)
                .cancelReason("停业").rejectionReason("售罄").cancelTime(LocalDateTime.of(2026,10,8,12,30)).deliveryTime(LocalDateTime.of(2026,10,8,13,0)).build();
        assertThat(mapper.update(update,5)).isEqualTo(1); Orders saved = mapper.getById(2L);
        assertThat(saved.getCancelReason()).isEqualTo("停业"); assertThat(saved.getRejectionReason()).isEqualTo("售罄");
        assertThat(saved.getCancelTime()).isEqualTo(update.getCancelTime()); assertThat(saved.getDeliveryTime()).isEqualTo(update.getDeliveryTime());
    }
    @Test void historyFiltersUserStatusSortsAndPaginates() {
        PageHelper.startPage(1,1); Page<Orders> page = mapper(OrdersMapper.class).pageQuery(42L,null);
        assertThat(page.getTotal()).isEqualTo(3); assertThat(page).extracting(Orders::getNumber).containsExactly("order-4");
        PageHelper.startPage(1,10); assertThat(mapper(OrdersMapper.class).pageQuery(42L,5)).extracting(Orders::getNumber).containsExactly("order-2");
    }
    @Test void adminSearchCombinesEveryFilter() {
        OrdersPageQueryDTO dto = new OrdersPageQueryDTO(); dto.setNumber("order-"); dto.setPhone("00000000"); dto.setStatus(5); dto.setUserId(42L);
        dto.setBeginTime(LocalDateTime.of(2026,10,2,0,0)); dto.setEndTime(LocalDateTime.of(2026,10,3,23,59,59));
        PageHelper.startPage(1,10); assertThat(mapper(OrdersMapper.class).adminPageSearch(dto)).extracting(Orders::getId).containsExactly(2L);
    }
    @Test void timeoutCutoffIsStrictAndStatusIsRequired() {
        OrdersMapper mapper = mapper(OrdersMapper.class); LocalDateTime boundary = LocalDateTime.of(2026,10,1,0,0);
        assertThat(mapper.getByStatusAndOrderTime(1,boundary)).isEmpty();
        assertThat(mapper.getByStatusAndOrderTime(1,boundary.plusSeconds(1))).extracting(Orders::getId).containsExactly(1L);
        assertThat(mapper.getByStatusAndOrderTime(4,boundary.plusDays(10))).isEmpty();
    }
    @Test void turnoverCountsOnlyCompletedOrdersAndIncludesDateBoundaries() {
        OrdersMapper mapper = mapper(OrdersMapper.class);
        assertThat(mapper.getTurnoverByTime(LocalDateTime.of(2026,10,2,12,0),LocalDateTime.of(2026,10,3,12,0))).isEqualTo(150.0);
        assertThat(mapper.getTotalOrderCountByTimeAndStatus(null,null,null)).isEqualTo(4);
        assertThat(mapper.countByStatus(5)).isEqualTo(2); assertThat(mapper.countByStatus(null)).isEqualTo(4);
        assertThat(mapper.getTurnoverByTime(LocalDateTime.of(2026,11,1,0,0),LocalDateTime.of(2026,11,2,0,0))).isNull();
    }
    @Test void mapAggregatesRespectDatesAndStatus() {
        Map<String,Object> filter = new HashMap<>(); filter.put("begin",LocalDateTime.of(2026,10,2,0,0)); filter.put("end",LocalDateTime.of(2026,10,3,23,59,59)); filter.put("status",5);
        assertThat(mapper(OrdersMapper.class).countByMap(filter)).isEqualTo(2); assertThat(mapper(OrdersMapper.class).sumByMap(filter)).isEqualTo(150.0);
    }
}

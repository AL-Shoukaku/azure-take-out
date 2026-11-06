package com.azure.mapper;

import com.azure.dto.coupon.CouponPageQueryDTO;
import com.azure.entity.Coupon;
import com.azure.support.SqlTestSupport;
import com.azure.vo.coupon.CouponVO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/** 执行真实 Mapper 注解和 XML，每个用例结束后回滚。 */
class CouponMapperTest extends SqlTestSupport {
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 10, 0);

    private Coupon insert(String name, int type, int status, LocalDateTime end) {
        Coupon row = new Coupon();
        row.setName(name);
        row.setType(type);
        row.setTotalStock(100);
        row.setStock(80);
        row.setDiscountAmount(20.5);
        row.setThresholdAmount(type == 1 ? 0.0 : 50.0);
        row.setPrice(type == 1 ? 9.9 : 0.0);
        row.setStatus(status);
        row.setStartTime(START);
        row.setEndTime(end);
        row.setValidTime(30);
        row.setCreateTime(START.minusDays(1));
        row.setUpdateTime(START);
        mapper(CouponMapper.class).insertCoupon(row);
        CouponPageQueryDTO filter = new CouponPageQueryDTO();
        filter.setName(name);
        row.setId(query(filter).getFirst().getId());
        return row;
    }

    private Page<CouponVO> query(CouponPageQueryDTO filter) {
        PageHelper.startPage(filter.getPage(), filter.getPageSize());
        return mapper(CouponMapper.class).pageQuery(filter);
    }

    @Test
    void insertAndDetailRoundTripAllFields() {
        Coupon row = insert("代金券", 1, 0, START.plusDays(10));
        assertThat(mapper(CouponMapper.class).selectById(row.getId()))
                .usingRecursiveComparison().isEqualTo(row);
        assertThat(mapper(CouponMapper.class).selectById(Long.MAX_VALUE)).isNull();
    }

    @Test
    void partialUpdatePreservesInventoryAmountsActivityAndCreationTime() {
        Coupon original = insert("待修改券", 1, 0, START.plusDays(10));
        Coupon change = new Coupon();
        change.setId(original.getId());
        change.setName("新名称");
        change.setUpdateTime(START.plusHours(1));

        assertThat(mapper(CouponMapper.class).updateCoupon(change)).isEqualTo(1);

        original.setName(change.getName());
        original.setUpdateTime(change.getUpdateTime());
        assertThat(mapper(CouponMapper.class).selectById(original.getId()))
                .usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void dynamicUpdateSupportsAllNonNullFieldsIncludingZero() {
        Coupon original = insert("全部更新券", 1, 1, START.plusDays(10));
        Coupon change = new Coupon();
        change.setId(original.getId());
        change.setName("更新后的券");
        change.setType(2);
        change.setTotalStock(200);
        change.setStock(0);
        change.setDiscountAmount(15.5);
        change.setThresholdAmount(60.0);
        change.setPrice(0.0);
        change.setStatus(0);
        change.setStartTime(START.plusDays(1));
        change.setEndTime(START.plusDays(20));
        change.setValidTime(7);
        change.setCreateTime(START.minusDays(2));
        change.setUpdateTime(START.plusDays(1));

        assertThat(mapper(CouponMapper.class).updateCoupon(change)).isEqualTo(1);
        assertThat(mapper(CouponMapper.class).selectById(original.getId()))
                .usingRecursiveComparison().isEqualTo(change);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void statusUpdateRefreshesAuditTimeAndPreservesOtherFields(int status) throws Exception {
        Coupon original = insert("状态券", 1, 1 - status, START.plusDays(10));
        LocalDateTime before = databaseNow();
        assertThat(mapper(CouponMapper.class).updateStatus(original.getId(), status)).isEqualTo(1);
        Coupon saved = mapper(CouponMapper.class).selectById(original.getId());
        assertThat(saved).usingRecursiveComparison().ignoringFields("status", "updateTime").isEqualTo(original);
        assertThat(saved.getStatus()).isEqualTo(status);
        assertThat(saved.getUpdateTime()).isBetween(before, databaseNow());
    }

    @Test
    void updatesOfMissingCouponReportZeroRows() {
        Coupon change = new Coupon();
        change.setId(Long.MAX_VALUE);
        change.setName("不存在");
        assertThat(mapper(CouponMapper.class).updateCoupon(change)).isZero();
        assertThat(mapper(CouponMapper.class).updateStatus(Long.MAX_VALUE, 1)).isZero();
    }

    @Test
    void pageFiltersCombineAndSortByEndTimeDescending() {
        Coupon first = insert("午餐代金券甲", 1, 1, START.plusDays(10));
        Coupon second = insert("午餐代金券乙", 1, 1, START.plusDays(20));
        insert("午餐停售券", 1, 0, START.plusDays(30));
        insert("午餐满减券", 2, 1, START.plusDays(40));
        insert("晚餐代金券", 1, 1, START.plusDays(50));
        CouponPageQueryDTO filter = new CouponPageQueryDTO();
        filter.setName("午餐");
        filter.setType(1);
        filter.setStatus(1);
        filter.setPageSize(1);

        Page<CouponVO> page = query(filter);
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page).extracting(CouponVO::getId).containsExactly(second.getId());
        filter.setPage(2);
        assertThat(query(filter)).extracting(CouponVO::getId).containsExactly(first.getId());
        filter.setPage(3);
        Page<CouponVO> empty = query(filter);
        assertThat(empty.getTotal()).isEqualTo(2);
        assertThat(empty).isEmpty();
    }

    @Test
    void activityFiltersIncludeStartAndExcludeEndAtExactDatabaseTime() throws Exception {
        // 临界值用例临时采用 H2 事务内固定的 NOW()，让写入和筛选使用完全相同的时刻。
        try (var statement = session.getConnection().createStatement()) {
            statement.execute("SET MODE REGULAR");
            try {
                Coupon future = insert("未开始", 1, 0, START.plusDays(1));
                Coupon active = insert("进行中", 2, 0, START.plusDays(1));
                Coupon ended = insert("已结束", 1, 1, START.plusDays(1));
                LocalDateTime now = databaseNow();
                setActivity(future.getId(), "DATEADD('DAY', 1, NOW())", "DATEADD('DAY', 2, NOW())");
                setActivity(active.getId(), "NOW()", "DATEADD('DAY', 1, NOW())");
                setActivity(ended.getId(), "DATEADD('DAY', -1, NOW())", "NOW()");
                assertThat(mapper(CouponMapper.class).selectById(active.getId()).getStartTime()).isEqualTo(now);
                assertThat(mapper(CouponMapper.class).selectById(ended.getId()).getEndTime()).isEqualTo(now);

                long[] expected = {future.getId(), active.getId(), ended.getId()};
                for (int activity = 0; activity <= 2; activity++) {
                    CouponPageQueryDTO filter = new CouponPageQueryDTO();
                    filter.setActivityStatus(activity);
                    assertThat(query(filter)).extracting(CouponVO::getId).containsExactly(expected[activity]);
                }
                assertThat(databaseNow()).isEqualTo(now);
            } finally {
                // SET MODE 会提交事务，恢复模式前先回滚，避免测试数据影响后续用例。
                session.rollback();
                statement.execute("SET MODE MySQL");
            }
        }
    }

    private LocalDateTime databaseNow() throws Exception {
        try (var statement = session.getConnection().createStatement();
             var result = statement.executeQuery("SELECT NOW()")) {
            assertThat(result.next()).isTrue();
            return result.getTimestamp(1).toLocalDateTime();
        }
    }

    private void setActivity(long id, String start, String end) throws Exception {
        try (PreparedStatement statement = session.getConnection().prepareStatement(
                "UPDATE coupon SET start_time = " + start + ", end_time = " + end + " WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
        session.clearCache();
    }

    @Test
    void unfilteredPageIncludesBothTypesAndStatusesAndMissingNameReturnsEmpty() {
        insert("普通代金券", 1, 0, START.plusDays(1));
        insert("普通满减券", 2, 1, START.plusDays(2));
        CouponPageQueryDTO filter = new CouponPageQueryDTO();
        assertThat(query(filter).getTotal()).isEqualTo(2);
        filter.setName("没有匹配项");
        Page<CouponVO> empty = query(filter);
        assertThat(empty.getTotal()).isZero();
        assertThat(empty).isEmpty();
    }
}

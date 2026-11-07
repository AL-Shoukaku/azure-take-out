package com.azure.mapper;

import com.azure.dto.coupon.CouponOrderPageQueryDTO;
import com.azure.entity.CouponOrder;
import com.azure.support.SqlTestSupport;
import com.azure.vo.coupon.CouponOrderPageQueryVO;
import com.azure.vo.coupon.CouponOrderVO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/** 执行真实 Mapper 注解和 XML，每个用例结束后回滚。 */
class CouponOrderMapperTest extends SqlTestSupport {
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 10, 0);

    private CouponOrder insert(long userId, String number, String username, int status, LocalDateTime createTime) {
        CouponOrder row = new CouponOrder();
        row.setUserId(userId);
        row.setCouponId(1L);
        row.setNumber(number);
        row.setCouponNumber(number + "-COUPON");
        row.setUsername(username);
        row.setCouponName("午餐代金券");
        row.setType(1);
        row.setPrice(new BigDecimal("9.90"));
        row.setDiscountAmount(new BigDecimal("20.00"));
        row.setThresholdAmount(new BigDecimal("0.00"));
        row.setStatus(status);
        row.setPayDeadline(createTime.plusMinutes(15));
        row.setCreateTime(createTime);
        if (status == CouponOrder.STATUS_PAID) {
            row.setPayTime(createTime.plusMinutes(1));
        }
        mapper(CouponOrderMapper.class).insertCouponOrder(row);
        return row;
    }

    private Page<CouponOrderPageQueryVO> adminQuery(CouponOrderPageQueryDTO filter) {
        PageHelper.startPage(filter.getPage(), filter.getPageSize());
        return mapper(CouponOrderMapper.class).adminPageSearch(filter);
    }

    private Page<CouponOrderPageQueryVO> userQuery(Integer status) {
        PageHelper.startPage(1, 10);
        return mapper(CouponOrderMapper.class).userPageQuery(USER_ID, status);
    }

    @Test
    void adminFiltersCombineNumberUsernameTimeStatusAndSortByCreateTimeDescending() {
        CouponOrder oldest = insert(USER_ID, "ORDER-AAA-1", "张三", 1, START);
        CouponOrder middle = insert(USER_ID, "ORDER-BBB-2", "李四", 2, START.plusHours(1));
        CouponOrder newest = insert(USER_ID, "ORDER-CCC-3", "张三丰", 3, START.plusHours(2));

        // 无条件查询返回全部，按下单时间倒序
        assertThat(adminQuery(new CouponOrderPageQueryDTO())).extracting(CouponOrderPageQueryVO::getId)
                .containsExactly(newest.getId(), middle.getId(), oldest.getId());

        // 订单号模糊匹配
        CouponOrderPageQueryDTO byNumber = new CouponOrderPageQueryDTO();
        byNumber.setNumber("BBB");
        assertThat(adminQuery(byNumber)).extracting(CouponOrderPageQueryVO::getId).containsExactly(middle.getId());

        // 用户名模糊匹配，命中不同长度的同前缀用户名
        CouponOrderPageQueryDTO byUsername = new CouponOrderPageQueryDTO();
        byUsername.setUsername("张三");
        assertThat(adminQuery(byUsername)).extracting(CouponOrderPageQueryVO::getId)
                .containsExactly(newest.getId(), oldest.getId());

        // 订单状态精确匹配
        CouponOrderPageQueryDTO byStatus = new CouponOrderPageQueryDTO();
        byStatus.setStatus(2);
        assertThat(adminQuery(byStatus)).extracting(CouponOrderPageQueryVO::getId).containsExactly(middle.getId());

        // 下单时间范围匹配，包含起止边界
        CouponOrderPageQueryDTO byTime = new CouponOrderPageQueryDTO();
        byTime.setBeginTime(START);
        byTime.setEndTime(START.plusHours(1));
        assertThat(adminQuery(byTime)).extracting(CouponOrderPageQueryVO::getId)
                .containsExactly(middle.getId(), oldest.getId());

        // 组合筛选 + 分页
        CouponOrderPageQueryDTO combined = new CouponOrderPageQueryDTO();
        combined.setUsername("张三");
        combined.setStatus(3);
        combined.setPageSize(1);
        Page<CouponOrderPageQueryVO> page = adminQuery(combined);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page).extracting(CouponOrderPageQueryVO::getId).containsExactly(newest.getId());
    }

    @Test
    void userQueryReturnsOnlyOwnOrdersFilteredByOptionalStatus() {
        CouponOrder mineUnpaid = insert(USER_ID, "ORDER-MINE-1", "当前用户", 0, START);
        CouponOrder minePaid = insert(USER_ID, "ORDER-MINE-2", "当前用户", 1, START.plusHours(1));
        insert(USER_ID + 1, "ORDER-OTHER-1", "其他用户", 0, START.plusHours(2));

        // 不带状态查询只返回自己的订单，按下单时间倒序
        assertThat(userQuery(null)).extracting(CouponOrderPageQueryVO::getId)
                .containsExactly(minePaid.getId(), mineUnpaid.getId());

        // 按状态查询
        assertThat(userQuery(0)).extracting(CouponOrderPageQueryVO::getId).containsExactly(mineUnpaid.getId());
        assertThat(userQuery(1)).extracting(CouponOrderPageQueryVO::getId).containsExactly(minePaid.getId());
        assertThat(userQuery(3)).isEmpty();
    }

    @Test
    void pageResultMapsListColumns() {
        CouponOrder inserted = insert(USER_ID, "ORDER-FIELDS-1", "快照用户", 1, START);

        Page<CouponOrderPageQueryVO> page = userQuery(1);
        assertThat(page).hasSize(1);
        CouponOrderPageQueryVO vo = page.getFirst();
        assertThat(vo.getId()).isEqualTo(inserted.getId());
        // 分页返回对象不含 userId、couponId、couponNumber、type，其余字段与实体逐一比对
        assertThat(vo).usingRecursiveComparison().ignoringFields("id").isEqualTo(inserted);
        assertThat(vo.getCreateTime()).isEqualTo(START);
    }

    @Test
    void detailQueriesRespectOwnershipAndMissingIds() {
        CouponOrder mine = insert(USER_ID, "ORDER-DETAIL-1", "当前用户", 1, START);
        CouponOrder other = insert(USER_ID + 1, "ORDER-DETAIL-2", "其他用户", 1, START);

        // 商家详情查询不限制归属
        assertThat(mapper(CouponOrderMapper.class).selectById(mine.getId()))
                .usingRecursiveComparison().ignoringFields("id").isEqualTo(mine);
        assertThat(mapper(CouponOrderMapper.class).selectById(other.getId()))
                .usingRecursiveComparison().ignoringFields("id").isEqualTo(other);
        assertThat(mapper(CouponOrderMapper.class).selectById(Long.MAX_VALUE)).isNull();

        // 用户详情查询只能查到本人订单，非本人订单与不存在的订单都返回 null，避免泄露订单存在性
        assertThat(mapper(CouponOrderMapper.class).selectByIdAndUserId(mine.getId(), USER_ID))
                .usingRecursiveComparison().ignoringFields("id").isEqualTo(mine);
        assertThat(mapper(CouponOrderMapper.class).selectByIdAndUserId(other.getId(), USER_ID)).isNull();
        assertThat(mapper(CouponOrderMapper.class).selectByIdAndUserId(mine.getId(), USER_ID + 2)).isNull();
    }
}

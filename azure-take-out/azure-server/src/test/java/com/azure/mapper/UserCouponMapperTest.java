package com.azure.mapper;

import com.azure.dto.coupon.UserCouponPageQueryDTO;
import com.azure.entity.UserCoupon;
import com.azure.support.SqlTestSupport;
import com.azure.vo.coupon.UserCouponVO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/** 执行真实 Mapper 注解和 XML，每个用例结束后回滚。 */
class UserCouponMapperTest extends SqlTestSupport {
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 10, 0);

    private UserCoupon insert(long userId, long couponId, int type, int status, LocalDateTime expireTime, LocalDateTime createTime) {
        UserCoupon row = new UserCoupon();
        row.setUserId(userId);
        row.setCouponId(couponId);
        row.setNumber("COUPON-" + userId + "-" + couponId);
        row.setType(type);
        row.setName(type == 1 ? "午餐代金券" : "双人满减券");
        row.setDiscountAmount(new BigDecimal("20.00"));
        row.setThresholdAmount(type == 1 ? new BigDecimal("0.00") : new BigDecimal("60.00"));
        row.setStatus(status);
        row.setExpireTime(expireTime);
        row.setCreateTime(createTime);
        if (status == UserCoupon.STATUS_USED) {
            row.setUseTime(createTime.plusDays(1));
        }
        if (status == UserCoupon.STATUS_REFUNDED) {
            row.setRefundTime(createTime.plusDays(1));
        }
        mapper(UserCouponMapper.class).insertUserCoupon(row);
        // 插入语句不回填自增主键，通过唯一券号取回 id。
        row.setId(mapper(UserCouponMapper.class).getByCouponNumber(row.getNumber()).getId());
        return row;
    }

    private Page<UserCouponVO> query(Integer status) {
        PageHelper.startPage(1, 10);
        return mapper(UserCouponMapper.class).userPageQuery(USER_ID, status);
    }

    @Test
    void pageQueryReturnsOnlyOwnCouponsSortedByCreateTimeDescending() {
        UserCoupon older = insert(USER_ID, 1, 1, UserCoupon.STATUS_USED, START.plusDays(30), START);
        UserCoupon newer = insert(USER_ID, 2, 2, UserCoupon.STATUS_UNUSED, START.plusDays(30), START.plusHours(1));
        insert(USER_ID + 1, 1, 1, UserCoupon.STATUS_UNUSED, START.plusDays(30), START.plusHours(2));

        assertThat(query(null)).extracting(UserCouponVO::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void statusFilterMatchesStoredStatusValues() {
        UserCoupon unused = insert(USER_ID, 1, 1, UserCoupon.STATUS_UNUSED, START.plusDays(30), START);
        UserCoupon staleUnused = insert(USER_ID, 2, 1, UserCoupon.STATUS_UNUSED, START.minusHours(1), START.plusHours(1));
        UserCoupon locked = insert(USER_ID, 3, 1, UserCoupon.STATUS_LOCKED, START.plusDays(30), START.plusHours(2));
        UserCoupon used = insert(USER_ID, 4, 2, UserCoupon.STATUS_USED, START.plusDays(30), START.plusHours(3));
        UserCoupon expired = insert(USER_ID, 5, 1, UserCoupon.STATUS_EXPIRED, START.minusHours(2), START.plusHours(4));
        UserCoupon refunded = insert(USER_ID, 6, 2, UserCoupon.STATUS_REFUNDED, START.plusDays(30), START.plusHours(5));

        assertThat(query(null)).hasSize(6);
        // 状态按存储值筛选：过期时间不参与筛选，已过期(3)由后续定时任务流转。
        assertThat(query(0)).extracting(UserCouponVO::getId)
                .containsExactly(staleUnused.getId(), unused.getId());
        assertThat(query(1)).extracting(UserCouponVO::getId).containsExactly(locked.getId());
        assertThat(query(2)).extracting(UserCouponVO::getId).containsExactly(used.getId());
        assertThat(query(3)).extracting(UserCouponVO::getId).containsExactly(expired.getId());
        assertThat(query(4)).extracting(UserCouponVO::getId).containsExactly(refunded.getId());
    }

    @Test
    void queryResultKeepsRawStatusAndMapsAllSnapshotColumns() {
        UserCoupon expiredStored = insert(USER_ID, 5, 1, UserCoupon.STATUS_EXPIRED, START.minusHours(1), START);
        UserCoupon refunded = insert(USER_ID, 6, 2, UserCoupon.STATUS_REFUNDED, START.plusDays(30), START.plusHours(1));

        Page<UserCouponVO> page = query(3);
        assertThat(page).hasSize(1);
        assertThat(page.getFirst().getId()).isEqualTo(expiredStored.getId());
        assertThat(page.getFirst().getStatus()).isEqualTo(UserCoupon.STATUS_EXPIRED);
        assertThat(page.getFirst().getExpireTime()).isEqualTo(START.minusHours(1));

        Page<UserCouponVO> all = query(null);
        assertThat(all).hasSize(2);
        UserCouponVO vo = all.getFirst();
        assertThat(vo.getId()).isEqualTo(refunded.getId());
        assertThat(vo.getStatus()).isEqualTo(UserCoupon.STATUS_REFUNDED);
        assertThat(vo).usingRecursiveComparison().ignoringFields("id", "status").isEqualTo(refunded);
    }

    @Test
    void detailReturnsOwnCouponWithStoredStatusAndHidesOthers() {
        UserCoupon mine = insert(USER_ID, 3, 1, UserCoupon.STATUS_UNUSED, START.plusDays(30), START);
        UserCoupon other = insert(USER_ID + 1, 3, 1, UserCoupon.STATUS_UNUSED, START.plusDays(30), START);

        assertThat(mapper(UserCouponMapper.class).selectByIdAndUserId(mine.getId(), USER_ID))
                .usingRecursiveComparison().ignoringFields("id").isEqualTo(mine);
        assertThat(mapper(UserCouponMapper.class).selectByIdAndUserId(other.getId(), USER_ID)).isNull();
        assertThat(mapper(UserCouponMapper.class).selectByIdAndUserId(mine.getId(), USER_ID + 1)).isNull();
        assertThat(mapper(UserCouponMapper.class).selectByIdAndUserId(Long.MAX_VALUE, USER_ID)).isNull();
    }

    @Test
    void detailReturnsStoredExpiredStatus() {
        UserCoupon expired = insert(USER_ID, 4, 1, UserCoupon.STATUS_EXPIRED, START.minusHours(1), START);

        UserCouponVO vo = mapper(UserCouponMapper.class).selectByIdAndUserId(expired.getId(), USER_ID);
        assertThat(vo).isNotNull();
        assertThat(vo.getStatus()).isEqualTo(UserCoupon.STATUS_EXPIRED);
        assertThat(vo.getExpireTime()).isEqualTo(START.minusHours(1));
    }

    @Test
    void pageQueryDtoDefaultsMatchPageHelperUsage() {
        UserCouponPageQueryDTO dto = new UserCouponPageQueryDTO();
        assertThat(dto.getPage()).isEqualTo(1);
        assertThat(dto.getPageSize()).isEqualTo(10);
        assertThat(dto.getStatus()).isNull();
    }
}

package com.azure.service.impl;

import com.azure.cache.CacheClient;
import com.azure.cache.RedisConstants;
import com.azure.dto.coupon.CouponOrderRefundDTO;
import com.azure.exception.BaseException;
import com.azure.mapper.CouponMapper;
import com.azure.mapper.CouponOrderMapper;
import com.azure.mapper.UserCouponMapper;
import com.azure.support.TransactionTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证购券退款提交后才恢复 Redis 库存，回滚和重复退款不恢复。 */
class CouponRefundIntegrationTest extends TransactionTestSupport {
    private JdbcTemplate jdbc;
    private CouponOrderServiceImpl service;
    private StringRedisTemplate redis;
    private static final List<String> KEYS = List.of(RedisConstants.COUPON_STOCK_KEY_PREFIX + 1);

    @BeforeEach
    void initializeRefund() {
        jdbc = new JdbcTemplate(dataSource);
        redis = mock(StringRedisTemplate.class);
        CacheClient cache = mockedCacheClient();
        inject(cache, "stringRedisTemplate", redis);
        CouponOrderServiceImpl target = new CouponOrderServiceImpl();
        inject(target, "couponOrderMapper", transactionalMapper(CouponOrderMapper.class));
        inject(target, "couponMapper", transactionalMapper(CouponMapper.class));
        inject(target, "userCouponMapper", transactionalMapper(UserCouponMapper.class));
        inject(target, "cacheClient", cache);
        service = transactionalService(target, CouponOrderServiceImpl.class);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("INSERT INTO coupon (id,type,name,total_stock,stock,discount_amount,threshold_amount,price," +
                        "start_time,end_time,status,valid_time,create_time,update_time) " +
                        "VALUES (1,1,'退款测试券',100,99,20,0,9.9,?,?,1,30,?,?)",
                now.minusDays(2), now.plusDays(1), now, now);
        jdbc.update("INSERT INTO coupon_order (id,user_id,coupon_id,number,coupon_number,username,coupon_name," +
                        "type,price,discount_amount,threshold_amount,status,pay_deadline,create_time,pay_time) " +
                        "VALUES (1,?,1,'refund-order','refund-coupon','测试用户','退款测试券',1,9.9,20,0,1,?,?,?)",
                USER_ID, now.plusMinutes(15), now, now);
        jdbc.update("INSERT INTO user_coupon (user_id,coupon_id,number,type,name,discount_amount,threshold_amount," +
                        "status,expire_time,create_time) VALUES (?,1,'refund-coupon',1,'退款测试券',20,0,0,?,?)",
                USER_ID, now.plusDays(30), now);
    }

    private CouponOrderRefundDTO refund() {
        CouponOrderRefundDTO dto = new CouponOrderRefundDTO();
        dto.setId(1L);
        dto.setRefundReason("测试退款");
        return dto;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void refundRestoresRedisOnlyAfterDatabaseCommit(int couponStatus) {
        jdbc.update("UPDATE user_coupon SET status=?", couponStatus);
        when(redis.execute(any(RedisScript.class), eq(KEYS))).thenAnswer(invocation -> {
            // 用独立连接确认 Redis 执行时数据库结果已对其他连接可见。
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                try (ResultSet rows = statement.executeQuery("SELECT stock FROM coupon WHERE id=1")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(100);
                }
                try (ResultSet rows = statement.executeQuery("SELECT status FROM coupon_order WHERE id=1")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                }
            }
            return 1L;
        });

        service.refundCouponOrder(refund());

        assertThat(jdbc.queryForObject("SELECT status FROM user_coupon", Integer.class)).isEqualTo(4);
        verify(redis).execute(any(RedisScript.class), eq(KEYS));
        verifyNoMoreInteractions(redis);
    }

    @Test
    void outerTransactionRollbackDoesNotRestoreRedisStock() {
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.refundCouponOrder(refund());
            verifyNoInteractions(redis);
            throw new IllegalStateException("模拟提交前失败");
        })).isInstanceOf(IllegalStateException.class).hasMessage("模拟提交前失败");

        assertThat(jdbc.queryForObject("SELECT stock FROM coupon WHERE id=1", Integer.class)).isEqualTo(99);
        assertThat(jdbc.queryForObject("SELECT status FROM coupon_order WHERE id=1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM user_coupon", Integer.class)).isEqualTo(0);
        verifyNoInteractions(redis);
    }

    @Test
    void repeatedRefundDoesNotRestoreStockTwice() {
        service.refundCouponOrder(refund());

        assertThatThrownBy(() -> service.refundCouponOrder(refund())).isInstanceOf(BaseException.class);

        assertThat(jdbc.queryForObject("SELECT stock FROM coupon WHERE id=1", Integer.class)).isEqualTo(100);
        verify(redis).execute(any(RedisScript.class), eq(KEYS));
        verifyNoMoreInteractions(redis);
    }
}

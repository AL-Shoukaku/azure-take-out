package com.azure.service.impl;

import com.azure.dto.coupon.CouponDTO;
import com.azure.dto.coupon.CouponPageQueryDTO;
import com.azure.dto.coupon.CouponUpdateDTO;
import com.azure.entity.Coupon;
import com.azure.entity.CouponOrder;
import com.azure.entity.User;
import com.azure.entity.UserCoupon;
import com.azure.exception.BaseException;
import com.azure.mapper.CouponMapper;
import com.azure.mapper.CouponOrderMapper;
import com.azure.mapper.UserCouponMapper;
import com.azure.mapper.UserMapper;
import com.azure.result.PageResult;
import com.azure.support.ServiceTestSupport;
import com.azure.vo.coupon.CouponVO;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CouponServiceImplTest extends ServiceTestSupport {
    @InjectMocks private CouponServiceImpl service;
    @Mock private CouponMapper mapper;
    @Mock private UserCouponMapper userCouponMapper;
    @Mock private CouponOrderMapper couponOrderMapper;
    @Mock private UserMapper userMapper;
    @Captor private ArgumentCaptor<Coupon> coupon;

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 10, 0);

    private CouponDTO publication() {
        CouponDTO dto = new CouponDTO();
        dto.setName("午餐券");
        dto.setType(1);
        dto.setTotalStock(100);
        dto.setDiscountAmount(new BigDecimal("20"));
        dto.setThresholdAmount(BigDecimal.ZERO);
        dto.setPrice(new BigDecimal("9.9"));
        dto.setStartTime(START);
        dto.setEndTime(START.plusDays(7));
        dto.setValidTime(30);
        return dto;
    }

    private Coupon availableCoupon(int type) {
        Coupon row = new Coupon();
        org.springframework.beans.BeanUtils.copyProperties(publication(), row);
        row.setId(7L);
        row.setType(type);
        row.setStatus(1);
        row.setStock(100);
        row.setStartTime(LocalDateTime.now().minusDays(1));
        row.setEndTime(LocalDateTime.now().plusDays(1));
        row.setDiscountAmount(new BigDecimal("20.10"));
        row.setThresholdAmount(type == Coupon.TYPE_FULL_REDUCTION ? new BigDecimal("50.20") : BigDecimal.ZERO);
        row.setPrice(type == Coupon.TYPE_VOUCHER ? new BigDecimal("9.90") : BigDecimal.ZERO);
        return row;
    }

    @Test
    void claimingPreservesDecimalAmountsAndLeavesUserCouponIdForDatabase() {
        Coupon template = availableCoupon(Coupon.TYPE_FULL_REDUCTION);
        when(mapper.selectById(7L)).thenReturn(template);

        service.userGetCoupon(7L);

        ArgumentCaptor<UserCoupon> issued = ArgumentCaptor.forClass(UserCoupon.class);
        verify(userCouponMapper).userHasGetCoupon(USER_ID, 7L);
        verify(userCouponMapper).insertUserCoupon(issued.capture());
        assertThat(issued.getValue().getId()).isNull();
        assertThat(issued.getValue().getCouponId()).isEqualTo(7L);
        assertThat(issued.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(issued.getValue().getDiscountAmount()).isEqualByComparingTo("20.10");
        assertThat(issued.getValue().getThresholdAmount()).isEqualByComparingTo("50.20");
        verify(mapper).updateCoupon(coupon.capture());
        assertThat(coupon.getValue().getStock()).isEqualTo(99);
    }

    @Test
    void purchaseCreatesUnpaidOrderWithDecimalPriceAndFaceValue() {
        Coupon template = availableCoupon(Coupon.TYPE_VOUCHER);
        when(mapper.selectById(7L)).thenReturn(template);
        when(userMapper.getById(USER_ID)).thenReturn(User.builder().id(USER_ID).username("测试用户").build());

        service.userBuyCoupon(7L);

        ArgumentCaptor<CouponOrder> order = ArgumentCaptor.forClass(CouponOrder.class);
        verify(couponOrderMapper).insertCouponOrder(order.capture());
        assertThat(order.getValue().getPrice()).isEqualByComparingTo("9.90");
        assertThat(order.getValue().getDiscountAmount()).isEqualByComparingTo("20.10");
        assertThat(order.getValue().getThresholdAmount()).isEqualByComparingTo("0");
        assertThat(order.getValue().getStatus()).isEqualTo(CouponOrder.STATUS_UNPAID);
        assertThat(order.getValue().getId()).isNull();
        verify(userCouponMapper, never()).insertUserCoupon(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void publicationInitializesStockStatusAndAuditTimes(int type) {
        CouponDTO dto = publication();
        dto.setType(type);
        if (type == 2) {
            dto.setPrice(BigDecimal.ZERO);
            dto.setThresholdAmount(new BigDecimal("50"));
        }
        LocalDateTime before = LocalDateTime.now();

        service.publishCoupon(dto);

        verify(mapper).insertCoupon(coupon.capture());
        Coupon saved = coupon.getValue();
        assertThat(saved).usingRecursiveComparison()
                .ignoringFields("stock", "status", "createTime", "updateTime")
                .isEqualTo(dto);
        assertThat(saved.getStock()).isEqualTo(100);
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getValidTime()).isEqualTo(30); // 有效期以天保存
        assertThat(saved.getCreateTime()).isBetween(before, LocalDateTime.now());
        assertThat(saved.getUpdateTime()).isEqualTo(saved.getCreateTime());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingStart", "missingEnd", "bothMissing", "equal", "reversed"})
    void invalidActivityTimesAreRejectedBeforeInsert(String scenario) {
        CouponDTO dto = publication();
        switch (scenario) {
            case "missingStart" -> dto.setStartTime(null);
            case "missingEnd" -> dto.setEndTime(null);
            case "bothMissing" -> { dto.setStartTime(null); dto.setEndTime(null); }
            case "equal" -> dto.setEndTime(START);
            case "reversed" -> dto.setEndTime(START.minusMinutes(1));
            default -> throw new IllegalArgumentException(scenario);
        }

        assertThatThrownBy(() -> service.publishCoupon(dto))
                .isInstanceOf(BaseException.class).hasMessage("优惠券的开始时间必须早于结束时间");
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"omitted", "zero", "positive"})
    void updateOnlyPassesEditableFieldsAndRefreshesUpdateTime(String price) {
        CouponUpdateDTO dto = new CouponUpdateDTO();
        dto.setId(7L);
        dto.setName("新名称");
        dto.setPrice(switch (price) { case "zero" -> BigDecimal.ZERO; case "positive" -> new BigDecimal("8.8"); default -> null; });
        when(mapper.updateCoupon(any())).thenReturn(1);
        LocalDateTime before = LocalDateTime.now();

        service.updateCoupon(dto);

        verify(mapper).updateCoupon(coupon.capture());
        Coupon expected = new Coupon();
        expected.setId(7L);
        expected.setName("新名称");
        expected.setPrice(dto.getPrice());
        assertThat(coupon.getValue()).usingRecursiveComparison().ignoringFields("updateTime").isEqualTo(expected);
        assertThat(coupon.getValue().getUpdateTime()).isBetween(before, LocalDateTime.now());
    }

    @Test
    void updatingMissingCouponFails() {
        CouponUpdateDTO dto = new CouponUpdateDTO();
        dto.setId(999L);
        dto.setName("不存在的券");
        assertThatThrownBy(() -> service.updateCoupon(dto))
                .isInstanceOf(BaseException.class).hasMessage("优惠券不存在");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void startAndStopUseRequestedStatus(int status) {
        when(mapper.updateStatus(7L, status)).thenReturn(1);
        service.startOrStop(7L, status);
        verify(mapper).updateStatus(7L, status);
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,1", "7,null", "-1,1", "7,-1", "7,2"}, nullValues = "null")
    void invalidStatusRequestsNeverReachDatabase(Long id, Integer status) {
        assertThatThrownBy(() -> service.startOrStop(id, status)).isInstanceOf(BaseException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void changingStatusOfMissingCouponFails() {
        assertThatThrownBy(() -> service.startOrStop(999L, 1)).isInstanceOf(BaseException.class);
        verify(mapper).updateStatus(999L, 1);
    }

    @Test
    void detailIncludesAllStoredFields() {
        Coupon stored = new Coupon();
        org.springframework.beans.BeanUtils.copyProperties(publication(), stored);
        stored.setId(7L);
        stored.setStock(80);
        stored.setStatus(1);
        stored.setCreateTime(START.minusDays(1));
        stored.setUpdateTime(START);
        when(mapper.selectById(7L)).thenReturn(stored);

        assertThat(service.getById(7L)).usingRecursiveComparison().isEqualTo(stored);
    }

    @Test
    void missingDetailFails() {
        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(BaseException.class).hasMessage("优惠券不存在");
    }

    @ParameterizedTest
    @CsvSource(value = {"null", "-1"}, nullValues = "null")
    void invalidDetailIdNeverReachesDatabase(Long id) {
        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(BaseException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void pagePreservesPaginationTotalAndRecords() {
        CouponPageQueryDTO dto = new CouponPageQueryDTO();
        dto.setPage(2);
        dto.setPageSize(5);
        dto.setName("午餐");
        CouponVO row = new CouponVO();
        row.setId(7L);
        when(mapper.pageQuery(dto)).thenAnswer(invocation -> {
            assertThat(PageHelper.getLocalPage().getPageNum()).isEqualTo(2);
            assertThat(PageHelper.getLocalPage().getPageSize()).isEqualTo(5);
            return page(16, row);
        });

        PageResult result = service.pageQuery(dto);

        assertThat(result.getTotal()).isEqualTo(16);
        assertThat(result.getRecords()).containsExactly(row);
    }
}

package com.sky.common;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.Orders;
import com.sky.json.JacksonObjectMapper;
import com.sky.result.Result;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class CommonContractTest {
    private final JacksonObjectMapper json = new JacksonObjectMapper();
    @AfterEach void cleanup() { BaseContext.removeCurrentId(); }
    @Test void resultEnvelopeUsesExistingSuccessAndErrorCodes() {
        Result<String> success = Result.success("value"); assertThat(success.getCode()).isEqualTo(1); assertThat(success.getData()).isEqualTo("value");
        assertThat(Result.success().getData()).isNull();
        Result<?> error = Result.error("失败"); assertThat(error.getCode()).isZero(); assertThat(error.getMsg()).isEqualTo("失败"); assertThat(error.getData()).isNull();
    }
    @Test void contextIsIsolatedAcrossThreadsAndRemovable() throws Exception {
        BaseContext.setCurrentId(7L); ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            assertThat(pool.submit(() -> { Long initial = BaseContext.getCurrentId(); BaseContext.setCurrentId(8L); BaseContext.removeCurrentId(); return initial; }).get(5,TimeUnit.SECONDS)).isNull();
            assertThat(BaseContext.getCurrentId()).isEqualTo(7L); BaseContext.removeCurrentId(); assertThat(BaseContext.getCurrentId()).isNull();
        } finally { pool.shutdownNow(); }
    }
    @Test void jwtRoundTripPreservesClaimsAndExpiry() {
        long before = System.currentTimeMillis(); String token = JwtUtil.createJWT("test-secret",60000,new HashMap<>(Collections.singletonMap("empId",7L)));
        Claims claims = JwtUtil.parseJWT("test-secret",token); assertThat(claims.get("empId",Number.class).longValue()).isEqualTo(7L);
        assertThat(claims.getExpiration().getTime()).isBetween(before+59000,System.currentTimeMillis()+60000);
    }
    @Test void jwtRejectsWrongSignature() {
        String token = JwtUtil.createJWT("test-secret",60000,new HashMap<>(Collections.singletonMap("empId",7L)));
        assertThatThrownBy(() -> JwtUtil.parseJWT("other-secret",token)).isInstanceOf(SignatureException.class);
    }
    @Test void jwtRejectsExpiredTokenWithoutSleep() {
        String token = JwtUtil.createJWT("test-secret",-60000,new HashMap<>(Collections.singletonMap("empId",7L)));
        assertThatThrownBy(() -> JwtUtil.parseJWT("test-secret",token)).isInstanceOf(ExpiredJwtException.class);
    }
    @Test void jwtRejectsMalformedToken() { assertThatThrownBy(() -> JwtUtil.parseJWT("test-secret","invalid-token")).isInstanceOf(JwtException.class); }
    @Test void globalDateTimeSerializationPreservesMinutePrecision() throws Exception {
        assertThat(json.writeValueAsString(LocalDateTime.of(2026,10,8,12,30,45))).isEqualTo("\"2026-10-08 12:30\"");
        assertThat(json.readValue("\"2026-10-08 12:30\"",LocalDateTime.class)).isEqualTo(LocalDateTime.of(2026,10,8,12,30));
        assertThat(json.writeValueAsString(LocalDate.of(2026,10,8))).isEqualTo("\"2026-10-08\"");
        assertThat(json.readValue("\"12:30:45\"",LocalTime.class)).isEqualTo(LocalTime.of(12,30,45));
    }
    @Test void deliveryRequestAnnotationPreservesSecondsAndUnknownFieldsAreIgnored() throws Exception {
        OrdersSubmitDTO dto = json.readValue("{\"addressBookId\":7,\"estimatedDeliveryTime\":\"2026-10-08 12:30:45\",\"futureField\":1}",OrdersSubmitDTO.class);
        assertThat(dto.getEstimatedDeliveryTime()).isEqualTo(LocalDateTime.of(2026,10,8,12,30,45));
        assertThat(json.readTree(json.writeValueAsString(dto)).get("estimatedDeliveryTime").asText()).isEqualTo("2026-10-08 12:30:45");
    }
    @Test void orderResponseTimeUsesGlobalFormat() throws Exception {
        Orders order = Orders.builder().orderTime(LocalDateTime.of(2026,10,8,12,30,45)).build();
        assertThat(json.readTree(json.writeValueAsString(order)).get("orderTime").asText()).isEqualTo("2026-10-08 12:30");
    }
}

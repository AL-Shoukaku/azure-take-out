package com.sky.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.Orders;
import com.sky.entity.DishFlavor;
import com.sky.json.JacksonObjectMapper;
import com.sky.result.Result;
import com.sky.result.PageResult;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.sky.support.WebTestConfiguration.ADMIN_SECRET;
import static com.sky.support.WebTestConfiguration.USER_SECRET;
import static org.assertj.core.api.Assertions.*;

class CommonContractTest {
    private final JacksonObjectMapper json = new JacksonObjectMapper();
    @AfterEach void cleanup() { BaseContext.removeCurrentId(); }
    @Test void resultEnvelopeUsesExistingSuccessAndErrorCodes() {
        Result<String> success = Result.success("value"); assertThat(success.getCode()).isEqualTo(1); assertThat(success.getData()).isEqualTo("value");
        assertThat(Result.success().getData()).isNull();
        Result<?> error = Result.error("失败"); assertThat(error.getCode()).isZero(); assertThat(error.getMsg()).isEqualTo("失败"); assertThat(error.getData()).isNull();
    }

    @Test
    void serializedResultEnvelopesRetainNullFields() throws Exception {
        JsonNode success = json.readTree(json.writeValueAsString(Result.success()));
        assertThat(success.size()).isEqualTo(3);
        assertThat(success.path("code").intValue()).isEqualTo(1);
        assertThat(success.has("msg")).isTrue();
        assertThat(success.get("msg").isNull()).isTrue();
        assertThat(success.has("data")).isTrue();
        assertThat(success.get("data").isNull()).isTrue();

        JsonNode error = json.readTree(json.writeValueAsString(Result.error("失败")));
        assertThat(error.size()).isEqualTo(3);
        assertThat(error.path("code").intValue()).isZero();
        assertThat(error.path("msg").textValue()).isEqualTo("失败");
        assertThat(error.has("data")).isTrue();
        assertThat(error.get("data").isNull()).isTrue();
    }

    @Test
    void emptyPageRemainsAnArrayAndTotalRemainsNumeric() throws Exception {
        JsonNode page = json.readTree(json.writeValueAsString(
                Result.success(new PageResult(0, Collections.emptyList())))).path("data");
        assertThat(page.path("total").isIntegralNumber()).isTrue();
        assertThat(page.path("total").longValue()).isZero();
        assertThat(page.path("records").isArray()).isTrue();
        assertThat(page.path("records").size()).isZero();
    }

    @Test
    void orderIdsAndAmountsRemainJsonNumbers() throws Exception {
        Orders order = Orders.builder().id(2147483648L).amount(new BigDecimal("12.50")).build();
        JsonNode payload = json.readTree(json.writeValueAsString(order));
        assertThat(payload.path("id").isIntegralNumber()).isTrue();
        assertThat(payload.path("id").longValue()).isEqualTo(2147483648L);
        assertThat(payload.path("amount").isNumber()).isTrue();
        assertThat(payload.path("amount").decimalValue()).isEqualByComparingTo("12.50");
    }

    @Test
    void dishFlavorValueRetainsItsJsonArrayStringContract() throws Exception {
        String options = "[\"微辣\",\"中辣\"]";
        DishFlavor flavor = DishFlavor.builder().name("辣度").value(options).build();
        JsonNode payload = json.readTree(json.writeValueAsString(flavor));
        assertThat(payload.path("value").isTextual()).isTrue();
        assertThat(payload.path("value").textValue()).isEqualTo(options);
        assertThat(json.readTree(payload.path("value").textValue()).get(0).textValue())
                .isEqualTo("微辣");
    }
    @Test void contextIsIsolatedAcrossThreadsAndRemovable() throws Exception {
        BaseContext.setCurrentId(7L); ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            assertThat(pool.submit(() -> { Long initial = BaseContext.getCurrentId(); BaseContext.setCurrentId(8L); BaseContext.removeCurrentId(); return initial; }).get(5,TimeUnit.SECONDS)).isNull();
            assertThat(BaseContext.getCurrentId()).isEqualTo(7L); BaseContext.removeCurrentId(); assertThat(BaseContext.getCurrentId()).isNull();
        } finally { pool.shutdownNow(); }
    }
    @Test void jwtRoundTripPreservesClaimsAndExpiry() {
        long before = System.currentTimeMillis(); String token = JwtUtil.createJWT(ADMIN_SECRET,60000,new HashMap<>(Collections.singletonMap("empId",7L)));
        Claims claims = JwtUtil.parseJWT(ADMIN_SECRET,token); assertThat(claims.get("empId",Number.class).longValue()).isEqualTo(7L);
        assertThat(claims.getExpiration().getTime()).isBetween(before+59000,System.currentTimeMillis()+60000);
    }
    @Test void jwtRejectsWrongSignature() {
        String token = JwtUtil.createJWT(ADMIN_SECRET,60000,new HashMap<>(Collections.singletonMap("empId",7L)));
        assertThatThrownBy(() -> JwtUtil.parseJWT(USER_SECRET,token)).isInstanceOf(SignatureException.class);
    }
    @Test void jwtRejectsExpiredTokenWithoutSleep() {
        String token = JwtUtil.createJWT(ADMIN_SECRET,-60000,new HashMap<>(Collections.singletonMap("empId",7L)));
        assertThatThrownBy(() -> JwtUtil.parseJWT(ADMIN_SECRET,token)).isInstanceOf(ExpiredJwtException.class);
    }
    @Test void jwtRejectsMalformedToken() { assertThatThrownBy(() -> JwtUtil.parseJWT(ADMIN_SECRET,"invalid-token")).isInstanceOf(JwtException.class); }

    @Test
    void jwtRejectsTamperedPayload() throws Exception {
        String token = JwtUtil.createJWT(ADMIN_SECRET, 60000, Map.of("empId", 7L));
        String[] parts = token.split("\\.");
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode)
                json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        payload.put("empId", 999L);
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.writeValueAsBytes(payload));

        assertThatThrownBy(() -> JwtUtil.parseJWT(ADMIN_SECRET, String.join(".", parts)))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void jwtRejectsUnsignedClaims() {
        String unsigned = Jwts.builder()
                .claims(Map.of("empId", 7L))
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .compact();

        assertThatThrownBy(() -> JwtUtil.parseJWT(ADMIN_SECRET, unsigned))
                .isInstanceOf(UnsupportedJwtException.class);
    }

    @Test
    void jwtRejectsOtherSignatureAlgorithms() {
        // 合法的 48 字节测试密钥，确保失败原因是算法限制，而非密钥长度或错误签名。
        byte[] bytes = new byte[48];
        String secret = Base64.getEncoder().encodeToString(bytes);
        String hs384 = Jwts.builder()
                .claims(Map.of("empId", 7L))
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(bytes), Jwts.SIG.HS384)
                .compact();
        assertThat(Jwts.parser().verifyWith(Keys.hmacShaKeyFor(bytes)).build()
                .parseSignedClaims(hs384).getPayload().get("empId", Number.class).longValue())
                .isEqualTo(7L);

        assertThatThrownBy(() -> JwtUtil.parseJWT(secret, hs384))
                .isInstanceOf(SignatureException.class)
                .hasCauseInstanceOf(UnsupportedJwtException.class);
    }

    @Test
    void jwtRejectsWeakAndInvalidKeyConfiguration() {
        String shortKey = Base64.getEncoder()
                .encodeToString("itcast".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> JwtUtil.keyFromBase64(shortKey))
                .isInstanceOf(WeakKeyException.class);
        assertThatThrownBy(() -> JwtUtil.keyFromBase64("invalid-base64"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void globalDateTimeSerializationPreservesMinutePrecision() throws Exception {
        assertThat(json.writeValueAsString(LocalDateTime.of(2026,10,8,12,30,45))).isEqualTo("\"2026-10-08 12:30\"");
        assertThat(json.readValue("\"2026-10-08 12:30\"",LocalDateTime.class)).isEqualTo(LocalDateTime.of(2026,10,8,12,30));
        assertThat(json.writeValueAsString(LocalDate.of(2026,10,8))).isEqualTo("\"2026-10-08\"");
        assertThat(json.readValue("\"12:30:45\"",LocalTime.class)).isEqualTo(LocalTime.of(12,30,45));
        assertThat(json.writeValueAsString(LocalTime.of(12,30,45))).isEqualTo("\"12:30:45\"");
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

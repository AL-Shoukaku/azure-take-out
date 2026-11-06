package com.azure.web;

import com.azure.exception.BaseException;
import com.azure.result.PageResult;
import com.azure.support.WebTestSupport;
import com.azure.vo.coupon.CouponVO;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CouponMvcTest extends WebTestSupport {
    private ObjectNode publication() {
        return json.createObjectNode()
                .put("name", "午餐券").put("type", 1).put("totalStock", 100)
                .put("discountAmount", 20).put("thresholdAmount", 0).put("price", 9.9)
                .put("startTime", "2026-11-01 10:00").put("endTime", "2026-11-08 10:00")
                .put("validTime", 30);
    }

    @Test
    void publicationBindsAmountsTimesAndValidityInDays() throws Exception {
        successful(admin(post("/admin/coupon/publish").contentType("application/json")
                .content(publication().toString())));

        verify(couponService).publishCoupon(argThat(dto -> dto.getName().equals("午餐券")
                && dto.getType() == 1 && dto.getTotalStock() == 100
                && dto.getDiscountAmount() == 20 && dto.getThresholdAmount() == 0 && dto.getPrice() == 9.9
                && dto.getStartTime().equals(LocalDateTime.of(2026, 11, 1, 10, 0))
                && dto.getEndTime().equals(LocalDateTime.of(2026, 11, 8, 10, 0))
                && dto.getValidTime() == 30));
    }

    @ParameterizedTest
    @CsvSource({
            "type,0", "type,3", "totalStock,0", "totalStock,-1",
            "discountAmount,0", "discountAmount,-1", "thresholdAmount,-1", "price,-1",
            "validTime,0", "validTime,-1"
    })
    void invalidPublicationNumbersReturnValidationEnvelope(String field, double value) throws Exception {
        ObjectNode body = publication();
        body.put(field, value);
        validationError(post("/admin/coupon/publish").contentType("application/json").content(body.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "null", "blank"})
    void invalidPublicationNameIsRejected(String scenario) throws Exception {
        ObjectNode body = publication();
        switch (scenario) {
            case "missing" -> body.remove("name");
            case "null" -> body.putNull("name");
            case "blank" -> body.put("name", "   ");
            default -> throw new IllegalArgumentException(scenario);
        }
        validationError(post("/admin/coupon/publish").contentType("application/json").content(body.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"type", "totalStock", "discountAmount", "validTime"})
    void missingRequiredPublicationNumbersAreRejected(String field) throws Exception {
        ObjectNode body = publication();
        body.remove(field);
        validationError(post("/admin/coupon/publish").contentType("application/json").content(body.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"omitted", "zero", "positive"})
    void updateAllowsOmittedPriceAndZero(String scenario) throws Exception {
        ObjectNode body = json.createObjectNode().put("id", 7).put("name", "新名称");
        Double price = switch (scenario) { case "zero" -> 0.0; case "positive" -> 8.8; default -> null; };
        if (price != null) body.put("price", price);
        successful(admin(put("/admin/coupon/update").contentType("application/json").content(body.toString())));
        verify(couponService).updateCoupon(argThat(dto -> dto.getId() == 7L && dto.getName().equals("新名称")
                && java.util.Objects.equals(dto.getPrice(), price)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"午餐券\"}",
            "{\"id\":0,\"name\":\"午餐券\"}",
            "{\"id\":-1,\"name\":\"午餐券\"}",
            "{\"id\":7}",
            "{\"id\":7,\"name\":\" \"}",
            "{\"id\":7,\"name\":\"午餐券\",\"price\":-1}"
    })
    void invalidUpdatesReturnValidationEnvelope(String body) throws Exception {
        validationError(put("/admin/coupon/update").contentType("application/json").content(body));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void statusEndpointBindsIdAndStatus(int state) throws Exception {
        successful(admin(put("/admin/coupon/status").param("couponId", "7").param("status", String.valueOf(state))));
        verify(couponService).startOrStop(7L, state);
    }

    @Test
    void detailReturnsCouponWithConfiguredDateFormat() throws Exception {
        CouponVO row = new CouponVO();
        row.setId(7L);
        row.setName("午餐券");
        row.setStock(80);
        row.setValidTime(30);
        row.setStartTime(LocalDateTime.of(2026, 11, 1, 10, 0));
        when(couponService.getById(7L)).thenReturn(row);

        successful(admin(get("/admin/coupon/7")))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.name").value("午餐券"))
                .andExpect(jsonPath("$.data.stock").value(80))
                .andExpect(jsonPath("$.data.validTime").value(30))
                .andExpect(jsonPath("$.data.startTime").value("2026-11-01 10:00"));
        verify(couponService).getById(7L);
    }

    @Test
    void pageBindsAllFiltersAndReturnsTotalAndRecords() throws Exception {
        CouponVO row = new CouponVO();
        row.setId(7L);
        when(couponService.pageQuery(any())).thenReturn(new PageResult(16, List.of(row)));

        successful(admin(post("/admin/coupon/pageQuery").contentType("application/json")
                .content("{\"page\":2,\"pageSize\":5,\"name\":\"午餐\",\"type\":1,\"status\":0,\"activityStatus\":2}")))
                .andExpect(jsonPath("$.data.total").value(16))
                .andExpect(jsonPath("$.data.records[0].id").value(7));
        verify(couponService).pageQuery(argThat(dto -> dto.getPage() == 2 && dto.getPageSize() == 5
                && dto.getName().equals("午餐") && dto.getType() == 1 && dto.getStatus() == 0 && dto.getActivityStatus() == 2));
    }

    @Test
    void emptyPageBodyUsesDefaultsAndOptionalFiltersStayNull() throws Exception {
        when(couponService.pageQuery(any())).thenReturn(new PageResult(0, List.of()));
        successful(admin(post("/admin/coupon/pageQuery").contentType("application/json").content("{}")))
                .andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.records").isEmpty());
        verify(couponService).pageQuery(argThat(dto -> dto.getPage() == 1 && dto.getPageSize() == 10
                && dto.getName() == null && dto.getType() == null && dto.getStatus() == null && dto.getActivityStatus() == null));
    }

    @ParameterizedTest
    @CsvSource({"page,0", "page,-1", "pageSize,0", "pageSize,-1", "pageSize,101"})
    void invalidPaginationReturnsValidationEnvelope(String field, int value) throws Exception {
        validationError(post("/admin/coupon/pageQuery").contentType("application/json")
                .content(json.createObjectNode().put(field, value).toString()));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void paginationAcceptsBoundaryPageSizes(int size) throws Exception {
        when(couponService.pageQuery(any())).thenReturn(new PageResult(0, List.of()));
        successful(admin(post("/admin/coupon/pageQuery").contentType("application/json")
                .content(json.createObjectNode().put("pageSize", size).toString())));
        verify(couponService).pageQuery(argThat(dto -> dto.getPageSize() == size));
    }

    @ParameterizedTest
    @ValueSource(strings = {"publish", "update", "status", "detail", "page"})
    void businessErrorsUseExistingResultEnvelope(String endpoint) throws Exception {
        String message = "优惠券业务校验失败";
        switch (endpoint) {
            case "publish" -> doThrow(new BaseException(message)).when(couponService).publishCoupon(any());
            case "update" -> doThrow(new BaseException(message)).when(couponService).updateCoupon(any());
            case "status" -> doThrow(new BaseException(message)).when(couponService).startOrStop(7L, 1);
            case "detail" -> when(couponService.getById(7L)).thenThrow(new BaseException(message));
            case "page" -> when(couponService.pageQuery(any())).thenThrow(new BaseException(message));
            default -> throw new IllegalArgumentException(endpoint);
        }
        mvc.perform(admin(request(endpoint)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {"publish", "update", "status", "detail", "page"})
    void allCouponEndpointsRequireAdminAuthentication(String endpoint) throws Exception {
        mvc.perform(request(endpoint)).andExpect(status().isUnauthorized());
        verifyNoInteractions(couponService);
    }

    private MockHttpServletRequestBuilder request(String endpoint) {
        return switch (endpoint) {
            case "publish" -> post("/admin/coupon/publish").contentType("application/json").content(publication().toString());
            case "update" -> put("/admin/coupon/update").contentType("application/json").content("{\"id\":7,\"name\":\"午餐券\"}");
            case "status" -> put("/admin/coupon/status").param("couponId", "7").param("status", "1");
            case "detail" -> get("/admin/coupon/7");
            case "page" -> post("/admin/coupon/pageQuery").contentType("application/json").content("{}");
            default -> throw new IllegalArgumentException(endpoint);
        };
    }

    private void validationError(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(admin(request)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").isNotEmpty());
        verifyNoInteractions(couponService);
    }
}

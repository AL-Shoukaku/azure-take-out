package com.sky.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sky.utils.HttpClientUtil;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpClientFailureTest {
    @Test void getConnectionFailureMustNotBeMaskedByNullResponseCleanup() throws Exception {
        CloseableHttpClient client=mock(CloseableHttpClient.class); when(client.execute(any(HttpUriRequest.class))).thenThrow(new IOException("synthetic connection failure"));
        try (org.mockito.MockedStatic<HttpClients> factory=mockStatic(HttpClients.class)) {
            factory.when(HttpClients::createDefault).thenReturn(client);
            assertThat(HttpClientUtil.doGet("https://example.invalid",null)).isEmpty();
        }
        verify(client).close();
    }

    @Test
    void jsonPostConnectionFailureClosesClientAndPreservesException() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        IOException failure = new IOException("synthetic connection failure");
        when(client.execute(any(HttpUriRequest.class))).thenThrow(failure);
        try (org.mockito.MockedStatic<HttpClients> factory = mockStatic(HttpClients.class)) {
            factory.when(HttpClients::createDefault).thenReturn(client);
            assertThatThrownBy(() -> HttpClientUtil.doPost4Json(
                    "https://example.invalid", Map.of("code", "abc")))
                    .isSameAs(failure);
        }
        verify(client).close();
    }

    @Test
    void jsonPostReadFailureClosesBothResourcesAndRetainsCleanupFailureAsSuppressed()
            throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        HttpEntity entity = mock(HttpEntity.class);
        IOException readFailure = new IOException("synthetic read failure");
        IOException closeFailure = new IOException("synthetic response close failure");
        when(client.execute(any(HttpUriRequest.class))).thenReturn(response);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenThrow(readFailure);
        doThrow(closeFailure).when(response).close();

        try (org.mockito.MockedStatic<HttpClients> factory = mockStatic(HttpClients.class)) {
            factory.when(HttpClients::createDefault).thenReturn(client);
            assertThatThrownBy(() -> HttpClientUtil.doPost4Json(
                    "https://example.invalid", Map.of("code", "abc")))
                    .isSameAs(readFailure);
        }
        assertThat(readFailure.getSuppressed()).containsExactly(closeFailure);
        verify(response).close();
        verify(client).close();
    }

    @Test
    void jsonSerializationFailureDoesNotCreateAnHttpClient() {
        Map<String, String> params = new HashMap<>();
        params.put(null, "value"); // JSON 对象字段名不能为 null。
        try (org.mockito.MockedStatic<HttpClients> factory = mockStatic(HttpClients.class)) {
            assertThatThrownBy(() -> HttpClientUtil.doPost4Json("https://example.invalid", params))
                    .isInstanceOf(JsonProcessingException.class);
            factory.verifyNoInteractions();
        }
    }
}

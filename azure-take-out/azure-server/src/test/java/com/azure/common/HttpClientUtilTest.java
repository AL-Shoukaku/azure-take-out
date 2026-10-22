package com.azure.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.azure.utils.HttpClientUtil;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.*;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpClientUtilTest {
    @Mock private CloseableHttpClient client;
    @Mock private CloseableHttpResponse response;
    @Captor private ArgumentCaptor<HttpUriRequest> request;

    @Test void getEncodesQueryReadsUtf8AndClosesResources() throws Exception {
        when(client.execute(any(HttpUriRequest.class))).thenReturn(response);
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1,200,"OK"));
        when(response.getEntity()).thenReturn(new StringEntity("响应内容","UTF-8"));
        try (MockedStatic<HttpClients> factory=mockStatic(HttpClients.class)) {
            factory.when(HttpClients::createDefault).thenReturn(client);
            assertThat(HttpClientUtil.doGet("https://example.invalid/login",Collections.singletonMap("js_code","a b+c"))).isEqualTo("响应内容");
        }
        verify(client).execute(request.capture()); assertThat(request.getValue().getURI().getRawQuery()).isEqualTo("js_code=a+b%2Bc");
        assertThat(((HttpGet)request.getValue()).getConfig().getConnectTimeout()).isEqualTo(5000);
        assertThat(((HttpGet)request.getValue()).getConfig().getSocketTimeout()).isEqualTo(5000);
        verify(response).close(); verify(client).close();
    }
    @Test void getWithoutParametersHandlesNonSuccessResponse() throws Exception {
        when(client.execute(any(HttpUriRequest.class))).thenReturn(response);
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1,500,"Error"));
        try (MockedStatic<HttpClients> factory=mockStatic(HttpClients.class)) {
            factory.when(HttpClients::createDefault).thenReturn(client);
            assertThat(HttpClientUtil.doGet("https://example.invalid/login",null)).isEmpty();
        }
        verify(response).close(); verify(client).close();
    }
    @ParameterizedTest @ValueSource(strings={"form","json"})
    void postBuildsExpectedPayloadAndTimeouts(String format) throws Exception {
        when(client.execute(any(HttpUriRequest.class))).thenReturn(response); when(response.getEntity()).thenReturn(new StringEntity("ok","UTF-8"));
        try (MockedStatic<HttpClients> factory=mockStatic(HttpClients.class)) {
            factory.when(HttpClients::createDefault).thenReturn(client);
            String result=format.equals("form") ? HttpClientUtil.doPost("https://example.invalid",Collections.singletonMap("code","abc"))
                    : HttpClientUtil.doPost4Json("https://example.invalid",Collections.singletonMap("code","abc"));
            assertThat(result).isEqualTo("ok");
        }
        verify(client).execute(request.capture()); HttpPost post=(HttpPost)request.getValue();
        assertThat(post.getConfig().getConnectTimeout()).isEqualTo(5000); assertThat(post.getConfig().getSocketTimeout()).isEqualTo(5000);
        assertThat(post.getConfig().getConnectionRequestTimeout()).isEqualTo(5000);
        String payload=EntityUtils.toString(post.getEntity(),"UTF-8");
        if (format.equals("form")) assertThat(payload).isEqualTo("code=abc");
        else {
            JsonNode json = new ObjectMapper().readTree(payload);
            assertThat(json.isObject()).isTrue();
            assertThat(json.size()).isEqualTo(1);
            assertThat(json.path("code").textValue()).isEqualTo("abc");
            assertThat(post.getEntity().getContentType().getValue()).isEqualTo("application/json");
            assertThat(post.getEntity().getContentEncoding()).isNull();
            verify(client).close();
        }
        verify(response).close();
    }

    private HttpPost captureJsonPost(Map<String, String> params) throws Exception {
        when(client.execute(any(HttpUriRequest.class))).thenReturn(response);
        when(response.getEntity()).thenReturn(new StringEntity("ok", StandardCharsets.UTF_8));
        try (MockedStatic<HttpClients> factory = mockStatic(HttpClients.class)) {
            factory.when(HttpClients::createDefault).thenReturn(client);
            assertThat(HttpClientUtil.doPost4Json("https://example.invalid", params)).isEqualTo("ok");
        }
        verify(client).execute(request.capture());
        verify(response).close();
        verify(client).close();
        return (HttpPost) request.getValue();
    }

    @Test
    void jsonPostPreservesUtf8AndEscapedTextWhileOmittingOnlyNullValues() throws Exception {
        String text = "中文\"\\\n";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("note", text);
        params.put("nullable", null);
        params.put("literalNull", "null");
        params.put("empty", "");

        HttpPost post = captureJsonPost(params);
        String body = EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8);
        JsonNode json = new ObjectMapper().readTree(body);
        assertThat(json.isObject()).isTrue();
        assertThat(json.size()).isEqualTo(3);
        assertThat(json.path("note").textValue()).isEqualTo(text);
        assertThat(json.has("nullable")).isFalse();
        assertThat(json.path("literalNull").textValue()).isEqualTo("null");
        assertThat(json.path("empty").textValue()).isEqualTo("");
        assertThat(post.getEntity().getContentType().getValue()).isEqualTo("application/json");
        assertThat(post.getEntity().getContentEncoding()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void jsonPostDistinguishesNullAndEmptyMaps(boolean nullMap) throws Exception {
        HttpPost post = captureJsonPost(nullMap ? null : Collections.emptyMap());
        if (nullMap) {
            assertThat(post.getEntity()).isNull();
        } else {
            assertThat(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8)).isEqualTo("{}");
        }
    }
}

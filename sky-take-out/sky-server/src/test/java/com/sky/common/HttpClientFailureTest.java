package com.sky.common;

import com.sky.utils.HttpClientUtil;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.junit.jupiter.api.*;
import java.io.IOException;
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
}

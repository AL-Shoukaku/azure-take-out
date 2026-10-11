package com.sky.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.SkyApplication;
import com.sky.entity.Orders;
import com.sky.mapper.OrdersMapper;
import com.sky.properties.JwtProperties;
import com.sky.task.OrderTask;
import com.sky.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 测试用：启动随机端口的实际容器和 Jakarta WebSocket 端点，仅模拟外部 Redis 和定时任务。 */
@SpringBootTest(classes = SkyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.druid.url=jdbc:h2:mem:sky_websocket_network;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE",
                "server.address=127.0.0.1",
                "spring.cache.type=simple"
        })
@ActiveProfiles("test")
class WebSocketIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private JwtProperties jwt;
    @Autowired private OrdersMapper orders;
    @MockBean private LettuceConnectionFactory redisConnectionFactory;
    @MockBean private OrderTask orderTask;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void simulatedPaymentSendsNewOrderNotificationToBothRealWebSocketClients() throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
             ConnectedClient first = connect(client);
             ConnectedClient second = connect(client)) {
            String token = JwtUtil.createJWT(jwt.getUserSecretKey(), 60000, new HashMap<>(Map.of("userId", 42L)));
            HttpRequest payment = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/user/order/payment"))
                    .timeout(Duration.ofSeconds(10))
                    .header(jwt.getUserTokenName(), token).header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"orderNumber\":\"order-1\",\"payMethod\":1}"))
                    .build();
            HttpResponse<String> response = client.send(payment, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode result = json.readTree(response.body());
            assertThat(result.path("code").asInt()).isEqualTo(1);
            assertThat(result.path("data").path("packageStr").asText()).isEqualTo("prepay_id=fake_prepay_id");
            Orders saved = orders.getById(1L);
            assertThat(saved.getStatus()).isEqualTo(Orders.TO_BE_CONFIRMED);
            assertThat(saved.getPayStatus()).isEqualTo(Orders.PAID);
            for (ConnectedClient receiver : new ConnectedClient[]{first, second}) {
                JsonNode message = json.readTree(receiver.listener.message.get(10, TimeUnit.SECONDS));
                assertThat(message.path("type").asInt()).isEqualTo(1);
                assertThat(message.path("orderId").asLong()).isEqualTo(1L);
                assertThat(message.path("content").asText()).isEqualTo("订单号 order-1");
            }
        }
    }

    private ConnectedClient connect(HttpClient client) throws Exception {
        NotificationListener listener = new NotificationListener();
        WebSocket socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/test-" + UUID.randomUUID()), listener)
                .get(10, TimeUnit.SECONDS);
        ConnectedClient connection = new ConnectedClient(socket, listener);
        try {
            // 等待真实 ping/pong，确认服务器已完成 onOpen；不用 sleep 猜测连接注册时机。
            socket.sendPing(ByteBuffer.wrap(new byte[]{1})).get(10, TimeUnit.SECONDS);
            listener.ready.get(10, TimeUnit.SECONDS);
            return connection;
        } catch (Exception e) {
            socket.abort();
            throw e;
        }
    }

    private record ConnectedClient(WebSocket socket, NotificationListener listener) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete").get(10, TimeUnit.SECONDS);
                listener.closed.get(10, TimeUnit.SECONDS);
            } finally {
                socket.abort();
            }
        }
    }

    private static class NotificationListener implements WebSocket.Listener {
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private final CompletableFuture<String> message = new CompletableFuture<>();
        private final CompletableFuture<Void> closed = new CompletableFuture<>();
        private final StringBuilder text = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer data) {
            ready.complete(null);
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                message.complete(text.toString());
                text.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.complete(null);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            ready.completeExceptionally(error);
            message.completeExceptionally(error);
            closed.completeExceptionally(error);
        }
    }
}

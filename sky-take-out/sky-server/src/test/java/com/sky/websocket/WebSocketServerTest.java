package com.sky.websocket;

import org.junit.jupiter.api.*;
import javax.websocket.*;
import java.io.IOException;
import java.util.*;
import static org.mockito.Mockito.*;

class WebSocketServerTest {
    private final WebSocketServer server = new WebSocketServer();
    private final List<String> ids = new ArrayList<>();
    private Session connect() {
        String id = UUID.randomUUID().toString(); ids.add(id); Session session = mock(Session.class); server.onOpen(session,id); return session;
    }
    @AfterEach void disconnect() { ids.forEach(server::onClose); }
    @Test void broadcastReachesEveryConnectedSession() throws Exception {
        Session first = connect(); Session second = connect(); RemoteEndpoint.Basic a = mock(RemoteEndpoint.Basic.class); RemoteEndpoint.Basic b = mock(RemoteEndpoint.Basic.class);
        when(first.getBasicRemote()).thenReturn(a); when(second.getBasicRemote()).thenReturn(b);
        server.sendToAllClient("message"); verify(a).sendText("message"); verify(b).sendText("message");
    }
    @Test void closedSessionReceivesNoMessages() {
        Session closed = connect(); server.onClose(ids.get(0)); server.sendToAllClient("message"); verifyNoInteractions(closed);
    }
    @Test void failedSessionDoesNotPreventDeliveryToHealthySession() throws Exception {
        Session failed = connect(); Session healthy = connect(); RemoteEndpoint.Basic a = mock(RemoteEndpoint.Basic.class); RemoteEndpoint.Basic b = mock(RemoteEndpoint.Basic.class);
        when(failed.getBasicRemote()).thenReturn(a); when(healthy.getBasicRemote()).thenReturn(b); doThrow(new IOException("test send failure")).when(a).sendText("message");
        server.sendToAllClient("message"); verify(b).sendText("message");
    }
    @Test void broadcastWithoutSessionsAndIncomingMessageDoNotFail() { server.sendToAllClient("message"); server.onMessage("ping","test-client"); }
}

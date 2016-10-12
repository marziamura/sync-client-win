package com.github.tornaia.sync.client.win.remote.reader;

import com.github.tornaia.sync.client.win.ClientidService;
import com.github.tornaia.sync.shared.api.FileMetaInfo;
import com.github.tornaia.sync.shared.api.RemoteEventType;
import com.github.tornaia.sync.shared.api.RemoteFileEvent;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@ClientEndpoint
public class RemoteReaderService {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteReaderService.class);

    @Value("${frosch-sync.userid:7247234}")
    private String userid;

    @Autowired
    private SyncWebSocketReConnectService syncWebSocketReConnectService;

    @Autowired
    private RemoteRestQueryService remoteRestQueryService;

    @Autowired
    private ClientidService clientidService;

    private final List<RemoteFileEvent> events = new ArrayList<>();

    private volatile boolean initDone = false;

    private Session session;

    public boolean isInitDone() {
        return initDone;
    }

    @OnOpen
    public void open(Session session) {
        this.session = session;
        LOG.info("Session opened. SessionId: " + session.getId());
        sendMessage("hello-please-send-me-updates-of-" + userid);
        sendMessage("hello-i-am-" + clientidService.clientid);
    }

    @OnMessage
    public void onMessage(String message) {
        LOG.info("Received msg: " + message);
        if (Objects.equals("init-done", message)) {
            LOG.info("Init done");
            initDone = true;
            return;
        }
        RemoteFileEvent remoteFileEvent = new Gson().fromJson(message, RemoteFileEvent.class);
        addNewEvent(remoteFileEvent);
    }

    private synchronized void addNewEvent(RemoteFileEvent remoteFileEvent) {
        events.add(remoteFileEvent);
    }

    public synchronized Optional<RemoteFileEvent> getNextCreated() {
        Optional<RemoteFileEvent> first = events.stream()
                .filter(e -> Objects.equals(RemoteEventType.CREATED, e.eventType))
                .findFirst();
        if (first.isPresent()) {
            events.remove(first.get());
        }
        return first;
    }

    public synchronized Optional<RemoteFileEvent> getNextModified() {
        Optional<RemoteFileEvent> first = events.stream()
                .filter(e -> Objects.equals(RemoteEventType.MODIFIED, e.eventType))
                .findFirst();
        if (first.isPresent()) {
            events.remove(first.get());
        }
        return first;
    }

    public synchronized Optional<RemoteFileEvent> getNextDeleted() {
        Optional<RemoteFileEvent> first = events.stream()
                .filter(e -> Objects.equals(RemoteEventType.DELETED, e.eventType))
                .findFirst();
        if (first.isPresent()) {
            events.remove(first.get());
        }
        return first;
    }

    /*public synchronized Optional<RemoteFileEvent> getNext() {
        if (events.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(events.remove(0));
    }*/

    public void sendMessage(String message) {
        session.getAsyncRemote().sendText(message);
        LOG.info("Sent msg: " + message);
    }

    @OnClose
    public void closedConnection(Session session) {
        LOG.info("Session closed. SessionId: " + session.getId());
        syncWebSocketReConnectService.reconnect();
    }

    @OnError
    public void error(Session session, Throwable t) {
        LOG.info("Error on session. SessionId: " + session.getId(), t);
        syncWebSocketReConnectService.reconnect();
    }

    public byte[] getFile(FileMetaInfo fileMetaInfo) {
        return remoteRestQueryService.getFile(fileMetaInfo);
    }
}
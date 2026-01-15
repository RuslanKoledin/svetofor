package incuat.kg.svetoofor;


import javafx.application.Platform;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TrafficLightServer extends WebSocketServer {

    private final Set<WebSocket> clients = Collections.synchronizedSet(new HashSet<>());
    private TrafficLightApp app;

    // Хранение текущего состояния светофора для синхронизации новых клиентов
    private String currentRedState = null;      // RED_BLINK или GREEN_BLINK_INCIDENT или null
    private String currentYellowState = null;   // YELLOW_BLINK или GREEN_BLINK_ALERT или null
    private String currentQueueState = null;    // QUEUE_RED или QUEUE_GREEN или null

    public TrafficLightServer(int port) {
        super(new InetSocketAddress(port));
    }

    public void setApp(TrafficLightApp app) {
        this.app = app;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clients.add(conn);
        System.out.println("Client connected: " + conn.getRemoteSocketAddress());

        // Отправляем новому клиенту текущее состояние светофора
        if (currentRedState != null) {
            conn.send(currentRedState);
            System.out.println("Sent current red state to new client: " + currentRedState);
        }
        if (currentYellowState != null) {
            conn.send(currentYellowState);
            System.out.println("Sent current yellow state to new client: " + currentYellowState);
        }
        if (currentQueueState != null) {
            conn.send(currentQueueState);
            System.out.println("Sent current queue state to new client: " + currentQueueState);
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
        System.out.println("Client disconnected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Получено сообщение: " + message);

        // Сохраняем текущее состояние для синхронизации
        updateState(message);

        // Рассылаем всем подключенным клиентам
        synchronized (clients) {
            for (WebSocket client : clients) {
                client.send(message);
            }
        }

        // Вызываем обработчик в TrafficLightApp (в UI потоке JavaFX)
        if (app != null) {
            Platform.runLater(() -> app.handleServerMessage(message));
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Server started on port " + getPort());
    }

    public void broadcast(String message) {
        System.out.println("Рассылка сообщения: " + message);

        // Сохраняем текущее состояние для синхронизации
        updateState(message);

        synchronized (clients) {
            for (WebSocket client : clients) {
                client.send(message);
            }
        }

        // Также вызываем локальный обработчик
        if (app != null) {
            Platform.runLater(() -> app.handleServerMessage(message));
        }
    }

    /**
     * Обновляет сохраненное состояние светофора на основе полученного сообщения
     */
    private void updateState(String message) {
        switch (message) {
            // Красный индикатор (инциденты)
            case "RED_BLINK":
                currentRedState = message;
                break;
            case "GREEN_BLINK_INCIDENT":
                currentRedState = message;
                break;

            // Желтый индикатор (алерты)
            case "YELLOW_BLINK":
                currentYellowState = message;
                break;
            case "GREEN_BLINK_ALERT":
                currentYellowState = message;
                break;

            // Зеленый индикатор (очередь мониторинга)
            case "QUEUE_RED":
                currentQueueState = message;
                break;
            case "QUEUE_GREEN":
                currentQueueState = message;
                break;
        }
    }

    public static void main(String[] args) {
        TrafficLightServer server = new TrafficLightServer(52521); // порт 52521
        server.start();
    }
}
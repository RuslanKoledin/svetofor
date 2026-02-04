package incuat.kg.svetoofor;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.*;

public class TrafficLightSpec {

    private WebSocketClient client;
    private static final DateTimeFormatter LOG_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static PrintWriter logWriter;

    static {
        try {
            String logPath = System.getProperty("user.home") + "/AppData/Local/TrafficLightClient/svetoofor.log";
            File logFile = new File(logPath);
            logFile.getParentFile().mkdirs();
            logWriter = new PrintWriter(new FileWriter(logFile, true), true);
        } catch (IOException e) {
            System.err.println("Не удалось создать файл логов: " + e.getMessage());
        }
    }

    private static void log(String message) {
        String timestamp = LocalDateTime.now().format(LOG_FORMATTER);
        String logMessage = "[" + timestamp + "] " + message;
        System.out.println(logMessage);
        if (logWriter != null) {
            logWriter.println(logMessage);
        }
    }

    public void connect(String serverUri, TrafficLightApp app) {
        try {
            client = new WebSocketClient(new URI(serverUri)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log("Подключено к серверу: " + serverUri);
                }

                @Override
                public void onMessage(String message) {
                    log("Получена команда от сервера: " + message);
                    // Сообщение от сервера → запускаем метод в JavaFX
                    javafx.application.Platform.runLater(() -> app.handleServerMessage(message));
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log("Отключено от сервера. Код: " + code + ", Причина: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    log("Ошибка WebSocket: " + ex.getMessage());
                    ex.printStackTrace();
                }
            };
            client.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        if (client != null && client.isOpen()) {
            log("Отправка команды на сервер: " + message);
            client.send(message);
        } else {
            log("Невозможно отправить команду - нет подключения к серверу");
        }
    }
}


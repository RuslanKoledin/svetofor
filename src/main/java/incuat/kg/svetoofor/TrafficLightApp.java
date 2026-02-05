package incuat.kg.svetoofor;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.util.Pair;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class TrafficLightApp extends Application {
    private TrafficLightSpec wsClient = new TrafficLightSpec();

    private Ellipse redCircle;
    private Ellipse yellowCircle;
    private Ellipse greenCircle;

    private Timeline redBlinkTimeline;
    private Timeline yellowBlinkTimeline;
    private Timeline greenBlinkTimeline;

    private boolean isIncidentActive = false;
    private boolean isAdmin = false;

    // Configuration loaded from client.properties
    private String serverAddress = "10.10.90.170";
    private int serverPort = 52521;
    private String adminLogin = "admin";
    private String adminPassword = "qweasd123#$";
    private int windowWidth = 92;
    private int windowHeight = 212;
    private String configPath = "client.properties";  // Путь к конфигу для сохранения

    // Логирование
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

    public static void main(String[] args) {
        launch(args);
    }

    public static void launchApp(String admin) {
    }

    @Override
    public void start(Stage primaryStage) {
        loadConfiguration();
        showRoleSelection(primaryStage);
    }

    private void loadConfiguration() {
        Properties props = new Properties();
        boolean loaded = false;

        // Сначала пробуем загрузить из JAR (для jpackage установщика)
        try (var is = getClass().getClassLoader().getResourceAsStream("client.properties")) {
            if (is != null) {
                props.load(is);
                log("Loaded configuration from JAR resource: client.properties");
                loaded = true;
            }
        } catch (IOException e) {
            // Не удалось загрузить из JAR, продолжаем
        }

        // Если не загрузили из JAR, пробуем внешние файлы
        if (!loaded) {
            String[] configPaths = {
                "client.properties",
                System.getProperty("user.home") + "/AppData/Local/TrafficLightClient/client.properties",
                System.getProperty("user.dir") + "/client.properties"
            };

            for (String path : configPaths) {
                try (FileInputStream fis = new FileInputStream(path)) {
                    props.load(fis);
                    log("Loaded configuration from: " + path);
                    configPath = path;  // Сохраняем путь для записи
                    loaded = true;
                    break;
                } catch (IOException e) {
                    // Пробуем следующий путь
                }
            }
        }

        // Загружаем параметры из конфигурации (приоритет: переменные окружения > properties файл > значения по умолчанию)
        serverAddress = getConfigValue(props, "server.address", "SERVER_ADDRESS", serverAddress);
        serverPort = Integer.parseInt(getConfigValue(props, "server.port", "SERVER_PORT", String.valueOf(serverPort)));

        adminLogin = getConfigValue(props, "admin.login", "ADMIN_LOGIN", adminLogin);
        adminPassword = getConfigValue(props, "admin.password", "ADMIN_PASSWORD", adminPassword);

        windowWidth = Integer.parseInt(getConfigValue(props, "window.width", "WINDOW_WIDTH", String.valueOf(windowWidth)));
        windowHeight = Integer.parseInt(getConfigValue(props, "window.height", "WINDOW_HEIGHT", String.valueOf(windowHeight)));

        // Выводим адрес сервера (может уже содержать ws://)
        if (serverAddress.startsWith("ws://") || serverAddress.startsWith("wss://")) {
            log("Server: " + serverAddress);
        } else {
            log("Server: ws://" + serverAddress + ":" + serverPort);
        }
        log("Window size: " + windowWidth + "x" + windowHeight);
    }

    private String getConfigValue(Properties props, String propKey, String envKey, String defaultValue) {
        // Сначала проверяем переменные окружения
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }

        // Потом проверяем properties файл
        String propValue = props.getProperty(propKey);
        if (propValue != null && !propValue.isEmpty()) {
            return propValue;
        }

        // Возвращаем значение по умолчанию
        return defaultValue;
    }

    private void saveWindowSize() {
        try {
            Properties props = new Properties();
            // Загружаем текущие настройки
            try (FileInputStream fis = new FileInputStream(configPath)) {
                props.load(fis);
            }

            // Обновляем размеры
            props.setProperty("window.width", String.valueOf(windowWidth));
            props.setProperty("window.height", String.valueOf(windowHeight));

            // Сохраняем
            try (FileOutputStream fos = new FileOutputStream(configPath)) {
                props.store(fos, "Traffic Light Client Configuration");
            }

            log("Размер окна сохранен: " + windowWidth + "x" + windowHeight);
        } catch (IOException e) {
            System.err.println("Не удалось сохранить размер окна: " + e.getMessage());
        }
    }

    private void showRoleSelection(Stage stage) {
        // Устанавливаем иконку приложения
        try {
            javafx.scene.image.Image icon = new javafx.scene.image.Image(getClass().getResourceAsStream("/44_85245.ico"));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Не удалось загрузить иконку: " + e.getMessage());
        }

        Label label = new Label("Выберите роль:");
        Button adminButton = new Button("Админ");
        Button specialistButton = new Button("Специалист");

        HBox buttons = new HBox(10, adminButton, specialistButton);
        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(20, label, buttons);
        root.setAlignment(Pos.CENTER);
        root.setPrefSize(200, 100);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Светофор - Выбор роли");
        stage.show();

        adminButton.setOnAction(e -> {
            log("Пользователь выбрал режим: Админ");
            if (showLoginDialog()) {
                isAdmin = true;
                log("Авторизация админа успешна");
                showTrafficLightStage();
                stage.close();
            } else {
                log("Авторизация админа не удалась - неверные данные");
                Alert alert = new Alert(Alert.AlertType.ERROR, "Неверный логин или пароль", ButtonType.OK);
                alert.showAndWait();
            }
        });

        specialistButton.setOnAction(e -> {
            log("Пользователь выбрал режим: Специалист");
            isAdmin = false;
            showTrafficLightStage();
            stage.close();
        });
    }

    private boolean showLoginDialog() {
        // Если логин/пароль не заданы - пропускаем авторизацию
        if (adminLogin == null || adminLogin.isEmpty() || adminPassword == null || adminPassword.isEmpty()) {
            log("Предупреждение: учетные данные администратора не настроены!");
            log("Установите admin.login и admin.password в client.properties");
            Alert alert = new Alert(Alert.AlertType.WARNING,
                "Учетные данные администратора не настроены в client.properties\nПроверьте настройки admin.login и admin.password",
                ButtonType.OK);
            alert.showAndWait();
            return false;
        }

        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Авторизация Админа");

        Label userLabel = new Label("Логин:");
        Label passLabel = new Label("Пароль:");
        TextField userField = new TextField();
        PasswordField passField = new PasswordField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(userLabel, 0, 0);
        grid.add(userField, 1, 0);
        grid.add(passLabel, 0, 1);
        grid.add(passField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                return new Pair<>(userField.getText(), passField.getText());
            }
            return null;
        });

        var result = dialog.showAndWait();

        if (result.isPresent()) {
            String enteredLogin = result.get().getKey();
            String enteredPassword = result.get().getValue();

            log("Попытка входа - Логин: " + enteredLogin);
            log("Ожидаемый логин: '" + adminLogin + "'");
            log("Логин совпадает: " + adminLogin.equals(enteredLogin));
            log("Пароль совпадает: " + adminPassword.equals(enteredPassword));

            return adminLogin.equals(enteredLogin) && adminPassword.equals(enteredPassword);
        }

        return false;
    }

    private void showTrafficLightStage() {
        // Формируем WebSocket URL
        String wsUrl;
        if (serverAddress.startsWith("ws://") || serverAddress.startsWith("wss://")) {
            // Адрес уже полный (включает ws:// и возможно порт)
            wsUrl = serverAddress;
        } else {
            // Адрес без протокола - добавляем ws:// и порт
            wsUrl = "ws://" + serverAddress + ":" + serverPort;
        }
        wsClient.connect(wsUrl, this);
        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT); // Прозрачное окно без рамки
        stage.setResizable(false); // Запрещаем изменение размера
        stage.setAlwaysOnTop(true); // Окно всегда поверх всех других окон

        // Запрещаем сворачивание окна
        stage.iconifiedProperty().addListener((obs, wasIconified, isNowIconified) -> {
            if (isNowIconified) {
                stage.setIconified(false);
            }
        });

        // Переменные для хранения позиции мыши при перетаскивании
        final double[] xOffset = {0};
        final double[] yOffset = {0};

        // Устанавливаем иконку приложения
        try {
            javafx.scene.image.Image icon = new javafx.scene.image.Image(getClass().getResourceAsStream("/44_85245.ico"));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Не удалось загрузить иконку: " + e.getMessage());
        }

        // Вычисляем масштаб на основе размера окна
        double scale = Math.min(windowWidth / 92.0, windowHeight / 250.0);
        int circleRadius = (int)(20 * scale);
        int fontSize = Math.max(10, (int)(14 * scale));

        // Инициализируем овалы с адаптивным размером (вытянутые по горизонтали)
        int radiusX = (int)(circleRadius * 1.3); // Ширина овала больше
        int radiusY = circleRadius; // Высота овала = обычный радиус
        redCircle = new Ellipse(radiusX, radiusY);
        redCircle.setFill(Color.rgb(40, 40, 40));
        yellowCircle = new Ellipse(radiusX, radiusY);
        yellowCircle.setFill(Color.rgb(40, 40, 40));
        greenCircle = new Ellipse(radiusX, radiusY);
        greenCircle.setFill(Color.rgb(40, 40, 40));

        VBox trafficLight = new VBox(10 * scale);
        trafficLight.setAlignment(Pos.CENTER);
        // Зелёный корпус с градиентом (светлый → тёмный бирюзовый)
        trafficLight.setStyle(
                "-fx-padding: " + (15 * scale) + ";" +
                        "-fx-background-color: linear-gradient(to bottom, #0bb17c 0%, #007b7e 50%, #006064 100%);" +
                        "-fx-background-radius: " + (50 * scale) + ";" +
                        "-fx-border-color: linear-gradient(to bottom, #1dd9a0, #004d50);" +
                        "-fx-border-width: " + (2 * scale) + ";" +
                        "-fx-border-radius: " + (50 * scale) + ";" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 2, 2);"
        );



        Label redLabel = new Label("Инцидент");
        Label yellowLabel = new Label("Алерт");
        Label greenLabel = new Label("Очередь");

        String labelStyle = "-fx-font-size: " + fontSize + "px; -fx-font-weight: bold;";

        redLabel.setStyle(labelStyle);
        yellowLabel.setStyle(labelStyle);
        greenLabel.setStyle(labelStyle);

        redLabel.setTextFill(Color.WHITE);
        yellowLabel.setTextFill(Color.WHITE);
        greenLabel.setTextFill(Color.WHITE);

        // --- Простые круги без эффектов ---
        redCircle.setStroke(Color.DARKGREY);
        redCircle.setStrokeWidth(2);

        yellowCircle.setStroke(Color.DARKGREY);
        yellowCircle.setStrokeWidth(2);

        greenCircle.setStroke(Color.DARKGREY);
        greenCircle.setStrokeWidth(2);

        VBox redBox = new VBox(3, redLabel, redCircle);
        redBox.setAlignment(Pos.CENTER);
        VBox yellowBox = new VBox(3, yellowLabel, yellowCircle);
        yellowBox.setAlignment(Pos.CENTER);
        VBox greenBox = new VBox(3, greenLabel, greenCircle);
        greenBox.setAlignment(Pos.CENTER);

        trafficLight.getChildren().addAll(redBox, yellowBox, greenBox);

        VBox root = new VBox(10, trafficLight);
        root.setAlignment(Pos.CENTER);

        // Только админ может управлять очередью мониторинга (третий круг)
        // Красный и желтый управляются только из JIRA
        if (isAdmin) {
            // Клик по третьему кругу (очередь мониторинг) - переключение между красным и зеленым
            greenCircle.setOnMouseClicked(e -> {
                // Проверяем текущее состояние и переключаем
                Color currentColor = (Color) greenCircle.getFill();
                if (currentColor.equals(Color.rgb(40, 40, 40)) || currentColor.equals(Color.LIMEGREEN)) {
                    // Если выключен или зеленый - включаем красный (очередь большая)
                    log("Админ переключил очередь: КРАСНЫЙ (большая очередь)");
                    setQueueColor(greenCircle, Color.RED, "queue_red");
                    wsClient.sendMessage("QUEUE_RED");
                } else {
                    // Если красный - включаем зеленый (очередь не нагружена)
                    log("Админ переключил очередь: ЗЕЛЁНЫЙ (очередь не нагружена)");
                    setQueueColor(greenCircle, Color.LIMEGREEN, "queue_green");
                    wsClient.sendMessage("QUEUE_GREEN");
                }
            });
        }

        Scene scene = new Scene(root, windowWidth, windowHeight);
        scene.setFill(null); // Прозрачный фон сцены
        root.setStyle("-fx-background-color: transparent;"); // Прозрачный фон root
        stage.setScene(scene);

        // Добавляем возможность перемещения окна мышью
        root.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });

        root.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset[0]);
            stage.setY(event.getScreenY() - yOffset[0]);
        });

        // Автопозиция в правом нижнем углу
        double screenWidth = Screen.getPrimary().getBounds().getWidth();
        double screenHeight = Screen.getPrimary().getBounds().getHeight();
        stage.setX(screenWidth - windowWidth - 40);  // 40px отступ от края
        stage.setY(screenHeight - windowHeight - 70);  // 70px отступ от низа (панель задач)

        stage.show();
    }

    private void blinkColor(Ellipse circle, Color color, int seconds, String colorName) {
        // Если зажигается зеленый в кружке инцидента (решение инцидента)
        if (colorName.equals("green_incident")) {
            // Останавливаем красный таймер если он есть
            if (redBlinkTimeline != null) {
                redBlinkTimeline.stop();
            }
            isIncidentActive = false;
        }

        // Если зажигается зеленый в кружке алерта (решение алерта)
        if (colorName.equals("green_alert")) {
            // Останавливаем желтый таймер если он есть
            if (yellowBlinkTimeline != null) {
                yellowBlinkTimeline.stop();
            }
        }

        // Если зажигается красный - активируем флаг инцидента
        if (colorName.equals("red")) {
            isIncidentActive = true;
        }

        // Зажигаем нужный цвет
        circle.setFill(color);

        // Останавливаем предыдущий таймер для этого круга
        Timeline existingTimeline = null;
        if (circle == redCircle && redBlinkTimeline != null) {
            existingTimeline = redBlinkTimeline;
        } else if (circle == yellowCircle && yellowBlinkTimeline != null) {
            existingTimeline = yellowBlinkTimeline;
        } else if (circle == greenCircle && greenBlinkTimeline != null) {
            existingTimeline = greenBlinkTimeline;
        }

        if (existingTimeline != null) {
            existingTimeline.stop();
        }

        // Через указанное время гасим этот цвет (возвращаем темный)
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(seconds), e -> {
                    circle.setFill(Color.rgb(40, 40, 40));
                    // Если гаснет красный - снимаем флаг инцидента
                    if (colorName.equals("red")) {
                        isIncidentActive = false;
                    }
                })
        );
        timeline.setCycleCount(1);
        timeline.play();

        // Сохраняем таймер в зависимости от круга
        if (circle == redCircle) {
            redBlinkTimeline = timeline;
        } else if (circle == yellowCircle) {
            yellowBlinkTimeline = timeline;
        } else if (circle == greenCircle) {
            greenBlinkTimeline = timeline;
        }
    }

    private void turnOffAllLights() {
        redCircle.setFill(Color.rgb(40, 40, 40));
        yellowCircle.setFill(Color.rgb(40, 40, 40));
        greenCircle.setFill(Color.rgb(40, 40, 40));
    }

    // Метод для управления очередью мониторинга (без автоотключения)
    private void setQueueColor(Ellipse circle, Color color, String colorName) {
        // Просто устанавливаем цвет без таймера
        circle.setFill(color);
    }


    public void handleServerMessage(String message) {
        switch (message) {
            case "RED_BLINK" -> blinkColor(redCircle, Color.RED, 120, "red");
            case "YELLOW_BLINK" -> blinkColor(yellowCircle, Color.YELLOW, 120, "yellow");
            case "GREEN_BLINK_INCIDENT" -> blinkColor(redCircle, Color.LIMEGREEN, 100, "green_incident");  // Зеленый в кружке инцидента
            case "GREEN_BLINK_ALERT" -> blinkColor(yellowCircle, Color.LIMEGREEN, 60, "green_alert");  // Зеленый в кружке алерта
            case "QUEUE_RED" -> setQueueColor(greenCircle, Color.RED, "queue_red");
            case "QUEUE_GREEN" -> setQueueColor(greenCircle, Color.LIMEGREEN, "queue_green");
        }
    }
}

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
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.util.Pair;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class TrafficLightApp extends Application {
    private TrafficLightSpec wsClient = new TrafficLightSpec();

    private Circle redCircle;
    private Circle yellowCircle;
    private Circle greenCircle;

    private Timeline redBlinkTimeline;
    private Timeline yellowBlinkTimeline;
    private Timeline greenBlinkTimeline;

    private boolean isIncidentActive = false;
    private boolean isAdmin = false;

    // Configuration loaded from client.properties
    private String serverAddress = "10.10.90.170";
    private int serverPort = 52521;
    private String adminLogin = "";
    private String adminPassword = "";
    private int windowWidth = 130;
    private int windowHeight = 250;
    private String configPath = "client.properties";  // Путь к конфигу для сохранения


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

        // Пробуем загрузить client.properties из разных мест
        String[] configPaths = {
            "client.properties",
            System.getProperty("user.home") + "/AppData/Local/TrafficLightClient/client.properties",
            System.getProperty("user.dir") + "/client.properties"
        };

        for (String path : configPaths) {
            try (FileInputStream fis = new FileInputStream(path)) {
                props.load(fis);
                System.out.println("Loaded configuration from: " + path);
                configPath = path;  // Сохраняем путь для записи
                break;
            } catch (IOException e) {
                // Пробуем следующий путь
            }
        }

        // Загружаем параметры из конфигурации (приоритет: переменные окружения > properties файл)
        serverAddress = getConfigValue(props, "server.address", "SERVER_ADDRESS", serverAddress);
        serverPort = Integer.parseInt(getConfigValue(props, "server.port", "SERVER_PORT", String.valueOf(serverPort)));

        adminLogin = getConfigValue(props, "admin.login", "ADMIN_LOGIN", "");
        adminPassword = getConfigValue(props, "admin.password", "ADMIN_PASSWORD", "");

        windowWidth = Integer.parseInt(getConfigValue(props, "window.width", "WINDOW_WIDTH", String.valueOf(windowWidth)));
        windowHeight = Integer.parseInt(getConfigValue(props, "window.height", "WINDOW_HEIGHT", String.valueOf(windowHeight)));

        // Выводим адрес сервера (может уже содержать ws://)
        if (serverAddress.startsWith("ws://") || serverAddress.startsWith("wss://")) {
            System.out.println("Server: " + serverAddress);
        } else {
            System.out.println("Server: ws://" + serverAddress + ":" + serverPort);
        }
        System.out.println("Window size: " + windowWidth + "x" + windowHeight);
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

            System.out.println("Размер окна сохранен: " + windowWidth + "x" + windowHeight);
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
            if (showLoginDialog()) {
                isAdmin = true;
                showTrafficLightStage();
                stage.close();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Неверный логин или пароль", ButtonType.OK);
                alert.showAndWait();
            }
        });

        specialistButton.setOnAction(e -> {
            isAdmin = false;
            showTrafficLightStage();
            stage.close();
        });
    }

    private boolean showLoginDialog() {
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
        return result.isPresent() &&
                adminLogin.equals(result.get().getKey()) &&
                adminPassword.equals(result.get().getValue());
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
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setResizable(false); // Запрещаем изменение размера
        stage.setAlwaysOnTop(true); // Окно всегда поверх всех других окон

        // Устанавливаем иконку приложения
        try {
            javafx.scene.image.Image icon = new javafx.scene.image.Image(getClass().getResourceAsStream("/44_85245.ico"));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Не удалось загрузить иконку: " + e.getMessage());
        }

        // Вычисляем масштаб на основе размера окна
        double scale = Math.min(windowWidth / 130.0, windowHeight / 250.0);
        int circleRadius = (int)(27 * scale);
        int fontSize = Math.max(8, (int)(11 * scale));

        // Инициализируем круги с адаптивным размером
        redCircle = new Circle(circleRadius, Color.rgb(40, 40, 40));
        yellowCircle = new Circle(circleRadius, Color.rgb(40, 40, 40));
        greenCircle = new Circle(circleRadius, Color.rgb(40, 40, 40));

        VBox trafficLight = new VBox(5 * scale);
        trafficLight.setAlignment(Pos.CENTER);
        trafficLight.setStyle(
                "-fx-padding: " + (8 * scale) + ";" +
                        "-fx-background-color: #5a5a5a;" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-color: #777777;" +
                        "-fx-border-width: 2;" +
                        "-fx-border-radius: 12;"
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
        redCircle.setStroke(Color.BLACK);
        redCircle.setStrokeWidth(2);

        yellowCircle.setStroke(Color.BLACK);
        yellowCircle.setStrokeWidth(2);

        greenCircle.setStroke(Color.BLACK);
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
                    setQueueColor(greenCircle, Color.RED, "queue_red");
                    wsClient.sendMessage("QUEUE_RED");
                } else {
                    // Если красный - включаем зеленый (очередь не нагружена)
                    setQueueColor(greenCircle, Color.LIMEGREEN, "queue_green");
                    wsClient.sendMessage("QUEUE_GREEN");
                }
            });
        }

        Scene scene = new Scene(root, windowWidth, windowHeight);
        root.setStyle("-fx-background-color: #000000;");
        stage.setScene(scene);

        // Автопозиция в правом нижнем углу
        double screenWidth = Screen.getPrimary().getBounds().getWidth();
        double screenHeight = Screen.getPrimary().getBounds().getHeight();
        stage.setX(screenWidth - windowWidth - 40);  // 40px отступ от края
        stage.setY(screenHeight - windowHeight - 70);  // 70px отступ от низа (панель задач)

        stage.show();
    }

    private void blinkColor(Circle circle, Color color, int seconds, String colorName) {
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
    private void setQueueColor(Circle circle, Color color, String colorName) {
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

package incuat.kg.svetoofor;

import incuat.kg.svetoofor.jira.JiraClient;
import incuat.kg.svetoofor.jira.JiraPoller;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Headless launcher для сервера светофора
 * Запускает WebSocket сервер на порту 52521 и JIRA интеграцию
 */
public class ServerLauncher {
    private static final int DEFAULT_PORT = 52521;
    private static final DateTimeFormatter LOG_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static PrintWriter logWriter;

    static {
        try {
            // Создаём лог в рабочей директории (WorkingDirectory из systemd service)
            String logPath = "svetoofor-server.log";
            File logFile = new File(logPath);
            logWriter = new PrintWriter(new FileWriter(logFile, true), true);
        } catch (IOException e) {
            System.err.println("Не удалось создать файл логов: " + e.getMessage());
        }
    }

    private static void log(String message) {
        String timestamp = LocalDateTime.now().format(LOG_FORMATTER);
        String logMessage = "[" + timestamp + "] " + message;
        log(logMessage);
        if (logWriter != null) {
            logWriter.println(logMessage);
        }
    }

    public static void main(String[] args) {
        log("=== Traffic Light Server (JIRA Only) ===");

        // Загружаем конфигурацию
        Properties config = loadConfig();
        int port = Integer.parseInt(config.getProperty("server.port", String.valueOf(DEFAULT_PORT)));

        // Запускаем WebSocket сервер
        TrafficLightServer server = new TrafficLightServer(port);
        server.start();
        log("WebSocket server started on port " + port);

        // Запускаем JIRA интеграцию (если настроена)
        startJiraIntegration(config, server);

        // Держим процесс живым
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("Shutting down server...");
            try {
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        log("Server is running. Press Ctrl+C to stop.");
    }

    private static Properties loadConfig() {
        Properties props = new Properties();

        // Пробуем загрузить server.properties
        try (FileInputStream fis = new FileInputStream("server.properties")) {
            props.load(fis);
            log("Loaded server.properties");
            return props;
        } catch (IOException e) {
            log("server.properties not found, using defaults");
        }

        // Дефолтные значения
        props.setProperty("server.port", String.valueOf(DEFAULT_PORT));
        return props;
    }

    private static String getConfigValue(Properties props, String propKey, String envKey) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty() && !envValue.startsWith("${")) {
            return envValue;
        }

        String propValue = props.getProperty(propKey);
        if (propValue != null && !propValue.isEmpty() && !propValue.startsWith("${")) {
            return propValue;
        }

        return null;
    }

    private static void startJiraIntegration(Properties config, TrafficLightServer server) {
        // Приоритет: переменные окружения > properties файл
        String jiraUrl = getConfigValue(config, "jira.url", "JIRA_URL");
        String jiraUsername = getConfigValue(config, "jira.username", "JIRA_USERNAME");
        String jiraPassword = getConfigValue(config, "jira.password", "JIRA_PASSWORD");

        if (jiraUrl == null || jiraUrl.isEmpty() ||
            jiraUsername == null || jiraUsername.isEmpty() ||
            jiraPassword == null || jiraPassword.isEmpty()) {
            log("JIRA settings not found, skipping JIRA integration");
            log("Configure jira.url, jira.username, jira.password in server.properties to enable");
            return;
        }

        // Проверяем наличие кастомного JQL запроса
        String customJql = config.getProperty("jira.jql");

        // Если нет кастомного JQL, формируем запрос по старым параметрам (обратная совместимость)
        if (customJql == null || customJql.isEmpty() || customJql.startsWith("${")) {
            String issueType = config.getProperty("jira.issue.type", "11206");
            customJql = "issuetype = " + issueType + " AND status NOT IN (Closed,Resolved,Done)";
            log("Используется автоматически сформированный JQL запрос");
        } else {
            log("Используется кастомный JQL запрос из конфигурации");
        }

        int pollInterval = Integer.parseInt(config.getProperty("jira.poll.interval", "5"));

        try {
            log("Starting JIRA integration...");
            log("JIRA URL: " + jiraUrl);
            log("JIRA Username: " + jiraUsername);
            log("JQL Query: " + customJql);
            log("Poll interval: " + pollInterval + " minutes");

            JiraClient jiraClient = new JiraClient(jiraUrl, jiraUsername, jiraPassword);
            JiraPoller jiraPoller = new JiraPoller(jiraClient, server, customJql, pollInterval);
            jiraPoller.start();

            log("JIRA integration started successfully");
        } catch (Exception e) {
            System.err.println("Failed to start JIRA integration: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

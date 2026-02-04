package incuat.kg.svetoofor.jira;

import incuat.kg.svetoofor.TrafficLightServer;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Опрашивает JIRA каждые N минут и отправляет сигналы светофора
 */
public class JiraPoller {

    private final JiraClient jiraClient;
    private final TrafficLightServer trafficLightServer;
    private final ScheduledExecutorService scheduler;

    private final String customJql;
    private final int pollIntervalMinutes;

    // Отслеживание обработанных инцидентов
    private final Set<String> processedIssues = Collections.synchronizedSet(new HashSet<>());

    // Отслеживание активных (открытых) инцидентов
    private final Map<String, String> activeIncidents = Collections.synchronizedMap(new HashMap<>());

    // Флаг первого запуска - чтобы не отправлять сигналы на все существующие задачи
    private boolean isFirstRun = true;

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

    public JiraPoller(JiraClient jiraClient,
                      TrafficLightServer trafficLightServer,
                      String customJql, int pollIntervalMinutes) {
        this.jiraClient = jiraClient;
        this.trafficLightServer = trafficLightServer;
        this.customJql = customJql;
        this.pollIntervalMinutes = pollIntervalMinutes;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    /**
     * Запуск периодического опроса
     */
    public void start() {
        log("Запуск JIRA Poller...");
        log("Интервал опроса: " + pollIntervalMinutes + " минут");
        log("JQL запрос: " + customJql);

        // Проверка подключения
        if (!jiraClient.testConnection()) {
            System.err.println("❌ Не удалось подключиться к JIRA!");
            System.err.println("Проверьте настройки jira.url, jira.username, jira.password");
            return;
        }

        log("✅ Подключение к JIRA успешно");

        // Запускаем первый опрос сразу, затем по расписанию
        scheduler.schedule(this::poll, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::poll, pollIntervalMinutes, pollIntervalMinutes, TimeUnit.MINUTES);
    }

    /**
     * Опрос JIRA
     */
    private void poll() {
        try {
            if (isFirstRun) {
                log("\n🔍 Первый запуск - загрузка существующих задач (без отправки сигналов)...");
            } else {
                log("\n🔍 Опрос JIRA на наличие новых инцидентов/алертов...");
            }

            JiraSearchResult result = jiraClient.searchByCustomJql(customJql);

            if (result.getIssues() == null || result.getIssues().isEmpty()) {
                log("   Новых инцидентов/алертов не найдено");
                if (isFirstRun) {
                    isFirstRun = false;
                    log("✅ Инициализация завершена. Начинаем мониторинг новых задач...");
                }
                return;
            }

            log("   Найдено записей: " + result.getIssues().size());

            int newCount = 0;
            for (JiraIssue issue : result.getIssues()) {
                if (processIssue(issue)) {
                    newCount++;
                }
            }

            if (isFirstRun) {
                log("✅ Инициализация завершена. Загружено существующих задач: " + result.getIssues().size());
                log("   Начинаем мониторинг новых задач...");
                isFirstRun = false;
            } else if (newCount > 0) {
                log("✅ Обработано новых записей: " + newCount);
            }

        } catch (IOException e) {
            System.err.println("❌ Ошибка при опросе JIRA: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Неожиданная ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Обработка одного инцидента
     *
     * @param issue Инцидент из JIRA
     * @return true если это новый инцидент
     */
    private boolean processIssue(JiraIssue issue) {
        String key = issue.getKey();
        String currentStatus = issue.getFields() != null && issue.getFields().getStatus() != null
                ? issue.getFields().getStatus().getName() : "Unknown";

        // Определяем тип задачи
        String issueTypeId = issue.getFields() != null && issue.getFields().getIssuetype() != null
                ? issue.getFields().getIssuetype().getId() : "unknown";
        String issueTypeName = issue.getFields() != null && issue.getFields().getIssuetype() != null
                ? issue.getFields().getIssuetype().getName() : "unknown";

        boolean isIncident = "11206".equals(issueTypeId);
        boolean isAlert = "13802".equals(issueTypeId);

        // Проверяем статусы
        boolean isActive = isActiveStatus(currentStatus);
        boolean isResolved = isResolvedStatus(currentStatus);

        // Если задача уже была активной
        if (activeIncidents.containsKey(key)) {
            String previousStatus = activeIncidents.get(key);

            // Если статус изменился на решенный (ОДИН РАЗ показываем зеленый)
            if (!isResolvedStatus(previousStatus) && isResolved) {
                activeIncidents.remove(key);
                processedIssues.add(key);

                log("✅ Задача решена: " + key + " (тип: " + issueTypeName + ")");
                log("   Статус: " + previousStatus + " → " + currentStatus);

                // Отправляем зеленый сигнал ОДИН РАЗ (30 секунд) в соответствующий кружок
                if (trafficLightServer != null) {
                    if (isIncident) {
                        log("   🟢 Отправка сигнала: GREEN_BLINK_INCIDENT (решение инцидента)");
                        trafficLightServer.broadcast("GREEN_BLINK_INCIDENT");
                    } else if (isAlert) {
                        log("   🟢 Отправка сигнала: GREEN_BLINK_ALERT (решение алерта)");
                        trafficLightServer.broadcast("GREEN_BLINK_ALERT");
                    } else {
                        log("   🟢 Отправка сигнала: GREEN_BLINK (решение)");
                        trafficLightServer.broadcast("GREEN_BLINK");
                    }
                }

                return true;
            }

            // Если статус остается активным - просто обновляем, БЕЗ повторного сигнала
            if (isActive) {
                if (!previousStatus.equals(currentStatus)) {
                    activeIncidents.put(key, currentStatus);
                    log("🔄 Обновление статуса: " + key);
                    log("   Статус: " + previousStatus + " → " + currentStatus + " (без повторного сигнала)");
                }
                return false; // НЕ отправляем повторный сигнал
            }

            return false;
        }

        // Пропускаем уже обработанные (были решены ранее)
        if (processedIssues.contains(key)) {
            return false;
        }

        // Если попалась уже решенная задача - просто запоминаем, без сигнала
        if (isResolved) {
            processedIssues.add(key);
            return false;
        }

        // Новая активная задача
        if (isActive) {
            processedIssues.add(key);
            activeIncidents.put(key, currentStatus);

            // При первом запуске НЕ отправляем сигналы - только запоминаем задачи
            if (isFirstRun) {
                // Только логируем без отправки сигнала
                log("   📋 Существующая задача: " + key + " (тип: " + issueTypeName + ", ID: " + issueTypeId + ", статус: " + currentStatus + ")");
                return false;
            }

            // После первого запуска - отправляем сигнал на НОВЫЕ задачи
            log("📋 Новая активная задача: " + key + " (тип: " + issueTypeName + ", ID: " + issueTypeId + ")");

            // Формируем сообщение для консоли
            String message = formatIncidentMessage(issue);
            log("   " + message);

            // Сигнал светофора в зависимости от типа - ОДИН РАЗ
            if (trafficLightServer != null) {
                if (isIncident) {
                    log("   🔴 Отправка сигнала: RED_BLINK (инцидент) - ОДИН РАЗ");
                    trafficLightServer.broadcast("RED_BLINK");
                } else if (isAlert) {
                    log("   🟡 Отправка сигнала: YELLOW_BLINK (алерт) - ОДИН РАЗ");
                    trafficLightServer.broadcast("YELLOW_BLINK");
                } else {
                    log("   ⚪ Неизвестный тип, отправка RED_BLINK");
                    trafficLightServer.broadcast("RED_BLINK");
                }
            }

            return true;
        }

        // Задача не активная и не решенная - игнорируем
        return false;
    }

    /**
     * Проверка, является ли статус активным (требует красного светофора)
     * Активные статусы: Создан, Назначен, Исполнитель, Руководитель
     */
    private boolean isActiveStatus(String status) {
        if (status == null) {
            return false;
        }

        String statusLower = status.toLowerCase();

        return statusLower.contains("создан") ||
               statusLower.contains("назначен") ||
               statusLower.contains("исполнитель") ||
               statusLower.contains("руководитель") ||
               statusLower.contains("created") ||
               statusLower.contains("assigned") ||
               statusLower.contains("in progress") ||
               statusLower.contains("в работе");
    }

    /**
     * Проверка, является ли статус решенным (требует зеленого светофора)
     * Решенные статусы: В ожидании, Решен, Закрыто, Отклонен, Отменен, Завершен
     */
    private boolean isResolvedStatus(String status) {
        if (status == null) {
            return false;
        }

        String statusLower = status.toLowerCase();

        return statusLower.contains("ожидании") ||
               statusLower.contains("решен") ||
               statusLower.contains("закрыт") ||
               statusLower.contains("отклонен") ||
               statusLower.contains("отменен") ||
               statusLower.contains("завершен") ||
               statusLower.contains("pending") ||
               statusLower.contains("resolved") ||
               statusLower.contains("closed") ||
               statusLower.contains("rejected") ||
               statusLower.contains("declined") ||
               statusLower.contains("canceled") ||
               statusLower.contains("cancelled") ||
               statusLower.contains("done") ||
               statusLower.contains("finished") ||
               statusLower.contains("completed");
    }

    /**
     * Форматирование инцидента в текст для консоли
     */
    private String formatIncidentMessage(JiraIssue issue) {
        StringBuilder sb = new StringBuilder();

        JiraIssue.JiraFields fields = issue.getFields();

        sb.append("Инцидент: ").append(fields.getSummary()).append(" | ");

        // Приоритет
        if (fields.getPriority() != null) {
            String priority = getPriorityLevel(issue);
            sb.append("Приоритет: ").append(priority).append(" | ");
        }

        // Статус
        if (fields.getStatus() != null) {
            sb.append("Статус: ").append(fields.getStatus().getName()).append(" | ");
        }

        // Автор
        if (fields.getAuthor() != null) {
            sb.append("Автор: ").append(fields.getAuthor().getDisplayName());
        }

        return sb.toString();
    }

    /**
     * Определение уровня приоритета
     */
    private String getPriorityLevel(JiraIssue issue) {
        if (issue.getFields() == null || issue.getFields().getPriority() == null) {
            return "средний";
        }

        String priorityName = issue.getFields().getPriority().getName().toLowerCase();
        String priorityId = issue.getFields().getPriority().getId();

        // По названию
        if (priorityName.contains("критич") || priorityName.contains("critical")) {
            return "критичный";
        }
        if (priorityName.contains("высок") || priorityName.contains("high")) {
            return "высокий";
        }
        if (priorityName.contains("средн") || priorityName.contains("medium")) {
            return "средний";
        }
        if (priorityName.contains("низк") || priorityName.contains("low")) {
            return "низкий";
        }

        // По ID (1-2 высокий, 3 средний, 4-5 низкий)
        try {
            int id = Integer.parseInt(priorityId);
            if (id <= 2) return "высокий";
            if (id == 3) return "средний";
            if (id >= 4) return "низкий";
        } catch (NumberFormatException e) {
            // Игнорируем
        }

        return "средний";
    }

    /**
     * Остановка опроса
     */
    public void stop() {
        log("Остановка JIRA Poller...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        jiraClient.close();
    }

    /**
     * Очистка истории обработанных инцидентов (для освобождения памяти)
     */
    public void clearProcessedIssues() {
        processedIssues.clear();
        activeIncidents.clear();
        log("История обработанных инцидентов очищена");
    }
}

/**
 * Простейший потокобезопасный логгер.
 * Используется для наглядного отображения работы многопоточной системы.
 */
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    // Формат для вывода времени (часы:минуты:секунды.миллисекунды)
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    // Синхронизированный метод логирования для потокобезопасности
    public static synchronized void log(String message) {
        // Вывод: [время] [имя потока] сообщение
        System.out.printf("%s [%s] %s%n", LocalTime.now().format(FORMAT), Thread.currentThread().getName(), message);
    }
}

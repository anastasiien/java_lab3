/**
 * Инициализирует очередь заказов, таксопарк и запускает основные потоки системы.
 */
import java.util.*;
import java.util.concurrent.*;

public class Main {
    public static void main(String[] args) {
        BlockingQueue<Order> queue = new LinkedBlockingQueue<>();
        List<Taxi> taxis = new ArrayList<>();

        // Создание и запуск потоков такси
        for (int i = 0; i < 3; i++) {
            Taxi taxi = new Taxi(i, queue);
            taxis.add(taxi);
            new Thread(taxi, "Thread-Taxi-" + i).start();
        }
        // Запуск диспетчера и консольного ввода
        new Thread(new Dispatcher(queue, taxis), "Thread-Dispatcher").start();
        new Thread(new ConsoleInput(queue, taxis), "Thread-Console").start();

        Logger.log("Система такси запущена. Команды: order, status, history, test, cancel, exit");
    }
}

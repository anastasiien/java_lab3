/**
 * Диспетчер заказов.
 * Отслеживает очередь заказов и назначает их наиболее подходящему свободному такси.
 */
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class Dispatcher implements Runnable {
    private final BlockingQueue<Order> orders;  // Очередь заказов
    private final List<Taxi> taxis; // Список такси

    public Dispatcher(BlockingQueue<Order> orders, List<Taxi> taxis) {
        this.orders = orders;
        this.taxis = taxis;
    }

    @Override
    public void run() {
        int retryCount = 0; // Счетчик неудачных попыток назначить заказ
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Order order = orders.peek();  // Смотрим на первый заказ без извлечения

                // Поиск лучшего доступного такси для заказа
                if (order != null) {
                    Taxi bestTaxi = taxis.stream()
                            .filter(Taxi::isAvailable) // Только свободные
                            .filter(t -> t.canHandleOrder(order.fromX, order.fromY, order.toX, order.toY)) // С достаточным зарядом
                            .min(Comparator.comparingInt(t -> t.distanceTo(order.fromX, order.fromY))) // Ближайшее
                            .orElse(null);

                    if (bestTaxi != null) {
                        orders.poll(); // Извлекаем заказ из очереди
                        bestTaxi.assignOrder(order); // Назначаем такси
                        retryCount = 0; // Сброс счетчика
                        Logger.log("Диспетчер: Заказ назначен Taxi-" + bestTaxi.getId());
                    } else {
                        retryCount++;
                        // После 5 неудачных попыток удаляем невыполнимый заказ
                        if (retryCount >= 5) {
                            Logger.log("Диспетчер: Внимание! Заказ [" + order.fromX + "," + order.fromY + "] невозможно выполнить (не хватает заряда даже при 100%). Удаление заказа.");
                            orders.poll();
                            retryCount = 0;
                        }
                        Thread.sleep(1500); // Пауза перед следующей попыткой
                    }
                } else {
                    retryCount = 0;
                    Thread.sleep(500); // Пауза при пустой очереди
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

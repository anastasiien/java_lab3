/**
 * Клиентский заказ на поездку.
 * Содержит координаты маршрута, время создания и потокобезопасный флаг отмены.
 */
import java.util.concurrent.atomic.AtomicBoolean;

public class Order {
    // Координаты начала и конца поездки (final - неизменяемые после создания)
    public final int fromX;
    public final int fromY;
    public final int toX;
    public final int toY;
    public final long timestamp; // Время создания заказа

    // Атомарный флаг отмены заказа (потокобезопасный)
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public Order(int fromX, int fromY, int toX, int toY) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
        this.timestamp = System.currentTimeMillis();
    }

    // Метод для отмены заказа (устанавливает флаг cancelled в true)
    public void cancel() {
        cancelled.set(true);
    }

    // Проверка, отменен ли заказ
    public boolean isCancelled() {
        return cancelled.get();
    }
}

/**
 * Беспилотное такси.
 * Работает в отдельном потоке, обрабатывает заказы и управляет своим состоянием,
 * зарядом аккумулятора и историей поездок.
 */
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.BlockingQueue;

public class Taxi implements Runnable {
    private final int id; // Уникальный идентификатор такси
    private final BlockingQueue<Order> orders;
    private final ReentrantLock lock = new ReentrantLock(); // Блокировка для синхронизации
    private final Condition stateChanged = lock.newCondition(); // Условная переменная для ожидания изменений состояния
    private final List<String> history = new ArrayList<>(); // История поездок и зарядок

    private TaxiState state = TaxiState.FREE; // Текущее состояние такси
    private Order currentOrder; // Текущий выполняемый заказ
    private int x = 0; // Текущая координата X
    private int y = 0; // Текущая координата Y
    private int battery = 100; // Уровень заряда (в процентах)

    public Taxi(int id, BlockingQueue<Order> orders) {
        this.id = id;
        this.orders = orders;
    }


    public int getId() { return id; }

    // Проверка, доступно ли такси для новых заказов
    public boolean isAvailable() {
        lock.lock();
        try { return state == TaxiState.FREE; }
        finally { lock.unlock(); }
    }

    // Расчет расстояния от такси до точки
    public int distanceTo(int ox, int oy) {
        lock.lock();
        try { return Math.abs(x - ox) + Math.abs(y - oy); }
        finally { lock.unlock(); }
    }

    // Назначение заказа такси
    public void assignOrder(Order order) {
        lock.lock();
        try {
            this.currentOrder = order;
            this.state = TaxiState.GOING_TO_CLIENT;
            stateChanged.signal(); // Сигнализируем потоку такси о новом состоянии
        } finally { lock.unlock(); }
    }

    @Override
    public void run() {
        try {
            // Основной цикл выполнения такси
            while (!Thread.currentThread().isInterrupted()) {
                TaxiState currentState;
                lock.lock();
                try {
                    // Если такси свободно, проверяем необходимость зарядки
                    while (state == TaxiState.FREE) {
                        if (shouldGoToCharge()) {
                            state = TaxiState.CHARGING;
                            break; // Переходим к зарядке
                        }
                        // Ожидаем сигнала от диспетчера или таймаут 3 секунды
                        stateChanged.await(3, java.util.concurrent.TimeUnit.SECONDS);
                    }
                    currentState = this.state;
                } finally {
                    lock.unlock();
                }
                // Выполнение действий в зависимости от состояния
                switch (currentState) {
                    case GOING_TO_CLIENT -> goToClient();
                    case DRIVING -> driveClient();
                    case CHARGING -> charge();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Восстанавливаем флаг прерывания
        }
    }

    // Логика принятия решения о необходимости зарядки
    private boolean shouldGoToCharge() {
        // Если батарея почти полная, зарядка не нужна
        if (battery >= 95) return false;
        // Проверяем следующий заказ в очереди
        Order nextOrder = orders.peek();
        // Если заказ есть, но на него не хватает заряда - едем заряжаться
        if (nextOrder != null) {
            return !canHandleOrder(nextOrder.fromX, nextOrder.fromY, nextOrder.toX, nextOrder.toY);
        }
        // Если заказов нет и заряд низкий - едем заряжаться (на всякий случай)
        return battery < 50;
    }

    // Геттеры с синхронизацией
    public Order getCurrentOrder() {
        lock.lock();
        try { return currentOrder; }
        finally { lock.unlock(); }
    }

    public TaxiState getState() {
        lock.lock();
        try { return state; }
        finally { lock.unlock(); }
    }

    // Движение к клиенту
    private void goToClient() {
        // Расчет расстояния до клиента
        int dist = distanceTo(currentOrder.fromX, currentOrder.fromY);
        Logger.log("Taxi-" + id + " едет к клиенту (" + dist + " км)");
        sleep(2000); // Имитация времени поездки

        lock.lock();
        try {
            battery -= dist * 5; // Расход заряда (5% за единицу расстояния)
            x = currentOrder.fromX; // Обновление позиции
            y = currentOrder.fromY;

            // Проверка отмены заказа
            if (currentOrder.isCancelled()) {
                Logger.log("Taxi-" + id + ": заказ отменён.");
                currentOrder = null;
                state = TaxiState.FREE;
                return;
            }
            state = TaxiState.DRIVING;  // Переход к перевозке клиента
        } finally { lock.unlock(); }
    }

    // Перевозка клиента к точке назначения
    private void driveClient() {
        int dist = Math.abs(currentOrder.fromX - currentOrder.toX) + Math.abs(currentOrder.fromY - currentOrder.toY);
        Logger.log("Taxi-" + id + " везёт клиента (дистанция " + dist + ")");
        sleep(dist * 1000L); // Время поездки пропорционально расстоянию

        lock.lock();
        try {
            battery -= dist * 5; // Расход заряда
            x = currentOrder.toX; // Обновление позиции
            y = currentOrder.toY;

            addToHistory(currentOrder); // Запись в историю

            Logger.log("Taxi-" + id + " прибыл. Остаток заряда: " + battery + "%");
            currentOrder = null;
            // Если заряд низкий - на зарядку, иначе - свободен
            state = (battery < 20) ? TaxiState.CHARGING : TaxiState.FREE;
        } finally { lock.unlock(); }
    }

    // Получение копии истории (для потокобезопасности)
    public List<String> getHistory() {
        lock.lock();
        try {
            return new ArrayList<>(history);
        } finally {
            lock.unlock();
        }
    }

    // Добавление записи в историю
    private void addToHistory(Order order) {
        int dist = Math.abs(order.fromX - order.toX) + Math.abs(order.fromY - order.toY);
        String record = String.format("[%s] Из (%d,%d) в (%d,%d) | Дистанция: %d | Остаток заряда: %d%%",
                java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                order.fromX, order.fromY, order.toX, order.toY, dist, battery);
        history.add(record);
    }

    // Процесс зарядки
    private void charge() {
        // Запись начала зарядки
        lock.lock();
        try {
            String startChargeRecord = String.format("[%s] >>> Ушел на зарядку (остаток был %d%%)",
                    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")), battery);
            history.add(startChargeRecord);
        } finally {
            lock.unlock();
        }

        Logger.log("Taxi-" + id + " на зарядке");
        sleep(5000); // Имитация времени зарядки

        lock.lock();
        try {
            battery = 100; // Полная зарядка
            state = TaxiState.FREE;  // Возврат в свободное состояние

            // Запись окончания зарядки
            String endChargeRecord = String.format("[%s] <<< Зарядка завершена (заряд 100%%)",
                    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            history.add(endChargeRecord);

            Logger.log("Taxi-" + id + " заряжен.");
        } finally {
            lock.unlock();
        }
    }

    // Вспомогательный метод для имитации задержки
    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // Получение статуса такси в виде строки
    public String status() {
        lock.lock();
        try { return state + " | Батарея: " + battery + "% | Позиция: (" + x + "," + y + ")"; }
        finally { lock.unlock(); }
    }

    // Проверка возможности выполнения заказа с учетом заряда
    public boolean canHandleOrder(int startX, int startY, int endX, int endY) {
        lock.lock();
        try {
            int distToClient = Math.abs(x - startX) + Math.abs(y - startY);
            int distToDest = Math.abs(startX - endX) + Math.abs(startY - endY);

            int totalEnergyNeeded = (distToClient + distToDest) * 5;

            return battery >= totalEnergyNeeded;
        } finally {
            lock.unlock();
        }
    }
}

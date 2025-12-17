/**
 * Консольный интерфейс управления системой.
 * Позволяет создавать заказы, управлять тестированием и получать информацию о системе.
 */
import java.util.List;
import java.util.Scanner;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class ConsoleInput implements Runnable {

    private final BlockingQueue<Order> orders;
    private final List<Taxi> taxis;
    private Order lastOrder;  // Последний созданный заказ
    private final Random random = new Random();
    private volatile boolean stressTestRunning = false; // Флаг для контроля запуска теста

    public ConsoleInput(BlockingQueue<Order> orders, List<Taxi> taxis) {
        this.orders = orders;
        this.taxis = taxis;
    }

    // Вывод меню команд
    private void printMenu() {
        System.out.println("\n=== КОМАНДЫ СИСТЕМЫ ===");
        System.out.println("order x1 y1 x2 y2 : создать заказ");
        System.out.println("test              : запустить тест");
        System.out.println("status            : состояние таксопарка");
        System.out.println("history           : история всех поездок и зарядок");
        System.out.println("cancel            : отменить заказ");
        System.out.println("exit              : выход");
        System.out.println("=======================");
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        printMenu();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                String command = parts[0].toLowerCase();

                // Обработка команд пользователя
                switch (command) {
                    case "test" -> startStressTest();
                    case "order" -> handleOrderCommand(parts);
                    case "cancel" -> cancelLast();
                    case "status" -> printStatus();
                    case "history" -> printHistory();
                    case "exit" -> System.exit(0);
                    default -> System.out.println("Неизвестная команда.");
                }
            } catch (Exception e) {
                System.err.println("Ошибка ввода: " + e.getMessage());
            }
        }
    }

    // Запуск теста системы
    private void startStressTest() {
        if (stressTestRunning) {
            System.out.println("Тест уже выполняется!");
            return;
        }
        stressTestRunning = true;

        new Thread(() -> {
            try {
                Logger.log("=== Запуск теста ===");

                // Фаза средней загруженности
                System.out.println("\n>>> Средняя загруженность");
                for (int i = 0; i < 3; i++) {
                    generateSingleRandomOrder();
                    Thread.sleep(10000);
                }

                // Фаза высокой загруженности
                System.out.println("\n>>> Высокая загруженность");
                for (int i = 0; i < 10; i++) {
                    generateSingleRandomOrder();
                    Thread.sleep(2000);
                }

                // Фаза малой загруженности
                System.out.println("\n>>> Малая загруженность");
                for (int i = 0; i < 2; i++) {
                    generateSingleRandomOrder();
                    Thread.sleep(5000);
                }

                // Ожидание завершения всех заказов
                while (!orders.isEmpty() || taxis.stream().anyMatch(t ->
                        t.getState() != TaxiState.FREE)) {
                    Thread.sleep(500);
                }

                Logger.log("=== Тест завершен ===");
                System.out.println("Система вернулась в обычный режим.");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logger.log("Тест прерван.");
            } finally {
                stressTestRunning = false;
                printMenu();
            }
        }, "StressTest").start();
    }


    // Генерация случайного заказа
    private void generateSingleRandomOrder() {
        int x1 = random.nextInt(10), y1 = random.nextInt(10);
        int x2 = random.nextInt(10), y2 = random.nextInt(10);
        int dist = Math.abs(x1 - x2) + Math.abs(y1 - y2);

        // Проверка на допустимую дистанцию
        if (dist > 0 && dist < 19) {
            Order order = new Order(x1, y1, x2, y2);
            orders.add(order);
            Logger.log(String.format("[TEST] Новый заказ: из (%d,%d) в (%d,%d)", x1, y1, x2, y2));

            // Случайная отмена с вероятностью 15%
            if (random.nextInt(100) < 15) {
                new Thread(() -> {
                    try {
                        Thread.sleep(1500);
                        order.cancel();
                        Logger.log("[TEST] Клиент передумал! Заказ из (" + x1 + "," + y1 + ") отменен.");
                    } catch (InterruptedException ignored) {}
                }).start();
            }
        }
    }

    // Обработка команды создания заказа
    private void handleOrderCommand(String[] parts) {
        if (parts.length < 5) {
            System.err.println("Ошибка: Формат: order x1 y1 x2 y2");
            return;
        }
        try {
            int x1 = Integer.parseInt(parts[1]);
            int y1 = Integer.parseInt(parts[2]);
            int x2 = Integer.parseInt(parts[3]);
            int y2 = Integer.parseInt(parts[4]);
            int dist = Math.abs(x1 - x2) + Math.abs(y1 - y2);

            // Проверка максимальной дистанции (заряд на 100%)
            if (dist * 5 >= 100) {
                System.err.println("Ошибка: Дистанция " + dist + " слишком велика!");
                return;
            }

            Order order = new Order(x1, y1, x2, y2);
            orders.add(order);
            lastOrder = order;
            Logger.log(String.format("Ручной заказ: из (%d,%d) в (%d,%d). Дистанция: %d", x1, y1, x2, y2, dist));
        } catch (NumberFormatException e) {
            System.err.println("Ошибка: Координаты должны быть числами.");
        }
    }

    // Отмена последнего заказа
    private void cancelLast() {
        if (lastOrder == null) {
            System.out.println("Нет заказов для отмены.");
            return;
        }

        // Поиск такси, которому назначен заказ
        Taxi assignedTaxi = null;
        for (Taxi taxi : taxis) {
            if (taxi.getCurrentOrder() == lastOrder) {
                assignedTaxi = taxi;
                break;
            }
        }

        // Если заказ еще в очереди
        if (assignedTaxi == null) {
            lastOrder.cancel();
            Logger.log("Заказ еще в очереди. Помечен как отмененный.");
            return;
        }

        // Проверка состояния такси для определения возможности отмены
        TaxiState state = assignedTaxi.getState();
        if (state == TaxiState.DRIVING) {
            System.out.println("Ошибка: Нельзя отменить заказ, клиент уже находится в машине!");
        } else if (state == TaxiState.CHARGING) {
            System.out.println("Ошибка: Машина на зарядке, действие невозможно.");
        } else if (state == TaxiState.GOING_TO_CLIENT) {
            lastOrder.cancel();
            Logger.log("Заказ отменен! Taxi-" + assignedTaxi.getId() + ".");
        }
    }

    // Вывод статуса всех такси
    private void printStatus() {
        System.out.println("\n--- Статус такси ---");
        for (Taxi taxi : taxis) {
            System.out.println("Taxi-" + taxi.getId() + " : " + taxi.status());
        }
        System.out.println("Заказов в очереди: " + orders.size());
    }

    // Вывод истории всех такси
    private void printHistory() {
        System.out.println("\n========= История поездок =========");
        for (Taxi taxi : taxis) {
            List<String> records = taxi.getHistory();
            System.out.println("Taxi-" + taxi.getId() + " (" + records.size() + "):");
            if (records.isEmpty()) System.out.println("   - Пусто");
            else records.forEach(r -> System.out.println("   • " + r));
        }
        System.out.println("===================================\n");
    }
}

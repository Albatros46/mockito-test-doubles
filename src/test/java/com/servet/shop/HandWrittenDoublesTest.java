package com.servet.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.servet.shop.exception.PaymentFailedException;
import com.servet.shop.model.Order;
import com.servet.shop.model.PaymentResult;
import com.servet.shop.model.Product;
import com.servet.shop.repo.IdGenerator;
import com.servet.shop.repo.NotificationService;
import com.servet.shop.repo.OrderRepository;
import com.servet.shop.repo.PaymentGateway;
import com.servet.shop.repo.ProductRepository;
import com.servet.shop.service.OrderService;

/**
 * Mockito kullanmadan, 5 test double türünü elle yazarak öğrenmek için.
 * (Gerard Meszaros, "xUnit Test Patterns" sınıflandırması)
 */
@DisplayName("Elle yazılmış test double'lar .Manuell erstellte Test-Doubles")
class HandWrittenDoublesTest {

    /** DUMMY: Sadece parametre listesini doldurmak için verilir, hiç çağrılmamalıdır. */
    static class DummyNotificationService implements NotificationService {
        @Override
        public void sendOrderConfirmation(Order order) {
            throw new AssertionError("Dummy çağrılmamalıydı!");
        }
    }

    /** STUB: Her çağrıda önceden hazırlanmış sabit cevabı döner. */
    static class StubPaymentGateway implements PaymentGateway {
        private final PaymentResult result;

        StubPaymentGateway(PaymentResult result) {
            this.result = result;
        }

        @Override
        public PaymentResult charge(String customerEmail, BigDecimal amount) {
            return result;
        }
    }

    /** SPY: Kendisine yapılan çağrıları kaydeder; test sonradan bu kayda bakar. */
    static class SpyNotificationService implements NotificationService {
        final List<Order> sentOrders = new ArrayList<>();

        @Override
        public void sendOrderConfirmation(Order order) {
            sentOrders.add(order);
        }
    }

    /** MOCK: Beklenti önceden tanımlanır, verify() ile doğrulanır. */
    static class MockPaymentGateway implements PaymentGateway {
        private String expectedEmail;
        private BigDecimal expectedAmount;
        private int calls;

        void expectCharge(String email, BigDecimal amount) {
            this.expectedEmail = email;
            this.expectedAmount = amount;
        }

        @Override
        public PaymentResult charge(String customerEmail, BigDecimal amount) {
            calls++;
            if (!customerEmail.equals(expectedEmail) || amount.compareTo(expectedAmount) != 0) {
                throw new AssertionError("Beklenmeyen çağrı: " + customerEmail + ", " + amount);
            }
            return PaymentResult.success("TX-MOCK");
        }

        void verify() {
            if (calls != 1) {
                throw new AssertionError("charge() 1 kez bekleniyordu, " + calls + " kez çağrıldı");
            }
        }
    }

    /** FAKE: Gerçekten çalışan ama basitleştirilmiş implementasyon (veritabanı yerine Map). */
    static class FakeProductRepository implements ProductRepository {
        private final Map<String, Product> products = new HashMap<>();

        FakeProductRepository(Product... initialProducts) {
            for (Product product : initialProducts) {
                products.put(product.id(), product);
            }
        }

        @Override
        public Optional<Product> findById(String id) {
            return Optional.ofNullable(products.get(id));
        }

        @Override
        public void updateStock(String id, int newStock) {
            Product p = products.get(id);
            products.put(id, new Product(p.id(), p.name(), p.price(), newStock));
        }
    }

    static class FakeOrderRepository implements OrderRepository {
        final List<Order> orders = new ArrayList<>();

        @Override
        public Order save(Order order) {
            orders.add(order);
            return order;
        }
    }

    private static final String EMAIL = "servet@example.com";
    private static final Product LAPTOP = new Product("P1", "Laptop", new BigDecimal("1000.00"), 5);

    /** Tek metotlu (functional) interface'ler için lambda da bir stub'dır. */
    private final IdGenerator fixedIdGenerator = () -> "ORD-1";

    @Test
    @DisplayName("DUMMY: Validasyon hatasında bildirim servisine hiç dokunulmaz. \n DUMMY: Bei einem Validierungsfehler wird der Benachrichtigungsdienst überhaupt nicht berührt")
    void dummyIsNeverUsed() {
        OrderService service = new OrderService(new FakeProductRepository(), new FakeOrderRepository(),
                new StubPaymentGateway(PaymentResult.success("TX")), new DummyNotificationService(),
                new PriceCalculator(), fixedIdGenerator);

        assertThatIllegalArgumentException().isThrownBy(() -> service.placeOrder(EMAIL, "P1", 0));
    }

    @Test
    @DisplayName("STUB: Ödeme reddedilince sipariş kaydedilmez, stok değişmez. \n STUB: Wenn die Zahlung abgelehnt wird, wird die Bestellung nicht gespeichert und der Lagerbestand ändert sich nicht.")
    void stubReturnsFailedPayment() {
        FakeProductRepository products = new FakeProductRepository(LAPTOP);
        FakeOrderRepository orders = new FakeOrderRepository();
        OrderService service = new OrderService(products, orders,
                new StubPaymentGateway(PaymentResult.failed()), new SpyNotificationService(),
                new PriceCalculator(), fixedIdGenerator);

        assertThatThrownBy(() -> service.placeOrder(EMAIL, "P1", 1))
                .isInstanceOf(PaymentFailedException.class);
        assertThat(orders.orders).isEmpty();
        assertThat(products.findById("P1").orElseThrow().stock()).isEqualTo(5);
    }

    @Test
    @DisplayName("SPY: Başarılı siparişte onay bildirimi tam bir kez gönderilir")
    void spyRecordsNotification() {
        SpyNotificationService notifications = new SpyNotificationService();
        OrderService service = new OrderService(new FakeProductRepository(LAPTOP), new FakeOrderRepository(),
                new StubPaymentGateway(PaymentResult.success("TX-1")), notifications,
                new PriceCalculator(), fixedIdGenerator);

        Order order = service.placeOrder(EMAIL, "P1", 1);

        assertThat(notifications.sentOrders).containsExactly(order);
    }

    @Test
    @DisplayName("MOCK: Ödeme doğru e-posta ve tutarla çağrılır")
    void mockVerifiesExpectedCall() {
        MockPaymentGateway paymentGateway = new MockPaymentGateway();
        paymentGateway.expectCharge(EMAIL, new BigDecimal("2000.00"));
        OrderService service = new OrderService(new FakeProductRepository(LAPTOP), new FakeOrderRepository(),
                paymentGateway, new SpyNotificationService(), new PriceCalculator(), fixedIdGenerator);

        service.placeOrder(EMAIL, "P1", 2);

        paymentGateway.verify();
    }

    @Test
    @DisplayName("FAKE: Stok gerçekten düşer ve sipariş kaydedilir")
    void fakeRepositoriesKeepState() {
        FakeProductRepository products = new FakeProductRepository(LAPTOP);
        FakeOrderRepository orders = new FakeOrderRepository();
        OrderService service = new OrderService(products, orders,
                new StubPaymentGateway(PaymentResult.success("TX-1")), new SpyNotificationService(),
                new PriceCalculator(), fixedIdGenerator);

        service.placeOrder(EMAIL, "P1", 2);
        service.placeOrder(EMAIL, "P1", 3);

        assertThat(products.findById("P1").orElseThrow().stock()).isZero();
        assertThat(orders.orders).hasSize(2);
    }
}
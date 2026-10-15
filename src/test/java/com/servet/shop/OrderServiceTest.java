package com.servet.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.servet.shop.exception.OutOfStockException;
import com.servet.shop.exception.PaymentFailedException;
import com.servet.shop.exception.ProductNotFoundException;
import com.servet.shop.model.Order;
import com.servet.shop.model.PaymentResult;
import com.servet.shop.model.Product;
import com.servet.shop.repo.IdGenerator;
import com.servet.shop.repo.NotificationService;
import com.servet.shop.repo.OrderRepository;
import com.servet.shop.repo.PaymentGateway;
import com.servet.shop.repo.ProductRepository;
import com.servet.shop.service.OrderService;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService (Mockito)")
class OrderServiceTest {

    private static final String EMAIL = "servet@example.com";
    private static final Product LAPTOP = new Product("P1", "Laptop", new BigDecimal("1000.00"), 5);

    @Mock
    ProductRepository productRepository;
    @Mock
    OrderRepository orderRepository;
    @Mock
    PaymentGateway paymentGateway;
    @Mock
    NotificationService notificationService;
    @Mock
    IdGenerator idGenerator;

    /** Gerçek PriceCalculator çalışır, ama Mockito çağrıları izler. */
    @Spy
    PriceCalculator priceCalculator;

    /** Yukarıdaki mock ve spy'lar constructor üzerinden enjekte edilir. */
    @InjectMocks
    OrderService orderService;

    @Captor
    ArgumentCaptor<Order> orderCaptor;

    /** Başarılı bir siparişin ihtiyaç duyduğu tüm stub'lar. */
    private void stubSuccessfulCheckout(Product product) {
        when(productRepository.findById(product.id())).thenReturn(Optional.of(product));
        when(paymentGateway.charge(eq(EMAIL), any(BigDecimal.class))).thenReturn(PaymentResult.success("TX-42"));
        when(idGenerator.nextId()).thenReturn("ORD-1");
        // thenAnswer: save() kendisine gelen siparişi geri döndürsün
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("Başarılı sipariş")
    class HappyPath {

        @BeforeEach
        void setUp() {
            stubSuccessfulCheckout(LAPTOP);
        }

        @Test
        @DisplayName("Sipariş doğru alanlarla kaydedilir (ArgumentCaptor)")
        void orderIsSavedWithCorrectFields() {
            Order result = orderService.placeOrder(EMAIL, "P1", 2);

            verify(orderRepository).save(orderCaptor.capture());
            Order saved = orderCaptor.getValue();
            assertThat(saved.id()).isEqualTo("ORD-1");
            assertThat(saved.customerEmail()).isEqualTo(EMAIL);
            assertThat(saved.quantity()).isEqualTo(2);
            assertThat(saved.total()).isEqualByComparingTo("2000.00");
            assertThat(saved.transactionId()).isEqualTo("TX-42");
            assertThat(result).isEqualTo(saved);
        }

        @Test
        @DisplayName("Stok, satılan adet kadar düşürülür")
        void stockIsDecreased() {
            orderService.placeOrder(EMAIL, "P1", 2);

            verify(productRepository).updateStock("P1", 3);
        }

        @Test
        @DisplayName("Onay bildirimi tam bir kez gönderilir")
        void confirmationIsSentOnce() {
            orderService.placeOrder(EMAIL, "P1", 1);

            verify(notificationService, times(1)).sendOrderConfirmation(any(Order.class));
        }

        @Test
        @DisplayName("Adımlar doğru sırada çalışır: ödeme → stok → kayıt → bildirim (InOrder)")
        void stepsRunInOrder() {
            orderService.placeOrder(EMAIL, "P1", 1);

            InOrder inOrder = inOrder(paymentGateway, productRepository, orderRepository, notificationService);
            inOrder.verify(paymentGateway).charge(EMAIL, new BigDecimal("1000.00"));
            inOrder.verify(productRepository).updateStock("P1", 4);
            inOrder.verify(orderRepository).save(any(Order.class));
            inOrder.verify(notificationService).sendOrderConfirmation(any(Order.class));
        }

        @Test
        @DisplayName("Bildirim hatası siparişi bozmaz (void metotta doThrow)")
        void notificationFailureDoesNotBreakOrder() {
            doThrow(new RuntimeException("SMTP sunucusu kapalı"))
                    .when(notificationService).sendOrderConfirmation(any(Order.class));

            Order order = orderService.placeOrder(EMAIL, "P1", 1);

            assertThat(order).isNotNull();
            verify(orderRepository).save(any(Order.class));
        }
    }

    @Nested
    @DisplayName("Hata senaryoları")
    class Failures {

        @Test
        @DisplayName("Ürün bulunamazsa ödeme, kayıt ve bildirim hiç çağrılmaz")
        void productNotFound() {
            when(productRepository.findById("X")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.placeOrder(EMAIL, "X", 1))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("X");
            verifyNoInteractions(paymentGateway, orderRepository, notificationService);
        }

        @Test
        @DisplayName("Stok yetersizse ödeme alınmaz")
        void outOfStock() {
            when(productRepository.findById("P1")).thenReturn(Optional.of(LAPTOP));

            assertThatThrownBy(() -> orderService.placeOrder(EMAIL, "P1", 6))
                    .isInstanceOf(OutOfStockException.class);
            verify(paymentGateway, never()).charge(anyString(), any(BigDecimal.class));
            verify(productRepository, never()).updateStock(anyString(), anyInt());
        }

        @Test
        @DisplayName("Ödeme reddedilirse stok düşmez, sipariş kaydedilmez")
        void paymentDeclined() {
            when(productRepository.findById("P1")).thenReturn(Optional.of(LAPTOP));
            when(paymentGateway.charge(anyString(), any(BigDecimal.class))).thenReturn(PaymentResult.failed());

            assertThatThrownBy(() -> orderService.placeOrder(EMAIL, "P1", 1))
                    .isInstanceOf(PaymentFailedException.class);
            verify(productRepository, never()).updateStock(anyString(), anyInt());
            verifyNoInteractions(orderRepository, notificationService);
        }

        @Test
        @DisplayName("Ödeme servisi çökerse hata yukarı iletilir (thenThrow)")
        void paymentGatewayThrows() {
            when(productRepository.findById("P1")).thenReturn(Optional.of(LAPTOP));
            when(paymentGateway.charge(anyString(), any(BigDecimal.class)))
                    .thenThrow(new IllegalStateException("Gateway timeout"));

            assertThatThrownBy(() -> orderService.placeOrder(EMAIL, "P1", 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Gateway timeout");
            verifyNoInteractions(orderRepository);
        }

        @ParameterizedTest(name = "Adet = {0}")
        @ValueSource(ints = { 0, -1 })
        @DisplayName("Geçersiz adette hiçbir bağımlılığa gidilmez")
        void invalidQuantity(int quantity) {
            assertThatIllegalArgumentException().isThrownBy(() -> orderService.placeOrder(EMAIL, "P1", quantity));
            verifyNoInteractions(productRepository, paymentGateway);
        }

        @ParameterizedTest(name = "E-posta = \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = { "   " })
        @DisplayName("Boş e-posta reddedilir")
        void invalidEmail(String email) {
            assertThatIllegalArgumentException().isThrownBy(() -> orderService.placeOrder(email, "P1", 1));
            verifyNoInteractions(productRepository);
        }
    }

    @Nested
    @DisplayName("@Spy ile PriceCalculator")
    class SpyExamples {

        @Test
        @DisplayName("Spy gerçek metodu çalıştırır: 10 adette %10 indirim")
        void spyCallsRealMethod() {
            Product mouse = new Product("P2", "Mouse", new BigDecimal("50.00"), 100);
            stubSuccessfulCheckout(mouse);

            Order order = orderService.placeOrder(EMAIL, "P2", 10);

            verify(priceCalculator).calculateTotal(new BigDecimal("50.00"), 10);
            assertThat(order.total()).isEqualByComparingTo("450.00");
        }

        @Test
        @DisplayName("Spy'ın bir metodu doReturn ile ezilebilir (partial mock)")
        void spyCanBePartiallyStubbed() {
            stubSuccessfulCheckout(LAPTOP);
            doReturn(new BigDecimal("1.00")).when(priceCalculator).calculateTotal(any(BigDecimal.class), anyInt());

            Order order = orderService.placeOrder(EMAIL, "P1", 1);

            assertThat(order.total()).isEqualByComparingTo("1.00");
            verify(paymentGateway).charge(EMAIL, new BigDecimal("1.00"));
        }
    }

    @Nested
    @DisplayName("BDD stili (given / when / then)")
    class BddStyle {

        @Test
        @DisplayName("Ödeme reddedilince bildirim gönderilmez")
        void noNotificationWhenPaymentDeclined() {
            // given
            given(productRepository.findById("P1")).willReturn(Optional.of(LAPTOP));
            given(paymentGateway.charge(anyString(), any(BigDecimal.class))).willReturn(PaymentResult.failed());

            // when
            Throwable thrown = catchThrowable(() -> orderService.placeOrder(EMAIL, "P1", 1));

            // then
            assertThat(thrown).isInstanceOf(PaymentFailedException.class);
            then(notificationService).shouldHaveNoInteractions();
            then(productRepository).should(never()).updateStock(anyString(), anyInt());
        }
    }
}
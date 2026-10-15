package com.servet.shop.service;


import java.math.BigDecimal;

import com.servet.shop.PriceCalculator;
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



public class OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final PriceCalculator priceCalculator;
    private final IdGenerator idGenerator;

    public OrderService(ProductRepository productRepository, OrderRepository orderRepository,
            PaymentGateway paymentGateway, NotificationService notificationService,
            PriceCalculator priceCalculator, IdGenerator idGenerator) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
        this.priceCalculator = priceCalculator;
        this.idGenerator = idGenerator;
    }

    public Order placeOrder(String customerEmail, String productId, int quantity) {
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new IllegalArgumentException("E-posta boş olamaz");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Adet pozitif olmalı");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (product.stock() < quantity) {
            throw new OutOfStockException(productId, product.stock(), quantity);
        }

        BigDecimal total = priceCalculator.calculateTotal(product.price(), quantity);
        PaymentResult payment = paymentGateway.charge(customerEmail, total);
        if (!payment.success()) {
            throw new PaymentFailedException(customerEmail, total);
        }

        productRepository.updateStock(productId, product.stock() - quantity);
        Order order = new Order(idGenerator.nextId(), customerEmail, productId, quantity, total,
                payment.transactionId());
        Order savedOrder = orderRepository.save(order);

        try {
            notificationService.sendOrderConfirmation(savedOrder);
        } catch (RuntimeException e) {
            // Bildirim gönderilemese de sipariş geçerlidir; ödeme alındı, kayıt yapıldı.
        }
        return savedOrder;
    }
}
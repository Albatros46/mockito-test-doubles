package com.servet.shop.model;

import java.math.BigDecimal;

public record Order(String id, String customerEmail, String productId, int quantity,
        BigDecimal total, String transactionId) {
}
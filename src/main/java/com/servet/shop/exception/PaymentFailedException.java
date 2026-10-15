package com.servet.shop.exception;

import java.math.BigDecimal;

public class PaymentFailedException extends RuntimeException {

    public PaymentFailedException(String customerEmail, BigDecimal amount) {
        super("Die Zahlung wurde abgelehnt: " + customerEmail + ", Betrag: " + amount);
    }
}
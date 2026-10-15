package com.servet.shop.model;

public record PaymentResult(boolean success, String transactionId) {

    public static PaymentResult success(String transactionId) {
        return new PaymentResult(true, transactionId);
    }

    public static PaymentResult failed() {
        return new PaymentResult(false, null);
    }
}
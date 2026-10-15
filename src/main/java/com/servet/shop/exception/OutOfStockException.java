package com.servet.shop.exception;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException(String productId, int available, int requested) {
        super("Unzureichender Lagerbestand: " + productId + " (vorhanden: " + available + ", gewünscht: " + requested + ")");
    }
}
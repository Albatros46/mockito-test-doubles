package com.servet.shop.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("Das Produkt wurde nicht gefunden: " + productId);
    }
}
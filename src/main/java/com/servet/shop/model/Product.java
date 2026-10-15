package com.servet.shop.model;

import java.math.BigDecimal;

public record Product(String id, String name, BigDecimal price, int stock) {
}
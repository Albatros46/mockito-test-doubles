package com.servet.shop;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Gerçek (mock'lanmayan) bir sınıf — @Spy örneği için kullanılır. */
public class PriceCalculator {

    static final int BULK_THRESHOLD = 10;
    static final BigDecimal BULK_DISCOUNT = new BigDecimal("0.90");

    public BigDecimal calculateTotal(BigDecimal unitPrice, int quantity) {
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
        if (quantity >= BULK_THRESHOLD) {
            total = total.multiply(BULK_DISCOUNT);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
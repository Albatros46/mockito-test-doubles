package com.servet.shop.repo;



import java.math.BigDecimal;

import com.servet.shop.model.PaymentResult;

public interface PaymentGateway {

    PaymentResult charge(String customerEmail, BigDecimal amount);
}
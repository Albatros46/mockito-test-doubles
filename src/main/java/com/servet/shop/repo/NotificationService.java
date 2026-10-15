package com.servet.shop.repo;


import com.servet.shop.model.Order;

public interface NotificationService {

    void sendOrderConfirmation(Order order);
}
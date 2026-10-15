package com.servet.shop.repo;


import com.servet.shop.model.Order;

public interface OrderRepository {

    Order save(Order order);
}
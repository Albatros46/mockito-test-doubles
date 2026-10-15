package com.servet.shop.repo;


import java.util.Optional;

import com.servet.shop.model.Product;

public interface ProductRepository {

    Optional<Product> findById(String id);

    void updateStock(String id, int newStock);
}
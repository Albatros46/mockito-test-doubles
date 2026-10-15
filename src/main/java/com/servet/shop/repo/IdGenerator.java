package com.servet.shop.repo;



@FunctionalInterface
public interface IdGenerator {

    String nextId();
}
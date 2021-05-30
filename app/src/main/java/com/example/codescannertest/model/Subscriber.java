package com.example.codescannertest.model;

public interface Subscriber<T>{
    T getData();
    void setData(T data);
}
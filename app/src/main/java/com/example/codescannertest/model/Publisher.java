package com.example.codescannertest.model;

import android.util.Log;

import java.time.LocalDate;
import java.util.ArrayList;

public interface Publisher<T> {

    T getData();

    void subscribe(Subscriber<T> sub);

    void unsubscribe(Subscriber<T> sub);

    void notifyDataChange();

    void setData(T d);

}

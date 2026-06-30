package com.guingujig.yeolmumarket.global.lock;

@FunctionalInterface
public interface LockCallback<T> {

  T execute();
}

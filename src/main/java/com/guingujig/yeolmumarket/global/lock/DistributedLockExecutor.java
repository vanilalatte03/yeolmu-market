package com.guingujig.yeolmumarket.global.lock;

public interface DistributedLockExecutor {

  <T> T execute(String key, LockCallback<T> callback);
}

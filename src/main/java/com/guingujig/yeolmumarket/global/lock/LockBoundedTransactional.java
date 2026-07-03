package com.guingujig.yeolmumarket.global.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분산 락 콜백 안에서 실행되는 상태 변경 트랜잭션에 붙인다.
 *
 * <p>트랜잭션 타임아웃을 {@code yeolmu.lock.tx-timeout-seconds}로 고정해, 락 lease가 만료되기 전에 트랜잭션이 커밋되거나 롤백되도록
 * 강제한다(ADR-013). 이로써 "락 만료 후 같은 주문에 다른 상태 변경이 끼어드는" 경합을 구조적으로 차단한다.
 *
 * <p>timeout이 lease보다 작아야 한다는 불변식은 {@link LockProperties}가 기동 시점에 검증한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Transactional(timeoutString = "${yeolmu.lock.tx-timeout-seconds}")
public @interface LockBoundedTransactional {}

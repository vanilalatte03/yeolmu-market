# ADR-013: 분산 락 키 계약과 lease 만료 방지 (ADR-010 대체)

- 상태: 채택됨
- 날짜: 2026-06-30
- 관련: ADR-010(대체 대상), ADR-001(주문 생성 낙관 락), `docs/DEVELOPMENT.md`(테스트 정책 정본), PR #119 리뷰

## 맥락

ADR-010은 레코드 상태 변경 동시성 제어를 Redis 분산 락(Redisson)으로 통일하고 `Order`·`Payment`·`RefundRequest`의 비관 락을 제거하기로 했다. 그 핵심 결정은 유지한다. 그러나 PR #119 구현 리뷰에서 ADR-010의 두 부분이 구현·운영과 어긋남이 드러나, 같은 결정을 흔들리지 않게 다시 고정할 필요가 생겼다.

1. **락 키 계약이 ADR-010 내부에서 모순된다.** ADR-010 "결정"은 주문·결제·환불·리뷰가 공통 키 `lock:order:{orderId}` 하나만 공유한다고 못박지만, 같은 ADR "결과"는 `Payment`·`RefundRequest`도 각 자원 단위 키를 정의한다고 적어 서로 충돌한다. 구현(`LockKeys`)은 공통 키만 제공한다. 채택된 ADR에 모순이 남으면 이후 구현자가 자원별 키를 추가하거나 리뷰 기준이 흔들린다.

2. **고정 lease가 트랜잭션 중 만료될 수 있다.** 분산 락 실행기는 `tryLock(waitTime, leaseTime)` 오버로드로 고정 lease(운영 10s)를 넘기는데, 이 오버로드는 Redisson watchdog 자동 갱신을 끈다. 락 콜백 내부의 `@Transactional` 커맨드가 lease보다 길어지면 커밋 전에 락이 만료되고, ADR-010이 DB row 락을 제거했기 때문에 같은 주문의 상태 전이가 다시 경합에 노출된다. ADR-010은 "lease를 최대 트랜잭션 시간보다 길게 잡는다"고 가정만 했을 뿐 이를 강제하는 수단이 없었다.

3. **실 Redisson wiring·lease 동작이 CI에서 검증되지 않았다.** 분산 락 통합 테스트는 env-gated이고 실행기 단위 테스트는 `RLock`을 mock해, starter 자동설정이 `spring.data.redis.*`로 빈을 만드는지와 실제 lease 동작을 CI가 잡지 못했다.

## 검토한 대안

**lease 만료 대응**

- **Redisson watchdog(lease 자동 갱신)** — `tryLock(waitTime)`으로 lease를 watchdog에 위임해 트랜잭션 중 만료를 원천 차단. 단 `lease-time` 설정 의미가 watchdog timeout으로 바뀌고, 락 보유 JVM이 죽으면 락이 watchdog timeout까지 잔존하며, 행(hang) 스레드가 락을 무한 점유할 수 있다.
- **고정 lease 유지 + 트랜잭션 타임아웃 강제** — `tryLock(waitTime, leaseTime)`을 유지하되, 락 콜백 내부 `@Transactional`에 `timeout`(lease보다 작게)을 부여해 트랜잭션이 lease를 넘기기 전에 롤백되게 한다. "lease > 트랜잭션 시간"을 가정이 아니라 코드로 강제한다. 본 프로젝트의 락 보유 작업이 외부 호출 없는 짧은 로컬 DB 작업으로 한정돼 timeout 산정이 단순하다.

**테스트 검증**

- **현행(env-gated + mock)** — 추가 인프라가 없지만 실 wiring·lease 회귀를 CI에서 못 잡는다.
- **CI Redis service container** — CI에 Redis를 띄워 실 락 동작과 starter wiring을 검증한다. `docs/DEVELOPMENT.md`의 "Redis는 테스트에서 mock/fallback" 정책을 분산 락 한정으로 개정해야 한다.

## 결정

ADR-010을 본 ADR로 **대체**한다. ADR-010의 핵심(레코드 상태 변경을 Redis 분산 락으로 통일, `Order`·`Payment`·`RefundRequest` 비관 락 제거, `Product.@Version` 낙관 락 유지, `ChatRoom` 비관 락 제외)은 그대로 승계하고, 아래를 확정한다.

1. **락 키 계약 — 주문 단위 공통 키만 사용한다.** 주문 상태를 바꾸는 모든 도메인(주문·결제·환불·리뷰)은 공통 키 `lock:order:{orderId}` 하나만 공유한다. **`Payment`·`RefundRequest`를 위한 자원별 락 키는 두지 않는다.** (ADR-010 "결과"의 자원별 키 문구를 무효화한다.) 결제 중복 차단은 멱등키(`findByIdempotencyKey`)가 담당하며 분산 락과 역할이 다르다.

2. **lease 만료 방지 — 고정 lease + 트랜잭션 타임아웃 강제.** 분산 락은 `tryLock(waitTime, leaseTime)` 고정 lease를 유지하고 watchdog은 쓰지 않는다. 대신 락 콜백이 호출하는 모든 상태 변경 `@Transactional` 커맨드에 `timeout`을 부여하고, 그 값은 항상 `yeolmu.lock.lease-time`보다 작게 둔다. 트랜잭션이 lease를 넘기기 전에 타임아웃으로 롤백되므로 "락 만료 후 경합"이 구조적으로 발생하지 않는다. watchdog 안은 lease 의미 전환·JVM 다운 시 잔존·행 스레드 무한 점유 부담이 있고, 락 보유 작업이 짧은 로컬 DB 작업이라 고정 lease+타임아웃으로 충분해 제외한다.

3. **테스트·CI — 분산 락은 실 Redis로 검증한다.** 분산 락의 실제 Redisson wiring과 락 동작(획득 실패 CONFLICT, lease 경계)은 mock으로 대체할 수 없으므로 CI에서 Redis service container로 검증한다. 세부 테스트 정책은 `docs/DEVELOPMENT.md`를 정본으로 하며 본 ADR이 그 개정 근거다.

## 결과

- `LockKeys`는 주문 단위 키만 제공한다(현 구현 유지). `Payment`·`RefundRequest` 자원별 키 메서드를 추가하지 않는다.
- 락 콜백 내부 상태 변경 커맨드(`OrderLockedCommandService`, `PaymentLockedCommandService`, `RefundLockedCommandService`, `ReviewLockedCommandService`)는 공통 메타 애너테이션 `@LockBoundedTransactional`(= `@Transactional(timeoutString = "${yeolmu.lock.tx-timeout-seconds}")`)을 붙여 lease 경계 트랜잭션임을 코드에 드러낸다. 초 단위 timeout과 각 프로파일 `lease-time`이 "timeout < lease"를 만족하도록 설정값을 함께 맞춘다(운영 8s < 10s, 테스트 1s < 2s).
- "timeout < lease" 불변식은 `LockProperties`가 기동 시점에 검증해 위반 시 애플리케이션 기동을 막는다. 이 불변식 위반을 막는 단위 테스트(`LockPropertiesTest`)를 둔다.
- `docs/DEVELOPMENT.md` 테스트 정책을 개정해 분산 락 wiring·동작 검증을 CI Redis service container 전제로 한다. `.github/workflows/ci.yml`에 Redis를 추가하고, 실 Redis 락 테스트(통합·wiring smoke)를 CI에서 실행한다.
- ADR-010은 상태를 "대체됨 (ADR-013)"으로 바꾸고 내용은 보존한다.
- Redis 장애·락 획득 실패 동작(상태 변경 중단, `CONFLICT`/`REDIS_UNAVAILABLE`)은 ADR-010 결정을 그대로 따른다.

# Conventions

## 패키지 구조

- 도메인별로 묶는다. 한 도메인 안에 `controller / service / repository / dto / entity`.
- 공통은 `global`(config, exception, response, security), 외부 연동은 `infra`.

## 네이밍

### 클래스

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| Controller | `{Domain}Controller` | `OrderController` |
| Service | `{Domain}Service` | `OrderService` |
| Repository | `{Entity}Repository` | `OrderRepository` |
| Entity | 명사 단수형 | `Order` |
| Enum | `{Domain}Status`, `{Domain}Type` | `OrderStatus` |
| Request DTO | `{Action}{Domain}Request` | `CreateOrderRequest` |
| Response DTO | `{Action}{Domain}Response` | `CreateOrderResponse` |
| Exception | `{Domain}Exception` 또는 `BusinessException` | `OrderException` |

### 메서드

행위를 명확히 드러낸다. 권장: `createOrder()`, `sendMessage()`, `decreaseStock()`.
비권장: `process()`, `handle()`, `doX()`, `updateData()`, `check()`.
단, 의미가 분명한 이벤트 처리(`handleWebhook()` 등)는 허용.

### 변수

축약어 남발 금지. 권장 `orderTotalAmount`, `currentUserId`. 비권장 `ordAmt`, `uid`.

## Controller

요청/응답 처리만 담당한다.

- 책임: Request DTO 검증, 인증 사용자 식별, Service 호출, Response DTO 반환
- 하지 않는다: 비즈니스 로직, Entity 직접 조립, 외부 API 호출, 트랜잭션 처리, 복잡한 조건 분기
- 의존성 주입은 `@RequiredArgsConstructor` + `private final`. `@Autowired` 필드 주입 금지.
- Entity를 직접 반환하지 않고 항상 DTO로 변환한다.

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
}
```

## Service

- 유스케이스를 표현한다. 메서드명은 유스케이스 단위(`createOrder(userId, request)`).
- 트랜잭션 경계는 Service 계층에 둔다. 조회 전용 `@Transactional(readOnly = true)`, 상태 변경 `@Transactional`.
- 의존성 주입은 `@RequiredArgsConstructor` + `private final`. `@Autowired` 금지.
- 외부 API 연동은 별도 Client 클래스로 분리한다.

## Repository

- Spring Data JPA 인터페이스. 복잡한 조회는 메서드명 또는 `@Query`로 명확히.
- 비즈니스 로직을 Repository에 두지 않는다.

## Entity / DTO

- Entity와 DTO를 분리한다. 계층 간 전달은 DTO로.
- Entity에 setter를 남발하지 않고 의미 있는 변경 메서드를 둔다.
- DTO는 용도별로 Request/Response를 분리한다.

## Exception

- 도메인 예외는 커스텀 예외(`{Domain}Exception`) 또는 공통 `BusinessException`.
- 전역 처리는 `@RestControllerAdvice`로 일관되게.

## API 응답

- 공통 응답 래퍼(`ApiResponse<T>` 등)로 형식을 통일한다.
- 실제 HTTP 상태 코드와 에러 코드를 일관되게 사용한다.
- 공통 응답 body는 `success`로 API 처리 성공 여부를 표현한다.

## Validation

- Request DTO 검증은 `@Valid` + Bean Validation으로.
- 음수/0/빈 목록/잘못된 수량 등 경계값을 막는다.
- 인증 사용자와 요청 리소스의 소유자 일치를 검증한다.

## Transaction

- 트랜잭션은 Service 계층에서 시작한다.
- 외부 API 호출과 DB 트랜잭션 범위를 신중하게 잡는다 (외부 성공 후 내부 실패 가능성 고려).
- 읽기 전용은 `readOnly = true`.

## Logging

- 민감 정보(토큰, secret, 비밀번호 등)를 로그/응답에 남기지 않는다.
- 의미 있는 지점에 적절한 레벨로 남긴다.

## 테스트

- 단위 테스트: JUnit 5 + Mockito, given-when-then 구조.
- Service 테스트는 Repository를 목킹 (`@ExtendWith(MockitoExtension.class)`).
- 통합 테스트는 `@SpringBootTest`, 최소한으로.
- 테스트 클래스명: `대상클래스 + Test` (`OrderServiceTest`).
- **테스트 메서드명은 한글로 작성**한다. 예: `주문을_생성하면_재고가_차감된다()`
- 새 Service/Controller에는 핵심 경로 테스트를 작성한다.

## 시간 처리

- 서버/DB/JWT 시간 값은 UTC 기준으로 다룬다.
- Entity의 `LocalDateTime`은 DB `DATETIME`에 저장된 UTC 시각으로 해석한다.
- JWT/TTL/만료 계산은 `Instant`, epoch seconds, `Duration`을 사용한다.
- 테스트 fixture에서 정렬·만료·시간 비교용 값은 고정 시각을 사용한다.
- 현재 시각이 정말 필요한 코드나 테스트는 UTC 기준을 명시한다.

```java
// ❌ 테스트 결과가 실행 시각에 흔들림
ReflectionTestUtils.setField(room, "lastMessageAt", LocalDateTime.now());

// ✅ 비교 기준을 고정
LocalDateTime baseTime = LocalDateTime.of(2026, 6, 24, 10, 0);
ReflectionTestUtils.setField(room, "lastMessageAt", baseTime);
```

## Javadoc

사람이 읽고 리뷰하기 쉽도록, "왜"가 필요한 곳에만 단다. 모든 메서드에 기계적으로 붙이지 않는다.

### 단다

- public Service 메서드: 유스케이스 의도, 주요 부수효과(상태 변경/외부 호출), 던지는 예외
- 도메인 규칙이 들어간 Entity 변경 메서드 (예: 재고 차감 조건)
- 이름만으로 의도가 드러나지 않는 복잡한 분기·계산 로직
- 외부 연동(infra) Client의 호출 계약(파라미터 의미, 실패 시 동작)

### 달지 않는다

- getter/setter, 단순 위임 메서드, 자명한 Controller 매핑
- 코드를 그대로 읽어 옮긴 주석 (`// userId로 유저 조회` 같은 것)

```java
// ❌ 이름이 다 말하는데 중복
/** 주문을 생성한다. */
public Order createOrder(...) { ... }

// ✅ 코드만으론 안 보이는 의도·부수효과·예외를 적는다
/**
 * 주문을 생성하고 재고를 차감한다.
 * 재고가 부족하면 {@link OutOfStockException}을 던지며, 이때 주문은 저장되지 않는다.
 */
public Order createOrder(Long userId, CreateOrderRequest request) { ... }
```

## 과한 설계 제한

필요성이 명확하지 않으면 단순한 Spring Boot + JPA + DB 구조 안에서 해결한다.
다음은 신중하게만 제안한다: MSA 전환, Kafka 도입, Redis 분산락, CQRS/Event Sourcing, 복잡한 DDD 강제, 운영급 모니터링 구축.

## 커밋

- 커밋은 리뷰 가능한 의미 단위로 작게 나눈다. 기능 구현, 테스트 추가, 문서 수정, 리팩토링은 가능하면 분리한다.
- 커밋 메시지는 Conventional Commits 형식을 따른다: `type: subject`
- 허용 type은 `feat`, `fix`, `refactor`, `docs`, `test`, `chore` 중 하나다.
- subject는 한국어 한 줄로 간결하게 작성하고, 구현 내용이 바로 드러나게 현재형으로 쓴다.
- WIP, 임시 커밋, 서로 다른 목적이 섞인 대형 커밋은 남기지 않는다.

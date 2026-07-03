## 검색 API 캐시 전략

상품 검색 API는 동일한 검색 조건이 반복 호출될 가능성이 높고, 검색 시 MySQL에서 키워드/가격/상태/정렬/페이징 조건을 조합해 조회하므로 반복 요청이 DB 부하로 이어질 수 있다.  
따라서 `/api/search/v2/products`에는 Redis 기반 캐시를 적용했다. 기존 `/api/search/products`는 캐시 없는 검색 API로 유지하고, v2에서 같은 요청/응답 계약을 유지한 채 캐시를 적용했다.

### 캐시 전략

![](https://github.com/user-attachments/assets/6cb9baee-5d3b-4cbf-958e-3656b4fe536f)

이 프로젝트는 Cache-aside, Lazy Loading 전략을 사용한다.

검색 요청이 들어오면 먼저 Redis 캐시에서 `검색 조건 + 검색 인덱스 버전`에 해당하는 상품 ID 목록을 조회한다. 캐시 hit이면 DB 검색 쿼리를 생략하고, cache miss이면 MySQL에서 검색한 뒤 Redis에 저장한다.

Write-through나 Write-back을 선택하지 않은 이유는 상품 검색 결과가 단일 row가 아니라 키워드, 가격, 상태, 정렬, 페이지 조건에 따라 달라지는 파생 결과이기 때문이다. 상품 등록/수정/삭제 시점에 영향을 받는 모든 검색 조건 캐시를 즉시 갱신하는 것은 현실적으로 어렵고 비용이 크다. 또한 MySQL이 상품 데이터의 정본이므로, Redis에 먼저 쓰고 나중에 DB에 반영하는 Write-back은 정합성 위험이 크다.

### 캐시 분리

검색 캐시는 두 층으로 분리했다.

1. 검색 목록 캐시: `search:products:v2`
    - 값: `PageResponse<Long>`, 즉 상품 ID 목록과 페이지 메타데이터
    - TTL: 30초
    - 목적: 검색 조건별 반복 조회에서 DB 검색/count 쿼리 부담을 줄인다.

2. 상품 표시 캐시: `search:products:v2:display`
    - 값: 상품 제목, 가격, 상태, 썸네일, 판매자 ID, 생성일 등 검색 결과 표시용 데이터
    - key: productId
    - TTL: 5분
    - 목적: 여러 검색 조건에 같은 상품이 포함될 수 있으므로, 상품 표시 데이터를 productId 단위로 재사용한다.

사용자별 찜 여부와 판매자 닉네임은 캐시에 넣지 않고 응답 조립 시점에 별도로 조회한다. 개인화 데이터나 외부 엔티티 변경 때문에 검색 캐시 전체가 오염되는 것을 막기 위해서다.

### 캐시 Key 설계

`@Cacheable`에서는 캐시 저장소 이름과 데이터 식별자를 분리했다.

- cache name: `search:products:v2`
- key: `SearchProductCacheKey(condition, searchIndexVersion)`

`condition`에는 keyword, minPrice, maxPrice, status, page, size, sort가 포함된다. keyword는 trim/blank 처리를 하고, sort/status 기본값도 정규화해서 같은 의미의 요청이 같은 key를 사용하도록 했다.  
또한 `searchIndexVersion`을 key에 포함해 상품 등록/수정/삭제/숨김/상태 변경 후 이전 검색 목록 캐시가 재사용되지 않도록 했다.

Redis 실제 key는 `cache:` prefix와 cache name이 붙어 생성된다.

- `cache:search:products:v2::...`
- `cache:search:products:v2:display::...`

즉, 캐시 저장소 이름으로 도메인을 분리하고, key에는 검색 조건 또는 productId를 사용해 충돌 가능성을 줄였다.

### 무효화 전략

상품 목록 구성에 영향을 주는 변경이 발생하면 `searchIndexVersion`을 증가시킨다. 기존 key를 직접 전부 삭제하지 않고, 새 버전 key를 사용하게 만들어 사실상 이전 목록 캐시를 무효화한다. 이전 캐시는 TTL이 지나면 Redis에서 제거된다.

상품 표시값이 바뀌는 경우에는 해당 productId의 표시 캐시만 evict한다.  
예를 들어 상품 제목/가격/상태 변경은 검색 목록 구성과 표시값 모두에 영향을 줄 수 있으므로 searchIndexVersion 증가와 productId 표시 캐시 삭제를 함께 수행한다.

### 로컬 캐시 대신 Redis를 사용한 이유

로컬 캐시는 구현이 단순하지만 서버가 여러 대로 늘어났을 때 인스턴스 간 캐시가 공유되지 않는다. 한 서버에서 상품 변경 후 캐시가 갱신되어도 다른 서버의 로컬 캐시는 오래된 값을 들고 있을 수 있다.

이 프로젝트는 Scale-out 상황에서도 검색 캐시와 인기 검색어 집계를 공유하기 위해 Redis를 사용했다. Redis 장애나 캐시 miss가 발생해도 MySQL 조회로 검색 결과를 반환하도록 구성해, 캐시가 핵심 데이터 정합성의 기준이 되지 않도록 했다.

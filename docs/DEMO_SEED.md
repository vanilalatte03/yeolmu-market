# Demo Catalog Seed

프론트 화면 확인용 카탈로그 데이터 적재 절차의 정본이다. 일반 애플리케이션 실행과 자동 테스트에서는 동작하지 않고, `demo-seed` profile과 `YEOLMU_DEMO_SEED=true`를 명시한 경우에만 로컬 MySQL 또는 EKS에 연결된 RDS에 데이터를 넣는다.

## 동작 방식

seed는 기본 카테고리 10개를 보장한 뒤 데모 판매자와 판매 중 상품을 JDBC batch로 생성한다. 기본값은 상품 5,000건, 판매자 100명, batch size 1,000이다.

상품 이미지는 생성하지 않으므로 프론트는 기본 placeholder를 표시한다. 같은 `YEOLMU_DEMO_RUN_KEY`로 같은 상품 수가 이미 적재되어 있으면 상품 insert는 건너뛴다. 일부만 들어간 run key는 중복·부분 데이터를 피하기 위해 실패한다.

관련 파일은 아래를 먼저 확인한다.

| 목적 | 위치 |
| --- | --- |
| seed runner | `src/main/java/com/guingujig/yeolmumarket/global/seed/DemoCatalogSeeder.java` |
| profile 설정 | `src/main/resources/application-demo-seed.yml` |
| 기본 카테고리 migration | `src/main/resources/db/migration/V9__seed_default_categories.sql` |
| 환경변수 예시 | `.env.example` |

## 로컬 적재

로컬 실행 전에는 MySQL과 Redis를 먼저 띄운다.

```powershell
docker compose up -d mysql redis
```

| 작업 | Windows PowerShell |
| --- | --- |
| 로컬 5,000건 적재 | `$env:YEOLMU_DEMO_SEED='true'; .\gradlew.bat bootRun --args='--spring.profiles.active=demo-seed'; Remove-Item Env:\YEOLMU_DEMO_SEED -ErrorAction SilentlyContinue` |
| 로컬 50,000건 적재 | `$env:YEOLMU_DEMO_SEED='true'; $env:YEOLMU_DEMO_PRODUCT_COUNT='50000'; .\gradlew.bat bootRun --args='--spring.profiles.active=demo-seed'; Remove-Item Env:\YEOLMU_DEMO_SEED -ErrorAction SilentlyContinue; Remove-Item Env:\YEOLMU_DEMO_PRODUCT_COUNT -ErrorAction SilentlyContinue` |
| run key 고정 | `$env:YEOLMU_DEMO_SEED='true'; $env:YEOLMU_DEMO_RUN_KEY='front-demo-001'; .\gradlew.bat bootRun --args='--spring.profiles.active=demo-seed'; Remove-Item Env:\YEOLMU_DEMO_SEED -ErrorAction SilentlyContinue; Remove-Item Env:\YEOLMU_DEMO_RUN_KEY -ErrorAction SilentlyContinue` |

seed 판매자 계정은 `demo-seller-{runKey}-{0001부터 4자리 sequence}@example.com` 형식이고 비밀번호는 `password`다. `runKey`는 실행 로그의 `runKey=...` 값을 사용하거나 `YEOLMU_DEMO_RUN_KEY`로 직접 지정한다.

## EKS 적재

EKS에 배포된 RDS에 넣을 때는 애플리케이션 Deployment를 바꾸지 않고, 현재 배포 이미지를 재사용하는 1회성 Kubernetes Job으로 실행한다. CloudShell에서 클러스터 접속 권한이 있는 상태로 실행한다.

```bash
aws eks update-kubeconfig --region ap-northeast-2 --name yeolmu-eks

IMAGE=$(kubectl get deploy yeolmu-market -n yeolmu \
  -o jsonpath='{.spec.template.spec.containers[0].image}')

kubectl delete job yeolmu-demo-seed -n yeolmu --ignore-not-found

cat <<EOF | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: yeolmu-demo-seed
  namespace: yeolmu
spec:
  backoffLimit: 0
  ttlSecondsAfterFinished: 86400
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: demo-seed
          image: ${IMAGE}
          args:
            - "--spring.profiles.active=demo-seed"
          envFrom:
            - secretRef:
                name: yeolmu-secret
            - configMapRef:
                name: yeolmu-config
          env:
            - name: YEOLMU_DEMO_SEED
              value: "true"
            - name: YEOLMU_DEMO_PRODUCT_COUNT
              value: "5000"
            - name: YEOLMU_DEMO_SELLER_COUNT
              value: "100"
EOF

kubectl logs -n yeolmu job/yeolmu-demo-seed -f
```

5,000건으로 먼저 화면과 로그를 확인한 뒤, 필요하면 Job의 `YEOLMU_DEMO_PRODUCT_COUNT`를 `50000`으로 바꿔 한 번 더 실행한다. 같은 run key로 재실행하고 싶으면 Job env에 `YEOLMU_DEMO_RUN_KEY`를 추가한다.

# EKS 배포

이 문서는 `yeolmu-market`을 EKS Auto Mode, ECR, RDS MySQL, Redis Pod, ALB Ingress로 배포하는 최소 자동화 절차를 정리한다.

## 현재 구조

- GitHub Actions `CI/CD` 워크플로의 `validate` job이 테스트와 Spotless를 통과한 뒤, `deploy` job이 Docker 이미지를 빌드한다.
- 이미지는 Amazon ECR `yeolmu-market` 저장소에 `latest`와 커밋 SHA 태그로 push한다.
- GitHub Actions가 EKS `yeolmu-eks` 클러스터에 접속해 `k8s/` 매니페스트를 적용한다.
- `k8s/` 매니페스트는 기존 `kubectl` 구축 백업을 기준으로 두고, 애플리케이션 이미지 URI만 배포 시점의 커밋 SHA 이미지로 치환한다.
- 런타임 비밀값은 GitHub Secrets에서 Kubernetes Secret `yeolmu-secret`으로 주입한다.
- 비밀이 아닌 설정은 Kubernetes ConfigMap `yeolmu-config`으로 주입한다.

## GitHub Secrets

Repository settings -> Secrets and variables -> Actions -> Secrets에 아래 값을 등록한다.

| 이름 | 값 |
| --- | --- |
| `AWS_ROLE_ARN` | GitHub Actions가 assume할 AWS IAM Role ARN |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://<rds-endpoint>:3306/yeolmu_market?serverTimezone=UTC&characterEncoding=UTF-8` |
| `SPRING_DATASOURCE_USERNAME` | RDS 사용자명 |
| `SPRING_DATASOURCE_PASSWORD` | RDS 비밀번호 |
| `JWT_SECRET` | 충분히 긴 JWT 서명 키 |

## GitHub Variables

Repository settings -> Secrets and variables -> Actions -> Variables에 아래 값을 등록한다.

| 이름 | 값 |
| --- | --- |
| `ALB_SUBNET_IDS` | ALB가 사용할 subnet ID 목록. 쉼표로 구분한다. 예: `subnet-aaa,subnet-bbb` |
| `ACM_CERTIFICATE_ARN` | HTTPS 리스너에 사용할 ACM 인증서 ARN |

## AWS 권한

`AWS_ROLE_ARN`의 IAM Role은 GitHub OIDC로 assume 가능해야 한다. 신뢰 정책은 `main`, `develop` 브랜치의 `repo:vanilalatte03/yeolmu-market` 실행 주체를 허용해야 한다. 최소한 다음 권한이 필요하다.

- ECR 로그인, 이미지 push
- EKS 클러스터 조회와 kubeconfig 생성
- EKS 클러스터 내부 Kubernetes 리소스 적용 권한

EKS 콘솔의 Access entries에서 해당 IAM Role에 클러스터 접근 권한도 부여해야 한다. 처음 자동화를 검증할 때는 관리자 권한으로 붙이고, 동작 확인 후 네임스페이스 단위 권한으로 줄인다.

## 배포 실행

두 방식으로 실행된다.

- `main`, `develop` 브랜치 push 시 자동 실행
- GitHub Actions 화면에서 `main` 또는 `develop` 브랜치를 선택해 `CI/CD` workflow를 수동 실행

배포 후 확인:

```bash
kubectl get pods -n yeolmu
kubectl get ingress -n yeolmu
curl -i https://api.jiholim.pro/api/categories
```

정상 응답 예:

```json
{"success":true,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"categories":[]}}
```

## 주의

- `k8s/secret.example.yaml`은 예시 파일이다. 실제 Secret 값은 저장소에 커밋하지 않는다.
- ALB subnet ID와 ACM 인증서 ARN은 GitHub Variables에서 주입한다. 클러스터나 도메인을 새로 만들 때는 `ALB_SUBNET_IDS`, `ACM_CERTIFICATE_ARN` 값을 수정한다.
- 현재 Redis와 업로드 파일은 EKS 내부 PVC를 사용한다. 운영 안정성을 높이려면 Redis는 ElastiCache, 업로드 파일은 S3로 분리한다.
- WebSocket은 Spring simple broker 기반이므로 현재 배포는 `replicas: 1`을 유지한다.
- `latest` 태그도 push하지만 실제 Deployment에는 커밋 SHA 태그 이미지를 사용한다.

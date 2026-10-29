# Java 배포 자동화 실험실

Java 21 + Gradle 8.14.3으로 만든 작은 API입니다. Spring이나 DB 없이 Java 기본 HTTP 서버만 사용하므로 HTTP 수신부, 웹훅 DTO, 상태별 핸들러로 구조를 나누었습니다. JSON 역직렬화에는 Gson을 사용합니다.

## 1. 로컬 실행 (PowerShell)

JDK 21이 필요합니다. Gradle은 Wrapper가 내려받으므로 따로 설치하지 않습니다.

```powershell
$env:WEBHOOK_SECRET = 'local-demo-secret'
.\gradlew.bat run
```

다른 터미널에서:

```powershell
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod http://localhost:8080/api/hello
$env:WEBHOOK_SECRET = 'local-demo-secret'
.\scripts\send-webhook.ps1
```

서버는 Ctrl+C로 종료합니다. 포트는 `$env:PORT = '9090'`으로 변경할 수 있습니다.

| 요청 | 결과 |
| --- | --- |
| GET /health | 200, 상태 UP |
| GET /api/hello | 200, Hello from Java! |
| POST /hooks/github | 유효한 HMAC 서명이면 202 |

훅은 수신 확인만 하며 배포 명령을 실행하거나 데이터를 저장하지 않습니다. `WEBHOOK_SECRET` 미설정은 503, 서명 누락·불일치는 401, 본문 64 KiB 초과는 413입니다. GitHub의 `X-Hub-Signature-256` 방식으로 원본 바이트를 검증합니다.

## 2. 빌드와 테스트

```powershell
.\gradlew.bat clean build
java -jar build/libs/dummy-api-0.1.0.jar
```

`build`에 실제 HTTP 통합 테스트가 포함됩니다. 정상 API, 404/405, 정상 서명, 누락/잘못된 서명, 본문 변조, 용량 제한, 비밀키 미설정을 확인합니다. Linux/macOS에서는 `.\gradlew.bat` 대신 `bash ./gradlew`를 사용하세요.

## 3. GitHub Actions: 빌드 자동화

이 폴더를 GitHub 저장소에 커밋하고 push하세요. `.github/workflows/ci.yml`이 push/PR마다 다음을 실행합니다.

1. Java 21 설치 → Gradle 빌드·통합 테스트
2. JAR를 Actions 실행 결과의 Artifact로 업로드
3. Docker 이미지 빌드 → 컨테이너 실행 → `/health`, `/api/hello` 호출

별도 Secrets 없이 시작할 수 있습니다. GitHub 저장소의 **Actions → Build and test**에서 로그와 Artifact를 확인하세요.

## 4. Docker 로컬 실행

Docker Desktop을 실행한 상태에서:

```powershell
docker build -t dummy-api:local .
docker run --rm -p 8080:8080 -e WEBHOOK_SECRET=$env:WEBHOOK_SECRET dummy-api:local
```

Docker는 JDK 이미지에서 빌드하고 JRE 이미지에서 일반 사용자로 실행합니다. 로컬 Java 서버가 실행 중이면 먼저 종료하여 포트 충돌을 피하세요.

## 5. GHCR 이미지 발행

워크플로 파일을 기본 브랜치에 push한 뒤 **Actions → Publish Docker image → Run workflow**를 누르세요. 기본 브랜치를 선택해야 합니다.

`publish.yml`은 빌드·테스트 후 GitHub가 제공하는 `GITHUB_TOKEN`으로 아래 이미지를 발행합니다. 추가 PAT는 필요하지 않습니다. 저장소/조직에서 Actions 및 패키지 쓰기를 허용해야 하며, 기존 패키지라면 해당 저장소의 Actions 접근 권한도 필요할 수 있습니다.

```text
ghcr.io/<owner>/<repository>:latest
ghcr.io/<owner>/<repository>:sha-<전체 커밋 SHA>
```

이름은 모두 소문자로 변환됩니다. 이미지 발행은 실행 서버에 앱을 설치하는 단계와는 별개입니다. 실제 서버 자동 배포를 붙이려면 대상 서버/플랫폼 정보가 필요합니다.

이미지를 받아 실행할 서버에서 (이름을 실제 값으로 교체):

```powershell
docker run -d --name dummy-api -p 8080:8080 --restart unless-stopped -e WEBHOOK_SECRET=$env:WEBHOOK_SECRET ghcr.io/<owner>/<repository>:sha-<전체 커밋 SHA>
```

비공개 패키지는 먼저 GHCR에 읽기 권한으로 로그인해야 합니다. 새 버전 적용 시 기존 컨테이너를 교체해야 합니다. 재현 가능한 실험에는 커밋 SHA 태그를 사용하세요.

## 6. GitHub 웹훅 실험

외부에서 접근 가능한 HTTPS 주소에 앱을 실행한 다음 저장소의 **Settings → Webhooks → Add webhook**에서 설정합니다.

- Payload URL: `https://<서버 주소>/hooks/github`
- Content type: `application/json`
- Secret: 서버의 `WEBHOOK_SECRET`과 동일한 값
- 이벤트: Workflow runs 및 Workflow jobs 선택

Recent Deliveries에서 응답 202를 확인하고 Redeliver로 재전송할 수 있습니다. localhost는 GitHub에서 직접 접근할 수 없으므로 로컬 실험은 위 PowerShell 스크립트를 사용하세요. 중복 전송 방지는 아직 없습니다. `X-GitHub-Delivery`는 핸들러에 전달하며 향후 중복 처리 방지에 사용할 수 있습니다.

GitHub Actions 자체를 API로 시작하는 실험은 `workflow_dispatch`가 설정되어 있으므로 GitHub CLI로도 가능합니다 (로그인 및 저장소 쓰기 권한 필요).

```powershell
gh workflow run ci.yml --ref <기본브랜치>
gh run list --workflow ci.yml
```

참고: [Gradle 8.14.3](https://docs.gradle.org/8.14.3/release-notes.html), [GitHub Docker 이미지 발행](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images).

## 검증 결과

Docker에서 `clean build`와 HTTP 통합 테스트를 통과했고, 최종 이미지에서도 상태 API·메시지 API·서명 웹훅을 호출해 확인했습니다. 현재 Windows 실행 환경에서는 Java의 `Unable to establish loopback connection` 오류로 직접 Gradle 실행이 막혔습니다. 같은 오류가 발생하면 Docker 실행 방법을 사용하세요. GitHub Actions 및 GHCR 발행은 저장소에 push한 뒤 확인해야 합니다.

## 워크플로 웹훅 DTO 및 분기

`GitHubWebhookDto`는 action, repository, sender, workflow_run, workflow_job을 매핑합니다. 실행/작업 ID, 상태, 완료 결과(conclusion), 브랜치·커밋 및 실행 시간을 담고, 추가 JSON 필드는 무시합니다.

`GitHubWebhookHandler`는 `X-GitHub-Event`와 본문의 `action`으로 다음 6개 분기를 선택합니다. 각 분기의 실제 액션은 TODO입니다.

- workflow_run: requested, in_progress, completed
- workflow_job: queued, in_progress, completed

completed는 성공을 의미하지 않습니다. TODO 구현 시 DTO의 conclusion으로 success, failure, cancelled 등을 구분할 수 있습니다.

서명 검증 이후 DTO를 읽습니다. 지원 이벤트에서 JSON이 잘못됐거나 action 또는 해당 실행/작업 ID가 없으면 400을 반환합니다. 미지원 이벤트와 알 수 없는 액션은 202로 수신 확인만 합니다. 기존 202 응답 형식은 유지합니다. 통합 테스트는 6개 이벤트/액션 조합, 잘못된 본문, 미지원 액션 및 ping 수신도 검증합니다.

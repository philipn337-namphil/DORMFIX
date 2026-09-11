# Local development

필수 도구는 Git, Java 21 JDK, Linux container를 사용하는 Docker Compose v2+, Python 3이다. Gradle 8.14.3 wrapper가 고정 distribution을 다운로드하므로 전역 Gradle은 필요 없다. Repository-local `.tools`는 ignored validation cache다.

Root에서 `.env.example`을 `.env`로 복사하고 고유한 local `POSTGRES_PASSWORD`를 설정한다. 실제 credential은 제공하거나 commit하지 않는다. 빈 password는 Compose interpolation을 의도적으로 실패시킨다. Secret이 포함된 rendered `docker compose config`를 공개하지 말고 `--quiet`를 사용한다.

`docker compose up --build -d --wait` 후 liveness/readiness를 확인한다. App은 PostgreSQL health를 기다린 뒤 Flyway와 Hibernate validation을 실행한다. 초기 migration은 business table을 만들지 않는다. `/api/v1/me`는 authentication 보류로 401이며 test user/password를 seed하지 않는다. Port는 127.0.0.1에만 bind하고 DB volume은 보존한다.

종료는 `docker compose stop` 또는 `docker compose down`을 사용하고 routine recovery에서 `-v`를 사용하지 않는다. Password 변경은 기존 DB password를 자동 회전시키지 않으므로 의도적인 local SQL rotation 또는 승인된 disposable reset이 필요하다.

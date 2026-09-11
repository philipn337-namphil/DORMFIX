# Infra README

Root `compose.yml`은 `.env`를 채운 뒤 local PostgreSQL + app을 실행한다. `backend/Dockerfile`은 Java application과 unit/architecture check를 build한다. PostgreSQL integration test는 Docker가 있는 CI에서 image build와 분리해 실행한다. Production template은 operator가 host secret을 만든 뒤에만 사용할 수 있다.

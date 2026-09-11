# Coding conventions

Java 21, four spaces, UTF-8, 명시적 이름과 constructor injection을 사용한다. Immutable DTO/value payload에는 record를, entity/use case에는 일반 class를 선호한다. Lombok이나 generic base service를 줄 수만을 위해 추가하지 않는다. Method는 business intent에 집중하고 comment는 invariant의 이유를 설명할 때만 작성한다.

Package-by-feature와 explicit command service를 사용한다. Giant service, static service locator, controller transaction, entity HTTP response를 금지한다. Mapping은 API/application 경계에서 수행하고 Domain method가 state를 검증한다. Bean Validation은 shape/range, Domain/Application은 relationship/state를 담당한다. Stable error code와 safe message를 사용하며 fake success를 반환하지 않는다.

Test는 behavior를 설명하고 시간 의존 로직에는 Clock, 실제 boundary에만 Mockito를 사용한다. PostgreSQL constraint를 증명하려고 JPA를 mock하지 않는다. 민감 data를 log하지 않는다.

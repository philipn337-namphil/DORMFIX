# Backend architecture

Root package는 `com.dormfix`인 하나의 executable Spring Boot application이다. Package-by-feature 구조는 identity, location, catalog, maintenance, notification, platform으로 나뉜다.

각 feature에서 api는 HTTP/DTO/Bean Validation, application은 use case/query/authorization/transaction/port, domain은 invariant/value data, infrastructure는 JPA/S3/technical adapter를 담당한다. `package-info.java`는 확장 지도를 제공할 뿐 fake service/repository를 만들지 않는다.

허용 방향은 `api -> application -> domain`, `infrastructure -> application/domain`이다. API는 domain entity/infrastructure를 참조하지 않고, Application은 JpaRepository/EntityManager/AWS client 대신 실제 사용 시 좁은 port를 사용한다. Domain은 Jakarta Persistence mapping annotation이라는 제한적 예외를 제외하고 Spring/web/AWS에 의존하지 않는다. Feature 간에는 ID/value contract를 사용하며 package cycle과 private graph 접근을 금지한다.

CreateMaintenanceRequestService, AssignMaintenanceRequestService, StartMaintenanceService 등 command use-case service를 선호하고 query는 별도 projection service로 둔다. 이는 full CQRS가 아니다. Controller는 actor ID와 DTO를 전달하고 application이 authorize/load/version/aggregate/persistence/history를 조정한다. Controller는 transaction을 소유하거나 managed entity를 변경하지 않으며 giant MaintenanceRequestService와 generic CRUD status API를 만들지 않는다.

Foundation security chain은 probe 이외 traffic을 deny한다. Authentication은 아직 구현하지 않았으며 첫 authentication slice에서 Bearer validation과 negative test를 추가한다. 테스트를 통과시키려고 `permitAll`을 넣지 않는다.

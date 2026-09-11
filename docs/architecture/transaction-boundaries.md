# Frozen transaction과 concurrency

Controller는 transaction을 소유하지 않는다. Business command는 하나의 application-service `@Transactional` 안에서 수행하고 query는 필요한 경우 `readOnly=true`를 사용한다. 요청에 필요한 aggregate만 load하며 self-invocation으로 Spring proxy를 우회하지 않는다.

Assign는 version/관리자 scope/worker를 검증하고 기존 assignment를 종료한 뒤 새 assignment를 만들고 `request.assign()`과 RequestHistory를 같은 transaction에서 저장한다. Resolve는 current active worker와 IN_PROGRESS를 검증한 뒤 resolve timestamp/history를 저장한다. Close는 reporter 또는 scoped admin과 RESOLVED를 검증하고 close timestamp/history를 저장한다. 모든 실패는 함께 rollback한다.

Update, assign/reassign, start, hold, resume, resolve, close, reopen, reject, mark duplicate는 submitted version을 요구한다. JPA `@Version` race는 409 `VERSION_CONFLICT`로 변환하며 bulk update/manual increment를 사용하지 않는다. Partial unique index는 `unassigned_at IS NULL` assignment 하나를 보강한다.

S3, email, SQS, 장시간 network 작업을 core DB transaction 안에서 수행하지 않는다. Presign/authorization 후 transaction을 해제하고 upload와 metadata registration을 분리한다. Side effect는 성공적인 commit 뒤에만 수행하며 실패가 이미 commit된 business state를 rollback하지 않는다.

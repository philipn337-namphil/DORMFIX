# DormFix 제품 문서 권위

이 문서는 2026-09-10 foundation 요청으로 확정한 Frozen V1 problem definition이다. Product rule은 frozen이며 engineering decision은 ADR에 별도로 기록한다. `docs/context/`는 historical reference로 현재 baseline을 덮어쓰지 않는다.

DormFix는 대학 기숙사 거주자가 객실 또는 공용시설의 고장을 신고하고, 관리자가 신고를 분류·우선순위화·배정하며, maintenance worker가 방문을 예약하고 수리하고, resident/admin이 결과를 확인하는 dormitory facility maintenance workflow platform이다.

현재 신고는 전화, 게시판, message, 대면 전달처럼 분산되어 있다. Resident는 접수 여부, 담당자, 방문 시간, 완료 여부를 안정적으로 알기 어렵고, Administrator는 duplicate, priority, assignment, visit, repair history, recurring failure, communication을 체계적으로 관리하기 어렵다.

핵심 workflow는 REPORT → ASSIGN → VISIT → REPAIR → CONFIRM이다. Room/common-space location, worker visit, preferred visit time, room-entry consent는 first-class domain concept이다. DormFix는 generic issue tracker가 아니다.

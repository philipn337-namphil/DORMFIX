# Git과 solo PR workflow

현재 baseline branch는 `chore/foundation`이며 feature는 main에서 직접 개발하지 않는다. 최신 main에서 `git switch -c feature/<use-case>`로 시작하고 `feature/`, `fix/`, `docs/`, `chore/` branch를 사용한다. `backend/database`, `backend/controller`, `backend/service` 같은 layer branch는 금지한다.

각 branch는 API → application → domain → persistence → 필요한 migration → test → docs를 포함하는 complete vertical slice다. unrelated 변경을 섞지 않고 작은 commit은 의도를 설명한다. 작업 전후 status/diff를 확인하고 사용자의 uncommitted 변경을 보존한다. force push, destructive reset, history rewrite를 하지 않는다.

PR에는 문제와 결과 동작, scope, API/schema/permission 변경, validation, risk, ADR link를 기록한다. CI와 human checklist를 필수로 하고 authorization과 frozen rule을 검토한다. Architecture baseline 변경은 approved ADR 없이 구현하지 않는다.

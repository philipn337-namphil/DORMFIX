# Repository Guard

`scripts/check_repository.py`는 필수 파일, 깨진 local Markdown link, accidental frozen contract edit, Gradle wrapper tampering, unsafe configuration과 CI gate를 검사한다. 작은 guard이며 YAML parser, Gradle/ArchUnit/Testcontainers, secret scanner, human review를 대체하지 않는다. Historical `docs/context/`는 검사 대상에서 제외하지만 변경하거나 삭제하지 않는다.

`docs/product/frozen-baseline.sha256.json`은 canonical UTF-8/LF text hash를 저장한다. 다섯 product 문서와 aggregate/transaction/event contract를 포함한다. 자동 hash update 명령은 없으며 승인된 변경은 ADR, manifest, harness/docs/tests를 함께 review해야 한다. Manifest와 checker도 수정 가능하므로 PR review가 최종 권위다.

Gradle 8.14.3 wrapper/distribution hash는 publisher checksum endpoint 기준으로 고정한다. Tool upgrade 시 wrapper와 checker constant를 함께 검토한다.

# Infrastructure scope

`infra/`는 local/production Docker Compose, Nginx, systemd template과 운영 문서를 담는다. 실제 AWS resource나 production deployment를 수행하지 않는다. Secret은 template에만 두고 `/etc/dormfix/app.env` 같은 host-managed 경계를 유지한다.

변경 시 Compose 문법과 image build를 검증하고, production template은 명시적 operator authorization 없이는 실행하지 않는다. Nginx는 TLS termination/reverse proxy, systemd는 Docker restart/reboot recovery를 설명하는 template이다.

# ADR-004: Package by feature

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

Vertical slices should be discoverable without traversing global technical layers.

## Decision / 결정

Use identity/location/catalog/maintenance/notification, each with api/application/domain/infrastructure; a small platform package holds technical glue.

## Alternatives / 대안

Global controller/service/repository packages obscure ownership. One Gradle module per aggregate creates unnecessary build ceremony.

## Trade-offs / 장단점

JPA annotations may live in domain objects; technical repositories are adapters behind narrow ports when needed. No mandatory duplicate entity model.

## Consequences / 결과

ArchUnit enforces API/domain/application directions and feature cycles. Cross-feature collaboration uses application contracts/ID events and is reviewed.

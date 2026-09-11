# ADR-003: Pragmatic modular monolith

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

A solo developer needs explicit boundaries and local transactions without distributed operational cost.

## Decision / 결정

One Spring Boot deployment and PostgreSQL database, with feature-owned code and independent aggregates.

## Alternatives / 대안

Microservices introduce distributed transactions and operational burden. An unstructured monolith hides workflow invariants.

## Trade-offs / 장단점

Package boundaries require ArchUnit and code review, but cross-aggregate commands remain simple local transactions.

## Consequences / 결과

No Kubernetes, distributed CQRS, speculative service extraction or generic base-service hierarchy.

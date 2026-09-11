# DormFix: frozen V1 problem definition

Authority: foundation specification supplied 2026-09-10. Product rules are frozen; engineering decisions are recorded separately in ADRs. Historical context is retained under docs/context and cannot override this baseline.

DormFix is a dormitory facility maintenance workflow platform in which university dormitory residents report failures in rooms or common facilities, dormitory administrators triage and assign the reports, maintenance workers schedule visits and perform repairs, and residents/admins confirm the result.

Reports currently arrive through phone calls, bulletin boards, messages and in-person communication. Residents cannot reliably see receipt, handler, visit timing or completion. Administrators struggle with duplicates, priorities, assignment, visits, repair history, recurring failures and communication.

The core workflow is REPORT -> ASSIGN -> VISIT -> REPAIR -> CONFIRM. Room/common-space location, worker visits, preferred visit times and room-entry consent are first-class concepts. DormFix is not a generic issue tracker.

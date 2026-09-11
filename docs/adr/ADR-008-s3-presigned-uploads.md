# ADR-008: Private S3 presigned attachments

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context

Binary uploads should not hold application database transactions or pass through the Java server unnecessarily.

## Decision

Authorize presign, upload directly to S3, then reauthorize/verify/register metadata. Download also uses authorized short-lived URLs.

## Alternatives

Proxying every binary through the API increases load. Public buckets violate resource access rules.

## Trade-offs

Uploads can become orphaned; limits, content verification and cleanup need explicit attachment-slice policy. PUT headers alone cannot prove safe content/size.

## Consequences

Store keys/metadata only. Use least-privilege IAM and no signed URLs in logs; do not add AWS implementation in foundation.

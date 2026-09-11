/**
 * Users, roles and authentication; token storage requires ADR-013 approval.
 * Business invariants and value data; no Spring/web/infrastructure dependencies except JPA mapping annotations.
 * Business implementation intentionally deferred to vertical feature slices.
 */
package com.dormfix.identity.domain;

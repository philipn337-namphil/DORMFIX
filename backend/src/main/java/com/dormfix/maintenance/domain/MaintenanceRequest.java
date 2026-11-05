package com.dormfix.maintenance.domain;

import com.dormfix.catalog.domain.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "maintenance_request")
public class MaintenanceRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "request_number", nullable = false, unique = true, length = 30) private String requestNumber;
    @Column(name = "reporter_id", nullable = false) private Long reporterId;
    @Column(name = "space_id", nullable = false) private Long spaceId;
    @Column(name = "facility_id") private Long facilityId;
    @Column(name = "category_id", nullable = false) private Long categoryId;
    @Column(nullable = false, length = 150) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Priority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RequestStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "entry_policy", nullable = false, length = 40) private EntryPolicy entryPolicy;
    @Column(name = "contact_before_entry", nullable = false) private boolean contactBeforeEntry;
    @Column(name = "preferred_visit_start") private Instant preferredVisitStart;
    @Column(name = "preferred_visit_end") private Instant preferredVisitEnd;
    @Column(name = "duplicate_of_id") private Long duplicateOfId;
    @Column(name = "status_reason", length = 500) private String statusReason;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Version @Column(nullable = false) private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected MaintenanceRequest() { }
    public MaintenanceRequest(Long reporterId, Long spaceId, Long facilityId, Long categoryId, String title,
            String description, Priority priority, EntryPolicy entryPolicy, boolean contactBeforeEntry,
            Instant preferredVisitStart, Instant preferredVisitEnd, Instant occurredAt) {
        this.reporterId=Objects.requireNonNull(reporterId); this.spaceId=Objects.requireNonNull(spaceId); this.facilityId=facilityId;
        this.categoryId=Objects.requireNonNull(categoryId); this.title=Objects.requireNonNull(title); this.description=Objects.requireNonNull(description);
        this.priority=Objects.requireNonNull(priority); this.status=RequestStatus.REPORTED; this.entryPolicy=Objects.requireNonNull(entryPolicy);
        this.contactBeforeEntry=contactBeforeEntry; this.preferredVisitStart=preferredVisitStart; this.preferredVisitEnd=preferredVisitEnd;
        // IDENTITY assigns the database ID only during flush, but request_number is NOT NULL.
        // This private value exists only inside the uncommitted transaction and is replaced by
        // DF-<id> before the command returns, so it can never be observed at the API boundary.
        this.requestNumber = pendingRequestNumber();
        this.createdAt=Objects.requireNonNull(occurredAt); this.updatedAt=occurredAt;
    }
    private String pendingRequestNumber() {
        return "P-" + UUID.randomUUID().toString().replace("-", "").substring(0, 28);
    }
    public void assignRequestNumber() { if (id == null) throw new IllegalStateException("Request ID is required."); if (requestNumber != null && !requestNumber.startsWith("P-")) throw new IllegalStateException("Request number is already assigned."); requestNumber="DF-" + id; }

    /**
     * States an assignment command may act on. {@code REPORTED} is an initial assignment; the
     * other three are reassignments. {@code RESOLVED} is excluded because the frozen matrix does
     * not list it as a reassignment source, and the terminal states are never reassignable.
     */
    public boolean isAssignable() {
        return status == RequestStatus.REPORTED || status == RequestStatus.ASSIGNED
                || status == RequestStatus.IN_PROGRESS || status == RequestStatus.ON_HOLD;
    }

    public boolean matchesVersion(Long expected) {
        return version != null && version.equals(expected);
    }

    public boolean isIn(RequestStatus... allowed) {
        for (RequestStatus candidate : allowed) {
            if (status == candidate) {
                return true;
            }
        }
        return false;
    }

    /**
     * Named lifecycle command moving the request to {@code ASSIGNED} and returning the status it
     * came from, which the caller records in RequestHistory. There is no public status setter.
     */
    public RequestStatus markAssigned(Instant occurredAt) {
        if (!isAssignable()) {
            throw new IllegalStateException("Request cannot be assigned from " + status + ".");
        }
        return transition(RequestStatus.ASSIGNED, occurredAt, statusReason);
    }

    public RequestStatus start(Instant occurredAt, String reason) {
        require(RequestStatus.ASSIGNED);
        return transition(RequestStatus.IN_PROGRESS, occurredAt, reason);
    }

    public RequestStatus hold(Instant occurredAt, String reason) {
        require(RequestStatus.IN_PROGRESS);
        return transition(RequestStatus.ON_HOLD, occurredAt, reason);
    }

    public RequestStatus resume(Instant occurredAt, String reason) {
        require(RequestStatus.ON_HOLD);
        return transition(RequestStatus.IN_PROGRESS, occurredAt, reason);
    }

    public RequestStatus resolve(Instant occurredAt, String reason) {
        require(RequestStatus.IN_PROGRESS);
        this.resolvedAt = occurredAt;
        return transition(RequestStatus.RESOLVED, occurredAt, reason);
    }

    public RequestStatus close(Instant occurredAt, String reason) {
        require(RequestStatus.RESOLVED);
        this.closedAt = occurredAt;
        return transition(RequestStatus.CLOSED, occurredAt, reason);
    }

    /**
     * Returns a resolved request to {@code IN_PROGRESS}. {@code resolvedAt} is cleared because the
     * request is no longer resolved; {@code closedAt} is untouched because CLOSED cannot reopen.
     */
    public RequestStatus reopen(Instant occurredAt, String reason) {
        require(RequestStatus.RESOLVED);
        this.resolvedAt = null;
        return transition(RequestStatus.IN_PROGRESS, occurredAt, reason);
    }

    public RequestStatus reject(Instant occurredAt, String reason) {
        require(RequestStatus.REPORTED);
        return transition(RequestStatus.REJECTED, occurredAt, reason);
    }

    /**
     * Marks the request a duplicate of {@code originalId}. The caller has already proved the
     * original exists, is visible and is not itself a duplicate; this guards only self-reference.
     */
    public RequestStatus markDuplicate(Long originalId, Instant occurredAt, String reason) {
        require(RequestStatus.REPORTED);
        Objects.requireNonNull(originalId);
        if (originalId.equals(id)) {
            throw new IllegalStateException("A request cannot duplicate itself.");
        }
        this.duplicateOfId = originalId;
        return transition(RequestStatus.DUPLICATE, occurredAt, reason);
    }

    private void require(RequestStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Request is " + status + ", not " + expected + ".");
        }
    }

    /**
     * The single place status changes. {@code statusReason} is a projection of the current status,
     * so it is overwritten every transition and cleared when the command supplies nothing.
     */
    private RequestStatus transition(RequestStatus target, Instant occurredAt, String reason) {
        RequestStatus previous = status;
        this.status = target;
        this.statusReason = reason;
        this.updatedAt = Objects.requireNonNull(occurredAt);
        return previous;
    }
    public Long getId(){return id;} public String getRequestNumber(){return requestNumber;} public Long getReporterId(){return reporterId;}
    public Long getSpaceId(){return spaceId;} public Long getFacilityId(){return facilityId;} public Long getCategoryId(){return categoryId;}
    public String getTitle(){return title;} public String getDescription(){return description;} public Priority getPriority(){return priority;}
    public RequestStatus getStatus(){return status;} public EntryPolicy getEntryPolicy(){return entryPolicy;} public boolean isContactBeforeEntry(){return contactBeforeEntry;}
    public Instant getPreferredVisitStart(){return preferredVisitStart;} public Instant getPreferredVisitEnd(){return preferredVisitEnd;}
    public Long getDuplicateOfId(){return duplicateOfId;} public String getStatusReason(){return statusReason;}
    public Instant getResolvedAt(){return resolvedAt;} public Instant getClosedAt(){return closedAt;}
    public Long getVersion(){return version;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}

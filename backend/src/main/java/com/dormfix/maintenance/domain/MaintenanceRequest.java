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
    public Long getId(){return id;} public String getRequestNumber(){return requestNumber;} public Long getReporterId(){return reporterId;}
    public Long getSpaceId(){return spaceId;} public Long getFacilityId(){return facilityId;} public Long getCategoryId(){return categoryId;}
    public String getTitle(){return title;} public String getDescription(){return description;} public Priority getPriority(){return priority;}
    public RequestStatus getStatus(){return status;} public EntryPolicy getEntryPolicy(){return entryPolicy;} public boolean isContactBeforeEntry(){return contactBeforeEntry;}
    public Instant getPreferredVisitStart(){return preferredVisitStart;} public Instant getPreferredVisitEnd(){return preferredVisitEnd;}
    public Long getVersion(){return version;} public Instant getCreatedAt(){return createdAt;}
}

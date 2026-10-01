package com.dormfix.location.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "residence")
public class Residence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "resident_id", nullable = false) private Long residentId;
    @Column(name = "room_space_id", nullable = false) private Long roomSpaceId;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected Residence() { }
    public Residence(Long residentId, Long roomSpaceId, LocalDate startDate, LocalDate endDate, Instant createdAt) {
        this.residentId = Objects.requireNonNull(residentId); this.roomSpaceId = Objects.requireNonNull(roomSpaceId);
        this.startDate = Objects.requireNonNull(startDate); this.endDate = endDate; this.createdAt = Objects.requireNonNull(createdAt);
    }
    public Long getId() { return id; } public Long getResidentId() { return residentId; }
    public Long getRoomSpaceId() { return roomSpaceId; } public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; } public Instant getCreatedAt() { return createdAt; }
    public boolean isCurrent(LocalDate today) { return !startDate.isAfter(today) && (endDate == null || today.isBefore(endDate)); }
    public void end(LocalDate end) { if (!startDate.isBefore(end)) throw new IllegalStateException("Residence cannot end on its start date."); endDate = end; }
}

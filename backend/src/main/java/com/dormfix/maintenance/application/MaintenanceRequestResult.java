package com.dormfix.maintenance.application;
public record MaintenanceRequestResult(Long id, String requestNumber, String status, String priority, Long version) { }

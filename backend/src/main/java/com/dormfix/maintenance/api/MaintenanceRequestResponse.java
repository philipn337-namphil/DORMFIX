package com.dormfix.maintenance.api;
import com.dormfix.maintenance.application.MaintenanceRequestResult;
public record MaintenanceRequestResponse(Long id, String requestNumber, String status, String priority, Long version) { static MaintenanceRequestResponse from(MaintenanceRequestResult value){return new MaintenanceRequestResponse(value.id(),value.requestNumber(),value.status(),value.priority(),value.version());} }

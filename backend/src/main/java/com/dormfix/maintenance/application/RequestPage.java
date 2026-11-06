package com.dormfix.maintenance.application;

import com.dormfix.maintenance.domain.MaintenanceRequest;
import java.util.List;

/** A page of requests from a boundary port, without leaking a Spring Data type. */
public record RequestPage(List<MaintenanceRequest> content, long totalElements) { }

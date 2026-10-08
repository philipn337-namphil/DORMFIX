package com.dormfix.maintenance.application;

import com.dormfix.maintenance.domain.MaintenanceRequest;

public interface MaintenanceRequestRepository {
    MaintenanceRequest saveAndFlush(MaintenanceRequest request);
}

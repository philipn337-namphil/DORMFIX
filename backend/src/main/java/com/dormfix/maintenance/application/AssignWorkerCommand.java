package com.dormfix.maintenance.application;

public record AssignWorkerCommand(Long requestId, Long workerId, String reason, Long version) { }

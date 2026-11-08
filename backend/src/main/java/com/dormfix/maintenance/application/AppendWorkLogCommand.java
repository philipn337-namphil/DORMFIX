package com.dormfix.maintenance.application;

public record AppendWorkLogCommand(Long requestId, String actionType, String description,
        Long visitId) { }

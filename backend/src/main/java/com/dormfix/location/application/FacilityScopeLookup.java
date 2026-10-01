package com.dormfix.location.application;

import java.util.Optional;

public interface FacilityScopeLookup {
    Optional<Long> findSpaceIdByFacilityId(Long facilityId);
}

package com.dormfix.maintenance.application;

import com.dormfix.maintenance.domain.RequestHistory;
import java.util.List;

/**
 * The staff and resident listings are separate methods on purpose. Neither takes an event type or a
 * flag, so the resident path cannot reach COMMENT_DELETED rows through a wrong argument; the
 * exclusion is fixed inside the adapter.
 */
public interface RequestHistoryRepository {
    RequestHistory save(RequestHistory history);

    /** Every row of the request, ordered createdAt ASC, id ASC. */
    List<RequestHistory> findStaffPage(Long requestId, int page, int size);

    long countStaff(Long requestId);

    /** Every row except COMMENT_DELETED, ordered createdAt ASC, id ASC. */
    List<RequestHistory> findResidentPage(Long requestId, int page, int size);

    /** Counts the resident rows only, so the total never reveals a deleted internal remark. */
    long countResident(Long requestId);
}

package com.dormfix.notification.domain;

/**
 * Frozen notification types (ADR-029). Title and message are fixed server templates; the only
 * substituted value is the request number. No user-entered text is ever placed here, because a
 * notification outlives the recipient's relationship to the request.
 */
public enum NotificationType {
    REQUEST_SUBMITTED("새 신고가 접수되었습니다", "%s 신고가 접수되었습니다."),
    ASSIGNED_TO_YOU("새 작업이 배정되었습니다", "%s 신고가 배정되었습니다."),
    UNASSIGNED_FROM_YOU("작업 배정이 해제되었습니다", "%s 신고의 배정이 다른 담당자로 바뀌었습니다."),
    REQUEST_ASSIGNED("담당자가 배정되었습니다", "%s 신고에 담당자가 배정되었습니다."),
    REQUEST_STARTED("작업이 시작되었습니다", "%s 신고의 작업이 시작되었습니다."),
    REQUEST_HELD("작업이 보류되었습니다", "%s 신고의 작업이 보류되었습니다."),
    REQUEST_RESUMED("작업이 재개되었습니다", "%s 신고의 작업이 재개되었습니다."),
    REQUEST_RESOLVED("수리가 완료되었습니다", "%s 신고의 수리가 완료되었습니다. 결과를 확인해 주세요."),
    REQUEST_CLOSED("신고가 종료되었습니다", "%s 신고가 종료되었습니다."),
    REQUEST_REOPENED("신고가 다시 열렸습니다", "%s 신고가 다시 열렸습니다."),
    REQUEST_REJECTED("신고가 반려되었습니다", "%s 신고가 반려되었습니다."),
    REQUEST_MARKED_DUPLICATE("중복 신고로 처리되었습니다", "%s 신고가 중복 신고로 처리되었습니다."),
    VISIT_SCHEDULED("방문 일정이 잡혔습니다", "%s 신고의 방문 일정이 잡혔습니다."),
    VISIT_RESCHEDULED("방문 일정이 변경되었습니다", "%s 신고의 방문 일정이 변경되었습니다."),
    VISIT_CANCELED("방문이 취소되었습니다", "%s 신고의 방문이 취소되었습니다."),
    VISIT_COMPLETED("방문이 완료되었습니다", "%s 신고의 방문이 완료되었습니다."),
    VISIT_NO_ACCESS("방문 시 출입하지 못했습니다", "%s 신고의 방문에서 출입하지 못했습니다."),
    COMMENT_ADDED("새 댓글이 있습니다", "%s 신고에 새 댓글이 달렸습니다.");

    private final String title;
    private final String messageTemplate;

    NotificationType(String title, String messageTemplate) {
        this.title = title;
        this.messageTemplate = messageTemplate;
    }

    public String title() {
        return title;
    }

    public String message(String requestNumber) {
        return String.format(messageTemplate, requestNumber);
    }
}

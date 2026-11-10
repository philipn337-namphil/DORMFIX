package com.dormfix.maintenance.application;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Server-generated object keys of the form
 * {@code requests/{requestId}/attachments/{uploaderId}/{uuid}}. The request and the uploader are
 * part of the key so that registration can prove, without a pending-upload table, that a submitted
 * key was issued to this caller for this request. The file name is never part of a key: it is user
 * input, may be personal data, and would be a path-manipulation channel.
 */
public final class AttachmentObjectKeys {
    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private AttachmentObjectKeys() {
    }

    public static String newKey(Long requestId, Long uploaderId) {
        return prefix(requestId, uploaderId) + UUID.randomUUID();
    }

    /**
     * True only for a key with exactly this request, exactly this uploader, and a canonical UUID
     * as its final segment. Anything else, including extra segments or traversal sequences, is
     * rejected by construction because the remainder must be a bare UUID.
     */
    public static boolean belongsTo(String objectKey, Long requestId, Long uploaderId) {
        if (objectKey == null || requestId == null || uploaderId == null) {
            return false;
        }
        String prefix = prefix(requestId, uploaderId);
        return objectKey.startsWith(prefix)
                && UUID_SEGMENT.matcher(objectKey.substring(prefix.length())).matches();
    }

    private static String prefix(Long requestId, Long uploaderId) {
        return "requests/" + requestId + "/attachments/" + uploaderId + "/";
    }
}

package com.dormfix.maintenance.application;

import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.maintenance.application.AttachmentAccess.DownloadGrant;
import com.dormfix.maintenance.application.AttachmentStorage.PresignedDownload;
import com.dormfix.maintenance.application.AttachmentStorage.PresignedUpload;
import com.dormfix.maintenance.application.AttachmentStorage.StoredObject;
import com.dormfix.maintenance.domain.AttachmentType;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the attachment flow. This class is deliberately not transactional: it alternates
 * between short database transactions in {@link AttachmentAccess} and storage calls, so that no
 * storage call ever runs inside a transaction. It never logs a presigned URL.
 */
@Service
public class AttachmentService {
    private static final int MAX_FILENAME = 255;

    private final AttachmentAccess access;
    private final AttachmentStorage storage;
    private final AttachmentPolicy policy;

    public AttachmentService(AttachmentAccess access, AttachmentStorage storage,
            AttachmentPolicy policy) {
        this.access = access;
        this.storage = storage;
        this.policy = policy;
    }

    public PresignedUploadResult presignUpload(PresignUploadCommand command, Long actorId,
            Set<String> roleNames) {
        AttachmentType type = type(command.type());
        requireFilename(command.originalFilename());
        requireAllowed(command.contentType(), command.fileSize());

        access.authorizeUpload(command.requestId(), type, actorId, roleNames);

        String objectKey = AttachmentObjectKeys.newKey(command.requestId(), actorId);
        PresignedUpload presigned = storage.presignUpload(objectKey, command.contentType(),
                command.fileSize(), policy.uploadUrlLifetime());
        return new PresignedUploadResult(objectKey, presigned.url(), presigned.method(),
                presigned.headers(), presigned.expiresAt());
    }

    public AttachmentResult register(RegisterAttachmentCommand command, Long actorId,
            Set<String> roleNames) {
        AttachmentType type = type(command.type());
        requireFilename(command.originalFilename());

        access.authorizeUpload(command.requestId(), type, actorId, roleNames);

        // The key must have been issued to this caller for this request. This check is what
        // replaces a pending-upload table.
        if (!AttachmentObjectKeys.belongsTo(command.objectKey(), command.requestId(), actorId)) {
            throw new DormitoryScopeAccessDeniedException();
        }

        // What the store holds is recorded, not what the client declared at presign time.
        StoredObject stored = storage.describe(command.objectKey())
                .orElseThrow(() -> new MaintenanceRequestConflictException(
                        "The object has not been uploaded."));
        requireAllowed(stored.contentType(), stored.size());

        return access.register(command.requestId(), actorId, type, command.objectKey(),
                command.originalFilename(), stored.size(), stored.contentType());
    }

    public List<AttachmentResult> list(Long requestId, Long actorId, Set<String> roleNames) {
        return access.list(requestId, actorId, roleNames);
    }

    public DownloadUrlResult downloadUrl(Long attachmentId, Long actorId,
            Set<String> roleNames) {
        DownloadGrant grant = access.authorizeDownload(attachmentId, actorId, roleNames);
        PresignedDownload presigned = storage.presignDownload(grant.objectKey(),
                grant.originalFilename(), policy.downloadUrlLifetime());
        return new DownloadUrlResult(presigned.url(), presigned.expiresAt());
    }

    private void requireAllowed(String contentType, long fileSize) {
        if (contentType == null || !policy.allowedContentTypes().contains(contentType)) {
            throw new InvalidFileTypeException();
        }
        if (fileSize <= 0 || fileSize > policy.maxFileSizeBytes()) {
            throw new FileTooLargeException();
        }
    }

    /**
     * The name is stored for display only and never takes part in any path, but separators and
     * control characters are still refused so it cannot mislead whoever renders or downloads it.
     */
    private void requireFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > MAX_FILENAME) {
            throw new InvalidMaintenanceQueryException("originalFilename is invalid.");
        }
        for (int index = 0; index < filename.length(); index++) {
            char character = filename.charAt(index);
            if (character == '/' || character == '\\' || Character.isISOControl(character)) {
                throw new InvalidMaintenanceQueryException("originalFilename is invalid.");
            }
        }
    }

    private AttachmentType type(String value) {
        try {
            return AttachmentType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new InvalidMaintenanceQueryException("Invalid attachment type.");
        }
    }
}

package com.example.cloudshelf.model

enum class SourceType(val displayName: String) {
    TAKEOUT("Google Takeout"),
    GOOGLE_PHOTOS_PICKER("Google Photos Picker"),
    LOCAL_FOLDER("Local Folder"),
    LAPTOP_FUTURE("Laptop (Future)")
}

enum class MediaState {
    PENDING,
    IMPORTING,
    LOCAL_VERIFIED,
    BACKUP_PENDING,
    BACKUP_CONFIRMED,
    DELETED_LOCAL,
    FAILED
}

enum class BatchState(val label: String) {
    CREATED("Created"),
    SCANNING("Scanning"),
    IMPORTING("Importing"),
    LOCAL_VERIFIED("Local Verified"),
    READY_FOR_BACKUP("Ready for Backup"),
    BACKUP_PENDING("Backup Pending"),
    BACKUP_CONFIRMED("Backup Confirmed"),
    DELETING_LOCAL("Deleting Local Copy"),
    LOCAL_DELETION_VERIFIED("Deletion Verified"),
    COMPLETED("Completed"),
    PAUSED("Paused"),
    FAILED("Failed")
}

enum class ConfirmationMethod {
    NONE,
    USER_MANUAL,
    SEMI_AUTOMATIC,
    AUTOMATIC_FUTURE
}

enum class JobStatus {
    CREATED,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class AuthState {
    CONNECTED,
    EXPIRED,
    REVOKED,
    DISCONNECTED
}

enum class VerificationMode(val displayName: String) {
    MANUAL("Manual (Confirm each batch)"),
    SEMI_AUTOMATIC("Semi-Automatic (Prompt after backup)")
}

enum class DuplicatePolicy(val displayName: String) {
    KEEP_FIRST("Keep First Encounted"),
    KEEP_HIGHEST_QUALITY("Keep Highest Resolution"),
    KEEP_NEWEST("Keep Newest Timestamp")
}

enum class DeviceRole(val label: String) {
    RECEIVER_PIXEL("Pixel Receiver"),
    SOURCE_COMPANION("Source Companion")
}

enum class PairingStatus(val label: String) {
    PENDING_CONFIRMATION("Pending"),
    PAIRED("Paired"),
    REVOKED("Revoked")
}

enum class TransferStatus(val label: String) {
    DISCOVERED("Discovered"),
    QUEUED("Queued"),
    TRANSFERRING("Transferring"),
    PAUSED_BACKPRESSURE("Waiting for Pixel Space"),
    TRANSFERRED("Transferred"),
    HASH_VERIFIED("Hash Verified"),
    PIXEL_STAGED("Staged on Pixel"),
    FAILED("Failed")
}

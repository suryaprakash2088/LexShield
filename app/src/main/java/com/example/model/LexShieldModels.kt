package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class VerificationStatus(val label: String) {
    NOT_SUBMITTED("Not Submitted"),
    SUBMITTED("Submitted – Pending Review"),
    UNDER_REVIEW("Under Manual Review"),
    MORE_INFO_REQUIRED("More Info Required"),
    APPROVED("Platform Verified – Manual Document Review"),
    REJECTED("Verification Rejected"),
    SUSPENDED("Suspended"),
    APPEAL_PENDING("Appeal Pending");

    companion object {
        fun fromRaw(raw: String?): VerificationStatus =
            entries.firstOrNull { it.name == raw } ?: NOT_SUBMITTED
    }
}

enum class SosCategory(val code: String, val title: String, val targetDescription: String) {
    A("A", "Category A – Urgent", "Operational target: under 30 minutes (not a rescue guarantee)"),
    B("B", "Category B – Scheduled / Extended", "Operational target: response within or after 1 hour");

    companion object {
        fun fromRaw(raw: String?): SosCategory =
            entries.firstOrNull { it.code == raw || it.name == raw } ?: A
    }
}

enum class SosIncidentState(val label: String) {
    ACTIVE("Active SOS"),
    ESCALATED("Escalated Priority"),
    RESOLVED("Resolved"),
    CANCELLED("Cancelled");

    companion object {
        fun fromRaw(raw: String?): SosIncidentState =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class ResponderStatus(val label: String) {
    ALERTED("Alerted"),
    ACCEPTED("Accepted"),
    ON_THE_WAY("On the Way"),
    REACHED("Reached Location"),
    DECLINED("Declined"),
    CANCELLED("Cancelled");

    companion object {
        fun fromRaw(raw: String?): ResponderStatus =
            entries.firstOrNull { it.name == raw } ?: ALERTED
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val platformId: String = "",
    val city: String = "",
    val state: String = "",
    val courtLocation: String = "",
    val practiceAreas: List<String> = emptyList(),
    val yearsOfPractice: Long = 0L,
    val bio: String = "",
    val barCouncil: String = "",
    val enrolmentNumberMasked: String = "",
    val enrolmentDate: String = "",
    val evidenceDocName: String = "",
    val verificationStatus: String = VerificationStatus.NOT_SUBMITTED.name,
    val verificationNote: String = "",
    val e2eePublicKeyB64: String = "",
    val e2eeKeyFingerprint: String = "",
    val nearbyOptIn: Boolean = true,
    val locationConsent: Boolean = true,
    val discoverable: Boolean = true,
    val feedPublic: Boolean = true,
    val premiumActive: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val verificationEnum: VerificationStatus
        get() = VerificationStatus.fromRaw(verificationStatus)

    val verifiedPlatformUser: Boolean
        get() = verificationEnum == VerificationStatus.APPROVED
}

@Keep
@IgnoreExtraProperties
data class AdvocateCard(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val platformId: String = "",
    val city: String = "",
    val state: String = "",
    val courtLocation: String = "",
    val practiceAreas: List<String> = emptyList(),
    val verificationStatus: String = VerificationStatus.NOT_SUBMITTED.name,
    val nearbyOptIn: Boolean = true,
    val discoverable: Boolean = true,
    val e2eePublicKeyB64: String = "",
    val e2eeKeyFingerprint: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EmergencyContact(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val relationship: String = "",
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ConsentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val purpose: String = "",
    val policyVersion: String = "v1.0",
    val granted: Boolean = false,
    val sourceScreen: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SosIncident(
    @DocumentId val id: String = "",
    val ownerUserId: String = "",
    val ownerName: String = "",
    val ownerPlatformId: String = "",
    val ownerVerified: Boolean = false,
    val idempotencyKey: String = "",
    val category: String = "A",
    val state: String = "ACTIVE",
    val description: String = "",
    val coarseArea: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Double? = null,
    val locationShared: Boolean = false,
    val broadcastActive: Boolean = true,
    val currentRadiusKm: Long = 2L,
    val alertedCount: Long = 0L,
    val smsChannelStatus: String = "NOT_SENT",
    val pushChannelStatus: String = "PENDING",
    val responderUids: List<String> = emptyList(),
    val closureReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val resolvedAt: Timestamp? = null
) {
    val categoryEnum: SosCategory get() = SosCategory.fromRaw(category)
    val stateEnum: SosIncidentState get() = SosIncidentState.fromRaw(state)
    val activeFlag: Boolean get() = stateEnum == SosIncidentState.ACTIVE || stateEnum == SosIncidentState.ESCALATED
}

@Keep
@IgnoreExtraProperties
data class IncidentResponder(
    @DocumentId val id: String = "",
    val incidentId: String = "",
    val ownerUserId: String = "",
    val responderUserId: String = "",
    val responderName: String = "",
    val responderPlatformId: String = "",
    val status: String = ResponderStatus.ALERTED.name,
    val note: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Double? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: ResponderStatus get() = ResponderStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class EncryptedIncidentMessage(
    @DocumentId val id: String = "",
    val incidentId: String = "",
    val ownerUserId: String = "",
    val participantUids: List<String> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    val ciphertextB64: String = "",
    val ivB64: String = "",
    val senderPublicKeyB64: String = "",
    val senderKeyFingerprint: String = "",
    val protocolVersion: String = "ECDH-P256-AES256-GCM-v1",
    val wrappedKeys: Map<String, String> = emptyMap(),
    val messageType: String = "TEXT",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DecryptedIncidentMessageUi(
    val id: String,
    val senderId: String,
    val senderName: String,
    val plaintext: String,
    val isDecrypted: Boolean,
    val verifiedFingerprint: String,
    val protocolVersion: String,
    val createdAt: Timestamp?
)

@Keep
@IgnoreExtraProperties
data class IncidentEvidence(
    @DocumentId val id: String = "",
    val incidentId: String = "",
    val ownerUserId: String = "",
    val participantUids: List<String> = emptyList(),
    val uploaderId: String = "",
    val uploaderName: String = "",
    val mediaType: String = "NOTE",
    val caption: String = "",
    val durationSec: Long = 0L,
    val sha256Checksum: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class IncidentEvent(
    @DocumentId val id: String = "",
    val incidentId: String = "",
    val ownerUserId: String = "",
    val participantUids: List<String> = emptyList(),
    val actorId: String = "",
    val actorName: String = "",
    val eventType: String = "",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FeedPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPlatformId: String = "",
    val authorVerified: Boolean = false,
    val courtTag: String = "",
    val city: String = "",
    val body: String = "",
    val visibility: String = "NETWORK",
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    val likedByUids: List<String> = emptyList(),
    val reportedByUids: List<String> = emptyList(),
    val moderationStatus: String = "PUBLISHED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FeedComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPlatformId: String = "",
    val body: String = "",
    val visible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LegalUpdateItem(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val title: String = "",
    val court: String = "",
    val caseNumber: String = "",
    val decisionDate: String = "",
    val sourceUrl: String = "",
    val summary: String = "",
    val body: String = "",
    val reviewerName: String = "",
    val reviewStatus: String = "PUBLISHED",
    val tags: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class PoliceDirectoryEntry(
    val id: String,
    val entryType: String,
    val name: String,
    val rankOrDesignation: String,
    val agency: String,
    val state: String,
    val district: String,
    val city: String,
    val address: String,
    val phone: String,
    val email: String,
    val lat: Double,
    val lng: Double,
    val sourceAttribution: String,
    val lastVerifiedDate: String
)

@Keep
@IgnoreExtraProperties
data class SosAlert(
    @DocumentId val id: String = "",
    val ownerUserId: String = "",
    val ownerName: String = "",
    val ownerPlatformId: String = "",
    val category: String = "A",
    val status: String = "ACTIVE",
    val message: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracyM: Double = 0.0,
    val locationSource: String = "PlayServicesFusedGPS",
    val monitoringActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SafetyMonitoringSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val active: Boolean = false,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracyM: Double = 0.0,
    val locationSource: String = "FusedForegroundService",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

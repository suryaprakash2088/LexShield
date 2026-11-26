package com.example.data

import android.content.Context
import com.example.R
import com.example.crypto.E2eeCryptoManager
import com.example.location.LocationFix
import com.example.model.AdvocateCard
import com.example.model.ConsentRecord
import com.example.model.EmergencyContact
import com.example.model.EncryptedIncidentMessage
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.IncidentEvent
import com.example.model.IncidentEvidence
import com.example.model.IncidentResponder
import com.example.model.LegalUpdateItem
import com.example.model.ResponderStatus
import com.example.model.SafetyMonitoringSession
import com.example.model.SosAlert
import com.example.model.SosCategory
import com.example.model.SosIncident
import com.example.model.SosIncidentState
import com.example.model.UserProfile
import com.example.model.VerificationStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class LexShieldRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    private fun <T> Flow<T>.logFirestoreErrors(op: OperationType, path: String, expectedUid: String): Flow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val ex = firestoreError ?: (error as? Exception)
            if (ex != null && auth.currentUser?.uid == expectedUid) {
                handleFirestoreError(ex, op, path)
            }
            throw error
        }

    // --- PROFILE & IDENTITY ---

    fun observeMyProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID required" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .logFirestoreErrors(OperationType.GET, path, userId)
        )
    }

    suspend fun ensureProfileInitialized(
        userId: String,
        displayName: String,
        email: String,
        identityKeyInfo: E2eeCryptoManager.IdentityKeyInfo
    ): Result<Unit> {
        val userPath = "users/$userId"
        val dirPath = "advocate_directory/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Mismatched user session" }

            val userRef = db.collection("users").document(uid)
            val existing = userRef.get().await()
            val safeName = displayName.trim().ifBlank { "Advocate" }.take(80)
            val platformId = "ADV-${uid.take(6).uppercase()}"

            if (!existing.exists()) {
                val userPayload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to email.take(120),
                    "platformId" to platformId,
                    "city" to "New Delhi",
                    "state" to "Delhi",
                    "courtLocation" to "District & High Court Complex",
                    "practiceAreas" to listOf("Civil", "Criminal"),
                    "yearsOfPractice" to 1,
                    "bio" to "",
                    "verificationStatus" to VerificationStatus.NOT_SUBMITTED.name,
                    "e2eePublicKeyB64" to identityKeyInfo.publicKeyB64,
                    "e2eeKeyFingerprint" to identityKeyInfo.fingerprint,
                    "nearbyOptIn" to true,
                    "locationConsent" to true,
                    "discoverable" to true,
                    "feedPublic" to true,
                    "premiumActive" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(userPayload).await()

                val dirPayload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "platformId" to platformId,
                    "city" to "New Delhi",
                    "state" to "Delhi",
                    "courtLocation" to "District & High Court Complex",
                    "practiceAreas" to listOf("Civil", "Criminal"),
                    "verificationStatus" to VerificationStatus.NOT_SUBMITTED.name,
                    "nearbyOptIn" to true,
                    "discoverable" to true,
                    "e2eePublicKeyB64" to identityKeyInfo.publicKeyB64,
                    "e2eeKeyFingerprint" to identityKeyInfo.fingerprint,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("advocate_directory").document(uid).set(dirPayload).await()
            } else {
                // Keep E2EE public key fresh if rotated or missing
                val currentPub = existing.getString("e2eePublicKeyB64") ?: ""
                if (currentPub != identityKeyInfo.publicKeyB64) {
                    userRef.update(
                        mapOf(
                            "e2eePublicKeyB64" to identityKeyInfo.publicKeyB64,
                            "e2eeKeyFingerprint" to identityKeyInfo.fingerprint,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    val dirRef = db.collection("advocate_directory").document(uid)
                    if (dirRef.get().await().exists()) {
                        dirRef.update(
                            mapOf(
                                "e2eePublicKeyB64" to identityKeyInfo.publicKeyB64,
                                "e2eeKeyFingerprint" to identityKeyInfo.fingerprint,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$userPath / $dirPath")
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        city: String,
        state: String,
        courtLocation: String,
        practiceAreas: List<String>,
        yearsOfPractice: Int,
        bio: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val authedUid = requireUserId()
            val cleanName = displayName.trim().ifBlank { "Advocate" }.take(80)
            val cleanAreas = practiceAreas.map { it.trim() }.filter { it.isNotBlank() }.take(10)
            val cleanYears = yearsOfPractice.coerceIn(0, 80)

            db.collection("users").document(authedUid).update(
                mapOf(
                    "displayName" to cleanName,
                    "city" to city.trim().take(60),
                    "state" to state.trim().take(60),
                    "courtLocation" to courtLocation.trim().take(100),
                    "practiceAreas" to cleanAreas,
                    "yearsOfPractice" to cleanYears,
                    "bio" to bio.trim().take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("advocate_directory").document(authedUid).update(
                mapOf(
                    "displayName" to cleanName,
                    "city" to city.trim().take(60),
                    "state" to state.trim().take(60),
                    "courtLocation" to courtLocation.trim().take(100),
                    "practiceAreas" to cleanAreas,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun submitEnrolmentVerification(
        barCouncil: String,
        enrolmentNumber: String,
        enrolmentDate: String,
        evidenceDocName: String,
        isAppeal: Boolean = false
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val authedUid = requireUserId()
            val rawNum = enrolmentNumber.trim()
            val masked = if (rawNum.length > 4) {
                rawNum.take(2) + "****" + rawNum.takeLast(2)
            } else {
                "****" + rawNum.takeLast(2)
            }
            val newStatus = if (isAppeal) VerificationStatus.APPEAL_PENDING.name else VerificationStatus.SUBMITTED.name

            db.collection("users").document(authedUid).update(
                mapOf(
                    "barCouncil" to barCouncil.trim().take(80),
                    "enrolmentNumberMasked" to masked.take(40),
                    "enrolmentDate" to enrolmentDate.trim().take(30),
                    "evidenceDocName" to evidenceDocName.trim().take(120),
                    "verificationStatus" to newStatus,
                    "verificationNote" to "Submitted for manual Bar Council document review.",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("advocate_directory").document(authedUid).update(
                mapOf(
                    "verificationStatus" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updatePrivacyAndConsents(
        nearbyOptIn: Boolean,
        locationConsent: Boolean,
        discoverable: Boolean,
        feedPublic: Boolean,
        premiumActive: Boolean,
        changedPurpose: String,
        granted: Boolean,
        sourceScreen: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val authedUid = requireUserId()
            db.collection("users").document(authedUid).update(
                mapOf(
                    "nearbyOptIn" to nearbyOptIn,
                    "locationConsent" to locationConsent,
                    "discoverable" to discoverable,
                    "feedPublic" to feedPublic,
                    "premiumActive" to premiumActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("advocate_directory").document(authedUid).update(
                mapOf(
                    "nearbyOptIn" to nearbyOptIn,
                    "discoverable" to discoverable,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val consentId = changedPurpose.lowercase().replace(Regex("[^a-z0-9_]"), "_").take(60)
            val consentRef = db.collection("users").document(authedUid)
                .collection("consents").document(consentId)
            val snap = consentRef.get().await()
            if (snap.exists()) {
                consentRef.update(
                    mapOf(
                        "policyVersion" to "v1.0",
                        "granted" to granted,
                        "sourceScreen" to sourceScreen.take(60),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                consentRef.set(
                    mapOf(
                        "userId" to authedUid,
                        "purpose" to changedPurpose.take(60),
                        "policyVersion" to "v1.0",
                        "granted" to granted,
                        "sourceScreen" to sourceScreen.take(60),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    // --- ADMIN VERIFICATION QUEUE (For authorized admin accounts) ---

    fun observePendingVerifications(userId: String): Flow<List<UserProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.verificationStatus in listOf("SUBMITTED", "UNDER_REVIEW", "APPEAL_PENDING", "MORE_INFO_REQUIRED") }
                }
                .logFirestoreErrors(OperationType.LIST, path, userId)
        )
    }

    suspend fun adminReviewVerification(
        targetUserId: String,
        newStatus: VerificationStatus,
        note: String
    ): Result<Unit> {
        return try {
            requireUserId()
            db.collection("users").document(targetUserId).update(
                mapOf(
                    "verificationStatus" to newStatus.name,
                    "verificationNote" to note.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("advocate_directory").document(targetUserId).update(
                mapOf(
                    "verificationStatus" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$targetUserId")
            Result.failure(e)
        }
    }

    // --- EMERGENCY CONTACTS ---

    fun observeEmergencyContacts(userId: String): Flow<List<EmergencyContact>> = flow {
        val path = "users/$userId/emergency_contacts"
        emitAll(
            db.collection("users").document(userId).collection("emergency_contacts")
                .snapshots()
                .map { snap ->
                    snap.toObjects(EmergencyContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, userId)
        )
    }

    suspend fun addEmergencyContact(
        name: String,
        phoneNumber: String,
        relationship: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val authedUid = requireUserId()
            val cleanName = name.trim().take(80)
            val cleanPhone = phoneNumber.trim().take(24)
            val cleanRel = relationship.trim().ifBlank { "Colleague" }.take(40)
            require(cleanName.isNotEmpty() && cleanPhone.length >= 4) { "Valid name and phone number required" }

            val docRef = db.collection("users").document(authedUid)
                .collection("emergency_contacts").document()
            docRef.set(
                mapOf(
                    "userId" to authedUid,
                    "name" to cleanName,
                    "phoneNumber" to cleanPhone,
                    "relationship" to cleanRel,
                    "enabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/emergency_contacts")
            Result.failure(e)
        }
    }

    suspend fun toggleEmergencyContact(contactId: String, enabled: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val authedUid = requireUserId()
            db.collection("users").document(authedUid)
                .collection("emergency_contacts").document(contactId)
                .update(
                    mapOf(
                        "enabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/emergency_contacts/$contactId")
            Result.failure(e)
        }
    }

    suspend fun deleteEmergencyContact(contactId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val authedUid = requireUserId()
            db.collection("users").document(authedUid)
                .collection("emergency_contacts").document(contactId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/emergency_contacts/$contactId")
            Result.failure(e)
        }
    }

    // --- ADVOCATE DIRECTORY & E2EE PUBLIC KEYS ---

    fun observeDiscoverableAdvocates(userId: String): Flow<List<AdvocateCard>> = flow {
        val path = "advocate_directory"
        emitAll(
            db.collection("advocate_directory")
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AdvocateCard::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logFirestoreErrors(OperationType.LIST, path, userId)
        )
    }

    fun observeNearbyRespondersPool(userId: String): Flow<List<AdvocateCard>> = flow {
        val path = "advocate_directory"
        emitAll(
            db.collection("advocate_directory")
                .whereEqualTo("nearbyOptIn", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AdvocateCard::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logFirestoreErrors(OperationType.LIST, path, userId)
        )
    }

    // --- SOS INCIDENTS & COORDINATION ---

    fun observeMyIncidents(userId: String): Flow<List<SosIncident>> = flow {
        val path = "sos_incidents"
        emitAll(
            db.collection("sos_incidents")
                .whereEqualTo("ownerUserId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SosIncident::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, userId)
        )
    }

    fun observeActiveBroadcastIncidents(userId: String): Flow<List<SosIncident>> = flow {
        val path = "sos_incidents"
        emitAll(
            db.collection("sos_incidents")
                .whereEqualTo("broadcastActive", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SosIncident::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, userId)
        )
    }

    fun observeResponderAssignments(userId: String): Flow<List<SosIncident>> = flow {
        val path = "sos_incidents"
        emitAll(
            db.collection("sos_incidents")
                .whereArrayContains("responderUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SosIncident::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, userId)
        )
    }

    fun observeIncidentResponders(incidentId: String, ownerUserId: String, currentUserId: String): Flow<List<IncidentResponder>> = flow {
        val path = "sos_incidents/$incidentId/responders"
        val query = if (ownerUserId == currentUserId) {
            db.collection("sos_incidents").document(incidentId).collection("responders")
                .whereEqualTo("ownerUserId", currentUserId)
        } else {
            db.collection("sos_incidents").document(incidentId).collection("responders")
                .whereEqualTo("responderUserId", currentUserId)
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(IncidentResponder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logFirestoreErrors(OperationType.LIST, path, currentUserId)
        )
    }

    fun observeIncidentMessages(incidentId: String, currentUserId: String): Flow<List<EncryptedIncidentMessage>> = flow {
        val path = "sos_incidents/$incidentId/messages"
        emitAll(
            db.collection("sos_incidents").document(incidentId).collection("messages")
                .whereArrayContains("participantUids", currentUserId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(EncryptedIncidentMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .logFirestoreErrors(OperationType.LIST, path, currentUserId)
        )
    }

    fun observeIncidentEvidence(incidentId: String, currentUserId: String): Flow<List<IncidentEvidence>> = flow {
        val path = "sos_incidents/$incidentId/evidence"
        emitAll(
            db.collection("sos_incidents").document(incidentId).collection("evidence")
                .whereArrayContains("participantUids", currentUserId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(IncidentEvidence::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, currentUserId)
        )
    }

    fun observeIncidentEvents(incidentId: String, currentUserId: String): Flow<List<IncidentEvent>> = flow {
        val path = "sos_incidents/$incidentId/events"
        emitAll(
            db.collection("sos_incidents").document(incidentId).collection("events")
                .whereArrayContains("participantUids", currentUserId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(IncidentEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, currentUserId)
        )
    }

    suspend fun createSosIncident(
        profile: UserProfile,
        category: SosCategory,
        idempotencyKey: String,
        description: String,
        coarseArea: String,
        lat: Double?,
        lng: Double?,
        accuracyM: Float?,
        locationShared: Boolean,
        nearbyPoolCount: Int,
        hasContacts: Boolean
    ): Result<String> {
        val docId = "sos_${idempotencyKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(48)}"
        val path = "sos_incidents/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("sos_incidents").document(docId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                return Result.success(docId)
            }

            val payload = mutableMapOf<String, Any>(
                "ownerUserId" to uid,
                "ownerName" to profile.displayName.ifBlank { "Advocate" }.take(80),
                "ownerPlatformId" to profile.platformId.ifBlank { "ADV-${uid.take(6).uppercase()}" },
                "ownerVerified" to profile.verifiedPlatformUser,
                "idempotencyKey" to idempotencyKey.take(80),
                "category" to category.code,
                "state" to SosIncidentState.ACTIVE.name,
                "description" to description.trim().take(500),
                "coarseArea" to coarseArea.trim().ifBlank { profile.courtLocation.ifBlank { profile.city.ifBlank { "Location pending" } } }.take(120),
                "locationShared" to (locationShared && lat != null && lng != null),
                "broadcastActive" to true,
                "currentRadiusKm" to 2L,
                "alertedCount" to nearbyPoolCount.coerceIn(0, 500),
                "smsChannelStatus" to if (hasContacts) "SUBMITTED" else "NOT_SENT",
                "pushChannelStatus" to "SUBMITTED",
                "responderUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (locationShared && lat != null && lng != null) {
                payload["lat"] = lat
                payload["lng"] = lng
                if (accuracyM != null) payload["accuracyM"] = accuracyM.toDouble()
            }

            docRef.set(payload).await()

            docRef.collection("events").document().set(
                mapOf(
                    "incidentId" to docId,
                    "ownerUserId" to uid,
                    "participantUids" to listOf(uid),
                    "actorId" to uid,
                    "actorName" to profile.displayName.ifBlank { "Advocate" }.take(80),
                    "eventType" to "SOS_CREATED",
                    "summary" to "Category ${category.code} SOS activated • 2 km radius alert stage initiated",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateIncidentLocation(
        incidentId: String,
        lat: Double,
        lng: Double,
        accuracyM: Float
    ): Result<Unit> {
        return try {
            requireUserId()
            db.collection("sos_incidents").document(incidentId).update(
                mapOf(
                    "lat" to lat,
                    "lng" to lng,
                    "accuracyM" to accuracyM.toDouble(),
                    "locationShared" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "sos_incidents/$incidentId")
            Result.failure(e)
        }
    }

    suspend fun expandIncidentRadius(
        incident: SosIncident,
        nextRadiusKm: Int,
        newAlertedCount: Int,
        actorName: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            db.collection("sos_incidents").document(incident.id).update(
                mapOf(
                    "currentRadiusKm" to nextRadiusKm,
                    "alertedCount" to newAlertedCount.coerceIn(0, 500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val participants = (listOf(incident.ownerUserId) + incident.responderUids).distinct().take(25)
            db.collection("sos_incidents").document(incident.id).collection("events").document().set(
                mapOf(
                    "incidentId" to incident.id,
                    "ownerUserId" to incident.ownerUserId,
                    "participantUids" to participants,
                    "actorId" to uid,
                    "actorName" to actorName.take(80),
                    "eventType" to "RADIUS_EXPANDED",
                    "summary" to "Nearby responder broadcast expanded to $nextRadiusKm km",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "sos_incidents/${incident.id}")
            Result.failure(e)
        }
    }

    suspend fun escalateIncidentCategory(incident: SosIncident, actorName: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            db.collection("sos_incidents").document(incident.id).update(
                mapOf(
                    "category" to SosCategory.A.code,
                    "state" to SosIncidentState.ESCALATED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val participants = (listOf(incident.ownerUserId) + incident.responderUids).distinct().take(25)
            db.collection("sos_incidents").document(incident.id).collection("events").document().set(
                mapOf(
                    "incidentId" to incident.id,
                    "ownerUserId" to incident.ownerUserId,
                    "participantUids" to participants,
                    "actorId" to uid,
                    "actorName" to actorName.take(80),
                    "eventType" to "ESCALATED_TO_A",
                    "summary" to "Incident escalated to Category A (< 30 min target)",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "sos_incidents/${incident.id}")
            Result.failure(e)
        }
    }

    suspend fun markSmsHandoff(incidentId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("sos_incidents").document(incidentId).update(
                mapOf(
                    "smsChannelStatus" to "SMS_HANDOFF",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "sos_incidents/$incidentId")
            Result.failure(e)
        }
    }

    suspend fun closeSosIncident(
        incident: SosIncident,
        resolved: Boolean,
        reason: String,
        actorName: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val targetState = if (resolved) SosIncidentState.RESOLVED.name else SosIncidentState.CANCELLED.name
            db.collection("sos_incidents").document(incident.id).update(
                mapOf(
                    "state" to targetState,
                    "broadcastActive" to false,
                    "closureReason" to reason.trim().ifBlank { "Closed by incident owner" }.take(200),
                    "resolvedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val participants = (listOf(incident.ownerUserId) + incident.responderUids).distinct().take(25)
            db.collection("sos_incidents").document(incident.id).collection("events").document().set(
                mapOf(
                    "incidentId" to incident.id,
                    "ownerUserId" to incident.ownerUserId,
                    "participantUids" to participants,
                    "actorId" to uid,
                    "actorName" to actorName.take(80),
                    "eventType" to "INCIDENT_$targetState",
                    "summary" to "Incident marked $targetState: ${reason.ifBlank { "Closed by owner" }}",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "sos_incidents/${incident.id}")
            Result.failure(e)
        }
    }

    suspend fun updateResponderStatus(
        incident: SosIncident,
        responderProfile: UserProfile,
        newStatus: ResponderStatus,
        note: String,
        lat: Double? = null,
        lng: Double? = null,
        accuracyM: Float? = null
    ): Result<Unit> {
        val path = "sos_incidents/${incident.id}/responders/${responderProfile.userId}"
        return try {
            val uid = requireUserId()
            val incRef = db.collection("sos_incidents").document(incident.id)

            // Ensure responder is registered in responderUids
            if (uid !in incident.responderUids && incident.responderUids.size < 25) {
                val updatedUids = (incident.responderUids + uid).distinct().take(25)
                incRef.update(
                    mapOf(
                        "responderUids" to updatedUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val respRef = incRef.collection("responders").document(uid)
            val existing = respRef.get().await()
            if (!existing.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "incidentId" to incident.id,
                    "ownerUserId" to incident.ownerUserId,
                    "responderUserId" to uid,
                    "responderName" to responderProfile.displayName.ifBlank { "Advocate Responder" }.take(80),
                    "responderPlatformId" to responderProfile.platformId.ifBlank { "ADV-${uid.take(6).uppercase()}" },
                    "status" to newStatus.name,
                    "note" to note.trim().take(240),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (lat != null && lng != null) {
                    payload["lat"] = lat
                    payload["lng"] = lng
                    if (accuracyM != null) payload["accuracyM"] = accuracyM.toDouble()
                }
                respRef.set(payload).await()
            } else {
                val updateMap = mutableMapOf<String, Any>(
                    "status" to newStatus.name,
                    "note" to note.trim().take(240),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (lat != null && lng != null) {
                    updateMap["lat"] = lat
                    updateMap["lng"] = lng
                    if (accuracyM != null) updateMap["accuracyM"] = accuracyM.toDouble()
                }
                respRef.update(updateMap).await()
            }

            val participants = (listOf(incident.ownerUserId, uid) + incident.responderUids).distinct().take(25)
            incRef.collection("events").document().set(
                mapOf(
                    "incidentId" to incident.id,
                    "ownerUserId" to incident.ownerUserId,
                    "participantUids" to participants,
                    "actorId" to uid,
                    "actorName" to responderProfile.displayName.ifBlank { "Responder" }.take(80),
                    "eventType" to "RESPONDER_${newStatus.name}",
                    "summary" to "${responderProfile.displayName.ifBlank { "Responder" }} marked status: ${newStatus.label}",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun sendEncryptedMessage(
        incident: SosIncident,
        senderName: String,
        envelope: E2eeCryptoManager.EncryptedEnvelope
    ): Result<Unit> {
        val path = "sos_incidents/${incident.id}/messages"
        return try {
            val uid = requireUserId()
            val participants = (listOf(incident.ownerUserId, uid) + incident.responderUids).distinct().take(25)
            val msgRef = db.collection("sos_incidents").document(incident.id)
                .collection("messages").document()
            msgRef.set(
                mapOf(
                    "incidentId" to incident.id,
                    "ownerUserId" to incident.ownerUserId,
                    "participantUids" to participants,
                    "senderId" to uid,
                    "senderName" to senderName.ifBlank { "Advocate" }.take(80),
                    "ciphertextB64" to envelope.ciphertextB64.take(8192),
                    "ivB64" to envelope.ivB64.take(64),
                    "senderPublicKeyB64" to envelope.senderPublicKeyB64.take(512),
                    "senderKeyFingerprint" to envelope.senderKeyFingerprint.take(128),
                    "protocolVersion" to envelope.protocolVersion,
                    "wrappedKeys" to envelope.wrappedKeys,
                    "messageType" to "TEXT",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addIncidentEvidence(
        incident: SosIncident,
        uploaderName: String,
        mediaType: String,
        caption: String,
        durationSec: Int,
        sha256Checksum: String
    ): Result<Unit> {
        val path = "sos_incidents/${incident.id}/evidence"
        return try {
            val uid = requireUserId()
            val participants = (listOf(incident.ownerUserId, uid) + incident.responderUids).distinct().take(25)
            val incRef = db.collection("sos_incidents").document(incident.id)
            incRef.collection("evidence").document().set(
                mapOf(
                    "incidentId" to incident.id,
                    "ownerUserId" to incident.ownerUserId,
                    "participantUids" to participants,
                    "uploaderId" to uid,
                    "uploaderName" to uploaderName.ifBlank { "Advocate" }.take(80),
                    "mediaType" to mediaType,
                    "caption" to caption.trim().ifBlank { "$mediaType evidence" }.take(500),
                    "durationSec" to durationSec.coerceIn(0, 3600),
                    "sha256Checksum" to sha256Checksum.take(128),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            incRef.collection("events").document().set(
                mapOf(
                    "incidentId" to incident.id,
                    "ownerUserId" to incident.ownerUserId,
                    "participantUids" to participants,
                    "actorId" to uid,
                    "actorName" to uploaderName.ifBlank { "Advocate" }.take(80),
                    "eventType" to "EVIDENCE_$mediaType",
                    "summary" to "Logged $mediaType record (SHA-256: ${sha256Checksum.take(10)}…)",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- PROFESSIONAL NETWORK FEED ---

    fun observeNetworkFeed(userId: String): Flow<List<FeedPost>> = flow {
        val path = "feed_posts"
        emitAll(
            db.collection("feed_posts")
                .whereEqualTo("moderationStatus", "PUBLISHED")
                .whereEqualTo("visibility", "NETWORK")
                .snapshots()
                .map { snap ->
                    snap.toObjects(FeedPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, userId)
        )
    }

    suspend fun createFeedPost(
        profile: UserProfile,
        courtTag: String,
        body: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val cleanBody = body.trim().take(1500)
            require(cleanBody.isNotEmpty()) { "Post cannot be empty" }
            db.collection("feed_posts").document().set(
                mapOf(
                    "authorId" to uid,
                    "authorName" to profile.displayName.ifBlank { "Advocate" }.take(80),
                    "authorPlatformId" to profile.platformId.ifBlank { "ADV-${uid.take(6).uppercase()}" },
                    "authorVerified" to profile.verifiedPlatformUser,
                    "courtTag" to courtTag.trim().ifBlank { "Bar Network" }.take(60),
                    "city" to profile.city.take(60),
                    "body" to cleanBody,
                    "visibility" to "NETWORK",
                    "likeCount" to 0L,
                    "commentCount" to 0L,
                    "likedByUids" to emptyList<String>(),
                    "reportedByUids" to emptyList<String>(),
                    "moderationStatus" to "PUBLISHED",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "feed_posts")
            Result.failure(e)
        }
    }

    suspend fun toggleLikePost(post: FeedPost): Result<Unit> {
        return try {
            val uid = requireUserId()
            val currentlyLiked = uid in post.likedByUids
            val nextLiked = if (currentlyLiked) {
                post.likedByUids.filter { it != uid }
            } else {
                (post.likedByUids + uid).distinct().take(100)
            }
            val nextCount = nextLiked.size.toLong()
            db.collection("feed_posts").document(post.id).update(
                mapOf(
                    "likedByUids" to nextLiked,
                    "likeCount" to nextCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "feed_posts/${post.id}")
            Result.failure(e)
        }
    }

    suspend fun reportPost(post: FeedPost): Result<Unit> {
        return try {
            val uid = requireUserId()
            val nextReported = (post.reportedByUids + uid).distinct().take(100)
            val nextStatus = if (nextReported.size >= 3) "UNDER_REVIEW" else post.moderationStatus
            db.collection("feed_posts").document(post.id).update(
                mapOf(
                    "reportedByUids" to nextReported,
                    "moderationStatus" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "feed_posts/${post.id}")
            Result.failure(e)
        }
    }

    fun observePostComments(postId: String, currentUserId: String): Flow<List<FeedComment>> = flow {
        val path = "feed_posts/$postId/comments"
        emitAll(
            db.collection("feed_posts").document(postId).collection("comments")
                .whereEqualTo("visible", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FeedComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .logFirestoreErrors(OperationType.LIST, path, currentUserId)
        )
    }

    suspend fun addPostComment(post: FeedPost, profile: UserProfile, body: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val cleanBody = body.trim().take(500)
            require(cleanBody.isNotEmpty()) { "Comment cannot be empty" }
            val postRef = db.collection("feed_posts").document(post.id)
            postRef.collection("comments").document().set(
                mapOf(
                    "postId" to post.id,
                    "authorId" to uid,
                    "authorName" to profile.displayName.ifBlank { "Advocate" }.take(80),
                    "authorPlatformId" to profile.platformId.ifBlank { "ADV-${uid.take(6).uppercase()}" },
                    "body" to cleanBody,
                    "visible" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            postRef.update(
                mapOf(
                    "commentCount" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "feed_posts/${post.id}/comments")
            Result.failure(e)
        }
    }

    // --- LEGAL UPDATES ---

    fun observePublishedLegalUpdates(userId: String): Flow<List<LegalUpdateItem>> = flow {
        val path = "legal_updates"
        emitAll(
            db.collection("legal_updates")
                .whereEqualTo("reviewStatus", "PUBLISHED")
                .snapshots()
                .map { snap ->
                    snap.toObjects(LegalUpdateItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logFirestoreErrors(OperationType.LIST, path, userId)
        )
    }

    // --- SAFETY MONITORING & DEDICATED SOS ALERTS ---

    fun observeMySafetyMonitoring(userId: String): Flow<SafetyMonitoringSession?> = flow {
        val path = "users/$userId/safety_monitoring/live"
        emitAll(
            db.collection("users").document(userId)
                .collection("safety_monitoring").document("live")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(SafetyMonitoringSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .logFirestoreErrors(OperationType.GET, path, userId)
        )
    }

    fun observeMySosAlerts(userId: String): Flow<List<SosAlert>> = flow {
        val path = "sos_alerts"
        emitAll(
            db.collection("sos_alerts")
                .whereEqualTo("ownerUserId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SosAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, userId)
        )
    }

    fun observeActiveSosAlerts(userId: String): Flow<List<SosAlert>> = flow {
        val path = "sos_alerts"
        emitAll(
            db.collection("sos_alerts")
                .whereEqualTo("status", "ACTIVE")
                .snapshots()
                .map { snap ->
                    snap.toObjects(SosAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, userId)
        )
    }

    suspend fun pushImmediateSosAlert(
        profile: UserProfile,
        category: SosCategory,
        message: String,
        fix: LocationFix,
        monitoringActive: Boolean
    ): Result<String> {
        val alertId = "alert_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "sos_alerts/$alertId"
        return try {
            val uid = requireUserId()
            val payload = mapOf(
                "ownerUserId" to uid,
                "ownerName" to profile.displayName.ifBlank { "Advocate" }.take(80),
                "ownerPlatformId" to profile.platformId.ifBlank { "ADV-${uid.take(6).uppercase()}" },
                "category" to category.code,
                "status" to "ACTIVE",
                "message" to message.trim().ifBlank { "Immediate SOS Alert triggered" }.take(300),
                "lat" to fix.lat,
                "lng" to fix.lng,
                "accuracyM" to fix.accuracyM.toDouble(),
                "locationSource" to fix.source.ifBlank { "PlayServicesFusedGPS" }.take(40),
                "monitoringActive" to monitoringActive,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("sos_alerts").document(alertId).set(payload).await()
            Result.success(alertId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun resolveSosAlert(alertId: String, status: String = "RESOLVED"): Result<Unit> {
        val path = "sos_alerts/$alertId"
        return try {
            requireUserId()
            val cleanStatus = if (status in listOf("RESOLVED", "CANCELLED")) status else "RESOLVED"
            db.collection("sos_alerts").document(alertId).update(
                mapOf(
                    "status" to cleanStatus,
                    "monitoringActive" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}

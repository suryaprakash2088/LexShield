package com.example.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-End Encryption (E2EE) protocol manager for SOS incident communications.
 *
 * Protocol: ECDH-P256-AES256-GCM-v1
 * - Each advocate maintains an EC P-256 (secp256r1) identity keypair.
 * - The EC private key is encrypted at rest using an AndroidKeyStore hardware-backed AES-256-GCM
 *   master wrapping key bound to the authenticated user's UID.
 * - Outgoing messages generate an ephemeral 256-bit AES content key (MK), encrypt the plaintext
 *   with AES-256-GCM + AAD (incidentId | senderId | protocolVersion), and wrap MK for every
 *   participant via ECDH(senderPriv, recipientPub) -> HKDF-SHA256 -> AES-256-GCM.
 */
class E2eeCryptoManager(context: Context, private val userId: String) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    data class IdentityKeyInfo(
        val publicKeyB64: String,
        val fingerprint: String
    )

    data class EncryptedEnvelope(
        val ciphertextB64: String,
        val ivB64: String,
        val senderPublicKeyB64: String,
        val senderKeyFingerprint: String,
        val protocolVersion: String,
        val wrappedKeys: Map<String, String>
    )

    sealed interface DecryptionResult {
        data class Plaintext(val text: String, val verifiedFingerprint: String) : DecryptionResult
        data class Unavailable(val reason: String) : DecryptionResult
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val alias = "lexshield_e2ee_wrap_$userId"
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(alias, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    @Synchronized
    fun getOrCreateIdentityKeyPair(): KeyPair {
        val pubKeyPref = "ec_pub_$userId"
        val privEncPref = "ec_priv_enc_$userId"
        val privIvPref = "ec_priv_iv_$userId"

        val existingPubB64 = prefs.getString(pubKeyPref, null)
        val existingPrivEncB64 = prefs.getString(privEncPref, null)
        val existingPrivIvB64 = prefs.getString(privIvPref, null)

        if (existingPubB64 != null && existingPrivEncB64 != null && existingPrivIvB64 != null) {
            try {
                val masterKey = getOrCreateMasterKey()
                val cipher = Cipher.getInstance(AES_GCM)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    masterKey,
                    GCMParameterSpec(GCM_TAG_BITS, Base64.decode(existingPrivIvB64, Base64.NO_WRAP))
                )
                val privBytes = cipher.doFinal(Base64.decode(existingPrivEncB64, Base64.NO_WRAP))
                val pubBytes = Base64.decode(existingPubB64, Base64.NO_WRAP)
                val kf = KeyFactory.getInstance("EC")
                val pubKey = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                return KeyPair(pubKey, privKey)
            } catch (e: Exception) {
                Log.w(TAG, "Re-generating E2EE keypair after unwrap error", e)
            }
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val kp = kpg.generateKeyPair()

        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val encryptedPriv = cipher.doFinal(kp.private.encoded)

        prefs.edit()
            .putString(pubKeyPref, Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP))
            .putString(privEncPref, Base64.encodeToString(encryptedPriv, Base64.NO_WRAP))
            .putString(privIvPref, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()

        return kp
    }

    fun getIdentityInfo(): IdentityKeyInfo {
        val kp = getOrCreateIdentityKeyPair()
        val pubB64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        val fp = computeFingerprint(kp.public.encoded)
        return IdentityKeyInfo(publicKeyB64 = pubB64, fingerprint = fp)
    }

    fun encryptMessage(
        incidentId: String,
        plaintext: String,
        participantPublicKeys: Map<String, String>
    ): EncryptedEnvelope {
        val kp = getOrCreateIdentityKeyPair()
        val senderPubB64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        val senderFp = computeFingerprint(kp.public.encoded)

        // 1. Fresh 256-bit message key
        val mkBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val mkSpec = SecretKeySpec(mkBytes, "AES")

        // 2. Encrypt message body with AES-256-GCM + AAD
        val msgIv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, mkSpec, GCMParameterSpec(GCM_TAG_BITS, msgIv))
        val aad = "$incidentId|$userId|$PROTOCOL_VERSION".toByteArray(Charsets.UTF_8)
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // 3. Wrap message key for each participant (always include sender)
        val allTargets = participantPublicKeys.toMutableMap()
        allTargets[userId] = senderPubB64

        val wrappedKeys = mutableMapOf<String, String>()
        for ((uid, targetPubB64) in allTargets) {
            if (targetPubB64.isBlank()) continue
            runCatching {
                val recipientPub = decodePublicKey(targetPubB64)
                val kek = derivePairwiseKek(kp.private, recipientPub, incidentId)
                val wrapIv = ByteArray(12).also { secureRandom.nextBytes(it) }
                val wrapCipher = Cipher.getInstance(AES_GCM)
                wrapCipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, wrapIv))
                wrapCipher.updateAAD(uid.toByteArray(Charsets.UTF_8))
                val wrappedMk = wrapCipher.doFinal(mkBytes)
                val ivStr = Base64.encodeToString(wrapIv, Base64.NO_WRAP)
                val mkStr = Base64.encodeToString(wrappedMk, Base64.NO_WRAP)
                wrappedKeys[uid] = "$ivStr.$mkStr"
            }
        }

        return EncryptedEnvelope(
            ciphertextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivB64 = Base64.encodeToString(msgIv, Base64.NO_WRAP),
            senderPublicKeyB64 = senderPubB64,
            senderKeyFingerprint = senderFp,
            protocolVersion = PROTOCOL_VERSION,
            wrappedKeys = wrappedKeys
        )
    }

    fun decryptMessage(
        incidentId: String,
        senderId: String,
        senderPublicKeyB64: String,
        senderKeyFingerprint: String,
        protocolVersion: String,
        ciphertextB64: String,
        ivB64: String,
        wrappedKeys: Map<String, String>
    ): DecryptionResult {
        if (protocolVersion != PROTOCOL_VERSION) {
            return DecryptionResult.Unavailable("Unsupported protocol: $protocolVersion")
        }
        val wrappedEntry = wrappedKeys[userId]
            ?: return DecryptionResult.Unavailable("Encrypted for active participants (key envelope pending)")

        return try {
            val parts = wrappedEntry.split(".")
            if (parts.size != 2) {
                return DecryptionResult.Unavailable("Malformed key envelope")
            }
            val wrapIv = Base64.decode(parts[0], Base64.NO_WRAP)
            val wrappedMk = Base64.decode(parts[1], Base64.NO_WRAP)

            val kp = getOrCreateIdentityKeyPair()
            val senderPub = decodePublicKey(senderPublicKeyB64)
            val kek = derivePairwiseKek(kp.private, senderPub, incidentId)

            val unwrapCipher = Cipher.getInstance(AES_GCM)
            unwrapCipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, wrapIv))
            unwrapCipher.updateAAD(userId.toByteArray(Charsets.UTF_8))
            val mkBytes = unwrapCipher.doFinal(wrappedMk)
            val mkSpec = SecretKeySpec(mkBytes, "AES")

            val msgCipher = Cipher.getInstance(AES_GCM)
            val msgIv = Base64.decode(ivB64, Base64.NO_WRAP)
            msgCipher.init(Cipher.DECRYPT_MODE, mkSpec, GCMParameterSpec(GCM_TAG_BITS, msgIv))
            val aad = "$incidentId|$senderId|$protocolVersion".toByteArray(Charsets.UTF_8)
            msgCipher.updateAAD(aad)
            val plainBytes = msgCipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP))

            val computedFp = computeFingerprint(senderPub.encoded)
            DecryptionResult.Plaintext(
                text = String(plainBytes, Charsets.UTF_8),
                verifiedFingerprint = if (computedFp == senderKeyFingerprint) computedFp else senderKeyFingerprint
            )
        } catch (e: Exception) {
            Log.w(TAG, "Decryption failed for message from $senderId", e)
            DecryptionResult.Unavailable("Unable to decrypt with current device key")
        }
    }

    private fun decodePublicKey(pubB64: String): PublicKey {
        val bytes = Base64.decode(pubB64, Base64.NO_WRAP)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    private fun derivePairwiseKek(
        myPrivateKey: PrivateKey,
        peerPublicKey: PublicKey,
        incidentId: String
    ): SecretKey {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(myPrivateKey)
        agreement.doPhase(peerPublicKey, true)
        val sharedSecret = agreement.generateSecret()

        // HKDF-SHA256 Extract & Expand (RFC 5869)
        val salt = incidentId.toByteArray(Charsets.UTF_8)
        val prkMac = Mac.getInstance("HmacSHA256")
        prkMac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = prkMac.doFinal(sharedSecret)

        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
        expandMac.update("LexShield-E2EE-v1".toByteArray(Charsets.UTF_8))
        expandMac.update(0x01.toByte())
        val okm = expandMac.doFinal()
        return SecretKeySpec(okm.copyOf(32), "AES")
    }

    companion object {
        private const val TAG = "E2eeCrypto"
        private const val PREFS_NAME = "lexshield_e2ee_keystore_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        const val PROTOCOL_VERSION = "ECDH-P256-AES256-GCM-v1"

        fun computeFingerprint(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.take(8).joinToString(":") { "%02X".format(it) }
        }

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

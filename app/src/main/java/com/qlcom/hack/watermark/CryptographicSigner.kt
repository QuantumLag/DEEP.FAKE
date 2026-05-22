package com.qlcom.hack.watermark

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

object CryptographicSigner {
    private const val KEY_ALIAS = "DeepfakeDetectorWatermarkKey"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val SIGNING_ALGORITHM = "SHA256withRSA"

    init {
        ensureKeysExist()
    }

    private fun ensureKeysExist() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA,
                    KEYSTORE_PROVIDER
                )
                val parameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .build()
                keyPairGenerator.initialize(parameterSpec)
                keyPairGenerator.generateKeyPair()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPublicKey(): PublicKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.certificate.publicKey
    }

    fun getPublicKeyString(): String {
        return Base64.encodeToString(getPublicKey().encoded, Base64.NO_WRAP)
    }

    private fun getPrivateKey(): PrivateKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey
    }

    fun signData(data: ByteArray): String {
        val privateKey = getPrivateKey()
        val signature = Signature.getInstance(SIGNING_ALGORITHM)
        signature.initSign(privateKey)
        signature.update(data)
        val signatureBytes = signature.sign()
        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }

    fun verifySignature(data: ByteArray, signatureBase64: String): Boolean {
        return try {
            val publicKey = getPublicKey()
            val signature = Signature.getInstance(SIGNING_ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(data)
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
}

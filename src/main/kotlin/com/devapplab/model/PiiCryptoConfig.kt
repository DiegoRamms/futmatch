package com.devapplab.model

data class PiiCryptoConfig(
    val encryptionKeyBase64: String,
    val lookupPepperBase64: String,
    val keyVersion: String,
    val previousEncryptionKeys: Map<String, String> = emptyMap()
)

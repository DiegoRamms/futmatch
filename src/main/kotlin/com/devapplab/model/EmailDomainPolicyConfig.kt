package com.devapplab.model

data class EmailDomainPolicyConfig(
    val enforceAllowlist: Boolean,
    val allowedDomains: Set<String>,
    val blockedDomains: Set<String>,
)

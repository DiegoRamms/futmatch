package com.devapplab

import com.devapplab.model.EmailDomainPolicyConfig
import com.devapplab.service.email.EmailDomainPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailDomainPolicyTest {

    private val strictPolicy = EmailDomainPolicy(
        EmailDomainPolicyConfig(
            enforceAllowlist = true,
            allowedDomains = setOf("gmail.com", "outlook.com"),
            blockedDomains = setOf("mailinator.com", "temp-mail.org")
        )
    )

    private val blockedOnlyPolicy = EmailDomainPolicy(
        EmailDomainPolicyConfig(
            enforceAllowlist = false,
            allowedDomains = setOf("gmail.com", "outlook.com"),
            blockedDomains = setOf("mailinator.com", "temp-mail.org")
        )
    )

    @Test
    fun `allows known providers`() {
        assertTrue(strictPolicy.isAllowed("user@gmail.com"))
        assertTrue(strictPolicy.isAllowed("user@outlook.com"))
    }

    @Test
    fun `rejects unknown domains when allowlist is enforced`() {
        assertFalse(strictPolicy.isAllowed("user@empresa-propia.com"))
    }

    @Test
    fun `allows unknown domains when allowlist is disabled`() {
        assertTrue(blockedOnlyPolicy.isAllowed("user@empresa-propia.com"))
    }

    @Test
    fun `rejects blocked disposable domains`() {
        assertFalse(strictPolicy.isAllowed("user@mailinator.com"))
        assertFalse(strictPolicy.isAllowed("user@sub.temp-mail.org"))
        assertFalse(blockedOnlyPolicy.isAllowed("user@mailinator.com"))
        assertFalse(blockedOnlyPolicy.isAllowed("user@sub.temp-mail.org"))
    }
}

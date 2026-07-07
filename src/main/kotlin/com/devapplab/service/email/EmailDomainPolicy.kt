package com.devapplab.service.email

import com.devapplab.model.EmailDomainPolicyConfig
import org.slf4j.LoggerFactory
import java.util.Locale

class EmailDomainPolicy(
    config: EmailDomainPolicyConfig
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val allowedDomains = config.allowedDomains.normalizeDomains()
    private val blockedDomains = config.blockedDomains.normalizeDomains()
    private val enforceAllowlist = config.enforceAllowlist

    fun isAllowed(email: String): Boolean {
        val domain = extractDomain(email) ?: return false

        if (domain.matchesAny(blockedDomains)) {
            return false
        }

        if (!enforceAllowlist) {
            return true
        }

        return domain.matchesAny(allowedDomains)
    }

    fun getRejectionReason(email: String): String {
        val domain = extractDomain(email)
        return when {
            domain == null -> "invalid_domain"
            domain.matchesAny(blockedDomains) -> "blocked_domain"
            enforceAllowlist && !domain.matchesAny(allowedDomains) -> "unknown_domain"
            else -> "allowed"
        }
    }

    fun logRejectedEmail(email: String) {
        val domain = extractDomain(email).orEmpty()
        logger.warn("Rejected email delivery for domain='{}' reason='{}'", domain, getRejectionReason(email))
    }

    private fun extractDomain(email: String): String? {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        val atIndex = normalizedEmail.lastIndexOf('@')
        if (atIndex <= 0 || atIndex == normalizedEmail.lastIndex) {
            return null
        }
        return normalizedEmail.substring(atIndex + 1)
    }

    private fun Set<String>.normalizeDomains(): Set<String> =
        map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun String.matchesAny(domains: Set<String>): Boolean =
        domains.any { candidate -> this == candidate || this.endsWith(".$candidate") }
}

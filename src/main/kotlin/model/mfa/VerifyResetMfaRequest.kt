package model.mfa

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class VerifyResetMfaRequest(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val code: String
)

package com.devapplab.data.database.user

import com.devapplab.model.payment.PaymentProvider
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object UserPaymentProfileTable : Table("user_payment_profiles") {
    val userId = javaUUID("user_id").references(UserTable.id)
    val provider = enumerationByName("provider", 50, PaymentProvider::class)
    val providerCustomerId = varchar("provider_customer_id", 255)
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(userId, provider)
}

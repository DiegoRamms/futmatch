package com.devapplab.data.repository.discount

import com.devapplab.model.discount.Discount
import java.util.UUID

interface DiscountRepository {
    suspend fun getDiscountsByIds(discountIds: List<UUID>): List<Discount>
}

package com.devapplab.data.repository.admin

import com.devapplab.model.admin.DashboardSummary

interface DashboardRepository {
    /**
     * Returns aggregate counts only. It must not load dashboard entities or their related data.
     */
    suspend fun getSummary(): DashboardSummary
}

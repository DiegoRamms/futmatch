package com.devapplab.service.admin

import com.devapplab.data.repository.admin.DashboardRepository
import com.devapplab.model.AppResult
import com.devapplab.model.admin.DashboardSummary

class DashboardService(private val dashboardRepository: DashboardRepository) {
    suspend fun getSummary(): AppResult<DashboardSummary> =
        AppResult.Success(dashboardRepository.getSummary())
}

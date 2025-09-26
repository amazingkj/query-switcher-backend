package com.sqlswitcher.api

import com.sqlswitcher.service.SqlMetricsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/metrics")
class MetricsController(
    private val sqlMetricsService: SqlMetricsService
) {

    @GetMapping("/summary")
    fun getMetricsSummary() = sqlMetricsService.getMetricsSummary()
}

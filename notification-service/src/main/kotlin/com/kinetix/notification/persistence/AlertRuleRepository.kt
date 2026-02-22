package com.kinetix.notification.persistence

import com.kinetix.notification.model.AlertRule

interface AlertRuleRepository {
    suspend fun save(rule: AlertRule)
    suspend fun findAll(): List<AlertRule>
    suspend fun deleteById(id: String): Boolean
}

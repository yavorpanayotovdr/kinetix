package com.kinetix.notification.persistence

import com.kinetix.notification.model.AlertRule
import java.util.concurrent.ConcurrentHashMap

class InMemoryAlertRuleRepository : AlertRuleRepository {
    private val rules = ConcurrentHashMap<String, AlertRule>()

    override suspend fun save(rule: AlertRule) {
        rules[rule.id] = rule
    }

    override suspend fun findAll(): List<AlertRule> = rules.values.toList()

    override suspend fun deleteById(id: String): Boolean = rules.remove(id) != null
}

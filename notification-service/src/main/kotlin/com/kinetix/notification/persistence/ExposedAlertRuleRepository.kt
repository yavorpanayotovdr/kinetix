package com.kinetix.notification.persistence

import com.kinetix.notification.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

class ExposedAlertRuleRepository(private val db: Database? = null) : AlertRuleRepository {

    private val logger = LoggerFactory.getLogger(ExposedAlertRuleRepository::class.java)

    override suspend fun save(rule: AlertRule): Unit = newSuspendedTransaction(db = db) {
        AlertRulesTable.upsert(AlertRulesTable.id) {
            it[id] = rule.id
            it[name] = rule.name
            it[type] = rule.type.name
            it[threshold] = rule.threshold
            it[operator] = rule.operator.name
            it[severity] = rule.severity.name
            it[channels] = rule.channels.joinToString(",") { ch -> ch.name }
            it[enabled] = rule.enabled
        }
    }

    override suspend fun findAll(): List<AlertRule> = newSuspendedTransaction(db = db) {
        AlertRulesTable
            .selectAll()
            .mapNotNull { it.toAlertRuleOrNull() }
    }

    override suspend fun deleteById(id: String): Boolean = newSuspendedTransaction(db = db) {
        AlertRulesTable.deleteWhere { AlertRulesTable.id eq id } > 0
    }

    private fun ResultRow.toAlertRuleOrNull(): AlertRule? {
        val typeName = this[AlertRulesTable.type]
        val alertType = try {
            AlertType.valueOf(typeName)
        } catch (e: IllegalArgumentException) {
            logger.warn("Skipping alert rule with unknown type '{}', id={}", typeName, this[AlertRulesTable.id])
            return null
        }
        return AlertRule(
            id = this[AlertRulesTable.id],
            name = this[AlertRulesTable.name],
            type = alertType,
            threshold = this[AlertRulesTable.threshold],
            operator = ComparisonOperator.valueOf(this[AlertRulesTable.operator]),
            severity = Severity.valueOf(this[AlertRulesTable.severity]),
            channels = this[AlertRulesTable.channels].split(",").map { DeliveryChannel.valueOf(it) },
            enabled = this[AlertRulesTable.enabled],
        )
    }
}

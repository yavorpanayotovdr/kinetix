package com.kinetix.notification.persistence

import com.kinetix.notification.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedAlertRuleRepository(private val db: Database? = null) : AlertRuleRepository {

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
            .map { it.toAlertRule() }
    }

    override suspend fun deleteById(id: String): Boolean = newSuspendedTransaction(db = db) {
        AlertRulesTable.deleteWhere { AlertRulesTable.id eq id } > 0
    }

    private fun ResultRow.toAlertRule(): AlertRule = AlertRule(
        id = this[AlertRulesTable.id],
        name = this[AlertRulesTable.name],
        type = AlertType.valueOf(this[AlertRulesTable.type]),
        threshold = this[AlertRulesTable.threshold],
        operator = ComparisonOperator.valueOf(this[AlertRulesTable.operator]),
        severity = Severity.valueOf(this[AlertRulesTable.severity]),
        channels = this[AlertRulesTable.channels].split(",").map { DeliveryChannel.valueOf(it) },
        enabled = this[AlertRulesTable.enabled],
    )
}

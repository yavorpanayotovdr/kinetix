package com.kinetix.regulatory.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object SubmissionsTable : Table("regulatory_submissions") {
    val id = varchar("id", 255)
    val reportType = varchar("report_type", 100)
    val status = varchar("status", 30)
    val preparerId = varchar("preparer_id", 255)
    val approverId = varchar("approver_id", 255).nullable()
    val deadline = timestampWithTimeZone("deadline")
    val submittedAt = timestampWithTimeZone("submitted_at").nullable()
    val acknowledgedAt = timestampWithTimeZone("acknowledged_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

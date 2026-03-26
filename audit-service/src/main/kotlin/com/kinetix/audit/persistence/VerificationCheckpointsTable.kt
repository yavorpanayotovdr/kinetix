package com.kinetix.audit.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object VerificationCheckpointsTable : Table("audit_verification_checkpoints") {
    val id = long("id").autoIncrement()
    val lastEventId = long("last_event_id")
    val lastHash = varchar("last_hash", 64)
    val verifiedAt = timestampWithTimeZone("verified_at")
    val eventCount = long("event_count")

    override val primaryKey = PrimaryKey(id)
}

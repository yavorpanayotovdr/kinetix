package com.kinetix.position.persistence

import com.kinetix.position.model.BookHierarchyMapping
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedBookHierarchyRepository(private val db: Database? = null) : BookHierarchyRepository {

    override suspend fun findByBookId(bookId: String): BookHierarchyMapping? =
        newSuspendedTransaction(db = db) {
            BookHierarchyTable
                .selectAll()
                .where { BookHierarchyTable.bookId eq bookId }
                .singleOrNull()
                ?.let { row ->
                    BookHierarchyMapping(
                        bookId = row[BookHierarchyTable.bookId],
                        deskId = row[BookHierarchyTable.deskId],
                        bookName = row[BookHierarchyTable.bookName],
                        bookType = row[BookHierarchyTable.bookType],
                    )
                }
        }

    override suspend fun findByDeskId(deskId: String): List<BookHierarchyMapping> =
        newSuspendedTransaction(db = db) {
            BookHierarchyTable
                .selectAll()
                .where { BookHierarchyTable.deskId eq deskId }
                .map { row ->
                    BookHierarchyMapping(
                        bookId = row[BookHierarchyTable.bookId],
                        deskId = row[BookHierarchyTable.deskId],
                        bookName = row[BookHierarchyTable.bookName],
                        bookType = row[BookHierarchyTable.bookType],
                    )
                }
        }

    override suspend fun findAll(): List<BookHierarchyMapping> =
        newSuspendedTransaction(db = db) {
            BookHierarchyTable
                .selectAll()
                .map { row ->
                    BookHierarchyMapping(
                        bookId = row[BookHierarchyTable.bookId],
                        deskId = row[BookHierarchyTable.deskId],
                        bookName = row[BookHierarchyTable.bookName],
                        bookType = row[BookHierarchyTable.bookType],
                    )
                }
        }

    override suspend fun save(mapping: BookHierarchyMapping): Unit =
        newSuspendedTransaction(db = db) {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            BookHierarchyTable.upsert(BookHierarchyTable.bookId) {
                it[bookId] = mapping.bookId
                it[deskId] = mapping.deskId
                it[bookName] = mapping.bookName
                it[bookType] = mapping.bookType
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

    override suspend fun delete(bookId: String): Unit =
        newSuspendedTransaction(db = db) {
            BookHierarchyTable.deleteWhere { BookHierarchyTable.bookId eq bookId }
        }
}

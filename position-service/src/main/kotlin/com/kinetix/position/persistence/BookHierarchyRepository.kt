package com.kinetix.position.persistence

import com.kinetix.position.model.BookHierarchyMapping

interface BookHierarchyRepository {
    suspend fun findByBookId(bookId: String): BookHierarchyMapping?
    suspend fun findByDeskId(deskId: String): List<BookHierarchyMapping>
    suspend fun findAll(): List<BookHierarchyMapping>
    suspend fun save(mapping: BookHierarchyMapping)
    suspend fun delete(bookId: String)
}

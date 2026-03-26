package com.kinetix.risk.persistence

import com.kinetix.risk.model.FactorDefinition
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedFactorDefinitionRepository(
    private val db: Database? = null,
) : FactorDefinitionRepository {

    override suspend fun findAll(): List<FactorDefinition> =
        newSuspendedTransaction(db = db) {
            FactorDefinitionsTable
                .selectAll()
                .map { row ->
                    FactorDefinition(
                        factorName = row[FactorDefinitionsTable.factorName],
                        proxyInstrumentId = row[FactorDefinitionsTable.proxyInstrumentId],
                        description = row[FactorDefinitionsTable.description],
                    )
                }
        }

    override suspend fun findByName(factorName: String): FactorDefinition? =
        newSuspendedTransaction(db = db) {
            FactorDefinitionsTable
                .selectAll()
                .where { FactorDefinitionsTable.factorName eq factorName }
                .singleOrNull()
                ?.let { row ->
                    FactorDefinition(
                        factorName = row[FactorDefinitionsTable.factorName],
                        proxyInstrumentId = row[FactorDefinitionsTable.proxyInstrumentId],
                        description = row[FactorDefinitionsTable.description],
                    )
                }
        }
}

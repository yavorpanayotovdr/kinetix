package com.kinetix.position.client

import com.kinetix.common.model.Desk
import com.kinetix.common.model.DeskId
import com.kinetix.common.model.Division
import com.kinetix.common.model.DivisionId

interface ReferenceDataServiceClient {
    suspend fun getDeskById(deskId: DeskId): Desk?
    suspend fun getDivisionById(divisionId: DivisionId): Division?
}

package org.dhis2.android.rtsm.services

import io.reactivex.Single
import org.dhis2.android.rtsm.data.models.SearchParametersModel
import org.dhis2.android.rtsm.data.models.SearchResult
import org.dhis2.android.rtsm.data.models.StockEntry
import org.dhis2.android.rtsm.data.models.Transaction
import org.hisp.dhis.android.core.usecase.stock.StockUseCase

interface StockManager {
    /**
     * Get the list of stock items
     *
     * @param query The query object which comprises the search query, OU and other parameters
     * @param ou The organisation unit under consideration (optional)
     *
     * @return The search result containing the livedata of paged list of the matching stock items
     * and total count of matched items
     */

    suspend fun search(query: SearchParametersModel, ou: String?, config: StockUseCase): SearchResult

    fun saveTransaction(
        items: List<StockEntry>,
        transaction: Transaction,
        stockUseCase: StockUseCase,
    ): Single<Unit>

    suspend fun stockUseCase(program: String): StockUseCase?
}

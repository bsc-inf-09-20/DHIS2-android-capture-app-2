package org.dhis2.usescases.datasets.dataSetInitial

import io.reactivex.Single
import org.dhis2.usescases.datasets.datasetInitial.DataSetInitialModel
import org.dhis2.usescases.datasets.datasetInitial.DataSetInitialRepositoryImpl
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.category.Category
import org.hisp.dhis.android.core.category.CategoryCombo
import org.hisp.dhis.android.core.category.CategoryOption
import org.hisp.dhis.android.core.common.Access
import org.hisp.dhis.android.core.common.DataAccess
import org.hisp.dhis.android.core.common.ObjectWithUid
import org.hisp.dhis.android.core.dataset.DataSet
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import org.hisp.dhis.android.core.period.Period
import org.hisp.dhis.android.core.period.PeriodType
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.UUID

class DataSetInitialRepositoryImplTest {

    private lateinit var repository: DataSetInitialRepositoryImpl
    private val d2: D2 = Mockito.mock(D2::class.java, Mockito.RETURNS_DEEP_STUBS)
    private val dataSetUid = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        repository = DataSetInitialRepositoryImpl(d2, dataSetUid)
    }

    @Test
    fun `Should return dataSetInitialModel for dataSet`() {
        val dataSet = dummyDataSet()
        val categoryCombo = dummyCategoryCombo()
        whenever(
            d2.dataSetModule().dataSets().uid(dataSetUid).get(),
        ) doReturn Single.just(dataSet)

        whenever(
            d2.categoryModule()
                .categoryCombos().withCategories()
                .uid(dataSet.categoryCombo()!!.uid())
                .blockingGet(),
        ) doReturn categoryCombo

        val dataSetInitialModel = DataSetInitialModel.create(
            dataSet.displayName()!!,
            null,
            dataSet.categoryCombo()!!.uid(),
            categoryCombo.displayName()!!,
            dataSet.periodType()!!,
            categoryCombo.categories()!!,
            dataSet.openFuturePeriods(),
        )
        val testObserver = repository.dataSet().test()

        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        testObserver.assertValue(dataSetInitialModel)

        testObserver.dispose()
    }

    @Test
    fun `Should return organization units for dataSet`() {
        val orgUnits = listOf(dummyOrganisationUnit(), dummyOrganisationUnit())
        whenever(
            d2.organisationUnitModule().organisationUnits()
                .byDataSetUids(listOf(dataSetUid))
                .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                .get(),
        ) doReturn Single.just(orgUnits)

        val testObserver = repository.orgUnits().test()

        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        testObserver.assertValue(orgUnits)

        testObserver.dispose()
    }

    @Test
    fun `Should return category combos for category`() {
        val category = dummyCategory()
        whenever(
            d2.categoryModule().categories()
                .withCategoryOptions().uid(category.uid())
                .get(),
        ) doReturn Single.just(category)

        val testObserver = repository.catCombo(category.uid()).test()

        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        testObserver.assertValue(category.categoryOptions())

        testObserver.dispose()
    }

    @Test
    fun `Should not return category combos for category with no access data write`() {
        val category = dummyCategory().toBuilder()
            .categoryOptions(mutableListOf(dummyCategoryOptionNoAccess()))
            .build()
        whenever(
            d2.categoryModule().categories()
                .withCategoryOptions().uid(category.uid())
                .get(),
        ) doReturn Single.just(category)

        val testObserver = repository.catCombo(category.uid()).test()

        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        testObserver.assertValue(listOf())

        testObserver.dispose()
    }

    @Test
    fun `Should return periodId for a date with periodType`() {
        val date = Date()
        val periodType = PeriodType.Monthly
        val period = dummyPeriod()
        whenever(
            d2.periodModule().periodHelper().getPeriod(periodType, date),
        ) doReturn period

        val testObserver = repository.getPeriodId(periodType, date).test()

        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        testObserver.assertValue(period.periodId())

        testObserver.dispose()
    }
    private fun dummyDataSet(): DataSet = DataSet.builder()
        .uid(UUID.randomUUID().toString())
        .displayName("dataSet")
        .periodType(PeriodType.Monthly)
        .categoryCombo(ObjectWithUid.create(UUID.randomUUID().toString()))
        .build()

    private fun dummyPeriod(): Period = Period.builder()
        .periodId(UUID.randomUUID().toString())
        .build()

    private fun dummyCategoryCombo(): CategoryCombo = CategoryCombo.builder()
        .displayName("categoryCombo")
        .uid(UUID.randomUUID().toString())
        .categories(listOf(dummyCategory(), dummyCategory()))
        .build()

    private fun dummyOrganisationUnit(): OrganisationUnit = OrganisationUnit.builder()
        .uid(UUID.randomUUID().toString())
        .build()

    private fun dummyCategory(): Category = Category.builder()
        .uid(UUID.randomUUID().toString())
        .categoryOptions(listOf(dummyCategoryOption(), dummyCategoryOption()))
        .build()

    private fun dummyCategoryOption(): CategoryOption = CategoryOption.builder()
        .uid(UUID.randomUUID().toString())
        .access(Access.create(true, true, DataAccess.create(true, true)))
        .build()

    private fun dummyCategoryOptionNoAccess(): CategoryOption = CategoryOption.builder()
        .uid(UUID.randomUUID().toString())
        .access(Access.create(true, true, DataAccess.create(false, false)))
        .build()
}

package org.dhis2.usescases.datasets.dataSetInitial

import io.reactivex.Flowable
import io.reactivex.Observable
import org.dhis2.data.schedulers.TrampolineSchedulerProvider
import org.dhis2.usescases.datasets.datasetInitial.DataSetInitialContract
import org.dhis2.usescases.datasets.datasetInitial.DataSetInitialModel
import org.dhis2.usescases.datasets.datasetInitial.DataSetInitialPresenter
import org.dhis2.usescases.datasets.datasetInitial.DataSetInitialRepository
import org.hisp.dhis.android.core.category.CategoryOption
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import org.hisp.dhis.android.core.period.PeriodType
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.Date

class DataSetInitialPresenterTest {

    private lateinit var presenter: DataSetInitialPresenter

    private val view: DataSetInitialContract.View = mock()
    private val repository: DataSetInitialRepository = mock()
    private val scheduler = TrampolineSchedulerProvider()

    @Before
    fun setUp() {
        presenter = DataSetInitialPresenter(view, repository, scheduler)
    }

    private fun dummyDataSetInitial() = DataSetInitialModel.create(
        "name",
        "desc",
        "catComboUid",
        "catCombo",
        PeriodType.Daily,
        mutableListOf(),
        0,
    )

    @Test
    fun `Should set OrgUnit and data`() {
        val orgUnits = listOf(OrganisationUnit.builder().uid("orgUnitUid").build())
        val dataSet = dummyDataSetInitial()

        whenever(repository.orgUnits()) doReturn Observable.just(orgUnits)
        whenever(repository.dataSet()) doReturn Observable.just(dataSet)

        presenter.init()

        verify(view).setOrgUnit(orgUnits[0])
        verify(view).setData(dataSet)
    }

    @Test
    fun `Should not set OrgUnits when size is bigger than 1`() {
        val orgUnits = listOf(
            OrganisationUnit.builder().uid("orgUnitUid").build(),
            OrganisationUnit.builder().uid("orgUnitUid2").build(),
        )
        val dataSet = dummyDataSetInitial()

        whenever(repository.orgUnits()) doReturn Observable.just(orgUnits)
        whenever(repository.dataSet()) doReturn Observable.just(dataSet)

        presenter.init()

        verify(view, times(0)).setOrgUnit(orgUnits[0])
        verify(view).setData(dataSet)
    }

    @Test
    fun `Should not set OrgUnit when size is 0`() {
        val orgUnits = listOf<OrganisationUnit>()
        val dataSet = dummyDataSetInitial()

        whenever(repository.orgUnits()) doReturn Observable.just(orgUnits)
        whenever(repository.dataSet()) doReturn Observable.just(dataSet)

        presenter.init()

        verify(view).setData(dataSet)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `Should go back when back button is clicked`() {
        presenter.onBackClick()

        verify(view).back()
    }

    @Test
    fun `Should show orgUnitDialog when field is clicked`() {
        val orgUnits = listOf<OrganisationUnit>()

        presenter.onOrgUnitSelectorClick()

        verify(view).showOrgUnitDialog(orgUnits)
    }

    @Test
    fun `Should show periodSelector when field is clicked`() {
        val periodType = PeriodType.Monthly

        presenter.onReportPeriodClick(periodType)

        verify(view).showPeriodSelector(periodType, 0)
    }

    @Test
    fun `Should show catOptionSelector when field is clicked`() {
        val catOptionUid = "catOptionUid"
        val catOptions = listOf(CategoryOption.builder().uid(catOptionUid).build())

        whenever(repository.catCombo(catOptionUid)) doReturn Observable.just(catOptions)

        presenter.onCatOptionClick(catOptionUid)

        verify(view).showCatComboSelector(catOptionUid, catOptions)
    }

    @Test
    fun `Should navigate to dataSetTableActivity when actionbutton is clicked`() {
        val catCombo = "catComboUid"
        val catOptionCombo = "catOptionCombo"
        val periodId = "periodId"
        val periodType = PeriodType.Monthly

        whenever(repository.orgUnits()) doReturn Observable.just(listOf())
        whenever(repository.dataSet()) doReturn Observable.just(dummyDataSetInitial())

        whenever(view.selectedCatOptions) doReturn listOf("catOption")
        whenever(view.selectedPeriod) doReturn Date()

        whenever(
            repository.getCategoryOptionCombo(
                view.selectedCatOptions,
                catCombo,
            ),
        ) doReturn Flowable.just(catOptionCombo)
        whenever(
            repository.getPeriodId(
                periodType,
                view.selectedPeriod,
            ),
        ) doReturn Flowable.just(periodId)

        presenter.init()
        presenter.onActionButtonClick(periodType)

        verify(view).navigateToDataSetTable(catOptionCombo, periodId)
    }

    @Test
    fun `Should dispose of all disposables`() {
        presenter.onDettach()

        val disposableSize = presenter.compositeDisposable.size()

        Assert.assertTrue(disposableSize == 0)
    }

    @Test
    fun `Should display message`() {
        val message = "message"

        presenter.displayMessage(message)

        verify(view).displayMessage(message)
    }
}

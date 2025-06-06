package org.dhis2.usescases.datasets

import android.app.Instrumentation
import android.os.Bundle
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.dhis2.lazyActivityScenarioRule
import org.dhis2.usescases.BaseTest
import org.dhis2.usescases.datasets.dataSetTable.period.reportPeriodSelectorRobot
import org.dhis2.usescases.datasets.dataSetTable.pivotTestingData
import org.dhis2.usescases.datasets.datasetDetail.DataSetDetailActivity
import org.dhis2.usescases.flow.syncFlow.robot.dataSetRobot
import org.dhis2.usescases.orgunitselector.orgUnitSelectorRobot
import org.dhis2.usescases.searchte.robot.filterRobot
import org.hisp.dhis.android.core.D2Manager
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class DataSetTest : BaseTest() {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val ruleDataSetDetail = lazyActivityScenarioRule<DataSetDetailActivity>(launchActivity = false)

    @get:Rule
    val composeTestRule = createComposeRule()

    override fun teardown() {
        super.teardown()
        cleanLocalDatabase()
    }

    @Test
    fun datasetAutomate() = runTest {
        val period = "July 2025"
        val orgUnit = "Ngelehun CHC"

        enterDataSetStep(
            uid = "BfMAe6Itzgt",
            name = "Child Health",
        )
        dataSetInstanceInChronologicalOrderStep()
        createDataSetInstanceStep(
            period = period,
            orgUnit = orgUnit,
        )

        tableIsVisible()

        syncButtonIsAvailableStep()
        checkIndicatorsStep()
        checkTotals()
        enterDataStep(
            tableId = "dzjKKQq0cSO",
            cellId = "PGRlPnM0Nm01TVMwaHh1Ojxjb2M+UHJsdDBDMVJGMHM=",
            dataElementDescription = "BCG doses administered.",
            value = "12",
            inputTestTag = "INPUT_INTEGER_FIELD"
        )
        checkTotalsUpdated(
            tableId = "dzjKKQq0cSO",
            rowIndex = 0,
            value = "12.0",
        )
        reenterDataSetToCheckValueSavedStep()


        // Step - Test combination of filters - TODO Move the step after creating dataset instance
        // ORG unit add some dataset instance out of Ngelahun CHC to filter by Ngelahun CHC
        // Period filter from - to specific period where instances exist
        // Sync move after create dataset instance and check the filter afterwards
        // checkFilterCombination(orgUnit)
    }

    @Test
    fun formConfigurationTestAutomate() = runTest {
        // Start Activity
        enterDataSetStep("DMicXfEri6s", "Form configuration options")
        waitForTableToBeVisible()

        // Step - ANDROAPP-6858 Custom title is displayed
        checkCustomTitleIsDisplayed()
        // Step - ANDROAPP-6795 Check content boxes above and below the table
        checkContentBoxesAreDisplayed()
        // Step - ANDROAPP-6810 Move a category to rows (click on sections 8, 16, 24)
        checkCategoryIsMovedToRow()
        // Step - ANDROAPP-6828 Automatic grouping (click on sections 19, 20, 22)
        checkAutomaticGroupingDisabled()
        // Step - ANDROAPP-6811 Pivot options (click on sections 5, 13, 23)
        checkPivotOptions()
    }

    @Test
    fun checkAllSections() = runTest {
        enterDataSetStep("DMicXfEri6s", "Form configuration options")
        waitForTableToBeVisible()
        D2Manager.getD2().dataSetModule().sections().byDataSetUid().eq("DMicXfEri6s").blockingGet()
            .forEachIndexed { sectionIndex, section ->
                dataSetTableRobot(composeTestRule) {
                    clickOnSection(sectionIndex, section.displayName()!!)
                    assertTableIsDisplayed()
                }
            }
    }

    private fun checkCustomTitleIsDisplayed() {
        logStep("Starting check custom title is displayed")
        dataSetDetailRobot(composeTestRule) {
            assertItemWithTextIsDisplayed("Line end: Custom Title", true)
            assertItemWithTextIsDisplayed("Line end: Custom Subtitle test a very long subtitle", true)
        }
        logStep("Finish check custom title is displayed")
    }

    private suspend fun waitForTableToBeVisible() {
        composeTestRule.awaitIdle()
        dataSetRobot {
            clickOnDataSetAtPosition(0)
        }
        tableIsVisible()
    }

    private suspend fun checkContentBoxesAreDisplayed() {
        tableIsVisible()
        logStep("Starting Check content boxes above and below the table")
        // Check top and bottom content is displayed in initial section
        dataSetDetailRobot(composeTestRule) {
            assertItemWithTextIsDisplayed("CONTENT BEFORE 1:", true)
        }
        dataSetTableRobot(composeTestRule) {
            scrollToItemWithText("CONTENT AFTER 1:")
            assertItemWithTextIsDisplayed("CONTENT AFTER 1:", true)
        }
        // Check top and bottom content is displayed when changing sections
        dataSetDetailRobot(composeTestRule) {
            clickOnSection("SCROLLABLE_TAB_1")
        }
        tableIsVisible()
        // Check top and bottom content is displayed when changing sections
        dataSetDetailRobot(composeTestRule) {
            assertItemWithTextIsDisplayed("CONTENT BEFORE 2:", true)
        }
        dataSetTableRobot(composeTestRule) {
            scrollToItemWithText("CONTENT AFTER 2:")
            assertItemWithTextIsDisplayed("CONTENT AFTER 2:", true)
        }
        logStep("Finish Check content boxes above and below the table")

    }

    @Test
    fun saveAndCompleteMandatoryFieldMandatoryValidationRule() = runTest {
        val dataSetUid = "Lpw6GcnTrmS"
        val dataSetName = "Emergency Response"
        val periodListLabel = "Jan 2025"
        val periodSelectorLabel = "January 2025"
        val orgUnit = "Ngelehun CHC"
        val catCombo = "Result"
        val tableId = "bjDvmb4bfuf"
        val cellValidationRuleId = "PGRlPktGbkZwYnFEcWppOjxjb2M+SGxsdlg1MGNYQzA="
        val cellMandatoryId = "PGRlPnpGRmIzYmFyNEN0Ojxjb2M+SGxsdlg1MGNYQzA="

        enterDataSetStep(
            uid = dataSetUid,
            name = dataSetName,
        )

        createDataSetInstanceStep(
            period = periodSelectorLabel,
            orgUnit = orgUnit,
            catCombo = catCombo,
        )

        tapOnSaveButtonStep()

        checkValidationBarIsDisplayedAndReview()

        enterDataStep(
            tableId = tableId,
            cellId = cellValidationRuleId,
            value = "1",
            inputTestTag = "INPUT_NUMBER_FIELD"
        )

        tapOnSaveButtonStep()

        checkCompleteDialogIsDisplayedAndAttemptToCompleteStep()

        checkMandatoryDialogIsDisplayedAndAcceptStep()

        enterDataStep(
            tableId = tableId,
            cellId = cellMandatoryId,
            value = "2",
            inputTestTag = "INPUT_NUMBER_FIELD"
        )

        tapOnSaveButtonStep()

        checkCompleteDialogIsDisplayedAndAttemptToCompleteStep()

        checkDataSetInstanceHasBeenCreated(periodListLabel, orgUnit)
    }

    @Test
    fun saveAndCompleteOptionalValidationRule() = runTest {
        val dataSetUid = "Nyh6laLdBEJ"
        val dataSetName = "IDSR weekly"
        val periodListLabel = "Week 19 2025-05-05 To 2025-05-11"
        val periodSelectorLabel = "Week 19: May 5 - May 11, 2025"
        val orgUnit = "Ngelehun CHC"
        val tableId = "gbvX3pogf7p"
        val cellMandatoryFieldCombination01Id = "PGRlPkJveTNRd3p0Z2VaOjxjb2M+SjJRZjFqdFp1ajg="
        val cellMandatoryFieldCombination02Id = "PGRlPkJveTNRd3p0Z2VaOjxjb2M+clFMRm5OWFhJTDA="
        val cellMandatoryFieldCombination03Id = "PGRlPkJveTNRd3p0Z2VaOjxjb2M+S1BQNjN6SlBrT3U="
        val legendTableId = "bjDvmb4bfuf"
        val cellLegendId = "PGRlPlVzU1VYMGNwS3NIOjxjb2M+SGxsdlg1MGNYQzA="

        enterDataSetStep(
            uid = dataSetUid,
            name = dataSetName,
        )

        createDataSetInstanceStep(
            period = periodSelectorLabel,
            orgUnit = orgUnit,
        )

        checkLegendsStep(
            tableId = legendTableId,
            cellId = cellLegendId,
            legendData = legendTestingData
        )

        enterDataStep(
            tableId = tableId,
            cellId = cellMandatoryFieldCombination01Id,
            value = "1",
            inputTestTag = "INPUT_POSITIVE_INTEGER_OR_ZERO_FIELD"
        )

        tapOnSaveButtonStep()

        runOptionalValidationRules()

        checkValidationBarIsDisplayedAndCompleteAnyway()

        checkMandatoryDialogIsDisplayedAndAcceptStep()

        enterTwoSequentialSteps(
            tableId = tableId,
            firstCellId = cellMandatoryFieldCombination02Id,
            firstValue = "2",
            secondValue = "3",
            inputTestTag = "INPUT_POSITIVE_INTEGER_OR_ZERO_FIELD"
        )

        tapOnSaveButtonStep()

        runOptionalValidationRules()

        checkValidationBarIsDisplayedAndCompleteAnyway()

        checkDataSetInstanceHasBeenCreated(periodListLabel, orgUnit)
    }

    private fun checkLegendsStep(
        tableId: String,
        cellId: String,
        legendData: List<LegendTestingData>
    ) {

        logStep("Starting checking legends")

        dataSetTableRobot(composeTestRule) {
            clickOnCell(tableId, cellId)
            assertInputDialogIsDisplayed()
            legendData.forEach { data ->
                typeOnInputDialog(
                    value = data.valueToType,
                    inputTestTag = "INPUT_POSITIVE_INTEGER_OR_ZERO_FIELD",
                )
                closeKeyboard()
                composeTestRule.waitForIdle()
                assertCellBackgroundColor(
                    tableId,
                    cellId,
                    data.valueToType,
                    data.expectedColor
                )
                assertInputLegendDescription(data.expectedLabel)
            }
            typeOnInputDialog(
                value = "",
                inputTestTag = "INPUT_POSITIVE_INTEGER_OR_ZERO_FIELD",
            )
            pressOnInputDialogDismiss()
            closeKeyboard()
        }

        logStep("Finished checking legends")
    }

    private fun checkCategoryIsMovedToRow() {
        logStep("Starting Check category is moved to row")
        dataSetTableRobot(composeTestRule) {
            categoryToRowList.forEach { data ->
                clickOnSection(data.sectionIndex, data.sectionName)
                assertTableIsDisplayed()
                assertCategoryAsRowsAreDisplayed(data.dataElementsRowTestTags, data.rowTestTags)
                assertCategoryHeaderIsNotDisplayed(data.pivotedHeaderTestTags)
                assertCategoryHeaderIsDisplayed(data.headerTestTags)
            }
        }
        logStep("Finish Check category is moved to row")

    }

    private fun runOptionalValidationRules() {
        logStep("Starting Run Optional Validation Rules")
        dataSetTableRobot(composeTestRule) {
            acceptOptionalValidationRule()
        }
        logStep("Finished Run Optional Validation Rules")
    }

    private fun checkValidationBarIsDisplayedAndReview() {
        logStep("Starting Check Validation Rule errors and review")

        dataSetTableRobot(composeTestRule) {
            assertValidationBarIsDisplayed()
            expandValidationRulesErrorDialog()
            tapOnReview()
        }
        logStep("Finished Check Validation Rule errors and review")
    }

    private fun checkValidationBarIsDisplayedAndCompleteAnyway() {
        logStep("Starting Check Validation Rule errors and complete anyway")

        dataSetTableRobot(composeTestRule) {
            assertValidationBarIsDisplayed()
            expandValidationRulesErrorDialog()
            tapOnCompleteAnyway()
        }
        logStep("Finished Check Validation Rule errors and complete anyway")
    }

    private fun checkDataSetInstanceHasBeenCreated(
        period: String,
        orgUnit: String,
    ) {
        logStep("Starting Check dataset instance has been created")
        dataSetDetailRobot(composeTestRule) {
            checkDataSetInList(period, orgUnit)
        }
        logStep("Finished Check dataset instance has been created")
    }

    private fun checkMandatoryDialogIsDisplayedAndAcceptStep() {
        logStep("Starting Checking Mandatory Dialog")
        dataSetTableRobot(composeTestRule) {
            checkMandatoryDialogIsDisplayed()
            acceptMandatoryDialog()
        }
        logStep("Finished Checking Mandatory Dialog")
    }

    private fun checkAutomaticGroupingDisabled() {
        logStep("Starting Check Automatic Grouping")
        dataSetTableRobot(composeTestRule) {
            disableAutomaticGroupingList.forEach { data ->
                clickOnSection(data.sectionIndex, data.sectionName)
                assertTableIsDisplayed()
                assertTablesAreDisplayedInOrder(data.tableIdTestTags)
            }
        }
        logStep("Finish Check Automatic Grouping")

    }

    private fun checkPivotOptions() {
        logStep("Starting check Pivot Options")
        dataSetTableRobot(composeTestRule) {
            pivotTestingData.forEach { data ->
                clickOnSection(data.sectionIndex, data.sectionName)
                assertTableIsDisplayed()
                assertTableHeaders(data.headerTestTags)
                assertTableRows(data.rowTestTags)
            }
        }
        logStep("Finish check Pivot Options")
    }

    private fun checkCompleteDialogIsDisplayedAndAttemptToCompleteStep() {
        logStep("Starting Trying to complete dataset")

        dataSetTableRobot(composeTestRule) {
            checkCompleteDialogIsDisplayed()
            tapOnCompleteButton()
        }
        logStep("Finished Trying to complete dataset")
    }

    private fun tapOnSaveButtonStep() {
        logStep("Starting Tap on Done button")

        dataSetTableRobot(composeTestRule) {
            tapOnSaveButton()
        }
        logStep("Finished Tap on Done button")
    }

    private fun enterDataSetStep(
        uid: String,
        name: String,
    ) {
        logStep("Starting Entering dataset $name")
        startDataSetDetailActivity(
            dataSetUid = uid,
            dataSetName = name,
            rule = ruleDataSetDetail
        )
        logStep("Finished Entering dataset $name")
    }

    private fun dataSetInstanceInChronologicalOrderStep() {
        dataSetDetailRobot(composeTestRule) {
            checkDatasetListIsSortedChronologically()
        }
    }

    private suspend fun tableIsVisible() {
        composeTestRule.awaitIdle()
        dataSetTableRobot(composeTestRule) {
            assertTableIsDisplayed()
        }
    }

    private suspend fun syncButtonIsAvailableStep() {
        composeTestRule.awaitIdle()
        dataSetTableRobot(composeTestRule) {
            syncIsAvailable()
        }
    }

    private suspend fun checkTotals() {
        composeTestRule.awaitIdle()
        dataSetTableRobot(composeTestRule) {
            totalsAreDisplayed(
                tableId = "dzjKKQq0cSO",
                totalColumnHeaderRowIndex = 1,
                totalColumnHeaderColumnIndex = 4,
            )
        }
    }

    /**
     * Enters data in a cell and checks if the value is saved
     * @param tableId The table id
     * @param cellId The cell id
     * @param dataElementDescription The data element description (optional)
     * @param value The value to enter
     * @param inputTestTag The input test tag to identify the input field base on the ValueType
     */
    private fun enterDataStep(
        tableId: String,
        cellId: String,
        dataElementDescription: String? = null,
        value: String,
        inputTestTag: String,
    ) {
        logStep("Starting Enter value: $value into cell ${dataElementDescription ?: cellId}")

        dataSetTableRobot(composeTestRule) {
            clickOnCell(tableId, cellId)
            assertInputDialogIsDisplayed()
            dataElementDescription?.let {
                assertInputDescriptionIsDisplayed(dataElementDescription)
            }
            typeOnInputDialog(value, inputTestTag)
            pressOnInputDialogDismiss()
            assertCellHasValue(tableId, cellId, value)
        }

        logStep("Finished Enter value: $value into cell ${dataElementDescription ?: cellId}")
    }

    private fun enterTwoSequentialSteps(
        tableId: String,
        firstCellId: String,
        firstValue: String,
        secondValue: String,
        inputTestTag: String,

        ) {
        logStep("Starting Enter value: $firstValue into cell $firstCellId")
        dataSetTableRobot(composeTestRule) {
            clickOnCell(tableId, firstCellId)
            typeOnInputDialog(firstValue, inputTestTag)
            pressOnInputDialogNext()
            typeOnInputDialog(secondValue, inputTestTag)
            pressOnInputDialogDismiss()
        }
        logStep("Finished Enter value")
    }

    private fun checkTotalsUpdated(
        tableId: String,
        rowIndex: Int,
        value: String
    ) {
        dataSetTableRobot(composeTestRule) {
            assertRowTotalValue(tableId, rowIndex, value)
        }
    }

    private suspend fun checkIndicatorsStep() {
        composeTestRule.awaitIdle()
        dataSetTableRobot(composeTestRule) {
            indicatorTableIsDisplayed()
        }
    }

    private suspend fun reenterDataSetToCheckValueSavedStep() {
        val cell00Id = "PGRlPnM0Nm01TVMwaHh1Ojxjb2M+UHJsdDBDMVJGMHM="

        dataSetTableRobot(composeTestRule) {
            returnToDataSetInstanceList()
        }
        dataSetDetailRobot(composeTestRule) {
            clickOnDataSetAtPosition(0)
        }
        tableIsVisible()

        dataSetTableRobot(composeTestRule) {
            assertCellHasValue("dzjKKQq0cSO", cell00Id, "12")
        }
    }

    private fun checkFilterCombination(
        orgUnit: String,
    ) {
        filterRobot(composeTestRule) {
            //Open filter
            openFilters()

            //Filter by org unit Ngelehun CHC
            clickOnFilterBy(filter = "ORG. UNIT")
//            clickOnSortByField(orgUnitFilter) this icons are not visible but can b e pressed do we need them in dataset?
            typeOrgUnitField(orgUnit)
            checkFilterCounter("1")
        }

        dataSetDetailRobot(composeTestRule) {
            assertEquals(11, getListItemCount())
        }

        filterRobot(composeTestRule) {
            //Filter by period Last Month
            clickOnFilterBy(filter = "Period")
            clickOnLastMonthPeriodFilter()
            checkFilterCounter("2")

            clickOnAnytimePeriodFilter()
            checkFilterCounter("1")
        }

        dataSetDetailRobot(composeTestRule) {
            assertEquals(11, getListItemCount())
        }
    }

    private fun createDataSetInstanceStep(
        orgUnit: String,
        period: String,
        catCombo: String? = null,
    ) {
        logStep("Starting Creating dataset instance $period")

        dataSetDetailRobot(composeTestRule) {
            clickOnAddDataSet()
        }
        dataSetInitialRobot {
            clickOnInputOrgUnit()
        }

        orgUnitSelectorRobot(composeTestRule) {
            selectTreeOrgUnit(orgUnit)
        }

        dataSetInitialRobot {
            clickOnInputPeriod()
        }

        reportPeriodSelectorRobot(composeTestRule) {
            selectReportPeriod(period)
        }

        dataSetInitialRobot {
            catCombo?.let {
                clickOnInputCatCombo()
                selectCatCombo(catCombo)
            }
        }

        dataSetInitialRobot {
            clickOnActionButton()
        }
        logStep("Finished Creating dataset instance $period")
    }

    private fun logStep(message: String) {
        val bundle = Bundle()
        bundle.putString("Step", message)
        instrumentation.sendStatus(0, bundle)
    }
}

package org.dhis2.composetable

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.test.runTest
import org.dhis2.composetable.data.InputRowOption
import org.dhis2.composetable.data.TableAppScreenOptions
import org.dhis2.composetable.model.FakeModelType
import org.dhis2.composetable.model.TableCell
import org.dhis2.composetable.test.TestActivity
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class CellTableTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Ignore("Flaky test, will be looked up in ANDROAPP-6863")
    @Test
    fun shouldMoveToNextColumnWhenClickingNext() {
        tableRobot(composeTestRule) {
            val fakeModel = initTableAppScreen(
                FakeModelType.MANDATORY_TABLE
            )
            val firstId = fakeModel.first().id!!
            clickOnCell(firstId, 0, 0)
            composeTestRule.waitForIdle()
            typeOnInputComponent("check")
            composeTestRule.waitForIdle()
            clickOnAccept()
            composeTestRule.waitForIdle()
            assertCellSelected(firstId, 0, 2)
            assertInputComponentInfo(
                expectedMainLabel = "Text 1",
                expectedSecondaryLabels = fakeModel.find { it.id == firstId }?.tableHeaderModel?.rows
                    ?.joinToString(separator = ",") { it.cells[2 % it.cells.size].value } ?: ""
            )
        }
    }

    @Ignore("Flaky test, will be looked up in ANDROAPP-6863")
    @Test
    fun shouldMoveToNextRowWhenClickingNext() {
        tableRobot(composeTestRule) {
            val fakeModel = initTableAppScreen(
                FakeModelType.MANDATORY_TABLE
            )
            val firstId = fakeModel.first().id!!
            clickOnCell(firstId, 0, 47)
            composeTestRule.waitForIdle()
            typeOnInputComponent("check")
            composeTestRule.waitForIdle()
            clickOnAccept()
            composeTestRule.waitForIdle()
            assertCellSelected(firstId, 1, 0)
            clickOnCell(firstId, 1, 0)
            assertInputComponentInfo(
                expectedMainLabel = "Text 2",
                expectedSecondaryLabels =
                fakeModel.find { it.id == firstId }?.tableHeaderModel?.rows
                    ?.joinToString(separator = ",") { it.cells[0].value } ?: ""

            )
        }
    }

    @Ignore("Flaky test, will be looked up in ANDROAPP-6863")
    @Test
    fun shouldClearSelectionWhenClickingNextOnLastCell() = runTest {
        tableRobot(composeTestRule) {
            val fakeModel = initTableAppScreen(
                FakeModelType.MANDATORY_TABLE
            )
            val firstId = fakeModel.first().id!!
            clickOnCell(firstId, 2, 47)
            typeOnInputComponent("check")
            clickOnAccept()
            assertNoCellSelected()
            assertInputComponentIsHidden()
        }
    }

    @Ignore("Flaky test, will be looked up in ANDROAPP-6863")
    @Test
    fun shouldHideInputComponentIfSelectedCellDoesNotRequireIt() {
        val testingTableId = "PjKGwf9WxBE"
        tableRobot(composeTestRule) {
            initTableAppScreen(
                FakeModelType.MANDATORY_TABLE,
                TableAppScreenOptions(
                    inputRowsOptions = listOf(
                        InputRowOption(tableId = testingTableId, 1, false)
                    )
                )
            )
            clickOnCell(testingTableId, 0, 0)
            assertKeyBoardVisibility(true)
            assertInputComponentIsDisplayed()
            clickOnCell(testingTableId, 1, 0)
            assertKeyBoardVisibility(false)
            assertInputComponentIsHidden()
        }
    }
}
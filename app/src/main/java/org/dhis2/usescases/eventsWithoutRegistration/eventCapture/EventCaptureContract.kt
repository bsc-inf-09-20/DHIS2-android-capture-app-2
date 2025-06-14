package org.dhis2.usescases.eventsWithoutRegistration.eventCapture

import android.content.Intent
import androidx.compose.runtime.State
import androidx.lifecycle.LiveData
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import org.dhis2.tracker.NavigationBarUIState
import org.dhis2.usescases.general.AbstractActivityContracts
import org.dhis2.utils.customviews.navigationbar.NavigationPage
import org.hisp.dhis.android.core.common.ValidationStrategy
import org.hisp.dhis.android.core.event.EventStatus
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import org.hisp.dhis.android.core.trackedentity.TrackedEntityDataValue
import java.util.Date

class EventCaptureContract {
    interface View : AbstractActivityContracts.View {
        fun renderInitialInfo(stageName: String)
        val presenter: Presenter
        fun updatePercentage(primaryValue: Float)
        fun restartDataEntry()
        fun finishDataEntry()
        fun saveAndFinish()
        fun showSnackBar(messageId: Int, programStage: String)
        fun showEventIntegrityAlert()
        fun updateNoteBadge(numberOfNotes: Int)
        fun goBack()
        fun showProgress()
        fun hideProgress()
        fun showNavigationBar()
        fun hideNavigationBar()
        fun requestBluetoothPermission(permissionString:String)
        fun launchBluetooth(intent: Intent)
        fun setupViewPagerAdapter()
        fun setupNavigationBar()
        fun setupEventCaptureFormLandscape(string: String)

    }

    interface Presenter : AbstractActivityContracts.Presenter {
        fun observeActions(): LiveData<EventCaptureAction>
        fun init()
        fun onBackClick()

        fun saveAndExit(eventStatus: EventStatus?)
        fun isEnrollmentOpen(): Boolean
        fun completeEvent(addNew: Boolean)
        fun deleteEvent()
        fun skipEvent()
        fun rescheduleEvent(time: Date)
        fun canWrite(): Boolean
        fun hasExpired(): Boolean
        fun initNoteCounter()
        fun refreshTabCounters()
        fun hideProgress()
        fun showProgress()
        fun getCompletionPercentageVisibility(): Boolean
        fun emitAction(onBack: EventCaptureAction)
        fun programStage(): String
        fun getTeiUid(): String?
        fun getEnrollmentUid(): String?
        fun observeNavigationBarUIState(): State<NavigationBarUIState<NavigationPage>>
        fun onNavigationPageChanged(page: NavigationPage)
        fun onSetNavigationPage(index: Int)
        fun isDataEntrySelected(): Boolean
        fun updateNotesBadge(numberOfNotes: Int)
    }

    interface EventCaptureRepository {
        fun eventIntegrityCheck(): Flowable<Boolean>
        fun programStageName(): Flowable<String>
        fun orgUnit(): Flowable<OrganisationUnit>
        fun completeEvent(): Observable<Boolean>
        fun eventStatus(): Flowable<EventStatus>
        val isEnrollmentOpen: Boolean
        fun deleteEvent(): Observable<Boolean>
        fun updateEventStatus(skipped: EventStatus): Observable<Boolean>
        fun rescheduleEvent(time: Date): Observable<Boolean>
        fun programStage(): Observable<String>
        val accessDataWrite: Boolean
        val isEnrollmentCancelled: Boolean
        fun isEventEditable(eventUid: String): Boolean
        fun canReOpenEvent(): Single<Boolean>
        fun isCompletedEventExpired(eventUid: String): Observable<Boolean>
        val noteCount: Single<Int>
        fun showCompletionPercentage(): Boolean
        fun hasAnalytics(): Boolean
        fun hasRelationships(): Boolean
        fun validationStrategy(): ValidationStrategy
        fun getTeiUid(): String?
        fun getEnrollmentUid(): String?
        fun setTemperatureValue(targetElement:String,value:String)
        fun getEventDattaValues(eventUid:String):Single<List<TrackedEntityDataValue>>
    }
}

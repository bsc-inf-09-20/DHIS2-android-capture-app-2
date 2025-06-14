package org.dhis2.usescases.eventsWithoutRegistration.eventCapture.eventCaptureFragment

import MyPreferences
import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.dhis2.R
import org.dhis2.commons.resources.ResourceManager
import org.dhis2.commons.viewmodel.DispatcherProvider
import org.dhis2.data.dhislogic.AUTH_ALL
import org.dhis2.data.dhislogic.AUTH_UNCOMPLETE_EVENT
import org.dhis2.usescases.eventsWithoutRegistration.EventIdlingResourceSingleton
import org.dhis2.usescases.eventsWithoutRegistration.eventCapture.EventCaptureContract
import org.dhis2.usescases.eventsWithoutRegistration.eventCapture.configNetworkModules.ConfigManager
import org.dhis2.usescases.eventsWithoutRegistration.eventCapture.configNetworkModules.ConfigManagerImpl
import org.dhis2.usescases.eventsWithoutRegistration.eventCapture.configNetworkModules.TemperatureConfiguration
import org.dhis2.usescases.eventsWithoutRegistration.eventCapture.domain.ReOpenEventUseCase
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.event.Event
import org.hisp.dhis.android.core.event.EventEditableStatus
import org.hisp.dhis.android.core.event.EventNonEditableReason
import org.hisp.dhis.android.core.event.EventStatus
import timber.log.Timber

class EventCaptureFormPresenter(
    private val view: EventCaptureFormView,
    private val activityPresenter: EventCaptureContract.Presenter,
    private val d2: D2,
    private val eventUid: String,
    private val resourceManager: ResourceManager,
    private val reOpenEventUseCase: ReOpenEventUseCase,
    private val dispatcherProvider: DispatcherProvider
) {

    fun showOrHideSaveButton() {
        val isEditable =
            d2.eventModule().eventService().getEditableStatus(eventUid = eventUid).blockingGet()

        when (isEditable) {
            is EventEditableStatus.Editable -> {
                view.showSaveButton()
                view.hideNonEditableMessage()
            }

            is EventEditableStatus.NonEditable -> {
                view.hideSaveButton()
                configureNonEditableMessage(isEditable.reason)
            }
        }
    }

    fun saveAndExit(eventStatus: EventStatus?) {
        activityPresenter.saveAndExit(eventStatus)
    }

    private fun configureNonEditableMessage(eventNonEditableReason: EventNonEditableReason) {
        val (reason, canBeReOpened) = when (eventNonEditableReason) {
            EventNonEditableReason.BLOCKED_BY_COMPLETION -> resourceManager.getString(R.string.blocked_by_completion) to canReopen()
            EventNonEditableReason.EXPIRED -> resourceManager.getString(R.string.edition_expired) to false
            EventNonEditableReason.NO_DATA_WRITE_ACCESS -> resourceManager.getString(R.string.edition_no_write_access) to false
            EventNonEditableReason.EVENT_DATE_IS_NOT_IN_ORGUNIT_RANGE -> resourceManager.getString(R.string.event_date_not_in_orgunit_range) to false
            EventNonEditableReason.NO_CATEGORY_COMBO_ACCESS -> resourceManager.getString(R.string.edition_no_catcombo_access) to false
            EventNonEditableReason.ENROLLMENT_IS_NOT_OPEN -> resourceManager.formatWithEnrollmentLabel(
                d2.eventModule().events().uid(eventUid).blockingGet()?.program(),
                R.string.edition_enrollment_is_no_open_V2,
                1,
            ) to false

            EventNonEditableReason.ORGUNIT_IS_NOT_IN_CAPTURE_SCOPE -> resourceManager.getString(R.string.edition_orgunit_capture_scope) to false
        }
        view.showNonEditableMessage(reason, canBeReOpened)
    }

    fun reOpenEvent() {
        EventIdlingResourceSingleton.increment()
        CoroutineScope(dispatcherProvider.ui()).launch {
            reOpenEventUseCase(eventUid).fold(
                onSuccess = {
                    view.onReopen()
                    view.showSaveButton()
                    view.hideNonEditableMessage()
                    EventIdlingResourceSingleton.decrement()
                },
                onFailure = { error ->
                    resourceManager.parseD2Error(error)
                    EventIdlingResourceSingleton.decrement()
                },
            )
        }
    }

    private fun canReopen(): Boolean = getEvent()?.let {
        it.status() == EventStatus.COMPLETED && hasReopenAuthority()
    } ?: false

    fun getEvent(): Event? {
        return d2.eventModule().events().uid(eventUid).blockingGet()
    }

    fun getEventStatus(eventUid: String): EventStatus? {
        return d2.eventModule().events().uid(eventUid).blockingGet()?.status()
    }

    private fun hasReopenAuthority(): Boolean = d2.userModule().authorities()
        .byName().`in`(AUTH_UNCOMPLETE_EVENT, AUTH_ALL)
        .one()
        .blockingExists()


    suspend fun getEventDattaValues(context: Context) {

        val json = d2.dataStoreModule().dataStore()
            .byNamespace()
            .eq("Temperature-configuration")
            .byKey()
            .eq("temp-config")
            .blockingGet()

        val mappedRules = json.map {
            Gson().fromJson(
                it.value()?.split("json=")[1]?.split(")")[0],
                TemperatureConfiguration::class.java
            )
        }


        val programStage =
            d2.eventModule()
                .events()
                .uid(eventUid)
                .blockingGet()
                ?.programStage()

        val programStageDataElements =
            d2.programModule()
                .programStageDataElements()
                .byProgramStage()
                .eq(programStage)
                .blockingGet()

        programStageDataElements.find {
            mappedRules[0].mappingRules.map { tempRule -> tempRule.DataElementID }
                .contains(it.dataElement()?.uid())
        }.let{foundDe->

            val prefs = MyPreferences(context)

//            Timber.tag("CAPTURE_HERE").d()

            prefs.TempDataFlow.collect {

                Timber.tag("CAPTURE_HERE").d("Sensor reading: ${it.toString()}")

                d2.trackedEntityModule()
                    .trackedEntityDataValues()
                    .value(eventUid, foundDe?.dataElement()?.uid().toString())
                    .blockingSet(it.toString())
            }
        }

    }
}

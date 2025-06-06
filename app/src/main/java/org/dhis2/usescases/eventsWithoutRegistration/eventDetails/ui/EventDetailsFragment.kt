package org.dhis2.usescases.eventsWithoutRegistration.eventDetails.ui

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.collectAsLazyPagingItems
import org.dhis2.R
import org.dhis2.commons.Constants.ENROLLMENT_STATUS
import org.dhis2.commons.Constants.ENROLLMENT_UID
import org.dhis2.commons.Constants.EVENT_CREATION_TYPE
import org.dhis2.commons.Constants.EVENT_PERIOD_TYPE
import org.dhis2.commons.Constants.EVENT_SCHEDULE_INTERVAL
import org.dhis2.commons.Constants.EVENT_UID
import org.dhis2.commons.Constants.ORG_UNIT
import org.dhis2.commons.Constants.PROGRAM_STAGE_UID
import org.dhis2.commons.Constants.PROGRAM_UID
import org.dhis2.commons.data.EventCreationType
import org.dhis2.commons.date.toUiStringResource
import org.dhis2.commons.dialogs.AlertBottomDialog
import org.dhis2.commons.dialogs.bottomsheet.BottomSheetDialog
import org.dhis2.commons.dialogs.bottomsheet.BottomSheetDialogUiModel
import org.dhis2.commons.locationprovider.LocationSettingLauncher
import org.dhis2.commons.orgunitselector.OUTreeFragment
import org.dhis2.commons.orgunitselector.OrgUnitSelectorScope
import org.dhis2.commons.periods.ui.PeriodSelectorContent
import org.dhis2.commons.resources.ResourceManager
import org.dhis2.databinding.EventDetailsFragmentBinding
import org.dhis2.maps.views.MapSelectorActivity
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.injection.EventDetailsComponentProvider
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.injection.EventDetailsModule
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.models.EventCatCombo
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.models.EventCatComboUiModel
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.models.EventCoordinates
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.models.EventDate
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.models.EventDetails
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.models.EventInputDateUiModel
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.models.EventOrgUnit
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.providers.ProvideCategorySelector
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.providers.ProvideCoordinates
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.providers.ProvideEmptyCategorySelector
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.providers.ProvideInputDate
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.providers.ProvideOrgUnit
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.providers.ProvidePeriodSelector
import org.dhis2.usescases.general.FragmentGlobalAbstract
import org.hisp.dhis.android.core.common.FeatureType
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.android.core.period.PeriodType
import org.hisp.dhis.mobile.ui.designsystem.theme.Spacing
import javax.inject.Inject

class EventDetailsFragment : FragmentGlobalAbstract() {

    @Inject
    lateinit var factory: EventDetailsViewModelFactory

    @Inject
    lateinit var resourceManager: ResourceManager

    private val requestLocationPermissions =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            if (result.values.all { isGranted -> isGranted }) {
                viewModel.requestCurrentLocation()
            }
        }

    private val requestLocationByMap =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data?.extras != null
            ) {
                val featureType: String =
                    result.data!!.getStringExtra(MapSelectorActivity.LOCATION_TYPE_EXTRA)!!
                val coordinates = result.data?.getStringExtra(MapSelectorActivity.DATA_EXTRA)
                viewModel.onLocationByMapSelected(FeatureType.valueOf(featureType), coordinates)
            }
        }

    private val locationDisabledSettings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (locationProvider?.hasLocationEnabled() == true) {
                viewModel.requestCurrentLocation()
            } else {
                viewModel.cancelCoordinateRequest()
            }
        }

    private val viewModel: EventDetailsViewModel by viewModels {
        factory
    }

    var onEventDetailsChange: ((eventDetails: EventDetails) -> Unit)? = null
    var onButtonCallback: (() -> Unit)? = null
    var onEventReopened: (() -> Unit)? = null

    private lateinit var binding: EventDetailsFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        (requireActivity() as EventDetailsComponentProvider).provideEventDetailsComponent(
            EventDetailsModule(
                eventUid = requireArguments().getString(EVENT_UID),
                context = requireContext(),
                eventCreationType = getEventCreationType(
                    requireArguments().getString(EVENT_CREATION_TYPE),
                ),
                programStageUid = requireArguments().getString(PROGRAM_STAGE_UID),
                programUid = requireArguments().getString(PROGRAM_UID)!!,
                periodType = requireArguments()
                    .getSerializable(EVENT_PERIOD_TYPE) as PeriodType?,
                enrollmentId = requireArguments().getString(ENROLLMENT_UID),
                scheduleInterval = requireArguments().getInt(EVENT_SCHEDULE_INTERVAL),
                initialOrgUnitUid = requireArguments().getString(ORG_UNIT),
                enrollmentStatus = requireArguments()
                    .getSerializable(ENROLLMENT_STATUS) as EnrollmentStatus?,
            ),
        )?.inject(this)
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.event_details_fragment,
            container,
            false,
        )
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        binding.fieldsContainer.setContent {
            val date by viewModel.eventDate.collectAsState()
            val details by viewModel.eventDetails.collectAsState()
            val orgUnit by viewModel.eventOrgUnit.collectAsState()
            val catCombo by viewModel.eventCatCombo.collectAsState()
            val coordinates by viewModel.eventCoordinates.collectAsState()

            ProvideNewEventForm(
                date = date,
                details = details,
                orgUnit = orgUnit,
                catCombo = catCombo,
                coordinates = coordinates,
            )
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launchWhenStarted {
            viewModel.eventDetails.collect {
                onEventDetailsChange?.invoke(it)
            }
        }

        viewModel.showPeriods = ::showPeriodDialog

        viewModel.showOrgUnits = ::showOrgUnitDialog

        viewModel.showNoOrgUnits = ::showNoOrgUnitsDialog

        viewModel.requestLocationPermissions = {
            requestLocationPermissions.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            )
        }

        viewModel.requestLocationByMap = { featureType, initCoordinate ->
            requestLocationByMap.launch(
                MapSelectorActivity.create(
                    activity = requireActivity(),
                    fieldUid = null,
                    locationType = FeatureType.valueOfFeatureType(featureType)!!,
                    initialData = initCoordinate,
                    programUid = requireArguments().getString(PROGRAM_UID),
                ),
            )
        }

        viewModel.showEnableLocationMessage = {
            LocationSettingLauncher.requestEnableLocationSetting(
                requireContext(),
                {
                    locationDisabledSettings.launch(
                        LocationSettingLauncher.locationSourceSettingIntent(),
                    )
                },
                {
                    viewModel.cancelCoordinateRequest()
                },
            )
        }

        viewModel.onButtonClickCallback = onButtonCallback

        viewModel.showEventUpdateStatus = { message ->
            displayMessage(message)
        }

        viewModel.onReopenError = { message ->
            displayMessage(message)
        }

        viewModel.onReopenSuccess = { message ->
            displayMessage(message)
            onEventReopened?.invoke()
        }
    }

    @Composable
    private fun ProvideNewEventForm(
        date: EventDate,
        details: EventDetails,
        orgUnit: EventOrgUnit,
        catCombo: EventCatCombo,
        coordinates: EventCoordinates,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Spacing16)) {
            if (viewModel.getPeriodType() == null || (viewModel.getPeriodType() != null && viewModel.getPeriodType() == PeriodType.Daily)) {
                ProvideInputDate(
                    EventInputDateUiModel(
                        eventDate = date,
                        detailsEnabled = details.enabled,
                        onDateClick = {},
                        onDateSelected = { dateValues ->
                            viewModel.onDateSet(
                                dateValues.year,
                                dateValues.month - 1,
                                dateValues.day,
                            )
                        },
                        onClear = { viewModel.onClearEventReportDate() },
                        required = true,
                        showField = date.active,
                        selectableDates = viewModel.getSelectableDates(date),
                    ),
                )
            } else {
                ProvidePeriodSelector(
                    uiModel = EventInputDateUiModel(
                        eventDate = date,
                        detailsEnabled = details.enabled,
                        onDateClick = {
                            viewModel.getPeriodType()?.let {
                                showPeriodDialog(it)
                            }
                        },
                        onDateSelected = {},
                        onClear = { viewModel.onClearEventReportDate() },
                        required = true,
                        showField = date.active,
                        selectableDates = viewModel.getSelectableDates(date),
                    ),
                    modifier = Modifier,
                )
            }

            ProvideOrgUnit(
                orgUnit = orgUnit,
                detailsEnabled = details.enabled,
                onOrgUnitClick = { viewModel.onOrgUnitClick() },
                resources = resourceManager,
                onClear = {
                    viewModel.onClearOrgUnit()
                },
                required = true,
                showField = orgUnit.visible,
            )

            if (!catCombo.isDefault && catCombo.categories.isNotEmpty()) {
                catCombo.categories.forEach { category ->

                    ProvideCategorySelector(
                        eventCatComboUiModel = EventCatComboUiModel(
                            category = category,
                            eventCatCombo = catCombo,
                            detailsEnabled = details.enabled,
                            currentDate = date.currentDate,
                            selectedOrgUnit = details.selectedOrgUnit,
                            onClearCatCombo = {
                                viewModel.onClearCatCombo()
                            },
                            onOptionSelected = {
                                val selectedOption = Pair(category.uid, it?.uid())
                                viewModel.setUpCategoryCombo(selectedOption)
                            },
                            required = true,
                            noOptionsText = getString(R.string.no_options),
                            catComboText = getString(R.string.cat_combo),
                        ),
                    )
                }
            } else if (!catCombo.isDefault) {
                ProvideEmptyCategorySelector(
                    name = catCombo.displayName ?: getString(R.string.cat_combo),
                    option = getString(R.string.no_options),
                )
            }

            ProvideCoordinates(
                coordinates = coordinates,
                detailsEnabled = details.enabled,
                resources = resourceManager,
                showField = coordinates.active,
            )
        }
    }

    private fun showPeriodDialog(periodType: PeriodType) {
        BottomSheetDialog(
            showTopDivider = true,
            showBottomDivider = true,
            bottomSheetDialogUiModel = BottomSheetDialogUiModel(
                title = getString(periodType.toUiStringResource()),
                iconResource = -1,
            ),
            onSecondaryButtonClicked = {
            },
            onMainButtonClicked = { _ ->
            },
            content = { bottomSheetDialog, scrollState ->
                val periods = viewModel.fetchPeriods().collectAsLazyPagingItems()
                PeriodSelectorContent(
                    periods = periods,
                    scrollState = scrollState,
                ) { period ->
                    period.startDate.let { selectedDate ->
                        viewModel.setUpEventReportDate(selectedDate)
                    }
                    bottomSheetDialog.dismiss()
                }
            },
        ).show(childFragmentManager, AlertBottomDialog::class.java.simpleName)
    }

    private fun showOrgUnitDialog() {
        OUTreeFragment.Builder()
            .withPreselectedOrgUnits(
                viewModel.eventOrgUnit.value.selectedOrgUnit
                    ?.let { listOf(it.uid()) }
                    ?: emptyList(),
            )
            .singleSelection()
            .orgUnitScope(
                when (getEventCreationType(requireArguments().getString(EVENT_CREATION_TYPE))) {
                    EventCreationType.REFERAL ->
                        OrgUnitSelectorScope.ProgramSearchScope(
                            viewModel.eventOrgUnit.value.programUid!!,
                        )

                    EventCreationType.DEFAULT,
                    EventCreationType.ADDNEW,
                    EventCreationType.SCHEDULE,
                    ->
                        OrgUnitSelectorScope.ProgramCaptureScope(
                            viewModel.eventOrgUnit.value.programUid!!,
                        )
                },
            )
            .onSelection { selectedOrgUnits ->
                viewModel.setUpOrgUnit(selectedOrgUnit = selectedOrgUnits.firstOrNull()?.uid())
            }
            .build()
            .show(childFragmentManager, "ORG_UNIT_DIALOG")
    }

    private fun showNoOrgUnitsDialog() {
        showInfoDialog(getString(R.string.error), getString(R.string.no_org_units))
    }

    private fun getEventCreationType(typeString: String?): EventCreationType {
        return typeString?.let {
            EventCreationType.valueOf(it)
        } ?: EventCreationType.DEFAULT
    }
}

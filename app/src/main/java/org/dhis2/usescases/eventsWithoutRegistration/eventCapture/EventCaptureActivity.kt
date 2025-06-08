package org.dhis2.usescases.eventsWithoutRegistration.eventCapture

import TemperatureSensorManager
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.dhis2.R
import org.dhis2.bindings.app
import org.dhis2.commons.Constants
import org.dhis2.commons.animations.hide
import org.dhis2.commons.animations.show
import org.dhis2.commons.dialogs.AlertBottomDialog
import org.dhis2.commons.dialogs.CustomDialog
import org.dhis2.commons.dialogs.DialogClickListener
import org.dhis2.commons.dialogs.bottomsheet.BottomSheetDialog
import org.dhis2.commons.dialogs.bottomsheet.BottomSheetDialogUiModel
import org.dhis2.commons.dialogs.bottomsheet.DialogButtonStyle.DiscardButton
import org.dhis2.commons.dialogs.bottomsheet.DialogButtonStyle.MainButton
import org.dhis2.commons.resources.EventResourcesProvider
import org.dhis2.commons.sync.OnDismissListener
import org.dhis2.commons.sync.SyncContext
import org.dhis2.databinding.ActivityEventCaptureBinding
import org.dhis2.form.model.EventMode
import org.dhis2.tracker.relationships.ui.state.RelationshipTopBarIconState
import org.dhis2.ui.ThemeManager
import org.dhis2.usescases.eventsWithoutRegistration.eventCapture.eventCaptureFragment.EventCaptureFormFragment
import org.dhis2.usescases.eventsWithoutRegistration.eventCapture.temprecCoder.PermissionManager
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.injection.EventDetailsComponent
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.injection.EventDetailsComponentProvider
import org.dhis2.usescases.eventsWithoutRegistration.eventDetails.injection.EventDetailsModule
import org.dhis2.usescases.eventsWithoutRegistration.eventInitial.EventInitialActivity
import org.dhis2.usescases.general.ActivityGlobalAbstract
import org.dhis2.usescases.teiDashboard.DashboardViewModel
import org.dhis2.usescases.teiDashboard.dashboardfragments.relationships.MapButtonObservable
import org.dhis2.usescases.teiDashboard.dashboardfragments.teidata.TEIDataActivityContract
import org.dhis2.usescases.teiDashboard.dashboardfragments.teidata.TEIDataFragment.Companion.newInstance
import org.dhis2.usescases.teiDashboard.ui.RelationshipTopBarIcon
import org.dhis2.utils.analytics.CLICK
import org.dhis2.utils.analytics.DELETE_EVENT
import org.dhis2.utils.analytics.SHOW_HELP
import org.dhis2.utils.customviews.MoreOptionsWithDropDownMenuButton
import org.dhis2.utils.customviews.navigationbar.NavigationPage
import org.dhis2.utils.customviews.navigationbar.NavigationPageConfigurator
import org.dhis2.utils.granularsync.OPEN_ERROR_LOCATION
import org.dhis2.utils.granularsync.SyncStatusDialog
import org.dhis2.utils.granularsync.shouldLaunchSyncDialog
import org.dhis2.utils.isLandscape
import org.dhis2.utils.isPortrait
import org.hisp.dhis.mobile.ui.designsystem.component.menu.MenuItemData
import org.hisp.dhis.mobile.ui.designsystem.component.menu.MenuItemStyle
import org.hisp.dhis.mobile.ui.designsystem.component.menu.MenuLeadingElement
import org.hisp.dhis.mobile.ui.designsystem.component.navigationBar.NavigationBar
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import timber.log.Timber
import javax.inject.Inject

class EventCaptureActivity :
    ActivityGlobalAbstract(),
    EventCaptureContract.View,
    MapButtonObservable,
    EventDetailsComponentProvider,
    TEIDataActivityContract {

    @Inject
    override lateinit var presenter: EventCaptureContract.Presenter

    @JvmField
    @Inject
    var pageConfigurator: NavigationPageConfigurator? = null

    @JvmField
    @Inject
    var themeManager: ThemeManager? = null

    private var isEventCompleted = false
    private lateinit var eventMode: EventMode
    private lateinit var binding: ActivityEventCaptureBinding

    @Inject
    lateinit var eventResourcesProvider: EventResourcesProvider

    @JvmField
    var eventCaptureComponent: EventCaptureComponent? = null
    var programUid: String? = null
    var eventUid: String? = null
    private var teiUid: String? = null
    private var enrollmentUid: String? = null
    private val relationshipMapButton: LiveData<Boolean> = MutableLiveData(false)
    private var adapter: EventCapturePagerAdapter? = null
    private var eventViewPager: ViewPager2? = null
    private var dashboardViewModel: DashboardViewModel? = null

    // Bluetooth Temperature Monitoring
    private lateinit var tempSensorManager: TemperatureSensorManager
    private lateinit var permissionManager: PermissionManager

    // Permission launchers
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> handlePermissionResults(results) }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == Activity.RESULT_OK) {
                if (hasBluetoothPermissions()) {
                    checkBluetoothAndStartScanning()
                } else {
                    showError("Missing required Bluetooth permissions")
                    requestBluetoothPermissions()
                }
            } else {
                showError("Bluetooth is required for this feature")
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Bluetooth permission error")
            showError("Bluetooth permission error occurred")
            requestBluetoothPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize Dagger component first
        eventUid = intent.getStringExtra(Constants.EVENT_UID)
        programUid = intent.getStringExtra(Constants.PROGRAM_UID)
        eventMode = intent.getSerializableExtra(Constants.EVENT_MODE) as EventMode
        setUpEventCaptureComponent(eventUid ?: "")

        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_event_capture)
        binding.presenter = presenter

        // Now safe to use injected dependencies
        teiUid = presenter.getTeiUid()
        enrollmentUid = presenter.getEnrollmentUid()
        themeManager?.setProgramTheme(programUid ?: "")

        // Initialize Bluetooth components
        initializeBluetoothComponents()

        // Setup UI components
        setupViewPagerAdapter()
        setupNavigationBar()
        setupMoreOptionsMenu()
        setupEventCaptureFormLandscape(eventUid ?: "")

        // Setup landscape dashboard if needed
        if (isLandscape() && areTeiUidAndEnrollmentUidNotNull()) {
            setupLandscapeDashboard()
        }

        showProgress()
        presenter.initNoteCounter()
        presenter.init()
        binding.syncButton.setOnClickListener { showSyncDialog(EVENT_SYNC) }

        if (intent.shouldLaunchSyncDialog()) {
            showSyncDialog(EVENT_SYNC)
        }
    }

    private fun initializeBluetoothComponents() {
        try {
            permissionManager = PermissionManager(this)
            tempSensorManager = TemperatureSensorManager.create(
                context = this,
                permissionManager = permissionManager,
                bluetoothIntentLauncher = { intent ->
                    try {
                        enableBluetoothLauncher.launch(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to launch Bluetooth intent")
                        showError("Failed to launch Bluetooth settings")
                    }
                },
                permissionLauncher = permissionLauncher,
                uiMessageHandler = { message, isError ->
                    if (isError) showError(message) else showMessage(message)
                }
            ) ?: run {
                showError("Bluetooth LE not supported on this device")
                return
            }

            tempSensorManager.setStateChangeListener(createStateChangeListener())
            startTemperatureMonitoring()
        } catch (e: Exception) {
            Timber.e(e, "Bluetooth initialization failed")
            showError("Bluetooth initialization failed")
        }
    }

    private fun createStateChangeListener(): TemperatureSensorManager.StateChangeListener {
        return object : TemperatureSensorManager.StateChangeListener {
            override fun onScanStarted() {
                showMessage("Scanning for temperature sensor...")
            }

            override fun onScanStopped() {}
            override fun onScanFailed(errorCode: Int) {
                showError("Failed to scan for device: error $errorCode")
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onDeviceConnected(device: BluetoothDevice) {
                showMessage("Connected to ${device.name}")
            }

            override fun onDeviceDisconnected() {
                showError("Device disconnected")
            }

            override fun onTemperatureUpdate(temperature: Float) {
                showMessage("Temperature: $temperature°C")
            }

            override fun onConnectionAttempt(attempt: Int) {
                showMessage("Connection attempt $attempt of ${TemperatureSensorManager.MAX_CONNECTION_ATTEMPTS}")
            }

            override fun onError(message: String, isCritical: Boolean) {
                showError(message)
            }
        }
    }

    override fun setupViewPagerAdapter() {
        eventViewPager = if (isLandscape()) binding.eventViewLandPager else binding.eventViewPager
        adapter = EventCapturePagerAdapter(
            this,
            programUid ?: "",
            eventUid ?: "",
            pageConfigurator?.displayAnalytics() ?: false,
            pageConfigurator?.displayRelationships() ?: false,
            intent.getBooleanExtra(OPEN_ERROR_LOCATION, false),
            eventMode
        )
        eventViewPager?.adapter = adapter
        eventViewPager?.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == 0 && eventMode !== EventMode.NEW) {
                    binding.syncButton.visibility = View.VISIBLE
                } else {
                    binding.syncButton.visibility = View.GONE
                }
                if (position != 1) {
                    hideProgress()
                }
            }
        })
    }

    override fun setupNavigationBar() {
        eventViewPager?.registerOnPageChangeCallback(
            object : OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    presenter.onSetNavigationPage(position)
                }
            },
        )
        binding.navigationBar.setContent {
            DHIS2Theme {
                val uiState by presenter.observeNavigationBarUIState()
                val selectedItemIndex by remember(uiState) {
                    mutableIntStateOf(
                        uiState.items.indexOfFirst {
                            it.id == uiState.selectedItem
                        }
                    )
                }

                AnimatedVisibility(
                    visible = uiState.items.isNotEmpty(),
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth(),
                        items = uiState.items,
                        selectedItemIndex = selectedItemIndex,
                    ) { page ->
                        presenter.onNavigationPageChanged(page)
                        eventViewPager?.currentItem = adapter?.getDynamicTabIndex(page) ?: 0
                    }
                }
            }
        }
    }

    private fun setupMoreOptionsMenu() {
        binding.moreOptions.setContent {
            var expanded by remember { mutableStateOf(false) }

            MoreOptionsWithDropDownMenuButton(
                getMenuItems(),
                expanded,
                onMenuToggle = { expanded = it },
            ) { itemId ->
                when (itemId) {
                    EventCaptureMenuItem.SHOW_HELP -> {
                        analyticsHelper().setEvent(SHOW_HELP, CLICK, SHOW_HELP)
                        showTutorial(false)
                    }
                    EventCaptureMenuItem.DELETE -> confirmDeleteEvent()
                }
            }
        }
    }

    override fun setupEventCaptureFormLandscape(eventUid: String) {
        if (isLandscape()) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.event_form,
                    EventCaptureFormFragment.newInstance(eventUid, false, eventMode)
                )
                .commit()
        }
    }

    private fun setupLandscapeDashboard() {
        val viewModelFactory = app().dashboardComponent()?.dashboardViewModelFactory()
        viewModelFactory?.let { factory ->
            dashboardViewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.tei_column,
                    newInstance(programUid ?: "", teiUid ?: "", enrollmentUid ?: "")
                )
                .commit()
            eventUid?.let { dashboardViewModel?.updateSelectedEventUid(it) }
        }
    }

    private fun setUpEventCaptureComponent(eventUid: String) {
        eventCaptureComponent = app().userComponent()?.plus(
            EventCaptureModule(
                this,
                eventUid,
                isPortrait()
            )
        )
        eventCaptureComponent?.inject(this)
    }

    private fun updateLandscapeViewsOnEventChange(newEventUid: String) {
        if (newEventUid != this.eventUid) {
            this.eventUid = newEventUid
            setUpEventCaptureComponent(newEventUid)
            showProgress()
            presenter.initNoteCounter()
            presenter.init()
        }
    }

    private fun areTeiUidAndEnrollmentUidNotNull(): Boolean {
        return !teiUid.isNullOrEmpty() && !enrollmentUid.isNullOrEmpty()
    }

    private fun isLandscape(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /* Bluetooth Methods */
    private fun startTemperatureMonitoring() {
        if (!::tempSensorManager.isInitialized) {
            showError("Temperature sensor manager not initialized")
            return
        }
        if (hasBluetoothPermissions()) {
            checkBluetoothAndStartScanning()
        } else {
            requestBluetoothPermissions()
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return try {
            permissionManager.hasAllPermissions(permissionManager.checkBluetoothPermissions())
        } catch (e: Exception) {
            Timber.e(e, "Permission check failed")
            false
        }
    }

    private fun requestBluetoothPermissions() {
        try {
            val permissions = permissionManager.checkBluetoothPermissions()
            permissionLauncher.launch(permissions)
        } catch (e: Exception) {
            Timber.e(e, "Failed to request permissions")
            showError("Failed to request permissions")
        }
    }

    private fun handlePermissionResults(results: Map<String, Boolean>) {
        try {
            when {
                results.all { it.value } -> checkBluetoothAndStartScanning()
                results.any { !it.value } -> showError("Some permissions were denied")
                else -> showError("Permissions were not granted")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling permission results")
            showError("Error handling permissions")
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkBluetoothAndStartScanning() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            when {
                bluetoothAdapter == null -> showError("Bluetooth not supported")
                !bluetoothAdapter.isEnabled -> {
                    showMessage("Enabling Bluetooth...")
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBluetoothLauncher.launch(enableBtIntent)
                }
                else -> startBluetoothScanning()
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Bluetooth permission error")
            showError("Bluetooth permission error")
            requestBluetoothPermissions()
        } catch (e: Exception) {
            Timber.e(e, "Bluetooth check failed")
            showError("Bluetooth check failed")
        }
    }

    @Throws(SecurityException::class)
    private fun startBluetoothScanning() {
        if (!hasBluetoothPermissions()) {
            throw SecurityException("Missing required Bluetooth permissions")
        }
        try {
            if (!::tempSensorManager.isInitialized) {
                throw IllegalStateException("Temperature sensor manager not initialized")
            }
            tempSensorManager.startScan()
            showMessage("Scanning for devices...")
        } catch (e: SecurityException) {
            Timber.e(e, "Bluetooth scan permission denied")
            showError("Bluetooth scan permission denied")
            requestBluetoothPermissions()
        } catch (e: Exception) {
            Timber.e(e, "Bluetooth scan failed")
            showError("Bluetooth scan failed")
        }
    }

    /* Activity Lifecycle */
    override fun onResume() {
        super.onResume()
        try {
            presenter.refreshTabCounters()
            dashboardViewModel?.selectedEventUid()?.observe(this) { eventUid ->
                updateLandscapeViewsOnEventChange(eventUid)
            }

            if (hasBluetoothPermissions()) {
                checkBluetoothAndStartScanning()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in onResume")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onPause() {
        super.onPause()
        try {
            if (::tempSensorManager.isInitialized) {
                tempSensorManager.disconnect()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping Bluetooth")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::tempSensorManager.isInitialized) {
                tempSensorManager.disconnect()
                tempSensorManager.setStateChangeListener(null)
            }
            presenter.onDettach()
        } catch (e: Exception) {
            Timber.e(e, "Error in onDestroy")
        }
    }

    /* UI Helper Methods */
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        Timber.d(message)
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        Timber.e(message)
    }

    override fun goBack() {
        onBackPressed()
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishEditMode()
    }

    private fun finishEditMode() {
        if (binding.navigationBar.visibility == View.GONE) {
            showNavigationBar()
        } else {
            attemptFinish()
        }
    }

    private fun attemptFinish() {
        if (eventMode === EventMode.NEW) {
            val bottomSheetDialogUiModel = BottomSheetDialogUiModel(
                title = getString(R.string.title_delete_go_back),
                message = getString(R.string.discard_go_back),
                iconResource = R.drawable.ic_error_outline,
                mainButton = MainButton(R.string.keep_editing),
                secondaryButton = DiscardButton(),
            )
            val dialog = BottomSheetDialog(
                bottomSheetDialogUiModel,
                { /*Unused*/ },
                { presenter.deleteEvent() },
                showTopDivider = true,
            )
            dialog.show(supportFragmentManager, AlertBottomDialog::class.java.simpleName)
        } else if (isFormScreen()) {
            presenter.emitAction(EventCaptureAction.ON_BACK)
        } else {
            finishDataEntry()
        }
    }

    private fun isFormScreen(): Boolean {
        return if (isPortrait()) {
            adapter?.isFormScreenShown(binding.eventViewPager?.currentItem) == true
        } else {
            true
        }
    }

    override fun updatePercentage(primaryValue: Float) {
        binding.completion.setCompletionPercentage(primaryValue)
        if (!presenter.getCompletionPercentageVisibility()) {
            binding.completion.visibility = View.GONE
        }
    }

    override fun saveAndFinish() {
        displayMessage(getString(R.string.saved))
        finishDataEntry()
    }

    override fun showSnackBar(messageId: Int, programStage: String) {
        showToast(
            eventResourcesProvider.formatWithProgramStageEventLabel(
                messageId,
                programStage,
                programUid,
            ),
        )
    }

    override fun restartDataEntry() {
        val bundle = Bundle()
        startActivity(EventInitialActivity::class.java, bundle, true, false, null)
    }

    override fun finishDataEntry() {
        val intent = Intent()
        if (isEventCompleted) {
            intent.putExtra(
                Constants.EVENT_UID,
                getIntent().getStringExtra(Constants.EVENT_UID),
            )
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun renderInitialInfo(stageName: String) {
        binding.programStageName.text = stageName
    }

    private fun getMenuItems(): List<MenuItemData<EventCaptureMenuItem>> {
        return buildList {
            add(
                MenuItemData(
                    id = EventCaptureMenuItem.SHOW_HELP,
                    label = getString(R.string.showHelp),
                    leadingElement = MenuLeadingElement.Icon(icon = Icons.AutoMirrored.Outlined.HelpOutline),
                ),
            )
            if (presenter.canWrite() && presenter.isEnrollmentOpen()) {
                add(
                    MenuItemData(
                        id = EventCaptureMenuItem.DELETE,
                        label = getString(R.string.delete),
                        style = MenuItemStyle.ALERT,
                        leadingElement = MenuLeadingElement.Icon(icon = Icons.Outlined.DeleteForever),
                    ),
                )
            }
        }
    }

    override fun showTutorial(shaked: Boolean) {
        showToast(getString(R.string.no_intructions))
    }

    private fun confirmDeleteEvent() {
        presenter.programStage().let {
            CustomDialog(
                this,
                eventResourcesProvider.formatWithProgramStageEventLabel(
                    R.string.delete_event_label,
                    programStageUid = it,
                    programUid,
                ),
                eventResourcesProvider.formatWithProgramStageEventLabel(
                    R.string.confirm_delete_event_label,
                    programStageUid = it,
                    programUid,
                ),
                getString(R.string.delete),
                getString(R.string.cancel),
                0,
                object : DialogClickListener {
                    override fun onPositive() {
                        analyticsHelper().setEvent(DELETE_EVENT, CLICK, DELETE_EVENT)
                        presenter.deleteEvent()
                    }

                    override fun onNegative() {
                        // dismiss
                    }
                },
            ).show()
        }
    }

    override fun showEventIntegrityAlert() {
        MaterialAlertDialogBuilder(this, R.style.DhisMaterialDialog)
            .setTitle(R.string.conflict)
            .setMessage(
                eventResourcesProvider.formatWithProgramStageEventLabel(
                    R.string.event_label_date_in_future_message,
                    programStageUid = presenter.programStage(),
                    programUid = programUid,
                ),
            )
            .setPositiveButton(
                R.string.change_event_date,
            ) { _, _ ->
                presenter.onSetNavigationPage(0)
            }
            .setNegativeButton(R.string.go_back) { _, _ -> back() }
            .setCancelable(false)
            .show()
    }

    override fun updateNoteBadge(numberOfNotes: Int) {
        presenter.updateNotesBadge(numberOfNotes)
    }

    override fun showProgress() {
        runOnUiThread { binding.toolbarProgress.show() }
    }

    override fun hideProgress() {
        Handler(Looper.getMainLooper()).postDelayed(
            { runOnUiThread { binding.toolbarProgress.hide() } },
            1000,
        )
    }

    override fun showNavigationBar() {
        binding.navigationBar.show()
    }

    override fun hideNavigationBar() {
        binding.navigationBar.hide()
    }

    override fun requestBluetoothPermission(permissionString: String) {
        // Implementation not needed as we're using the new permission manager
    }

    override fun launchBluetooth(intent: Intent) {
        // Implementation not needed as we're using the new launcher
    }

    fun openDetails() {
        presenter.onNavigationPageChanged(NavigationPage.DETAILS)
    }

    fun openForm() {
        supportFragmentManager.findFragmentByTag("EVENT_SYNC")?.let {
            if (it is SyncStatusDialog) {
                it.dismiss()
            }
        }
        presenter.onNavigationPageChanged(NavigationPage.DATA_ENTRY)
    }

    override fun relationshipMap(): LiveData<Boolean> {
        return relationshipMapButton
    }

    override fun onRelationshipMapLoaded() {
        // there are no relationships on events
    }

    override fun updateRelationshipsTopBarIconState(topBarIconState: RelationshipTopBarIconState) {
        when (topBarIconState) {
            is RelationshipTopBarIconState.Selecting -> {
                binding.relationshipIcon.visibility = View.VISIBLE
                binding.relationshipIcon.setContent {
                    RelationshipTopBarIcon(
                        relationshipTopBarIconState = topBarIconState,
                    ) {
                        topBarIconState.onClickListener()
                    }
                }
            }
            else -> {
                binding.relationshipIcon.visibility = View.GONE
            }
        }
    }

    override fun provideEventDetailsComponent(module: EventDetailsModule?): EventDetailsComponent? {
        return eventCaptureComponent?.plus(module)
    }

    private fun showSyncDialog(syncType: String) {
        val syncContext = when (syncType) {
            TEI_SYNC -> enrollmentUid?.let { SyncContext.Enrollment(it) }
            EVENT_SYNC -> SyncContext.Event(eventUid!!)
            else -> null
        }

        syncContext?.let {
            SyncStatusDialog.Builder()
                .withContext(this)
                .withSyncContext(it)
                .onDismissListener(object : OnDismissListener {
                    override fun onDismiss(hasChanged: Boolean) {
                        if (hasChanged && syncType == TEI_SYNC) {
                            dashboardViewModel?.updateDashboard()
                        }
                    }
                })
                .onNoConnectionListener {
                    val contextView = findViewById<View>(R.id.navigationBar)
                    Snackbar.make(
                        contextView,
                        R.string.sync_offline_check_connection,
                        Snackbar.LENGTH_SHORT,
                    ).show()
                }
                .show(syncType)
        }
    }

    override fun openSyncDialog() {
        showSyncDialog(TEI_SYNC)
    }

    override fun finishActivity() {
        finish()
    }

    override fun restoreAdapter(programUid: String, teiUid: String, enrollmentUid: String) {
        // we do not restore adapter in events
    }

    override fun executeOnUIThread() {
        runOnUiThread {
            showDescription(getString(R.string.error_applying_rule_effects))
        }
    }

    override fun getContext(): Context {
        return this
    }

    override fun activityTeiUid(): String? {
        return teiUid
    }

    companion object {
        private const val SHOW_OPTIONS = "SHOW_OPTIONS"
        private const val TEI_SYNC = "SYNC_TEI"
        private const val EVENT_SYNC = "EVENT_SYNC"

        @JvmStatic
        fun getActivityBundle(eventUid: String, programUid: String, eventMode: EventMode): Bundle {
            val bundle = Bundle()
            bundle.putString(Constants.EVENT_UID, eventUid)
            bundle.putString(Constants.PROGRAM_UID, programUid)
            bundle.putSerializable(Constants.EVENT_MODE, eventMode)
            return bundle
        }

        fun intent(
            context: Context,
            eventUid: String,
            programUid: String,
            eventMode: EventMode,
        ): Intent {
            return Intent(context, EventCaptureActivity::class.java).apply {
                putExtra(Constants.EVENT_UID, eventUid)
                putExtra(Constants.PROGRAM_UID, programUid)
                putExtra(Constants.EVENT_MODE, eventMode)
            }
        }
    }
}

enum class EventCaptureMenuItem {
    SHOW_HELP,
    DELETE,
}
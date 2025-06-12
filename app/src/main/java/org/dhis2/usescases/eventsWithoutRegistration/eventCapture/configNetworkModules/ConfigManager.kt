package org.dhis2.usescases.eventsWithoutRegistration.eventCapture.configNetworkModules

import io.reactivex.Single

interface ConfigManager {
    fun getConfigurations(): Single<TemperatureConfiguration>
}
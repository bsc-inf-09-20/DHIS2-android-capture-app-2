package org.dhis2.usescases.eventsWithoutRegistration.eventCapture.configNetworkModules

import com.google.gson.Gson
import io.reactivex.Single
import org.hisp.dhis.android.core.D2
import timber.log.Timber

class ConfigManagerImpl(private val d2:D2) : ConfigManager {
    override fun getConfigurations(): Single<TemperatureConfiguration> {
        val config = d2.dataStoreModule()
            .dataStore()
            .byNamespace().eq("Temperature-configuration")
            .one().blockingGet()

        return if (config != null){
            d2.dataStoreModule()
                .dataStore()
                .byNamespace().eq("Temperature-configuration")
                .byKey().eq("temp-config").one().get().map {
                    Timber.tag("LOG_CONFIGURATIONS").d(it.value())

                    val formattedJson = it.value()?.split("json=")[1]?.split(")")[0]
                    Timber.tag("LOG_CONFIGURATIONS").d(formattedJson)

                    Gson().fromJson(
                        formattedJson,
                        TemperatureConfiguration::class.java
                    )
                }
        }
        else{
            Single.never()
        }
        TODO("Not yet implemented")
    }

}
package org.dhis2.usescases.eventsWithoutRegistration.eventCapture.configNetworkModules

data class MappingRule(
    val DataElementID: String,
    val ProgramID: String,
    val ProgramStageID: String
)
package org.dhis2.bindings

import android.content.Context
import org.dhis2.commons.date.DateUtils
import org.dhis2.commons.date.toDateSpan
import timber.log.Timber
import java.util.Date

const val WRONG_FORMAT = "Wrong format"
val String?.initials: String
    get() {
        val userNames = this
            ?.split(" ".toRegex())
            ?.dropLastWhile { it.isEmpty() }
            ?.toTypedArray()

        var userInit = ""
        userNames?.forEachIndexed { index, word ->
            if (index > 1) return@forEachIndexed
            userInit += word.first()
        }
        return userInit
    }

fun String?.toDateSpan(context: Context): String {
    return if (this == null) {
        ""
    } else {
        toDate().toDateSpan(context)
    }
}

fun String.toDate(): Date {
    var date: Date? = null
    try {
        date = DateUtils.databaseDateFormat().parse(this)
    } catch (e: Exception) {
        Timber.d(WRONG_FORMAT)
    }
    if (date == null) {
        try {
            date = DateUtils.databaseDateFormatNoZulu().parse(this)
        } catch (e: Exception) {
            Timber.d(WRONG_FORMAT)
        }
    }
    if (date == null) {
        try {
            date = DateUtils.dateTimeFormat().parse(this)
        } catch (e: Exception) {
            Timber.d(WRONG_FORMAT)
        }
    }
    if (date == null) {
        try {
            date = DateUtils.uiDateFormat().parse(this)
        } catch (e: Exception) {
            Timber.d(WRONG_FORMAT)
        }
    }

    if (date == null) {
        try {
            date = DateUtils.oldUiDateFormat().parse(this)
        } catch (e: Exception) {
            Timber.d(WRONG_FORMAT)
        }
    }

    if (date == null) {
        throw NullPointerException("$this can't be null or empty")
    }

    return date
}

fun String.toTime(): Date? = DateUtils.timeFormat().parse(this)

fun String.parseToDouble() = this.toDoubleOrNull()?.toString() ?: "0.0"

fun String.newVersion(oldVersion: String): Boolean {
    if (this == oldVersion) return false
    val new = this.split(".")
    val old = oldVersion.split(".")
    try {
        new.forEachIndexed { index, vNumber ->
            if (vNumber.toInt() < (old.getOrElse(index) { "0" }).toInt()) return false else if (vNumber.toInt() > (old.getOrElse(index) { "0" }).toInt()) return true
        }
    } catch (e: Exception) {
        return false
    }
    return true
}

package org.koitharu.kotatsu.core.util

import android.content.Context
import android.text.format.DateFormat
import androidx.preference.PreferenceManager
import org.koitharu.kotatsu.core.prefs.AppSettings
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The clock style the user picked: follow the system, or force 12 or 24 hours. */
object TimeFormat {

	const val SYSTEM = "system"
	const val HOURS_12 = "12"
	const val HOURS_24 = "24"

	fun pattern(context: Context): String {
		val choice = PreferenceManager.getDefaultSharedPreferences(context)
			.getString(AppSettings.KEY_TIME_FORMAT, SYSTEM)
		val is24 = when (choice) {
			HOURS_12 -> false
			HOURS_24 -> true
			else -> DateFormat.is24HourFormat(context)
		}
		return if (is24) "HH:mm" else "h:mm a"
	}

	fun formatter(context: Context): DateTimeFormatter =
		DateTimeFormatter.ofPattern(pattern(context), Locale.getDefault())
}

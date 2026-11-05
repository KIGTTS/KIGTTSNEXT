package com.kigtts.next.ui.album

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@Composable
internal fun rememberAlbumDateFormat(): (LocalDate) -> String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        val format = DateFormat.getDateFormat(context)
        val zone = ZoneId.systemDefault()
        val label: (LocalDate) -> String = { date -> format.format(Date.from(date.atStartOfDay(zone).toInstant())) }
        label
    }
}

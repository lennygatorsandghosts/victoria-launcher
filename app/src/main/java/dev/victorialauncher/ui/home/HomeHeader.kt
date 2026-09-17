// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import dev.victorialauncher.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun headerText(
    date: LocalDate,
    locale: Locale,
    batteryPct: Int?,
    datePattern: String,
): String {
    val dateText = date.format(DateTimeFormatter.ofPattern(datePattern, locale))
    return if (batteryPct == null) dateText else "$dateText  $batteryPct%"
}

fun batteryPercent(level: Int, scale: Int): Int? {
    if (level < 0 || scale <= 0) return null
    return (level.toFloat() * 100f / scale).roundToInt().coerceIn(0, 100)
}

@Composable
fun HomeHeader(
    contentColor: Color,
    sidePaddingDp: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val configuration = LocalConfiguration.current
    val locale = ConfigurationCompat.getLocales(configuration).get(0) ?: Locale.getDefault()
    val datePattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEMMMd") }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var batteryPct by remember { mutableStateOf<Int?>(null) }

    fun updateBattery(intent: Intent?) {
        if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
        batteryPct = batteryPercent(
            intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
        )
    }

    DisposableEffect(appContext) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_TIME_TICK,
                    Intent.ACTION_DATE_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> today = LocalDate.now()
                    Intent.ACTION_BATTERY_CHANGED -> updateBattery(intent)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val sticky = ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        updateBattery(sticky)
        onDispose { appContext.unregisterReceiver(receiver) }
    }

    Row(
        modifier = modifier.padding(horizontal = sidePaddingDp.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = headerText(today, locale, null, datePattern),
            color = contentColor,
            fontSize = 16.sp,
            modifier = Modifier.clickable { openCalendar(context) },
        )
        batteryPct?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$it%",
                color = contentColor,
                fontSize = 16.sp,
            )
        }
    }
}

private fun openCalendar(context: Context) {
    try {
        context.startActivity(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.toast_calendar_unavailable, Toast.LENGTH_SHORT).show()
    }
}

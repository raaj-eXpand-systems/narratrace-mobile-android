package io.narratrace.android.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** ISO strings belong to the API; people choose localized dates and times. */
@Composable
internal fun DeliverySchedulePicker(value: LocalDateTime, zone: ZoneId, onChange: (LocalDateTime) -> Unit, onZoneChange: (ZoneId) -> Unit) {
    val context = LocalContext.current
    var choosingZone by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val zones = remember { ZoneId.getAvailableZoneIds().filter { it.contains('/') || it == "UTC" }.sorted() }
    val datePicker = {
        DatePickerDialog(context, { _, year, month, day -> onChange(java.time.LocalDate.of(year, month + 1, day).atTime(value.toLocalTime())) }, value.year, value.monthValue - 1, value.dayOfMonth).show()
    }
    OutlinedTextField(value.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), {}, readOnly = true,
        modifier = Modifier.fillMaxWidth(), label = { Text("Delivery date") },
        trailingIcon = { IconButton(onClick = datePicker) { Icon(Icons.Default.DateRange, "Choose delivery date") } })
    OutlinedTextField(value.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), {}, readOnly = true,
        modifier = Modifier.fillMaxWidth(), label = { Text("Delivery time") },
        trailingIcon = { IconButton(onClick = {
            TimePickerDialog(context, { _, hour, minute -> onChange(value.withHour(hour).withMinute(minute).withSecond(0).withNano(0)) }, value.hour, value.minute, DateFormat.is24HourFormat(context)).show()
        }) { Icon(Icons.Default.Schedule, "Choose delivery time") } })
    OutlinedTextField(zone.id.replace('_', ' '), {}, readOnly = true, modifier = Modifier.fillMaxWidth(),
        label = { Text("Time zone") }, trailingIcon = { IconButton(onClick = { choosingZone = true }) { Icon(Icons.Default.Public, "Choose time zone") } })
    Text("Delivery uses the selected time zone. Choose a future date and time.", style = MaterialTheme.typography.bodySmall)
    if (choosingZone) AlertDialog(onDismissRequest = { choosingZone = false }, title = { Text("Choose time zone") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("Search city or time zone") }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(zones.filter { it.replace('_', ' ').contains(query, ignoreCase = true) }) { id ->
                    TextButton(onClick = { onZoneChange(ZoneId.of(id)); choosingZone = false; query = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text(id.replace('_', ' ') + if (id == zone.id) " ✓" else "")
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { choosingZone = false }) { Text("Done") } })
}

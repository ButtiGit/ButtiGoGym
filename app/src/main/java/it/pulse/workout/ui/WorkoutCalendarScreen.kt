package it.pulse.workout.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val italianMonth = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
private val italianDay = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

private fun CalendarDayStatus.title(): String = when (this) {
    CalendarDayStatus.COMPLETED -> "Allenamento completato"
    CalendarDayStatus.PLANNED -> "Allenamento programmato"
    CalendarDayStatus.SKIPPED -> "Allenamento saltato"
    CalendarDayStatus.REST -> "Riposo"
    CalendarDayStatus.FREE -> "Nessuna attività programmata"
}

private fun CalendarDayStatus.mark(): String = when (this) {
    CalendarDayStatus.COMPLETED -> "✓"
    CalendarDayStatus.PLANNED -> "●"
    CalendarDayStatus.SKIPPED -> "×"
    CalendarDayStatus.REST -> "–"
    CalendarDayStatus.FREE -> ""
}

/** Month view and editing both use stored plans and actual completed sessions. */
@Composable
fun WorkoutCalendarScreen(
    days: List<WorkoutDay>,
    entries: List<CalendarEntry>,
    history: List<SessionRecord>,
    wellness: WellnessData,
    onSchedule: (LocalDate, String) -> String?,
    onRest: (LocalDate) -> String?,
    onSkip: (LocalDate) -> String?,
    onClear: (LocalDate) -> String?,
    onMove: (LocalDate, LocalDate) -> String?,
    onStart: (WorkoutDay) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    var monthString by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedString by rememberSaveable { mutableStateOf(today.toString()) }
    val month = remember(monthString) { YearMonth.parse(monthString) }
    val selected = remember(selectedString) { LocalDate.parse(selectedString) }
    val completedDates = remember(history) { WorkoutCalendarRules.completedDates(history) }
    val restDates = remember(wellness.restDates) {
        wellness.restDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
    }
    val summary = remember(month, entries, completedDates, restDates, today) {
        WorkoutCalendarRules.summary(month, entries, completedDates, restDates, today)
    }
    val selectedEntry = entries.firstOrNull { it.date == selected }
    val selectedStatus = WorkoutCalendarRules.status(
        selected, entries, completedDates, restDates, today
    )
    val chosenWorkout = days.firstOrNull { it.id == selectedEntry?.workoutDayId }
    val completedSessions = history.filter {
        it.completedSets > 0 && it.completedAt > 0 &&
            Instant.ofEpochMilli(it.completedAt).atZone(ZoneId.systemDefault()).toLocalDate() == selected
    }
    var optionsVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun execute(operation: () -> String?) {
        error = operation()
    }

    fun selectDate(day: LocalDate) {
        selectedString = day.toString()
        if (YearMonth.from(day) != month) monthString = YearMonth.from(day).toString()
        error = null
    }

    fun showMovePicker() {
        val initial = selected.plusDays(1)
        DatePickerDialog(
            context,
            { _, year, calendarMonth, day ->
                execute { onMove(selected, LocalDate.of(year, calendarMonth + 1, day)) }
            },
            initial.year, initial.monthValue - 1, initial.dayOfMonth
        ).show()
    }

    val offset = month.atDay(1).dayOfWeek.value - 1
    val numberOfCells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    val grid = (0 until numberOfCells).map { index ->
        val number = index - offset + 1
        if (number in 1..month.lengthOfMonth()) month.atDay(number) else null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PulseColors.Background),
        contentPadding = PaddingValues(start = BGLayout.pageHorizontal, end = BGLayout.pageHorizontal, top = 14.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            BGBackHeader("Programma e recupera", "Calendario", onBack) {
                IconButton(onClick = { selectDate(today) },
                    modifier = Modifier.size(BGLayout.minTouch)) {
                    Icon(Icons.Default.CalendarToday, "Vai a oggi",
                        tint = PulseColors.Accent)
                }
            }
        }

        item {
            CalendarPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        monthString = month.minusMonths(1).toString()
                        selectDate(month.minusMonths(1).atDay(1))
                    }) {
                        Icon(Icons.Default.ArrowBackIosNew, "Mese precedente",
                            tint = PulseColors.Accent, modifier = Modifier.size(17.dp))
                    }
                    Text(
                        month.atDay(1).format(italianMonth).replaceFirstChar { it.titlecase(Locale.ITALIAN) },
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.ExtraBold,
                        color = PulseColors.Foreground,
                        fontSize = 19.sp
                    )
                    IconButton(onClick = {
                        monthString = month.plusMonths(1).toString()
                        selectDate(month.plusMonths(1).atDay(1))
                    }) {
                        Icon(Icons.Default.ArrowForwardIos, "Mese successivo",
                            tint = PulseColors.Accent, modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    listOf("L", "M", "M", "G", "V", "S", "D").forEach { label ->
                        Box(Modifier.weight(1f).height(26.dp), contentAlignment = Alignment.Center) {
                            Text(label, color = PulseColors.Muted, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
                grid.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { date ->
                            if (date == null) {
                                Spacer(Modifier.weight(1f).height(60.dp))
                            } else {
                                val status = WorkoutCalendarRules.status(
                                    date, entries, completedDates, restDates, today)
                                val selectedDay = selected == date
                                val filled = status == CalendarDayStatus.COMPLETED ||
                                    status == CalendarDayStatus.PLANNED
                                val background = if (selectedDay) PulseColors.SoftGreen
                                    else if (filled) PulseColors.Raised else PulseColors.Surface
                                val outline = if (selectedDay) PulseColors.Accent else PulseColors.Border
                                Column(
                                    Modifier.weight(1f).heightIn(min = 60.dp)
                                        .background(background, RoundedCornerShape(12.dp))
                                        .border(if (selectedDay) 2.dp else 1.dp, outline,
                                            RoundedCornerShape(12.dp))
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = "Apri il giorno ${date.dayOfMonth}"
                                        ) { selectDate(date) }
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = "${date.format(italianDay)}, ${status.title()}"
                                            this.selected = selectedDay
                                        }
                                        .padding(top = 7.dp, bottom = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(date.dayOfMonth.toString(), color = PulseColors.Foreground,
                                        fontWeight = if (selectedDay || date == today)
                                            FontWeight.ExtraBold else FontWeight.Medium,
                                        fontSize = 13.sp)
                                    Text(status.mark(),
                                        color = if (status == CalendarDayStatus.SKIPPED)
                                            Color(0xFFC0714F) else PulseColors.Accent,
                                        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                }
                Spacer(Modifier.height(9.dp))
                Text("✓ Completato  ·  ● Programmato  ·  – Riposo  ·  × Saltato",
                    color = PulseColors.Muted,
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            CalendarPanel {
                Text("COSTANZA DEL MESE", color = PulseColors.Accent, fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(7.dp))
                Text(
                    if (summary.adherencePercent == null) "—"
                    else "${summary.adherencePercent}%",
                    color = PulseColors.Foreground, fontSize = 31.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    if (summary.adherencePercent == null)
                        "Nessuna seduta programmata ancora scaduta."
                    else "${summary.plannedCompleted}/${summary.plannedDue} sedute previste concluse",
                    color = PulseColors.Muted, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CalendarStat("Fatte", summary.completedDays, Modifier.weight(1f))
                    CalendarStat("Saltate", summary.skippedDays, Modifier.weight(1f))
                    CalendarStat("Riposo", summary.restDays, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text("${summary.plannedUpcoming} in programma · " +
                    "${summary.unplannedCompleted} completate extra",
                    color = PulseColors.Muted, fontSize = 11.sp)
                Text("I riposi e le giornate non pianificate non abbassano la costanza. " +
                    "Oggi non è contato come saltato finché non termina.",
                    color = PulseColors.Muted, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 7.dp))
            }
        }

        item {
            CalendarPanel {
                Text(selected.format(italianDay).replaceFirstChar { it.titlecase(Locale.ITALIAN) },
                    color = PulseColors.Foreground, fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(5.dp))
                Text(selectedStatus.title(), color = PulseColors.Accent,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (chosenWorkout != null) {
                    Text(chosenWorkout.name, color = PulseColors.Foreground,
                        fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                } else if (selectedEntry?.workoutDayId != null) {
                    Text("Scheda non più disponibile: ${selectedEntry.workoutDayId}",
                        color = PulseColors.Muted, fontSize = 12.sp)
                }
                if (completedSessions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Registrate nello storico", color = PulseColors.Muted, fontSize = 11.sp)
                    completedSessions.forEach { record ->
                        val name = record.daySnapshot?.name ?:
                            days.firstOrNull { it.id == record.dayId }?.name ?: "Allenamento"
                        Text("✓ $name · ${record.completedSets} serie",
                            color = PulseColors.Foreground, fontSize = 12.sp)
                    }
                }

                if (selectedStatus != CalendarDayStatus.COMPLETED) {
                    Spacer(Modifier.height(14.dp))
                    Box {
                        Button(
                            onClick = { optionsVisible = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                        ) {
                            Icon(Icons.Default.FitnessCenter, null,
                                tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Programma allenamento", color = Color.White)
                        }
                        DropdownMenu(expanded = optionsVisible,
                            onDismissRequest = { optionsVisible = false }) {
                            days.forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day.name + if (day.optional) " · Bonus" else "") },
                                    onClick = {
                                        optionsVisible = false
                                        execute { onSchedule(selected, day.id) }
                                    }
                                )
                            }
                        }
                    }
                    if (selectedEntry?.kind == CalendarEntryKind.WORKOUT) {
                        OutlinedButton(
                            onClick = ::showMovePicker,
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, PulseColors.Border)
                        ) {
                            Icon(Icons.Default.SwapHoriz, null, tint = PulseColors.Accent)
                            Spacer(Modifier.width(8.dp))
                            Text("Sposta seduta", color = PulseColors.Accent)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { execute { onRest(selected) } },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, PulseColors.Border)
                        ) { Text("Riposo", color = PulseColors.Accent) }
                        OutlinedButton(
                            onClick = { execute { onSkip(selected) } },
                            enabled = !selected.isAfter(today),
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, PulseColors.Border)
                        ) { Text("Saltato", color = PulseColors.Accent) }
                    }
                    if (selectedEntry != null || selected in restDates) {
                        TextButton(onClick = { execute { onClear(selected) } },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Rimuovi pianificazione / stato", color = PulseColors.Muted)
                        }
                    }
                    if (selected == today && chosenWorkout != null &&
                        selectedEntry?.kind == CalendarEntryKind.WORKOUT) {
                        Button(
                            onClick = { onStart(chosenWorkout) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Accent)
                        ) {
                            Text("Inizia la seduta di oggi", color = Color.White)
                        }
                    }
                } else {
                    Text("Le sedute completate sono lette dallo storico e non " +
                        "possono essere trasformate in assenze.",
                        color = PulseColors.Muted, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 12.dp))
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun CalendarPanel(content: @Composable ColumnScope.() -> Unit) {
    BGCard(padding = 16.dp, content = content)
}

@Composable
private fun CalendarStat(title: String, value: Int, modifier: Modifier) {
    Column(modifier.background(PulseColors.Raised, RoundedCornerShape(12.dp))
        .padding(12.dp)) {
        Text(title, color = PulseColors.Muted,
            style = MaterialTheme.typography.labelMedium)
        Text(value.toString(), color = PulseColors.Foreground,
            fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
    }
}

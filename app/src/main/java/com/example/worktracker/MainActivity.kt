package com.example.worktracker

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.*
import java.time.temporal.TemporalAdjusters

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getInstance(this)
        val jobDao = db.jobDao()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WorkTrackerScreen(jobDao = jobDao)
                }
            }
        }
    }
}

@Composable
fun WorkTrackerScreen(jobDao: JobDao) {
    val scope = rememberCoroutineScope()
    var startTime by remember { mutableStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    var elapsedMillis by remember { mutableStateOf(0L) }
    var ticketTotal by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var allJobs by remember { mutableStateOf(emptyList<JobEntity>()) }
    LaunchedEffect(Unit) {
        jobDao.getAllJobs().collectLatest { jobs ->
            allJobs = jobs
        }
    }
    LaunchedEffect(running) {
        if (running) {
            while (running) {
                elapsedMillis = SystemClock.elapsedRealtime() - startTime
                kotlinx.coroutines.delay(100L)
            }
        }
    }
    val hours = (elapsedMillis / 3_600_000L)
    val minutes = (elapsedMillis / 60_000L) % 60
    val seconds = (elapsedMillis / 1_000L) % 60
    val timeStr = "%02d:%02d:%02d".format(hours, minutes, seconds)
    val ticketValue = ticketTotal.toDoubleOrNull() ?: 0.0
    val costValue = cost.toDoubleOrNull() ?: 0.0
    val profit = ticketValue - costValue
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
    val nextWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val nextMonthStart = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun totals(start: Long, end: Long): Pair<Long, Double> {
        val f = allJobs.filter { it.dateMillis in start until end }
        return f.sumOf { it.elapsedMillis } to f.sumOf { it.profit }
    }
    fun fmtMs(ms: Long): String {
        val h = ms / 3_600_000L
        val m = (ms / 60_000L) % 60
        return "%dh %02dm".format(h, m)
    }
    val (dMs, dPr) = totals(todayStart, tomorrowStart)
    val (wMs, wPr) = totals(weekStart, nextWeekStart)
    val (mMs, mPr) = totals(monthStart, nextMonthStart)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Work Tracker", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Current Job Timer", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(text = timeStr, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = if (running) Color(0xFF1565C0) else Color.DarkGray)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { startTime = SystemClock.elapsedRealtime() - elapsedMillis; running = true }, enabled = !running) { Text("Start") }
                    Button(onClick = { running = false }, enabled = running, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))) { Text("Stop") }
                    OutlinedButton(onClick = { running = false; startTime = 0L; elapsedMillis = 0L }) { Text("Reset") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Job Details", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = ticketTotal, onValueChange = { ticketTotal = it }, label = { Text("Ticket Total ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Cost ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Profit: $${"%.2f".format(profit)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (profit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828))                    Button(onClick = { if (elapsedMillis > 0 || ticketValue > 0.0) { scope.launch { jobDao.insert(JobEntity(dateMillis = System.currentTimeMillis(), elapsedMillis = elapsedMillis, ticketTotal = ticketValue, cost = costValue)) }; running = false; startTime = 0L; elapsedMillis = 0L; ticketTotal = ""; cost = "" } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("Save Job") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Totals", style = MaterialTheme.typography.headlineMedium)
        TotalsRow("Today", fmtMs(dMs), dPr)
        TotalsRow("This Week", fmtMs(wMs), wPr)
        TotalsRow("This Month", fmtMs(mMs), mPr)
    }
}

@Composable
fun TotalsRow(label: String, timeText: String, profit: Double) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text("Time: $timeText", style = MaterialTheme.typography.bodyMedium)
        Text("Profit: %.2f".format(profit), style = MaterialTheme.typography.bodyMedium, color = if (profit >= 0) Color.Green else Color.Red)
    }
}

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.clockapp

import android.Manifest
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

val Bg = Color(0xFFF7F7F7)
val Blue = Color(0xFF2F80FF)
val Gray = Color(0xFF9A9A9A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Blue)) { App() } }
    }
}

@Composable
fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sw = remember { StopwatchState() }
    val tm = remember { TimerState() }
    val tabs = listOf("Alarm" to Icons.Default.Alarm, "Clock" to Icons.Default.AccessTime,
        "Stopwatch" to Icons.Default.Timer, "Timer" to Icons.Default.HourglassBottom)
    Scaffold(containerColor = Bg, bottomBar = {
        NavigationBar(containerColor = Bg) {
            tabs.forEachIndexed { i, (label, icon) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i },
                    icon = { Icon(icon, null) }, label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent,
                        selectedIconColor = Color.Black, selectedTextColor = Color.Black))
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) { 0 -> AlarmScreen(); 1 -> ClockScreen(); 2 -> StopwatchScreen(sw); else -> TimerScreen(tm) }
        }
    }
}

@Composable
fun Header(title: String) {
    var open by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box {
                IconButton({ open = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(open, { open = false }) {
                    DropdownMenuItem(text = { Text("Settings") }, onClick = {
                        open = false; ctx.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                    })
                }
            }
        }
        Text(title, fontSize = 40.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    }
}

@Composable
fun Pill(icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = Color.White, shadowElevation = 6.dp,
        modifier = Modifier.size(width = 120.dp, height = 56.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Blue, modifier = Modifier.size(32.dp)) }
    }
}

@Composable
fun Fab(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = Color.White, shadowElevation = 6.dp,
        modifier = Modifier.size(58.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Blue, modifier = Modifier.size(32.dp)) }
    }
}

// ---------------- ALARM ----------------
@Composable
fun AlarmScreen() {
    val ctx = LocalContext.current
    var alarms by remember { mutableStateOf(AlarmStore.load(ctx)) }
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AlarmItem?>(null) }
    var deleting by remember { mutableStateOf<AlarmItem?>(null) }
    fun update(l: List<AlarmItem>) { alarms = l; AlarmStore.save(ctx, l) }

    Box(Modifier.fillMaxSize()) {
        Column {
            Header("Alarm")
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(alarms, key = { it.id }) { a ->
                    val h12 = if (a.hour % 12 == 0) 12 else a.hour % 12
                    val col = if (a.enabled) Color.Black else Gray
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White)
                        .combinedClickable(onClick = { editing = a; showDialog = true }, onLongClick = { deleting = a })
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(String.format("%02d:%02d", h12, a.minute), fontSize = 48.sp, color = col)
                                Text(if (a.hour < 12) " am" else " pm", fontSize = 20.sp, color = col, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Text(if (a.repeatDaily) "Daily" else "Once", color = Gray, fontSize = 18.sp)
                        }
                        Switch(checked = a.enabled, onCheckedChange = { on ->
                            val n = a.copy(enabled = on)
                            if (on) AlarmStore.schedule(ctx, n) else AlarmStore.cancel(ctx, n)
                            update(alarms.map { if (it.id == a.id) n else it })
                        })
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(24.dp)) { Fab { editing = null; showDialog = true } }
    }

    if (showDialog) AlarmDialog(editing, { showDialog = false }) { h, m, daily ->
        showDialog = false
        val old = editing
        val n = AlarmItem(old?.id ?: (System.currentTimeMillis() % 1_000_000_000).toInt(), h, m, daily, true)
        if (old != null) AlarmStore.cancel(ctx, old)
        AlarmStore.schedule(ctx, n)
        update(if (old != null) alarms.map { if (it.id == old.id) n else it } else alarms + n)
    }
    deleting?.let { d ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("Delete alarm?") },
            confirmButton = { TextButton({ AlarmStore.cancel(ctx, d); update(alarms.filter { it.id != d.id }); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } })
    }
}

@Composable
fun AlarmDialog(initial: AlarmItem?, onDismiss: () -> Unit, onSave: (Int, Int, Boolean) -> Unit) {
    val cal = Calendar.getInstance()
    val ts = rememberTimePickerState(initial?.hour ?: cal.get(Calendar.HOUR_OF_DAY), initial?.minute ?: cal.get(Calendar.MINUTE), false)
    var daily by remember { mutableStateOf(initial?.repeatDaily ?: false) }
    AlertDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp),
        confirmButton = { TextButton({ onSave(ts.hour, ts.minute, daily) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(ts)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(daily, { daily = it }); Text("Repeat daily") }
            }
        })
}

// ---------------- CLOCK ----------------
val cities = listOf("Albuquerque" to "America/Denver", "New York" to "America/New_York", "Los Angeles" to "America/Los_Angeles",
    "Chicago" to "America/Chicago", "Toronto" to "America/Toronto", "Sao Paulo" to "America/Sao_Paulo",
    "London" to "Europe/London", "Paris" to "Europe/Paris", "Moscow" to "Europe/Moscow", "Cairo" to "Africa/Cairo",
    "Johannesburg" to "Africa/Johannesburg", "Dubai" to "Asia/Dubai", "Karachi" to "Asia/Karachi",
    "Kathmandu" to "Asia/Kathmandu", "Dhaka" to "Asia/Dhaka", "Singapore" to "Asia/Singapore",
    "Hong Kong" to "Asia/Hong_Kong", "Tokyo" to "Asia/Tokyo", "Sydney" to "Australia/Sydney", "Auckland" to "Pacific/Auckland")

@Composable
fun BigClock(label: String, now: ZonedDateTime, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 18.sp)
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), fontSize = 34.sp)
        Text(now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy a", Locale.ENGLISH)).lowercase(), fontSize = 16.sp, color = Gray)
    }
}

@Composable
fun ClockScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("clock", 0) }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000) } }
    var saved by remember { mutableStateOf(prefs.getString("cities", "Albuquerque")!!.split(",").filter { it.isNotBlank() }) }
    var add by remember { mutableStateOf(false) }
    fun update(l: List<String>) { saved = l; prefs.edit().putString("cities", l.joinToString(",")).apply() }
    val local = ZoneId.systemDefault()

    Box(Modifier.fillMaxSize()) {
        Column {
            Header("Clock")
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp)) {
                BigClock("Local time", now, Modifier.weight(1f))
                BigClock(local.id.substringAfter('/').replace('_', ' '), now, Modifier.weight(1f))
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(saved) { name ->
                    val zone = cities.firstOrNull { it.first == name }?.second ?: return@items
                    val t = now.withZoneSameInstant(ZoneId.of(zone))
                    val diff = (t.offset.totalSeconds - now.offset.totalSeconds) / 3600.0
                    val diffTxt = (if (diff >= 0) "+" else "") + (if (diff % 1.0 == 0.0) "%.0f" else "%.1f").format(diff) + " hrs"
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White)
                        .combinedClickable(onClick = {}, onLongClick = { update(saved - name) })
                        .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(t.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 48.sp)
                                Text(" $name", fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Text(t.format(DateTimeFormatter.ofPattern("dd MMM a", Locale.ENGLISH)).lowercase() + "  |  " + diffTxt,
                                color = Gray, fontSize = 17.sp)
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(24.dp)) { Fab { add = true } }
    }
    if (add) AlertDialog(onDismissRequest = { add = false }, title = { Text("Add city") },
        confirmButton = {}, dismissButton = { TextButton({ add = false }) { Text("Cancel") } },
        text = {
            LazyColumn {
                items(cities.filter { it.first !in saved }) { (n, _) ->
                    Text(n, fontSize = 18.sp, modifier = Modifier.fillMaxWidth().clickable { update(saved + n); add = false }.padding(vertical = 12.dp))
                }
            }
        })
}

// ---------------- STOPWATCH ----------------
class StopwatchState {
    var running by mutableStateOf(false)
    var elapsed by mutableLongStateOf(0L)
    private var base = 0L
    fun start() { base = SystemClock.elapsedRealtime() - elapsed; running = true }
    fun pause() { running = false }
    fun reset() { running = false; elapsed = 0 }
    fun tick() { if (running) elapsed = SystemClock.elapsedRealtime() - base }
}

@Composable
fun StopwatchScreen(sw: StopwatchState) {
    LaunchedEffect(sw.running) { while (sw.running) { sw.tick(); delay(16) } }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Header("Stopwatch")
        Spacer(Modifier.weight(1f))
        val e = sw.elapsed
        Text(String.format("%02d:%02d.%02d", e / 60000, (e / 1000) % 60, (e / 10) % 100), fontSize = 76.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!sw.running && sw.elapsed > 0) TextButton({ sw.reset() }) { Text("Reset", fontSize = 18.sp) }
            Pill(if (sw.running) Icons.Default.Pause else Icons.Default.PlayArrow) { if (sw.running) sw.pause() else sw.start() }
        }
        Spacer(Modifier.height(48.dp))
    }
}

// ---------------- TIMER ----------------
class TimerState {
    var h by mutableIntStateOf(0)
    var m by mutableIntStateOf(25)
    var s by mutableIntStateOf(0)
    var running by mutableStateOf(false)
    var remaining by mutableLongStateOf(0L)
    var finished by mutableStateOf(false)
    private var end = 0L
    fun start() {
        remaining = (h * 3600L + m * 60 + s) * 1000
        if (remaining <= 0) return
        end = SystemClock.elapsedRealtime() + remaining; running = true; finished = false
    }
    fun cancel() { running = false }
    fun tick() {
        if (!running) return
        remaining = end - SystemClock.elapsedRealtime()
        if (remaining <= 0) { remaining = 0; running = false; finished = true }
    }
}

@Composable
fun Wheel(count: Int, initial: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val itemH = 64.dp
    val st = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val fling = rememberSnapFlingBehavior(st)
    val sel = st.firstVisibleItemIndex
    LaunchedEffect(sel) { onChange(sel.coerceIn(0, count - 1)) }
    LazyColumn(modifier.height(itemH * 5), state = st, flingBehavior = fling) {
        items(count + 4) { j ->
            val d = j - 2
            Box(Modifier.height(itemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (d in 0 until count) Text(String.format("%02d", d),
                    fontSize = if (d == sel) 64.sp else 50.sp, fontWeight = FontWeight.Light,
                    color = if (d == sel) Color.Black else Color(0xFFB0B0B0))
            }
        }
    }
}

@Composable
fun TimerScreen(tm: TimerState) {
    val ctx = LocalContext.current
    var ringtone by remember { mutableStateOf<Ringtone?>(null) }
    LaunchedEffect(tm.running) { while (tm.running) { tm.tick(); delay(100) } }
    LaunchedEffect(tm.finished) {
        if (tm.finished) {
            ringtone = RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)).also { it.play() }
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Header("Timer")
        Spacer(Modifier.weight(1f))
        if (tm.running) {
            val t = (tm.remaining + 999) / 1000
            Text(String.format("%02d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60), fontSize = 64.sp, fontWeight = FontWeight.Light)
        } else {
            Row(Modifier.fillMaxWidth()) {
                Wheel(24, tm.h, Modifier.weight(1f)) { tm.h = it }
                Wheel(60, tm.m, Modifier.weight(1f)) { tm.m = it }
                Wheel(60, tm.s, Modifier.weight(1f)) { tm.s = it }
            }
        }
        Spacer(Modifier.weight(1f))
        Pill(if (tm.running) Icons.Default.Pause else Icons.Default.PlayArrow) { if (tm.running) tm.cancel() else tm.start() }
        Spacer(Modifier.height(48.dp))
    }
    if (tm.finished) AlertDialog(onDismissRequest = {}, title = { Text("Time's up!") },
        confirmButton = { TextButton({ ringtone?.stop(); tm.finished = false }) { Text("Stop") } })
}

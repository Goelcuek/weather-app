package com.goelcuek.hava.ui

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goelcuek.hava.MainVM
import com.goelcuek.hava.R
import com.goelcuek.hava.data.Day
import com.goelcuek.hava.data.Hour
import com.goelcuek.hava.data.LocationHelper
import com.goelcuek.hava.data.Sky
import com.goelcuek.hava.data.Weather
import com.goelcuek.hava.data.WeatherCodes
import com.goelcuek.hava.data.dayLabel
import com.goelcuek.hava.data.deg
import com.goelcuek.hava.data.hourLabel
import com.goelcuek.hava.data.timeLabel
import java.util.Date
import kotlin.math.roundToInt

val Glass = Color.White.copy(alpha = 0.14f)
val Sub = Color.White.copy(alpha = 0.72f)
val RainBlue = Color(0xFF9ED2FF)

/** Specular rim: bright top-left, faint middle, soft reflection bottom-right. */
val GlassRim = Brush.linearGradient(
    listOf(
        Color.White.copy(alpha = 0.65f),
        Color.White.copy(alpha = 0.08f),
        Color.White.copy(alpha = 0.06f),
        Color.White.copy(alpha = 0.35f),
    )
)
private val GlassSheen = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.12f))
)

@Composable
fun HavaScreen(vm: MainVM = viewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showSearch by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var askedPermission by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res.values.any { it }) vm.useGps() else vm.refresh(allowLocation = false)
    }
    val requestGps = {
        askedPermission = true
        permLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = LocationHelper.hasPermission(ctx)
        if (st.useGps && !granted && !vm.hasLocation && !askedPermission) {
            requestGps()
        } else if (st.weather == null || System.currentTimeMillis() - vm.lastFetch > 10 * 60_000L) {
            vm.refresh(allowLocation = true)
        }
    }

    val w = st.weather
    val snap = w?.snapshot()
    val sky = snap?.let { WeatherCodes.sky(it.code, it.isDay) } ?: Sky.CLEAR_DAY
    val top by animateColorAsState(Color(sky.top), tween(900), label = "top")
    val bottom by animateColorAsState(Color(sky.bottom), tween(900), label = "bottom")

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF4FA3F7))) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(top, bottom)))
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .systemBarsPadding()
                        .padding(horizontal = 16.dp)
                ) {
                    TopBar(
                        place = st.place,
                        gps = st.useGps,
                        loading = st.loading,
                        onPlace = { showSearch = true },
                        onRefresh = { vm.refresh() },
                        onSettings = { showSettings = true },
                    )
                    if (w == null || snap == null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 160.dp)
                                .clickable { vm.refresh() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (st.loading) stringResource(R.string.loading) else stringResource(R.string.error_retry),
                                color = Sub,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Hero(w)
                        Spacer(Modifier.height(20.dp))
                        GlassCard {
                            SectionTitle(stringResource(R.string.hourly))
                            HourlyRow(w.upcomingHours(24))
                        }
                        Spacer(Modifier.height(12.dp))
                        GlassCard {
                            SectionTitle(stringResource(R.string.daily))
                            DailyList(w)
                        }
                        Spacer(Modifier.height(12.dp))
                        DetailsGrid(w)
                        Footer(st.updatedAt, st.error)
                    }
                }
            }

            if (showSearch) {
                SearchSheet(
                    vm = vm,
                    onGps = {
                        if (LocationHelper.hasPermission(ctx)) vm.useGps() else requestGps()
                    },
                    onDismiss = { showSearch = false },
                )
            }
            if (showSettings) {
                SettingsSheet(
                    style = st.style,
                    unit = st.unit,
                    onStyle = vm::setStyle,
                    onUnit = vm::setUnit,
                    onDismiss = { showSettings = false },
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    place: String,
    gps: Boolean,
    loading: Boolean,
    onPlace: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onPlace)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (gps) Icons.Filled.LocationOn else Icons.Filled.Place,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                place.ifBlank { "—" },
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = null) }
            }
        }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = null) }
    }
}

@Composable
private fun Hero(w: Weather) {
    val snap = w.snapshot()
    val today = w.today()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(WeatherCodes.icon(snap.code, snap.isDay)),
            contentDescription = null,
            modifier = Modifier.size(88.dp),
        )
        Text(
            snap.temp.deg(),
            fontSize = 96.sp,
            lineHeight = 100.sp,
            fontWeight = FontWeight.Thin,
        )
        Text(stringResource(WeatherCodes.label(snap.code)), fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        if (today != null) {
            Text(
                "${today.max.deg()} / ${today.min.deg()}   ·   ${stringResource(R.string.feels_like)} ${w.current.feels.deg()}",
                fontSize = 15.sp,
                color = Sub,
            )
        }
    }
}

@Composable
fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GlassSheen)
            .border(1.dp, GlassRim, RoundedCornerShape(24.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, color = Sub, modifier = Modifier.padding(bottom = 10.dp))
}

private fun buildCurve(path: Path, pts: List<Offset>) {
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val cx = (pts[i - 1].x + pts[i].x) / 2f
        path.cubicTo(cx, pts[i - 1].y, cx, pts[i].y, pts[i].x, pts[i].y)
    }
}

@Composable
private fun HourlyRow(hours: List<Hour>) {
    if (hours.isEmpty()) return
    val colW = 58.dp
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            hours.forEachIndexed { i, h ->
                Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (i == 0) stringResource(R.string.now) else hourLabel(h.time),
                        fontSize = 13.sp,
                        color = Sub,
                    )
                    Spacer(Modifier.height(6.dp))
                    Image(
                        painterResource(WeatherCodes.icon(h.code, h.isDay)),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        if (h.pop >= 20) stringResource(R.string.pct, h.pop) else " ",
                        fontSize = 11.sp,
                        color = RainBlue,
                    )
                    Text(h.temp.deg(), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Canvas(
            Modifier
                .width(colW * hours.size)
                .height(34.dp)
        ) {
            if (hours.size < 2) return@Canvas
            val temps = hours.map { it.temp }
            val mn = temps.min()
            val span = (temps.max() - mn).takeIf { it > 0.01 } ?: 1.0
            val pad = 6.dp.toPx()
            val n = temps.size
            val pts = temps.mapIndexed { i, t ->
                Offset(
                    (i + 0.5f) / n * size.width,
                    pad + (1f - ((t - mn) / span).toFloat()) * (size.height - 2 * pad),
                )
            }
            val fill = Path().apply {
                buildCurve(this, pts)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)))
            val line = Path().apply { buildCurve(this, pts) }
            drawPath(line, Color.White.copy(alpha = 0.9f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            pts.forEachIndexed { i, p ->
                drawCircle(Color.White, radius = if (i == 0) 4.dp.toPx() else 2.6.dp.toPx(), center = p)
            }
        }
    }
}

@Composable
private fun DailyList(w: Weather) {
    val ctx = LocalContext.current
    val days = w.upcomingDays().take(7)
    if (days.isEmpty()) return
    val gMin = days.minOf { it.min }
    val gMax = days.maxOf { it.max }
    val today = w.nowLocal().toLocalDate()
    days.forEach { d ->
        DayRow(dayLabel(ctx, d.date, today, full = false), d, gMin, gMax)
    }
}

@Composable
private fun DayRow(label: String, d: Day, gMin: Double, gMax: Double) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.width(64.dp), maxLines = 1)
        Image(
            painterResource(WeatherCodes.icon(d.code, true)),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
        )
        Text(
            if (d.pop >= 20) stringResource(R.string.pct, d.pop) else "",
            fontSize = 12.sp,
            color = RainBlue,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp),
        )
        Text(d.min.deg(), fontSize = 15.sp, color = Sub, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
        RangeBar(
            d.min, d.max, gMin, gMax,
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(5.dp),
        )
        Text(d.max.deg(), fontSize = 15.sp, modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun RangeBar(lo: Double, hi: Double, gMin: Double, gMax: Double, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.height / 2f
        drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = CornerRadius(r, r))
        val span = (gMax - gMin).takeIf { it > 0.01 } ?: 1.0
        var x0 = ((lo - gMin) / span).toFloat() * size.width
        var x1 = ((hi - gMin) / span).toFloat() * size.width
        if (x1 - x0 < size.height) {
            x0 = ((x0 + x1) / 2f - size.height / 2f).coerceAtLeast(0f)
            x1 = (x0 + size.height).coerceAtMost(size.width)
        }
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF6EC1FF), Color(0xFF9BE07A), Color(0xFFFFCF5C), Color(0xFFFF8A57)),
                startX = 0f,
                endX = size.width,
            ),
            topLeft = Offset(x0, 0f),
            size = Size(x1 - x0, size.height),
            cornerRadius = CornerRadius(r, r),
        )
    }
}

@Composable
private fun DetailsGrid(w: Weather) {
    val today = w.today()
    val c = w.current
    val tiles = listOf(
        stringResource(R.string.feels_like) to c.feels.deg(),
        stringResource(R.string.humidity) to stringResource(R.string.pct, c.humidity),
        stringResource(R.string.wind) to stringResource(R.string.km_h, c.wind.roundToInt()),
        stringResource(R.string.uv) to (today?.uv?.roundToInt()?.toString() ?: "--"),
        stringResource(R.string.sunrise) to timeLabel(today?.sunrise),
        stringResource(R.string.sunset) to timeLabel(today?.sunset),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (title, value) -> Tile(title, value, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Tile(title: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(GlassSheen)
            .border(1.dp, GlassRim, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(title, fontSize = 13.sp, color = Sub)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Footer(updatedAt: Long, error: String?) {
    val ctx = LocalContext.current
    val time = if (updatedAt > 0) DateFormat.getTimeFormat(ctx).format(Date(updatedAt)) else "--"
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.updated, time), fontSize = 12.sp, color = Sub)
        if (error != null) {
            Text(stringResource(R.string.offline), fontSize = 12.sp, color = Color(0xFFFFC2B3))
        }
    }
}

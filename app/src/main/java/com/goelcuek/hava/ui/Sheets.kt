package com.goelcuek.hava.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goelcuek.hava.MainVM
import com.goelcuek.hava.R
import com.goelcuek.hava.widget.WidgetKind
import com.goelcuek.hava.widget.WidgetUpdater

private val SheetBg = Color(0xFF1C1C1E)
private val Accent = Color(0xFF4FA3F7)
private val Muted = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(vm: MainVM, onGps: () -> Unit, onDismiss: () -> Unit) {
    val results by vm.results.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding()
        ) {
            Text(stringResource(R.string.choose_location), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = q,
                onValueChange = {
                    q = it
                    vm.search(it)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_city), color = Muted) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Muted) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                    cursorColor = Accent,
                ),
            )
            Spacer(Modifier.height(8.dp))
            SheetRow(Icons.Filled.LocationOn, stringResource(R.string.use_my_location), null, Accent) {
                onGps()
                onDismiss()
            }
            if (searching) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = Accent,
                )
            }
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(results) { p ->
                    SheetRow(Icons.Filled.Place, p.name, p.detail, Color.White) {
                        vm.choose(p)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, sub: String?, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 16.sp, color = tint)
            if (!sub.isNullOrBlank()) Text(sub, fontSize = 13.sp, color = Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    style: String,
    unit: String,
    onStyle: (String) -> Unit,
    onUnit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val mgr = remember { AppWidgetManager.getInstance(ctx) }
    val canPin = remember { runCatching { mgr.isRequestPinAppWidgetSupported }.getOrDefault(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(stringResource(R.string.settings), fontSize = 20.sp, fontWeight = FontWeight.Medium)

            Label(stringResource(R.string.widget_style))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StyleChip(
                    stringResource(R.string.style_dynamic), style == "dynamic",
                    Brush.linearGradient(listOf(Color(0xFF2E7FE8), Color(0xFF5CC2F2))), Modifier.weight(1f),
                ) { onStyle("dynamic") }
                StyleChip(
                    stringResource(R.string.style_dark), style == "dark",
                    Brush.linearGradient(listOf(Color(0xFF2C2C2E), Color(0xFF2C2C2E))), Modifier.weight(1f),
                ) { onStyle("dark") }
                StyleChip(
                    stringResource(R.string.style_light), style == "light",
                    Brush.linearGradient(listOf(Color(0xFFF5F5F7), Color(0xFFFFFFFF))), Modifier.weight(1f),
                ) { onStyle("light") }
            }

            Label(stringResource(R.string.units))
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp)
            ) {
                listOf("c" to "°C", "f" to "°F").forEach { (k, label) ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (unit == k) Accent else Color.Transparent)
                            .clickable { onUnit(k) }
                            .padding(horizontal = 26.dp, vertical = 8.dp)
                    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                }
            }

            Label(stringResource(R.string.add_widget))
            if (canPin) {
                val names = listOf(
                    WidgetKind.SMALL to R.string.w_small,
                    WidgetKind.COMPACT to R.string.w_compact,
                    WidgetKind.HOURLY to R.string.w_hourly,
                    WidgetKind.LARGE to R.string.w_large,
                )
                names.chunked(2).forEach { row ->
                    Row(
                        Modifier.padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { (kind, res) ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        val cls = WidgetUpdater.providers.getValue(kind)
                                        runCatching { mgr.requestPinAppWidget(ComponentName(ctx, cls), null, null) }
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(stringResource(res), fontSize = 14.sp) }
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.pin_unsupported), fontSize = 14.sp, color = Muted)
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.battery_tip), fontSize = 12.sp, color = Muted, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = Muted,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun StyleChip(label: String, selected: Boolean, brush: Brush, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, if (selected) Accent else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(brush)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, color = if (selected) Color.White else Muted)
    }
}

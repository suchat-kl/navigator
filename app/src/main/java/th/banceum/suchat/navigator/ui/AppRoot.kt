package th.banceum.suchat.navigator.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import th.banceum.suchat.navigator.ads.Ads
import th.banceum.suchat.navigator.ads.BannerAd
import th.banceum.suchat.navigator.geo.Geo
import th.banceum.suchat.navigator.nav.LocationSource
import th.banceum.suchat.navigator.nav.NavState
import th.banceum.suchat.navigator.route.Place
import th.banceum.suchat.navigator.route.Profile
import th.banceum.suchat.navigator.route.Route
import th.banceum.suchat.navigator.route.Step
import th.banceum.suchat.navigator.route.ThaiInstructions
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class Screen { MAP, SAVED, ABOUT }

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    var screen by rememberSaveable { mutableStateOf(Screen.MAP) }
    var follow by remember { mutableStateOf(false) }
    var recenter by remember { mutableIntStateOf(0) }
    var topPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
    var confirmStop by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // ---------- สิทธิ์ตำแหน่ง ----------
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(granted()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = granted()
        if (!hasPermission) vm.message = "ไม่ได้รับสิทธิ์ตำแหน่ง ดูแผนที่และค้นหาได้ แต่หาเส้นทางจากตำแหน่งปัจจุบันไม่ได้"
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    // รับตำแหน่งเฉพาะตอนแอปอยู่หน้าจอ ระหว่างนำทางถี่ขึ้น
    LaunchedEffect(hasPermission, vm.navigating) {
        if (!hasPermission) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            LocationSource.updates(context, vm.navigating).collect { vm.onLocation(it) }
        }
    }

    // จอไม่ดับระหว่างนำทาง
    val view = LocalView.current
    DisposableEffect(vm.navigating) {
        view.keepScreenOn = vm.navigating
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(vm.message) {
        // แสดงก่อนแล้วค่อยล้าง (ถ้าล้างก่อน effect จะเริ่มใหม่และยกเลิก Snackbar ทันที)
        val m = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(m)
        vm.message = null
    }

    fun endNav() {
        vm.stopNav()
        follow = false
        activity?.let { Ads.maybeShowInterstitial(it) }
    }

    BackHandler(enabled = screen != Screen.MAP || vm.navigating || vm.selected != null || vm.results.isNotEmpty()) {
        when {
            screen != Screen.MAP -> screen = Screen.MAP
            vm.navigating -> confirmStop = true
            vm.selected != null -> vm.clearSelection()
            else -> vm.clearSearch()
        }
    }

    when (screen) {
        Screen.SAVED -> {
            SavedScreen(vm, onBack = { screen = Screen.MAP }, onPick = { vm.select(it); screen = Screen.MAP })
            return
        }
        Screen.ABOUT -> {
            AboutScreen(vm, onBack = { screen = Screen.MAP })
            return
        }
        Screen.MAP -> Unit
    }

    Box(Modifier.fillMaxSize()) {
        MapScreen(
            initialCenter = vm.prefs.lastCenter,
            hasPermission = hasPermission,
            location = vm.location,
            route = vm.route,
            destination = vm.selected?.pos,
            navigating = vm.navigating,
            follow = follow,
            recenter = recenter,
            topInset = with(density) { topPx.toDp() },
            bottomInset = with(density) { bottomPx.toDp() },
            onLongPress = vm::pickOnMap,
            onUserMove = { follow = false },
            modifier = Modifier.fillMaxSize(),
        )

        // ---------- ส่วนบน ----------
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .onSizeChanged { topPx = it.height } // วัดรวมแถบสถานะ ใช้วางเครดิตแผนที่ใต้แถบค้นหา
                .statusBarsPadding().padding(12.dp),
        ) {
            val st = vm.nav
            if (vm.navigating) InstructionBanner(vm.route, st, vm.routing)
            else SearchBar(vm, onMenu = { screen = it })
        }
        if (!vm.navigating && vm.results.isNotEmpty()) {
            Card(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 84.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth().heightIn(max = 360.dp),
                elevation = CardDefaults.cardElevation(6.dp),
            ) {
                LazyColumn {
                    items(vm.results) { p ->
                        PlaceRow(p, distance = vm.here?.let { Geo.formatDistance(Geo.distance(it, p.pos)) }) { vm.select(p) }
                        HorizontalDivider()
                    }
                }
            }
        }

        // ---------- ส่วนล่าง ----------
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { bottomPx = it.height }
                .navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                FloatingActionButton(onClick = {
                    if (!hasPermission) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    } else {
                        follow = true
                        recenter++
                    }
                }) {
                    Icon(if (follow) Icons.Filled.MyLocation else Icons.Filled.LocationSearching, "ตำแหน่งของฉัน")
                }
            }
            val sel = vm.selected
            when {
                vm.navigating -> NavPanel(vm, onStop = { if (vm.nav?.arrived == true) endNav() else confirmStop = true }, onFollow = { follow = true; recenter++ }, follow = follow)
                sel != null -> PlacePanel(vm, sel, onStart = { vm.startNav(); follow = true })
            }
            if (!vm.navigating) BannerAd()
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.Center).padding(16.dp))
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("จบการนำทาง?") },
            confirmButton = { TextButton(onClick = { confirmStop = false; endNav() }) { Text("จบ") } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("นำทางต่อ") } },
        )
    }
}

// ---------- แถบค้นหา ----------

@Composable
private fun SearchBar(vm: MainViewModel, onMenu: (Screen) -> Unit) {
    val focus = LocalFocusManager.current
    var menu by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.extraLarge, shadowElevation = 6.dp, tonalElevation = 2.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Menu, "เมนู") }
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                placeholder = { Text("ค้นหาสถานที่") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
                trailingIcon = {
                    when {
                        vm.searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        vm.query.isNotEmpty() -> IconButton(onClick = { vm.clearSearch() }) { Icon(Icons.Filled.Close, "ล้าง") }
                    }
                },
            )
            IconButton(onClick = { focus.clearFocus(); vm.search() }) { Icon(Icons.Filled.Search, "ค้นหา") }
        }
    }
    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("นำทางไทย OSM") },
            text = {
                Column {
                    MenuItem(Icons.Filled.Bookmark, "สถานที่ที่บันทึก (${vm.saved.size})") { menu = false; onMenu(Screen.SAVED) }
                    MenuItem(Icons.Filled.Navigation, "ตั้งค่าและเกี่ยวกับแอป") { menu = false; onMenu(Screen.ABOUT) }
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        "แตะค้างบนแผนที่เพื่อเลือกจุดหมาย หรือพิมพ์ชื่อสถานที่ในช่องค้นหา",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text("ปิด") } },
        )
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PlaceRow(p: Place, distance: String?, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.detail.isNotBlank()) {
                Text(p.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (distance != null) Text(distance, style = MaterialTheme.typography.labelMedium)
        trailing?.invoke()
    }
}

// ---------- แผงสถานที่/เส้นทาง ----------

@Composable
private fun PlacePanel(vm: MainViewModel, place: Place, onStart: () -> Unit) {
    var showSteps by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (place.detail.isNotBlank()) {
                        Text(place.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = { vm.toggleSaved(place) }) {
                    Icon(if (vm.isSaved(place)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "บันทึกสถานที่")
                }
                IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Filled.Close, "ปิด") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Profile.entries.forEach { p ->
                    FilterChip(
                        selected = vm.profile == p,
                        onClick = { vm.changeProfile(p) },
                        label = { Text(p.label) },
                        leadingIcon = { Icon(profileIcon(p), null, Modifier.size(18.dp)) },
                    )
                }
            }
            val r = vm.route
            when {
                vm.routing -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("กำลังหาเส้นทาง…")
                }
                r == null -> Button(onClick = { vm.requestRoute() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Filled.Directions, null)
                    Spacer(Modifier.width(8.dp))
                    Text("หาเส้นทาง")
                }
                else -> {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                        Text(Geo.formatDuration(r.duration), style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Text(Geo.formatDistance(r.distance), style = MaterialTheme.typography.titleMedium)
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Navigation, null)
                            Spacer(Modifier.width(8.dp))
                            Text("เริ่มนำทาง")
                        }
                        FilledTonalButton(onClick = { showSteps = !showSteps }) { Text(if (showSteps) "ซ่อนขั้นตอน" else "ขั้นตอน") }
                    }
                    if (showSteps) StepList(r, Modifier.heightIn(max = 240.dp).padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun StepList(route: Route, modifier: Modifier = Modifier) {
    LazyColumn(modifier) {
        itemsIndexed(route.steps) { _, s ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(stepIcon(s), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Text(ThaiInstructions.text(s), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (s.distance > 0) Text(Geo.formatDistance(s.distance), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ---------- ระหว่างนำทาง ----------

@Composable
private fun InstructionBanner(route: Route?, st: NavState?, rerouting: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val next = st?.next ?: route?.steps?.getOrNull(1)
            when {
                rerouting -> {
                    CircularProgressIndicator(Modifier.size(36.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(16.dp))
                    Text("กำลังหาเส้นทางใหม่…", style = MaterialTheme.typography.titleLarge)
                }
                st?.arrived == true -> {
                    Icon(Icons.Filled.Flag, null, Modifier.size(48.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("ถึงจุดหมายแล้ว", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                // ออกนอกเส้นทาง: ระยะถึงจุดเลี้ยวไม่มีความหมาย ให้บอกให้กลับเข้าเส้นทางแทน
                st?.offRoute == true -> {
                    Icon(Icons.Filled.Navigation, null, Modifier.size(48.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("ออกนอกเส้นทาง", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("กลับเข้าสู่เส้นทางสีน้ำเงินเพื่อไปต่อ", style = MaterialTheme.typography.titleMedium)
                    }
                }
                next != null -> {
                    Icon(stepIcon(next), null, Modifier.size(48.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(st?.let { Geo.formatDistance(it.toNext) } ?: "", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(ThaiInstructions.text(next), style = MaterialTheme.typography.titleMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                else -> Text("กำลังหาตำแหน่ง…", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun NavPanel(vm: MainViewModel, onStop: () -> Unit, onFollow: () -> Unit, follow: Boolean) {
    val st = vm.nav
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), elevation = CardDefaults.cardElevation(6.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (st != null && !st.arrived) {
                    val eta = LocalTime.now().plusSeconds(st.remainingTime.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text(Geo.formatDuration(st.remainingTime), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("${Geo.formatDistance(st.remaining)} · ถึงประมาณ $eta น.", style = MaterialTheme.typography.bodyMedium)
                } else if (st?.arrived == true) {
                    Text(vm.selected?.name ?: "ถึงจุดหมายแล้ว", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("รอสัญญาณ GPS…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!follow) IconButton(onClick = onFollow) { Icon(Icons.Filled.MyLocation, "กลับมาที่ตำแหน่ง") }
            IconButton(onClick = { vm.toggleVoice() }) {
                Icon(if (vm.voiceOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, if (vm.voiceOn) "ปิดเสียง" else "เปิดเสียง")
            }
            Button(onClick = onStop) { Text(if (st?.arrived == true) "เสร็จ" else "จบ") }
        }
    }
}

// ---------- ไอคอน ----------

private fun profileIcon(p: Profile): ImageVector = when (p) {
    Profile.CAR -> Icons.Filled.DirectionsCar
    Profile.BIKE -> Icons.Filled.DirectionsBike
    Profile.WALK -> Icons.Filled.DirectionsWalk
}

fun stepIcon(s: Step): ImageVector = when (s.type) {
    ThaiInstructions.LEFT -> Icons.Filled.TurnLeft
    ThaiInstructions.RIGHT -> Icons.Filled.TurnRight
    ThaiInstructions.SHARP_LEFT -> Icons.Filled.TurnSharpLeft
    ThaiInstructions.SHARP_RIGHT -> Icons.Filled.TurnSharpRight
    ThaiInstructions.SLIGHT_LEFT, ThaiInstructions.KEEP_LEFT -> Icons.Filled.TurnSlightLeft
    ThaiInstructions.SLIGHT_RIGHT, ThaiInstructions.KEEP_RIGHT -> Icons.Filled.TurnSlightRight
    ThaiInstructions.ENTER_ROUNDABOUT, ThaiInstructions.EXIT_ROUNDABOUT -> Icons.Filled.RoundaboutLeft
    ThaiInstructions.U_TURN -> Icons.Filled.UTurnRight
    ThaiInstructions.GOAL -> Icons.Filled.Flag
    ThaiInstructions.DEPART -> Icons.Filled.Navigation
    else -> Icons.Filled.Straight
}

@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
    }
}

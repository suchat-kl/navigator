package th.banceum.suchat.navigator.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.location.Location
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import th.banceum.suchat.navigator.geo.LatLon
import th.banceum.suchat.navigator.route.Route

/** แผนที่ฐาน OpenFreeMap (ข้อมูล OpenStreetMap) ไม่ต้องใช้กุญแจ */
const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

private const val SRC_ROUTE = "route"
private const val SRC_DEST = "dest"
private const val SRC_ALERTS = "alerts"

/**
 * แผนที่ MapLibre ใน Compose
 * - [follow] = กล้องตามตำแหน่งและทิศทาง (ระหว่างนำทาง หรือหลังกดปุ่มตำแหน่งของฉัน)
 * - [recenter] เพิ่มค่าเมื่อต้องการให้กล้องกลับมาที่ตำแหน่งปัจจุบัน
 * - [onUserMove] เรียกเมื่อผู้ใช้ลากแผนที่เอง (ให้เลิกตามตำแหน่ง)
 */
@Composable
fun MapScreen(
    initialCenter: LatLon,
    hasPermission: Boolean,
    location: Location?,
    route: Route?,
    destination: LatLon?,
    /** GeoJSON จุดเตือน (null = ไม่แสดง) */
    alertsJson: String?,
    navigating: Boolean,
    follow: Boolean,
    recenter: Int,
    topInset: Dp,
    bottomInset: Dp,
    onLongPress: (LatLon) -> Unit,
    onUserMove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var centeredOnce by remember { mutableStateOf(false) }
    val longPress by rememberUpdatedState(onLongPress)
    val userMove by rememberUpdatedState(onUserMove)

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle) {
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> { mapView.onDestroy(); destroyed = true }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (!destroyed) mapView.onDestroy()
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(initialCenter.lat, initialCenter.lon)).zoom(12.0).build()
            m.uiSettings.apply {
                // ย้ายเครดิตแผนที่ (ต้องแสดงตามสัญญาอนุญาต OSM) และเข็มทิศขึ้นด้านบน ไม่ให้แผงล่างบัง
                setAttributionGravity(Gravity.TOP or Gravity.END)
                setLogoGravity(Gravity.TOP or Gravity.START)
                setCompassGravity(Gravity.TOP or Gravity.END)
            }
            m.addOnMapLongClickListener { p ->
                longPress(LatLon(p.latitude, p.longitude)); true
            }
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) userMove()
            }
            m.setStyle(Style.Builder().fromUri(MAP_STYLE)) { s ->
                thaiLabels(s)
                addRouteLayers(s)
                map = m
                style = s
            }
        }
    }

    // ระยะขอบบน/ล่าง (แถบค้นหา แผงข้อมูล) สำหรับเครดิตแผนที่และการจัดกล้อง
    LaunchedEffect(map, topInset) {
        val m = map ?: return@LaunchedEffect
        val top = with(density) { topInset.roundToPx() }
        val side = with(density) { 8.dp.roundToPx() }
        m.uiSettings.setAttributionMargins(0, top, side, 0)
        m.uiSettings.setLogoMargins(side, top, 0, 0)
        m.uiSettings.setCompassMargins(0, top + with(density) { 28.dp.roundToPx() }, side, 0)
    }

    // จุดตำแหน่งปัจจุบัน (ใช้ตำแหน่งจาก Fused Location ของแอปเอง)
    LaunchedEffect(style, hasPermission) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (!hasPermission) return@LaunchedEffect
        enableLocation(m, s, context)
    }
    LaunchedEffect(style, location) {
        val m = map ?: return@LaunchedEffect
        val loc = location ?: return@LaunchedEffect
        if (m.locationComponent.isLocationComponentActivated) m.locationComponent.forceLocationUpdate(loc)
        // ได้ตำแหน่งครั้งแรกและยังไม่ได้เลือกจุดหมาย → เลื่อนแผนที่ไปที่ตำแหน่งปัจจุบัน
        if (!centeredOnce && destination == null && route == null) {
            centeredOnce = true
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.0), 800)
        }
    }

    // โหมดกล้อง
    LaunchedEffect(style, follow, navigating, recenter) {
        val m = map ?: return@LaunchedEffect
        if (!m.locationComponent.isLocationComponentActivated) {
            if (recenter > 0 && location != null) {
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 16.0))
            }
            return@LaunchedEffect
        }
        val lc = m.locationComponent
        when {
            navigating && follow -> {
                lc.setCameraMode(CameraMode.TRACKING_GPS)
                lc.renderMode = RenderMode.GPS
                lc.zoomWhileTracking(17.0)
                lc.tiltWhileTracking(45.0)
            }
            follow -> {
                lc.setCameraMode(CameraMode.TRACKING)
                lc.renderMode = RenderMode.COMPASS
                lc.zoomWhileTracking(16.0)
                lc.tiltWhileTracking(0.0)
            }
            else -> {
                lc.setCameraMode(CameraMode.NONE)
                lc.renderMode = if (navigating) RenderMode.GPS else RenderMode.COMPASS
            }
        }
    }

    // วาดเส้นทาง + จุดหมาย และจัดกล้องให้เห็นทั้งเส้น (ก่อนเริ่มนำทาง)
    // จุดเตือน (ด่านชั่ง ด่านเก็บเงิน กล้อง)
    LaunchedEffect(style, alertsJson) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SRC_ALERTS)?.setGeoJson(alertsJson ?: """{"type":"FeatureCollection","features":[]}""")
    }

    LaunchedEffect(style, route, destination) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        val line = route?.points?.map { Point.fromLngLat(it.lon, it.lat) }
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(
            if (line != null && line.size >= 2) FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(line)))
            else FeatureCollection.fromFeatures(emptyList()),
        )
        s.getSourceAs<GeoJsonSource>(SRC_DEST)?.setGeoJson(
            destination?.let { FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))) }
                ?: FeatureCollection.fromFeatures(emptyList()),
        )
        if (navigating) return@LaunchedEffect
        val pad = with(density) { 48.dp.roundToPx() }
        val top = with(density) { topInset.roundToPx() } + pad
        val bottom = with(density) { bottomInset.roundToPx() } + pad
        if (route != null && route.points.size >= 2) {
            val bounds = LatLngBounds.Builder().includes(route.points.map { LatLng(it.lat, it.lon) }).build()
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad, top, pad, bottom), 800)
        } else if (destination != null) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(destination.lat, destination.lon), 15.0), 800)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

@SuppressLint("MissingPermission")
private fun enableLocation(m: MapLibreMap, s: Style, context: android.content.Context) {
    val lc = m.locationComponent
    if (!lc.isLocationComponentActivated) {
        lc.activateLocationComponent(
            LocationComponentActivationOptions.Builder(context, s)
                .useDefaultLocationEngine(false)
                .build(),
        )
    }
    lc.isLocationComponentEnabled = true
}

/** เปลี่ยนป้ายชื่อบนแผนที่ให้ใช้ชื่อภาษาไทยก่อน (style เดิมแสดงอักษรละติน + ชื่อท้องถิ่นคู่กัน) */
private fun thaiLabels(s: Style) {
    val thai = Expression.coalesce(Expression.get("name:th"), Expression.get("name"))
    for (layer in s.layers) {
        if (layer !is SymbolLayer) continue
        val tf = layer.textField
        val text = (if (tf.isExpression) tf.expression?.toString() else tf.value?.toString()) ?: continue
        if ("name" in text) layer.setProperties(PropertyFactory.textField(thai))
    }
}

/** ชั้นเส้นทางวางใต้ป้ายชื่อชั้นแรก เพื่อไม่ให้บังชื่อถนน */
private fun addRouteLayers(s: Style) {
    s.addSource(GeoJsonSource(SRC_ROUTE))
    s.addSource(GeoJsonSource(SRC_DEST))
    val casing = LineLayer("route-casing", SRC_ROUTE).withProperties(
        PropertyFactory.lineColor(Color.WHITE),
        PropertyFactory.lineWidth(10f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    val line = LineLayer("route-line", SRC_ROUTE).withProperties(
        PropertyFactory.lineColor(Color.parseColor("#1A73E8")),
        PropertyFactory.lineWidth(6f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    val dest = CircleLayer("dest-point", SRC_DEST).withProperties(
        PropertyFactory.circleRadius(9f),
        PropertyFactory.circleColor(Color.parseColor("#D93025")),
        PropertyFactory.circleStrokeColor(Color.WHITE),
        PropertyFactory.circleStrokeWidth(3f),
    )
    val firstLabel = s.layers.firstOrNull { it is SymbolLayer }?.id
    if (firstLabel != null) {
        s.addLayerBelow(casing, firstLabel)
        s.addLayerAbove(line, casing.id)
    } else {
        s.addLayer(casing)
        s.addLayer(line)
    }
    s.addLayer(dest)
    // จุดเตือน: ส้ม = ด่านเก็บเงิน, ม่วง = ด่านชั่งน้ำหนัก, แดง = กล้องตรวจจับความเร็ว (แสดงตั้งแต่ซูม 10)
    s.addSource(GeoJsonSource(SRC_ALERTS))
    s.addLayerBelow(
        CircleLayer("alert-points", SRC_ALERTS).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor(
                Expression.match(
                    Expression.get("kind"), Expression.color(Color.parseColor("#F57C00")),
                    Expression.stop("weighbridge", Expression.color(Color.parseColor("#7B1FA2"))),
                    Expression.stop("speed_camera", Expression.color(Color.parseColor("#D32F2F"))),
                ),
            ),
            PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.circleStrokeWidth(2f),
        ).apply { minZoom = 10f },
        dest.id,
    )
}

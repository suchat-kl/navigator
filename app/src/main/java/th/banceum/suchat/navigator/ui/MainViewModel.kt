package th.banceum.suchat.navigator.ui

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import th.banceum.suchat.navigator.alert.AlertData
import th.banceum.suchat.navigator.alert.AlertSource
import th.banceum.suchat.navigator.alert.AlertTracker
import th.banceum.suchat.navigator.alert.UpcomingAlert
import th.banceum.suchat.navigator.BuildConfig
import th.banceum.suchat.navigator.data.Prefs
import th.banceum.suchat.navigator.geo.Geo
import th.banceum.suchat.navigator.geo.LatLon
import th.banceum.suchat.navigator.nav.NavState
import th.banceum.suchat.navigator.nav.NavTracker
import th.banceum.suchat.navigator.nav.SpeedData
import th.banceum.suchat.navigator.nav.SpeedLimit
import th.banceum.suchat.navigator.nav.SpeedLimitResolver
import th.banceum.suchat.navigator.nav.SpeedMonitor
import th.banceum.suchat.navigator.nav.SpeedSource
import th.banceum.suchat.navigator.nav.Voice
import th.banceum.suchat.navigator.route.Geocoder
import th.banceum.suchat.navigator.route.NavServer
import th.banceum.suchat.navigator.route.navFailover
import th.banceum.suchat.navigator.route.OrsException
import th.banceum.suchat.navigator.route.Place
import th.banceum.suchat.navigator.route.Profile
import th.banceum.suchat.navigator.route.Route
import th.banceum.suchat.navigator.route.Router

/** สถานะทั้งหมดของหน้าจอแผนที่ */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val voice = Voice(app)

    var location by mutableStateOf<Location?>(null)
        private set
    val here: LatLon? get() = location?.let { LatLon(it.latitude, it.longitude) }

    var query by mutableStateOf("")
    var results by mutableStateOf<List<Place>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set

    /** สถานที่ที่เลือก (จุดหมาย) */
    var selected by mutableStateOf<Place?>(null)
        private set
    var profile by mutableStateOf(prefs.profile)
        private set
    var route by mutableStateOf<Route?>(null)
        private set
    var routing by mutableStateOf(false)
        private set

    var navigating by mutableStateOf(false)
        private set
    var nav by mutableStateOf<NavState?>(null)
        private set
    var voiceOn by mutableStateOf(prefs.voice)
        private set

    var saved by mutableStateOf(prefs.saved)
        private set

    /** จุดเตือน (ด่านชั่ง ด่านเก็บเงิน กล้อง) โหลดครั้งเดียวตอนเปิดแอป */
    var alertData by mutableStateOf<AlertData?>(null)
        private set
    var alertsOn by mutableStateOf(prefs.alerts)
        private set
    /** จุดเตือนถัดไปบนเส้นทาง (ภายใน 1 กม.) สำหรับป้ายบนจอ */
    var nextAlert by mutableStateOf<UpcomingAlert?>(null)
        private set
    private var alertTracker: AlertTracker? = null

    /** เตือนความเร็ว: กติกาตามพื้นที่ + ป้ายรายถนน (โหลดตอนเปิดแอป) */
    private var speedData: SpeedData? = null
    var speedOn by mutableStateOf(prefs.speedWarn)
        private set
    /** อัตราจำกัดของถนนที่อยู่ตอนนี้ (null = ไม่ทราบ ไม่แสดงป้าย) */
    var speedLimit by mutableStateOf<SpeedLimit?>(null)
        private set
    /** ความเร็วจาก GPS (กม./ชม.) */
    var speedKmh by mutableStateOf<Int?>(null)
        private set
    var speeding by mutableStateOf(false)
        private set
    private val speedMonitor = SpeedMonitor()
    private var limitJob: Job? = null
    /** จุด/เวลาที่ถามอัตราจำกัดครั้งล่าสุด และจุดที่ได้ค่ามาล่าสุด */
    private var limitAskedAt: LatLon? = null
    private var limitAskedTime = 0L
    private var limitKnownAt: LatLon? = null

    /** เครื่องนี้เตือนความเร็วได้ไหม (ต้องใช้ Nominatim ของกรมหาว่าอยู่บนถนนเส้นไหน) */
    val speedSupported get() = NavServer.searchEnabled

    /** ข้อความแจ้งผู้ใช้ครั้งเดียว (แสดงเป็น Snackbar แล้วล้าง) */
    var message by mutableStateOf<String?>(null)

    private var tracker: NavTracker? = null
    private var routeJob: Job? = null
    private var lastReroute = 0L
    /** ตำแหน่งตอนหาเส้นทางใหม่ครั้งล่าสุด กันหาซ้ำทุก 15 วินาทีขณะยังจอดอยู่นอกถนน (เปลืองโควตา ORS) */
    private var lastRerouteAt: LatLon? = null

    val orsReady get() = Geocoder.configured

    init {
        viewModelScope.launch { alertData = AlertSource.load(app) }
        if (speedSupported) viewModelScope.launch { speedData = SpeedSource.load(app) }
    }

    // ---------- ค้นหา ----------

    fun search() {
        val text = query.trim()
        if (text.length < 2) return
        searching = true
        viewModelScope.launch {
            try {
                results = Geocoder.search(text, here)
                if (results.isEmpty()) message = "ไม่พบ \"$text\""
            } catch (e: OrsException) {
                message = e.message
            } finally {
                searching = false
            }
        }
    }

    fun clearSearch() {
        query = ""
        results = emptyList()
    }

    // ---------- เลือกจุดหมาย ----------

    fun select(place: Place) {
        selected = place
        results = emptyList()
        route = null
    }

    /** แตะค้างบนแผนที่: ตั้งเป็นจุดหมายทันที แล้วค่อยถามชื่อสถานที่ */
    fun pickOnMap(p: LatLon) {
        if (navigating) return
        val temp = Place("จุดที่เลือก", String.format(Locale.ROOT, "%.5f, %.5f", p.lat, p.lon), p)
        select(temp)
        if (!orsReady) return
        viewModelScope.launch {
            val named = runCatching { Geocoder.reverse(p) }.getOrNull() ?: return@launch
            // ผู้ใช้อาจเลือกที่อื่นไปแล้วระหว่างรอ
            if (selected == temp) selected = named.copy(pos = p)
        }
    }

    fun clearSelection() {
        if (navigating) return
        routeJob?.cancel()
        selected = null
        route = null
        routing = false
    }

    // ---------- สถานที่ที่บันทึก ----------

    fun isSaved(place: Place) = saved.any { Geo.distance(it.pos, place.pos) < 15 }

    fun toggleSaved(place: Place) {
        saved = if (isSaved(place)) saved.filterNot { Geo.distance(it.pos, place.pos) < 15 } else saved + place
        prefs.saved = saved
        message = if (isSaved(place)) "บันทึก \"${place.name}\" แล้ว" else "ลบ \"${place.name}\" แล้ว"
    }

    fun deleteSaved(place: Place) {
        saved = saved - place
        prefs.saved = saved
    }

    fun clearAllData() {
        prefs.clearAll()
        saved = emptyList()
        voiceOn = prefs.voice
        alertsOn = prefs.alerts
        speedOn = prefs.speedWarn
        profile = prefs.profile
        message = "ล้างข้อมูลในเครื่องแล้ว"
    }

    // ---------- เส้นทาง ----------

    fun changeProfile(p: Profile) {
        if (p == profile) return
        profile = p
        prefs.profile = p
        if (route != null || routing) requestRoute()
    }

    fun requestRoute() {
        val dest = selected ?: return
        val from = here
        if (from == null) {
            message = "ยังไม่ทราบตำแหน่งปัจจุบัน — เปิด GPS และอนุญาตสิทธิ์ตำแหน่ง"
            return
        }
        routeJob?.cancel()
        routing = true
        val p = profile
        routeJob = viewModelScope.launch {
            try {
                route = Router.route(from, dest.pos, p)
            } catch (e: OrsException) {
                message = e.message
            } finally {
                routing = false
            }
        }
    }

    // ---------- นำทาง ----------

    fun startNav() {
        val r = route ?: return
        tracker = NavTracker(r)
        alertTracker = newAlertTracker(r)
        lastRerouteAt = null
        navigating = true
        nav = null
        speak(tracker!!.intro())
        location?.let { onLocation(it) }
    }

    fun stopNav() {
        navigating = false
        nav = null
        tracker = null
        alertTracker = null
        nextAlert = null
        clearSpeed()
        voice.stop()
    }

    fun toggleVoice() {
        voiceOn = !voiceOn
        prefs.voice = voiceOn
        if (!voiceOn) voice.stop()
        else if (voice.available == false) message = "เครื่องนี้ไม่มีเสียงพูดภาษาไทย ติดตั้งได้ที่ ตั้งค่า → ภาษา → เอาต์พุตการอ่านออกเสียง"
    }

    fun onLocation(loc: Location) {
        location = loc
        prefs.lastCenter = LatLon(loc.latitude, loc.longitude)
        val t = tracker ?: return
        val u = t.update(LatLon(loc.latitude, loc.longitude), loc.accuracy)
        nav = u.state
        u.speech?.let { speak(it) }
        // จุดเตือนบนเส้นทาง (ไม่เตือนตอนออกนอกเส้นทาง เพราะระยะตามแนวทางไม่มีความหมาย)
        val at = alertTracker
        if (at != null && !u.state.offRoute && !u.state.arrived) {
            at.speech(u.state.along)?.let { speak(it, queue = true) }
            nextAlert = at.upcoming(u.state.along)
        } else {
            nextAlert = null
        }
        val now = System.currentTimeMillis()
        val p = LatLon(loc.latitude, loc.longitude)
        updateSpeed(loc, p, now)
        val moved = lastRerouteAt?.let { Geo.distance(it, p) > 100 } ?: true
        if (u.reroute && !routing && now - lastReroute > 15_000 && moved) {
            lastReroute = now
            lastRerouteAt = p
            reroute(loc)
        }
    }

    /** ออกนอกเส้นทาง: หาเส้นทางใหม่จากตำแหน่งปัจจุบันไปจุดหมายเดิม */
    private fun reroute(loc: Location) {
        val dest = selected ?: return
        routing = true
        speak("กำลังคำนวณเส้นทางใหม่")
        routeJob = viewModelScope.launch {
            try {
                val r = Router.route(LatLon(loc.latitude, loc.longitude), dest.pos, profile)
                if (!navigating) return@launch
                route = r
                tracker = NavTracker(r)
                alertTracker = newAlertTracker(r)
            } catch (e: OrsException) {
                message = e.message
            } finally {
                routing = false
            }
        }
    }

    fun toggleAlerts() {
        alertsOn = !alertsOn
        prefs.alerts = alertsOn
        val r = route
        alertTracker = if (navigating && r != null) newAlertTracker(r) else null
        if (!alertsOn) nextAlert = null
    }

    fun toggleSpeedWarn() {
        speedOn = !speedOn
        prefs.speedWarn = speedOn
        if (!speedOn) clearSpeed()
    }

    /**
     * เตือนความเร็วเกิน (รถยนต์ ระหว่างนำทาง): ถามเซิร์ฟเวอร์กรมว่าอยู่บนถนนอะไร ทุก 250 ม. หรือทุก 30 วินาที
     * แล้วหาอัตราจำกัด (ป้ายใน OSM > ทางหลวงพิเศษ > เขตเมือง/เทศบาล > นอกเขตเมือง)
     * ถามไม่ได้ใช้ค่าเดิมต่อจนห่างจุดที่รู้ค่าเกิน 1 กม. แล้วซ่อนป้าย
     */
    private fun updateSpeed(loc: Location, p: LatLon, now: Long) {
        val data = speedData
        if (!navigating || !speedOn || profile != Profile.CAR || data == null || !speedSupported) {
            clearSpeed()
            return
        }
        val dist = limitAskedAt?.let { Geo.distance(it, p) } ?: Double.MAX_VALUE
        if (limitJob?.isActive != true && !navFailover.primaryDown &&
            (dist > 250 || (now - limitAskedTime > 30_000 && dist > 30))
        ) {
            limitAskedAt = p
            limitAskedTime = now
            limitJob = viewModelScope.launch {
                runCatching { SpeedLimitResolver.resolve(NavServer.roadAt(p), data.rules, data::sign) }
                    .onSuccess { if (navigating) { speedLimit = it; limitKnownAt = p } }
            }
        }
        if (limitKnownAt?.let { Geo.distance(it, p) > 1000 } == true) {
            speedLimit = null
            limitKnownAt = null
        }
        val v = if (loc.hasSpeed()) (loc.speed * 3.6).roundToInt() else null
        speedKmh = v
        val warn = speedMonitor.update(v?.toDouble() ?: 0.0, speedLimit, data.rules.tolerance, now)
        speeding = v != null && speedMonitor.over
        warn?.let { speak(it, queue = true) }
    }

    private fun clearSpeed() {
        limitJob?.cancel()
        speedLimit = null
        speedKmh = null
        speeding = false
        limitAskedAt = null
        limitKnownAt = null
    }

    private fun newAlertTracker(r: Route): AlertTracker? {
        val points = alertData?.points ?: return null
        return if (alertsOn) AlertTracker(r, points) else null
    }

    private fun speak(text: String, queue: Boolean = false) {
        // บันทึกประโยคลง log ในรุ่น debug ใช้ตรวจบน emulator ที่ไม่มีเสียงไทย (adb logcat -s NavVoice)
        if (BuildConfig.DEBUG) Log.d("NavVoice", text)
        if (voiceOn) voice.say(text, queue)
    }

    override fun onCleared() {
        voice.shutdown()
    }
}

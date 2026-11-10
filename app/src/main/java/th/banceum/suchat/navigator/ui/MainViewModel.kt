package th.banceum.suchat.navigator.ui

import android.app.Application
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import th.banceum.suchat.navigator.data.Prefs
import th.banceum.suchat.navigator.geo.Geo
import th.banceum.suchat.navigator.geo.LatLon
import th.banceum.suchat.navigator.nav.NavState
import th.banceum.suchat.navigator.nav.NavTracker
import th.banceum.suchat.navigator.nav.Voice
import th.banceum.suchat.navigator.route.OrsClient
import th.banceum.suchat.navigator.route.OrsException
import th.banceum.suchat.navigator.route.Place
import th.banceum.suchat.navigator.route.Profile
import th.banceum.suchat.navigator.route.Route

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

    /** ข้อความแจ้งผู้ใช้ครั้งเดียว (แสดงเป็น Snackbar แล้วล้าง) */
    var message by mutableStateOf<String?>(null)

    private var tracker: NavTracker? = null
    private var routeJob: Job? = null
    private var lastReroute = 0L

    val orsReady get() = OrsClient.configured

    // ---------- ค้นหา ----------

    fun search() {
        val text = query.trim()
        if (text.length < 2) return
        searching = true
        viewModelScope.launch {
            try {
                results = OrsClient.search(text, here)
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
            val named = runCatching { OrsClient.reverse(p) }.getOrNull() ?: return@launch
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
                route = OrsClient.route(from, dest.pos, p)
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
        navigating = true
        nav = null
        speak(tracker!!.intro())
        location?.let { onLocation(it) }
    }

    fun stopNav() {
        navigating = false
        nav = null
        tracker = null
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
        val now = System.currentTimeMillis()
        if (u.reroute && !routing && now - lastReroute > 15_000) {
            lastReroute = now
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
                val r = OrsClient.route(LatLon(loc.latitude, loc.longitude), dest.pos, profile)
                if (!navigating) return@launch
                route = r
                tracker = NavTracker(r)
            } catch (e: OrsException) {
                message = e.message
            } finally {
                routing = false
            }
        }
    }

    private fun speak(text: String) {
        if (voiceOn) voice.say(text)
    }

    override fun onCleared() {
        voice.shutdown()
    }
}

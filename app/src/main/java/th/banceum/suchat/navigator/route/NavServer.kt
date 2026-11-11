package th.banceum.suchat.navigator.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import th.banceum.suchat.navigator.BuildConfig
import th.banceum.suchat.navigator.geo.LatLon
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * เซิร์ฟเวอร์ระบบนำทางของกรมทางหลวง (bkdbv2 — OSRM + Nominatim หลัง Nginx, ภายนอกเข้าทาง sal.doh.go.th)
 * ตั้งตอน build ใน local.properties: navBaseUrl=https://sal.doh.go.th/nav, navApiKey=..., navSearch=true (ค้นหาด้วย Nominatim)
 * ไม่ได้ตั้ง = ใช้ OpenRouteService แทน (ดู Router)
 */
object NavServer {
    private val base get() = BuildConfig.NAV_BASE_URL.trimEnd('/')
    private val key get() = BuildConfig.NAV_API_KEY

    val configured get() = base.isNotBlank()

    /** URL ไฟล์จุดเตือนบนเซิร์ฟเวอร์ (null = ไม่ได้ตั้งเซิร์ฟเวอร์) */
    val alertsUrl get() = if (configured) "$base/alerts.geojson" else null

    /** header ที่ต้องแนบทุกคำขอไปเซิร์ฟเวอร์นี้ */
    fun authorize(conn: HttpURLConnection) {
        if (key.isNotBlank()) conn.setRequestProperty("X-Api-Key", key)
    }

    /** ค้นหาสถานที่ด้วย Nominatim ของกรม (เปิดด้วย navSearch=true ตอน build) */
    val searchEnabled get() = configured && BuildConfig.NAV_SEARCH

    suspend fun search(text: String, near: LatLon?): List<Place> {
        val q = buildString {
            append("$base/search?format=jsonv2&addressdetails=1&limit=10&countrycodes=th&accept-language=th&q=")
            append(URLEncoder.encode(text, "UTF-8"))
            // ให้ผลใกล้ตำแหน่งปัจจุบันขึ้นก่อน (ไม่จำกัดเฉพาะในกรอบ)
            if (near != null) append(String.format(Locale.ROOT, "&viewbox=%.4f,%.4f,%.4f,%.4f",
                near.lon - 0.3, near.lat + 0.3, near.lon + 0.3, near.lat - 0.3))
        }
        return NominatimParser.places(get(q, "ค้นหาสถานที่"))
    }

    suspend fun reverse(p: LatLon): Place? = NominatimParser.reverse(get(
        String.format(Locale.ROOT, "%s/reverse?format=jsonv2&addressdetails=1&zoom=18&accept-language=th&lat=%.6f&lon=%.6f", base, p.lat, p.lon),
        "ระบุชื่อสถานที่",
    ))

    private suspend fun get(url: String, what: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            // สั้นกว่า ORS เพื่อให้สลับไปบริการสำรองเร็วเมื่อเครื่องกรมล่ม
            connectTimeout = 5_000
            readTimeout = 15_000
            authorize(this)
        }
        try {
            when (val code = conn.responseCode) {
                in 200..299 -> conn.inputStream.bufferedReader().use { it.readText() }
                401 -> throw ServerUnavailable("กุญแจเชื่อมต่อเซิร์ฟเวอร์นำทางไม่ถูกต้อง")
                429 -> throw ServerUnavailable("มีผู้ใช้งานพร้อมกันมาก ลองใหม่อีกครั้ง")
                in 500..599 -> throw ServerUnavailable("เซิร์ฟเวอร์นำทางขัดข้องชั่วคราว ลองใหม่ภายหลัง")
                else -> throw OrsException("$what ไม่สำเร็จ (รหัส $code)")
            }
        } catch (e: IOException) {
            throw ServerUnavailable("เชื่อมต่อเซิร์ฟเวอร์นำทางไม่ได้")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun route(from: LatLon, to: LatLon, profile: Profile): Route = withContext(Dispatchers.IO) {
        // จักรยานใช้ข้อมูลชุดเดิน (เซิร์ฟเวอร์ประมวลผลไว้แค่รถยนต์ + เดิน เพื่อประหยัด RAM)
        val path = if (profile == Profile.CAR) "driving" else "foot"
        val coords = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", from.lon, from.lat, to.lon, to.lat)
        val url = "$base/route/v1/$path/$coords?overview=false&steps=true&geometries=geojson"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            // สั้นกว่า ORS เพื่อให้สลับไปบริการสำรองเร็วเมื่อเครื่องกรมล่ม
            connectTimeout = 5_000
            readTimeout = 15_000
            authorize(this)
        }
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }
            when {
                code == 401 -> throw ServerUnavailable("กุญแจเชื่อมต่อเซิร์ฟเวอร์นำทางไม่ถูกต้อง")
                code == 429 -> throw ServerUnavailable("มีผู้ใช้งานพร้อมกันมาก ลองใหม่อีกครั้ง")
                code >= 500 || body == null -> throw ServerUnavailable("เซิร์ฟเวอร์นำทางขัดข้องชั่วคราว ลองใหม่ภายหลัง")
                // OSRM ตอบ 400 พร้อม code เช่น NoSegment → OsrmParser แปลงเป็นข้อความไทย
                else -> runCatching { OsrmParser.route(body, profile) }
                    .getOrElse { if (it is OrsException) throw it else throw OrsException("อ่านผลเส้นทางไม่ได้") }
            }
        } catch (e: IOException) {
            throw ServerUnavailable("เชื่อมต่อเซิร์ฟเวอร์นำทางไม่ได้")
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * ใช้เซิร์ฟเวอร์กรมก่อน ถ้าใช้ไม่ได้ (ServerUnavailable) สลับไป OpenRouteService ทันทีในคำขอเดียวกัน
 * แล้วพักเซิร์ฟเวอร์กรมไว้ [cooldownMs] ก่อนลองใหม่ — เครื่องกรมกลับมาเมื่อไรแอปกลับไปใช้เอง
 * หาเส้นทางไม่ได้จริง (เช่น ไม่มีถนนใกล้จุด) ไม่สลับ เพราะเซิร์ฟเวอร์ตอบได้ปกติ
 */
class Failover(private val cooldownMs: Long = 120_000, private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile private var downUntil = 0L

    /** กำลังพักเซิร์ฟเวอร์กรมอยู่ (ใช้บริการสำรอง) */
    val primaryDown get() = clock() < downUntil

    suspend fun <T> run(primary: (suspend () -> T)?, backup: (suspend () -> T)?): T {
        if (primary != null && (backup == null || !primaryDown)) {
            try {
                return primary().also { downUntil = 0 }
            } catch (e: ServerUnavailable) {
                if (backup == null) throw e
                downUntil = clock() + cooldownMs
            }
        }
        return (backup ?: throw OrsException("ยังไม่ได้ตั้งค่าบริการหาเส้นทาง/ค้นหาในแอปรุ่นนี้"))()
    }
}

/** สถานะสลับร่วมกันของทุกบริการบนเครื่องกรม (เครื่องเดียวกัน ล่มพร้อมกัน) */
val navFailover = Failover()

/** ค้นหาสถานที่: Nominatim ของกรม (navSearch) สำรองด้วย OpenRouteService */
object Geocoder {
    val configured get() = NavServer.searchEnabled || OrsClient.configured

    suspend fun search(text: String, near: LatLon?): List<Place> = navFailover.run(
        if (NavServer.searchEnabled) ({ NavServer.search(text, near) }) else null,
        if (OrsClient.configured) ({ OrsClient.search(text, near) }) else null,
    )

    suspend fun reverse(p: LatLon): Place? = navFailover.run(
        if (NavServer.searchEnabled) ({ NavServer.reverse(p) }) else null,
        if (OrsClient.configured) ({ OrsClient.reverse(p) }) else null,
    )
}

/** หาเส้นทาง: OSRM ของกรม สำรองด้วย OpenRouteService */
object Router {
    val configured get() = NavServer.configured || OrsClient.configured

    suspend fun route(from: LatLon, to: LatLon, profile: Profile): Route = navFailover.run(
        if (NavServer.configured) ({ NavServer.route(from, to, profile) }) else null,
        if (OrsClient.configured) ({ OrsClient.route(from, to, profile) }) else null,
    )
}

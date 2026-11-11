package th.banceum.suchat.navigator.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import th.banceum.suchat.navigator.BuildConfig
import th.banceum.suchat.navigator.geo.LatLon
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * เซิร์ฟเวอร์ระบบนำทางของหน่วยงาน (backup.doh.go.th — OSRM หลัง Nginx)
 * ตั้งตอน build ใน local.properties: navBaseUrl=https://backup.doh.go.th/nav และ navApiKey=...
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

    suspend fun route(from: LatLon, to: LatLon, profile: Profile): Route = withContext(Dispatchers.IO) {
        // จักรยานใช้ข้อมูลชุดเดิน (เซิร์ฟเวอร์ประมวลผลไว้แค่รถยนต์ + เดิน เพื่อประหยัด RAM)
        val path = if (profile == Profile.CAR) "driving" else "foot"
        val coords = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", from.lon, from.lat, to.lon, to.lat)
        val url = "$base/route/v1/$path/$coords?overview=false&steps=true&geometries=geojson"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            authorize(this)
        }
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }
            when {
                code == 401 -> throw OrsException("กุญแจเชื่อมต่อเซิร์ฟเวอร์นำทางไม่ถูกต้อง")
                code == 429 -> throw OrsException("มีผู้ใช้งานพร้อมกันมาก ลองใหม่อีกครั้ง")
                code >= 500 || body == null -> throw OrsException("เซิร์ฟเวอร์นำทางขัดข้องชั่วคราว ลองใหม่ภายหลัง")
                // OSRM ตอบ 400 พร้อม code เช่น NoSegment → OsrmParser แปลงเป็นข้อความไทย
                else -> runCatching { OsrmParser.route(body, profile) }
                    .getOrElse { if (it is OrsException) throw it else throw OrsException("อ่านผลเส้นทางไม่ได้") }
            }
        } catch (e: IOException) {
            throw OrsException("เชื่อมต่อเซิร์ฟเวอร์นำทางไม่ได้")
        } finally {
            conn.disconnect()
        }
    }
}

/** เลือกบริการหาเส้นทาง: เซิร์ฟเวอร์หน่วยงานถ้าตั้งไว้ ไม่งั้น OpenRouteService */
object Router {
    val configured get() = NavServer.configured || OrsClient.configured

    suspend fun route(from: LatLon, to: LatLon, profile: Profile): Route =
        if (NavServer.configured) NavServer.route(from, to, profile) else OrsClient.route(from, to, profile)
}

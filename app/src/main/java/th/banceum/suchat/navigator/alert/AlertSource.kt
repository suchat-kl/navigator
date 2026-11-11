package th.banceum.suchat.navigator.alert

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import th.banceum.suchat.navigator.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** ชุดจุดเตือนที่โหลดแล้ว: [json] ใช้วาดบนแผนที่, [points] ใช้คำนวณการเตือน */
class AlertData(val points: List<AlertPoint>, val json: String)

/**
 * ที่มาของจุดเตือน
 * 1. ถ้าตั้ง alertsUrl ตอน build (BuildConfig.ALERTS_URL) ดาวน์โหลดฉบับล่าสุดเก็บในเครื่องทุกครั้งที่เปิดแอป
 *    — สำหรับข้อมูลของหน่วยงานในอนาคต (PostGIS/API) ใช้รูปแบบ GeoJSON เดียวกัน
 * 2. ดาวน์โหลดไม่ได้ ใช้ฉบับที่เก็บไว้ครั้งก่อน
 * 3. ไม่มีเลย ใช้ข้อมูลตั้งต้นจาก OpenStreetMap ที่มากับแอป (assets/alerts.geojson, สร้างด้วย docs/tools/osm_alerts.py)
 */
object AlertSource {
    private const val ASSET = "alerts.geojson"
    private const val CACHE = "alerts.geojson"

    suspend fun load(context: Context): AlertData = withContext(Dispatchers.IO) {
        val cache = File(context.filesDir, CACHE)
        val url = BuildConfig.ALERTS_URL
        if (url.isNotBlank()) {
            runCatching { download(url) }
                .getOrNull()
                ?.takeIf { runCatching { AlertParser.parse(it) }.getOrDefault(emptyList()).isNotEmpty() }
                ?.let { cache.writeText(it) }
        }
        val candidates = sequence {
            if (url.isNotBlank() && cache.exists()) yield(runCatching { cache.readText() }.getOrNull())
            yield(context.assets.open(ASSET).bufferedReader().use { it.readText() })
        }
        for (json in candidates) {
            if (json == null) continue
            val points = runCatching { AlertParser.parse(json) }.getOrNull() ?: continue
            if (points.isNotEmpty()) return@withContext AlertData(points, json)
        }
        AlertData(emptyList(), """{"type":"FeatureCollection","features":[]}""")
    }

    private fun download(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
        }
        try {
            check(conn.responseCode in 200..299)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

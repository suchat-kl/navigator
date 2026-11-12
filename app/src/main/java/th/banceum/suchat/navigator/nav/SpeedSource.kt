package th.banceum.suchat.navigator.nav

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import th.banceum.suchat.navigator.route.NavServer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** ข้อมูลสำหรับเตือนความเร็ว: กติกาตามพื้นที่ + ป้ายจำกัดความเร็วรายถนนจาก OSM (osm_id → กม./ชม.) */
class SpeedData(val rules: SpeedRules, private val signs: Map<Long, Int>) {
    fun sign(wayId: Long): Int? = signs[wayId]
    val signCount get() = signs.size
}

/**
 * โหลดจากเซิร์ฟเวอร์กรมทุกครั้งที่เปิดแอป (/nav/map/speed-rules.json, /nav/map/maxspeed.json) เก็บสำเนาในเครื่อง
 * โหลดไม่ได้ใช้สำเนาครั้งก่อน ไม่มีเลยใช้กติกาที่มากับแอป (assets/speed-rules.json) โดยไม่มีป้ายรายถนน
 */
object SpeedSource {
    private const val RULES = "speed-rules.json"
    private const val SIGNS = "maxspeed.json"

    suspend fun load(context: Context): SpeedData = withContext(Dispatchers.IO) {
        val base = NavServer.mapBaseUrl
        fun fetch(name: String, valid: (String) -> Boolean): String? {
            val cache = File(context.filesDir, name)
            if (base != null) {
                runCatching { download("$base/$name") }.getOrNull()?.takeIf(valid)?.let { cache.writeText(it) }
            }
            return if (cache.exists()) runCatching { cache.readText() }.getOrNull()?.takeIf(valid) else null
        }

        val rulesJson = fetch(RULES) { runCatching { SpeedRules.parse(it) }.isSuccess }
            ?: context.assets.open(RULES).bufferedReader().use { it.readText() }
        val rules = runCatching { SpeedRules.parse(rulesJson) }.getOrDefault(SpeedRules())
        val signs = fetch(SIGNS) { runCatching { JSONObject(it).getJSONObject("ways") }.isSuccess }
            ?.let { runCatching { parseSigns(it, rules) }.getOrNull() }
            .orEmpty()
        SpeedData(rules, signs)
    }

    /** {"ways": {"80": [id, ...], "TH:urban": [...]}} → osm_id → กม./ชม. */
    fun parseSigns(json: String, rules: SpeedRules): Map<Long, Int> {
        val ways = JSONObject(json).getJSONObject("ways")
        val out = HashMap<Long, Int>(40_000)
        for (key in ways.keys()) {
            val kmh = SpeedLimitResolver.signValue(key, rules) ?: continue
            val ids = ways.getJSONArray(key)
            for (i in 0 until ids.length()) out[ids.getLong(i)] = kmh
        }
        return out
    }

    private fun download(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
        }
        try {
            check(conn.responseCode in 200..299)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

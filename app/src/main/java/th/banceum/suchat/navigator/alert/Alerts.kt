package th.banceum.suchat.navigator.alert

import org.json.JSONObject
import th.banceum.suchat.navigator.geo.LatLon

/**
 * ชนิดจุดเตือน (ค่า kind ใน GeoJSON)
 * @param maxOffset ห่างแนวเส้นทางได้ไม่เกินกี่เมตรจึงนับว่าอยู่บนเส้นทาง
 * @param leftOnly ต้องอยู่ทางซ้ายของทิศทางที่ขับ (ไทยขับชิดซ้าย) — ด่านชั่งอยู่ในช่องแยกข้างถนนแยกตามทิศทาง
 *   วัดจากเส้นทางจริง: ฝั่งเดียวกันอยู่ซ้าย 5–29 ม., ฝั่งตรงข้ามอยู่ขวา 37–90 ม.
 */
enum class AlertKind(val code: String, val label: String, val maxOffset: Double, val leftOnly: Boolean) {
    WEIGHBRIDGE("weighbridge", "ด่านชั่งน้ำหนัก", 40.0, true),
    // ด่านเก็บเงินอยู่บนช่องทางจราจร (จุดต่อช่อง)
    TOLL_BOOTH("toll_booth", "ด่านเก็บเงิน", 25.0, false),
    SPEED_CAMERA("speed_camera", "กล้องตรวจจับความเร็ว", 30.0, false);

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code }
    }
}

/** จุดเตือนหนึ่งจุด (รัศมี/จำกัดความเร็ว ไม่บังคับ) */
data class AlertPoint(
    val kind: AlertKind,
    val name: String?,
    val pos: LatLon,
    val maxspeed: Int? = null,
)

/**
 * อ่านจุดเตือนจาก GeoJSON FeatureCollection ของจุด (Point)
 * properties: kind (บังคับ), name, maxspeed — ชนิดที่แอปไม่รู้จักจะข้ามไป
 * รูปแบบเดียวกันใช้ได้ทั้งข้อมูลตั้งต้นจาก OSM (assets/alerts.geojson) และข้อมูลของหน่วยงานในอนาคต
 */
object AlertParser {
    fun parse(json: String): List<AlertPoint> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        val out = ArrayList<AlertPoint>(features.length())
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val g = f.optJSONObject("geometry") ?: continue
            if (g.optString("type") != "Point") continue
            val c = g.optJSONArray("coordinates") ?: continue
            val p = f.optJSONObject("properties") ?: continue
            val kind = AlertKind.of(p.optString("kind")) ?: continue
            out += AlertPoint(
                kind = kind,
                name = p.optString("name").takeIf { it.isNotBlank() },
                pos = LatLon(c.getDouble(1), c.getDouble(0)),
                maxspeed = if (p.has("maxspeed")) p.optInt("maxspeed").takeIf { it > 0 } else null,
            )
        }
        return out
    }
}

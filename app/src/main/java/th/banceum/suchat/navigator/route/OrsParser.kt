package th.banceum.suchat.navigator.route

import org.json.JSONArray
import org.json.JSONObject
import th.banceum.suchat.navigator.geo.LatLon

/** อ่านผลตอบกลับของ OpenRouteService (แยกจากส่วนเรียกเครือข่ายเพื่อให้ทดสอบได้) */
object OrsParser {

    /** ผลจาก POST /v2/directions/{profile}/geojson */
    fun route(json: String, profile: Profile): Route {
        val feature = JSONObject(json).getJSONArray("features").getJSONObject(0)
        val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
        val points = List(coords.length()) { i -> coords.getJSONArray(i).toLatLon() }
        val props = feature.getJSONObject("properties")
        val summary = props.optJSONObject("summary") ?: JSONObject()
        val steps = mutableListOf<Step>()
        val segments = props.optJSONArray("segments") ?: JSONArray()
        for (s in 0 until segments.length()) {
            val arr = segments.getJSONObject(s).optJSONArray("steps") ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val wp = o.getJSONArray("way_points")
                steps += Step(
                    type = o.optInt("type", -1),
                    name = o.optString("name", ""),
                    distance = o.optDouble("distance", 0.0),
                    duration = o.optDouble("duration", 0.0),
                    start = wp.getInt(0),
                    end = wp.getInt(1),
                    exit = if (o.has("exit_number")) o.getInt("exit_number") else null,
                )
            }
        }
        return Route(
            profile = profile,
            points = points,
            distance = summary.optDouble("distance", 0.0),
            duration = summary.optDouble("duration", 0.0),
            steps = steps,
        )
    }

    /** ผลจาก GET /geocode/search หรือ /geocode/reverse */
    fun places(json: String): List<Place> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        return List(features.length()) { i ->
            val f = features.getJSONObject(i)
            val p = f.optJSONObject("properties") ?: JSONObject()
            val name = p.optString("name").ifBlank { p.optString("label") }
            val label = p.optString("label")
            Place(
                name = name,
                detail = detailOf(label, name),
                pos = f.getJSONObject("geometry").getJSONArray("coordinates").toLatLon(),
            )
        }
    }

    /** ข้อความภาษาไทยจากรหัส HTTP และเนื้อหาข้อผิดพลาดของ ORS */
    fun error(httpCode: Int, body: String?): String {
        val code = runCatching { JSONObject(body ?: "").optJSONObject("error")?.optInt("code") }.getOrNull()
        return when {
            httpCode == 401 || httpCode == 403 -> "กุญแจ OpenRouteService ไม่ถูกต้องหรือถูกระงับ"
            httpCode == 429 -> "ใช้บริการหาเส้นทางเกินโควตาแล้ว ลองใหม่ภายหลัง"
            code == 2010 -> "ไม่พบถนนใกล้จุดเริ่มต้นหรือจุดหมาย ลองเลือกจุดที่อยู่ใกล้ถนน"
            code == 2004 -> "ระยะทางไกลเกินกว่าที่บริการหาเส้นทางรองรับ"
            code == 2009 -> "หาเส้นทางระหว่างสองจุดนี้ไม่ได้"
            httpCode >= 500 -> "บริการหาเส้นทางขัดข้องชั่วคราว ลองใหม่ภายหลัง"
            else -> "หาเส้นทางไม่สำเร็จ (รหัส $httpCode${code?.let { "/$it" } ?: ""})"
        }
    }

    /**
     * รายละเอียดใต้ชื่อสถานที่: ตัดชื่อซ้ำหน้า label, ชื่อประเทศ และรหัสจังหวัดย่อ 2 ตัว (เช่น BM)
     * "เซ็นทรัลชิดลม, ปทุมวัน, BM, ประเทศไทย" → "ปทุมวัน"
     */
    fun detailOf(label: String, name: String): String = label.split(",")
        .map { it.trim() }
        .filterIndexed { i, s -> !(i == 0 && s == name) }
        .filter { it.isNotEmpty() && it != "ประเทศไทย" && it != "Thailand" && !Regex("[A-Z]{2}").matches(it) }
        .joinToString(", ")

    /** ORS เรียงพิกัดเป็น [ลองจิจูด, ละติจูด] */
    private fun JSONArray.toLatLon() = LatLon(lat = getDouble(1), lon = getDouble(0))
}

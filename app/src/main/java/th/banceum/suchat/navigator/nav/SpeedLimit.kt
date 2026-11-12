package th.banceum.suchat.navigator.nav

import org.json.JSONObject

/**
 * กติกาความเร็วตามกฎหมายตามพื้นที่ — โหลดจากเซิร์ฟเวอร์กรม (/nav/map/speed-rules.json) แก้ได้โดยไม่ต้องออกแอปใหม่
 * ค่าในแอป (assets/speed-rules.json) เป็นค่าตั้งต้น — ต้องให้กรมยืนยันกับกฎกระทรวงฉบับล่าสุด
 */
data class SpeedRules(
    /** นอกเขตเมือง (กม./ชม.) */
    val rural: Int = 90,
    /** ในเขตกรุงเทพฯ เมืองพัทยา เขตเทศบาล */
    val urban: Int = 80,
    /** ทางหลวงพิเศษ (highway=motorway) */
    val motorway: Int = 120,
    /** ชื่อพื้นที่ที่นับเป็นเขตเมือง (ตรงทั้งชื่อ) */
    val urbanNames: List<String> = listOf("กรุงเทพมหานคร", "เมืองพัทยา"),
    /** คำขึ้นต้นชื่อพื้นที่ที่นับเป็นเขตเมือง */
    val urbanPrefixes: List<String> = listOf("เทศบาล"),
    /**
     * ช่องใน address ของ Nominatim ที่ถ้ามีค่า = อยู่ในเมือง (place=city/town ใน OSM)
     * ขอบเขตเทศบาลใน OSM ไม่ครบ หลายแห่งมีแค่ชื่อเมือง เช่น town = "แก่งคอย" (เทศบาลเมืองแก่งคอย)
     */
    val urbanPlaceKeys: List<String> = listOf("city", "town"),
    /**
     * ค่าใน [urbanPlaceKeys] ที่ขึ้นต้นด้วยคำเหล่านี้ไม่นับเป็นเมือง — Nominatim ใส่ชื่อตำบลไว้ในช่อง city บ่อย
     * เช่น ถนนมิตรภาพช่วงสีคิ้ว-สูงเนิน city = "ตำบลมะเกลือใหม่" ซึ่งเป็นนอกเขตเมือง (เทศบาลตำบลจะขึ้นต้นว่า "เทศบาล")
     */
    val notUrbanPrefixes: List<String> = listOf("ตำบล"),
    /** เกินได้กี่ กม./ชม. ก่อนเตือน (ความคลาดเคลื่อนของ GPS) */
    val tolerance: Int = 5,
) {
    companion object {
        fun parse(json: String): SpeedRules {
            val o = JSONObject(json)
            val d = SpeedRules()
            fun list(key: String, def: List<String>) = o.optJSONArray(key)?.let { a -> List(a.length()) { a.getString(it) } } ?: def
            return SpeedRules(
                rural = o.optInt("rural", d.rural),
                urban = o.optInt("urban", d.urban),
                motorway = o.optInt("motorway", d.motorway),
                urbanNames = list("urbanNames", d.urbanNames),
                urbanPrefixes = list("urbanPrefixes", d.urbanPrefixes),
                urbanPlaceKeys = list("urbanPlaceKeys", d.urbanPlaceKeys),
                notUrbanPrefixes = list("notUrbanPrefixes", d.notUrbanPrefixes),
                tolerance = o.optInt("tolerance", d.tolerance),
            )
        }
    }
}

/** อัตราจำกัดความเร็วของถนน ณ จุดหนึ่ง และที่มา (ป้ายใน OSM / กฎหมายตามพื้นที่) */
data class SpeedLimit(val kmh: Int, val fromSign: Boolean, val reason: String)

object SpeedLimitResolver {
    /**
     * จากผล Nominatim /reverse (jsonv2, zoom=17, addressdetails=1) ของถนนที่อยู่ และป้ายจำกัดความเร็วจาก OSM
     * ([signs] = osm_id ของถนน → กม./ชม. จาก maxspeed.json — Nominatim ไม่เก็บแท็ก maxspeed ไว้เอง)
     * 1. ถนนเส้นนั้นมีป้ายจำกัดความเร็วใน OSM ใช้ก่อนเสมอ
     * 2. ทางหลวงพิเศษ (motorway) → rules.motorway
     * 3. ในกรุงเทพฯ / เมืองพัทยา / เขตเทศบาล / ในเมือง (city, town ที่ไม่ใช่ชื่อตำบล) → rules.urban
     * 4. นอกนั้น → rules.rural
     * จุดที่ไม่ใช่ถนน → null ไม่เตือน
     */
    fun resolve(reverseJson: String, rules: SpeedRules, signs: (Long) -> Int?): SpeedLimit? {
        val o = JSONObject(reverseJson)
        if (o.has("error") || o.optString("category") != "highway") return null
        val type = o.optString("type")
        if (type in NOT_FOR_CARS) return null
        if (o.optString("osm_type") == "way") {
            signs(o.optLong("osm_id"))?.let { return SpeedLimit(it, true, "ป้ายจำกัดความเร็ว") }
        }
        if (type == "motorway" || type == "motorway_link") return SpeedLimit(rules.motorway, false, "ทางหลวงพิเศษ")
        val address = o.optJSONObject("address")
        val areas = AREA_KEYS.mapNotNull { k -> address?.optString(k)?.takeIf { it.isNotBlank() } }
        val urban = rules.urbanPlaceKeys.any { k ->
            val v = address?.optString(k).orEmpty()
            v.isNotBlank() && rules.notUrbanPrefixes.none { v.startsWith(it) }
        } ||
            areas.any { a -> a in rules.urbanNames || rules.urbanPrefixes.any { a.startsWith(it) } }
        return if (urban) SpeedLimit(rules.urban, false, "เขตเมือง/เทศบาล") else SpeedLimit(rules.rural, false, "นอกเขตเมือง")
    }

    /** ชื่อเขตปกครองใน address ของ Nominatim (ไม่ดู road/neighbourhood เพราะมีซอยชื่อ "เทศบาล 1" ฯลฯ) */
    private val AREA_KEYS = listOf("city", "town", "municipality", "county", "city_district", "suburb")

    /** ทางที่รถยนต์ไม่ได้ใช้ (ถ้า reverse ไปจับเจอ ไม่ควรเตือนตามค่านี้) */
    private val NOT_FOR_CARS = setOf("footway", "path", "cycleway", "steps", "pedestrian", "bridleway", "construction", "platform")

    /** ค่าใน maxspeed.json: "80" หรือ "TH:urban" / "TH:rural" / "TH:motorway" */
    fun signValue(key: String, rules: SpeedRules): Int? = when (key.lowercase()) {
        "th:urban" -> rules.urban
        "th:rural" -> rules.rural
        "th:motorway" -> rules.motorway
        else -> key.toIntOrNull()?.takeIf { it in 10..200 }
    }
}

/**
 * ตัดสินใจเตือนเมื่อขับเร็วเกิน: ต้องเกิน (จำกัด + tolerance) ติดกัน [needFixes] ครั้ง (กัน GPS กระโดด)
 * เตือนซ้ำได้หลัง [cooldownMs] หรือเมื่ออัตราจำกัดเปลี่ยน
 */
class SpeedMonitor(private val needFixes: Int = 3, private val cooldownMs: Long = 60_000) {
    private var overCount = 0
    private var lastWarnAt = Long.MIN_VALUE / 2
    private var lastWarnLimit = -1

    /** true = เกินกำหนดอยู่ตอนนี้ (ใช้ทำป้ายสีแดง) */
    var over = false
        private set

    /** คืนประโยคที่ควรพูด หรือ null */
    fun update(speedKmh: Double, limit: SpeedLimit?, tolerance: Int, nowMs: Long): String? {
        if (limit == null) { overCount = 0; over = false; return null }
        over = speedKmh > limit.kmh + tolerance
        overCount = if (over) overCount + 1 else 0
        if (overCount < needFixes) return null
        if (nowMs - lastWarnAt < cooldownMs && limit.kmh == lastWarnLimit) return null
        lastWarnAt = nowMs
        lastWarnLimit = limit.kmh
        return "ความเร็วเกินกำหนด จำกัด ${limit.kmh} กิโลเมตรต่อชั่วโมง"
    }
}

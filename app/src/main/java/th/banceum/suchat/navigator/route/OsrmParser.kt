package th.banceum.suchat.navigator.route

import org.json.JSONObject
import th.banceum.suchat.navigator.geo.LatLon

/**
 * อ่านผล OSRM /route/v1 (geometries=geojson, steps=true) เป็น Route รูปแบบเดียวกับ ORS
 * แปลงชนิดการเลี้ยวของ OSRM (maneuver.type + modifier) เป็นรหัสของ ORS ที่ ThaiInstructions/NavTracker ใช้อยู่
 */
object OsrmParser {

    fun route(json: String, profile: Profile): Route {
        val root = JSONObject(json)
        val code = root.optString("code")
        if (code != "Ok") throw OrsException(error(code))
        val r = root.getJSONArray("routes").getJSONObject(0)
        val points = ArrayList<LatLon>()
        val steps = ArrayList<Step>()
        val legs = r.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val arr = legs.getJSONObject(l).getJSONArray("steps")
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val coords = s.getJSONObject("geometry").getJSONArray("coordinates")
                // ต่อเรขาคณิตของแต่ละช่วงเอง (จุดแรกของช่วงถัดไปซ้ำกับจุดสุดท้ายของช่วงก่อน)
                val start = if (points.isEmpty()) 0 else points.size - 1
                for (k in 0 until coords.length()) {
                    if (k == 0 && points.isNotEmpty()) continue
                    val c = coords.getJSONArray(k)
                    points += LatLon(c.getDouble(1), c.getDouble(0))
                }
                val m = s.getJSONObject("maneuver")
                val type = m.optString("type")
                steps += Step(
                    type = typeOf(type, m.optString("modifier").takeIf { it.isNotEmpty() }),
                    name = nameOf(s.optString("name"), s.optString("ref")),
                    distance = s.optDouble("distance", 0.0),
                    duration = s.optDouble("duration", 0.0),
                    start = start,
                    end = points.size - 1,
                    exit = if (m.has("exit") && (type.contains("round") || type == "rotary")) m.optInt("exit").takeIf { it > 0 } else null,
                )
            }
        }
        return Route(profile, points, r.optDouble("distance", 0.0), r.optDouble("duration", 0.0), steps)
    }

    /** ชื่อ + หมายเลขทางหลวงในรูปแบบเดียวกับ ORS ("ชื่อ, 12") ให้ ThaiInstructions.roadName จัดการต่อ */
    fun nameOf(name: String, ref: String): String {
        val r = ref.split(";").firstOrNull()?.trim().orEmpty()
        return listOf(name.trim(), r).filter { it.isNotEmpty() }.joinToString(", ")
    }

    fun typeOf(type: String, modifier: String?): Int = when (type) {
        "depart" -> ThaiInstructions.DEPART
        "arrive" -> ThaiInstructions.GOAL
        "roundabout", "rotary", "roundabout turn" -> ThaiInstructions.ENTER_ROUNDABOUT
        "exit roundabout", "exit rotary" -> ThaiInstructions.EXIT_ROUNDABOUT
        "fork" -> when (modifier) {
            "slight left", "left", "sharp left" -> ThaiInstructions.KEEP_LEFT
            "slight right", "right", "sharp right" -> ThaiInstructions.KEEP_RIGHT
            else -> ThaiInstructions.STRAIGHT
        }
        else -> when (modifier) {
            "uturn" -> ThaiInstructions.U_TURN
            "sharp left" -> ThaiInstructions.SHARP_LEFT
            "left" -> ThaiInstructions.LEFT
            "slight left" -> ThaiInstructions.SLIGHT_LEFT
            "slight right" -> ThaiInstructions.SLIGHT_RIGHT
            "right" -> ThaiInstructions.RIGHT
            "sharp right" -> ThaiInstructions.SHARP_RIGHT
            else -> ThaiInstructions.STRAIGHT
        }
    }

    fun error(code: String): String = when (code) {
        "NoSegment" -> "ไม่พบถนนใกล้จุดเริ่มต้นหรือจุดหมาย ลองเลือกจุดที่อยู่ใกล้ถนน"
        "NoRoute" -> "หาเส้นทางระหว่างสองจุดนี้ไม่ได้"
        "TooBig" -> "ระยะทางไกลเกินกว่าที่บริการหาเส้นทางรองรับ"
        else -> "หาเส้นทางไม่สำเร็จ ($code)"
    }
}

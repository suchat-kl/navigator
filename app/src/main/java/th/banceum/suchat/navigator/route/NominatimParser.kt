package th.banceum.suchat.navigator.route

import org.json.JSONArray
import org.json.JSONObject
import th.banceum.suchat.navigator.geo.Geo
import th.banceum.suchat.navigator.geo.LatLon

/** อ่านผล Nominatim (format=jsonv2, addressdetails=1) เป็น Place รูปแบบเดียวกับ ORS */
object NominatimParser {

    /** ผล /search: อาร์เรย์ของสถานที่ — ชื่อเดียวกันห่างกันไม่เกิน 300 ม. (เช่น อาคารหลายหลังในศูนย์ราชการ) เหลือรายการเดียว */
    fun places(json: String): List<Place> {
        val arr = JSONArray(json)
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val p = place(arr.getJSONObject(i)) ?: continue
            if (out.none { it.name == p.name && Geo.distance(it.pos, p.pos) < 300 }) out += p
        }
        return out
    }

    /** ผล /reverse: วัตถุเดียว หรือ {"error": ...} เมื่อไม่พบ */
    fun reverse(json: String): Place? {
        val o = JSONObject(json)
        return if (o.has("error")) null else place(o)
    }

    private fun place(o: JSONObject): Place? {
        val lat = o.optString("lat").toDoubleOrNull() ?: return null
        val lon = o.optString("lon").toDoubleOrNull() ?: return null
        val display = o.optString("display_name")
        val name = o.optString("name").ifBlank { display.substringBefore(",").trim() }
        val address = o.optJSONObject("address")
        val detail = address?.let { detailOf(it, name) }?.takeIf { it.isNotEmpty() } ?: detailOf(display, name)
        return Place(name = name, detail = detail, pos = LatLon(lat, lon))
    }

    /**
     * รายละเอียดจากที่อยู่แยกส่วน (addressdetails=1): ถนน, แขวง/ตำบล, เขต/อำเภอ, จังหวัด — ไม่เกิน 3 ระดับ
     * ไม่ใช้บ้านเลขที่ ชื่อชุมชน รหัสไปรษณีย์ และประเทศ
     */
    fun detailOf(address: JSONObject, name: String): String {
        fun pick(vararg keys: String) = keys.firstNotNullOfOrNull { k -> address.optString(k).takeIf { it.isNotBlank() } }
        return listOfNotNull(
            pick("road"),
            pick("quarter", "city_district", "village", "hamlet"),
            pick("suburb", "county", "municipality", "town"),
            pick("province", "city", "state"),   // ต่างจังหวัดใช้ชื่อจังหวัด กรุงเทพฯ ไม่มี province ใช้ city
        ).distinct().filter { it != name }.take(3).joinToString(", ")
    }

    /** สำรองเมื่อไม่มีที่อยู่แยกส่วน: ตัดชื่อซ้ำ ประเทศ รหัสไปรษณีย์ เหลือไม่เกิน 3 ระดับ */
    fun detailOf(display: String, name: String): String = display.split(",")
        .map { it.trim() }
        .filterIndexed { i, s -> !(i == 0 && s == name) }
        .filter { it.isNotEmpty() && it != "ประเทศไทย" && it != "Thailand" && !Regex("[0-9]{5}").matches(it) }
        .take(3)
        .joinToString(", ")
}

package th.banceum.suchat.navigator.route

import org.json.JSONArray
import org.json.JSONObject
import th.banceum.suchat.navigator.geo.LatLon

/** อ่านผล Nominatim (format=jsonv2) เป็น Place รูปแบบเดียวกับ ORS */
object NominatimParser {

    /** ผล /search: อาร์เรย์ของสถานที่ */
    fun places(json: String): List<Place> {
        val arr = JSONArray(json)
        return List(arr.length()) { i -> place(arr.getJSONObject(i)) }.filterNotNull()
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
        return Place(name = name, detail = detailOf(display, name), pos = LatLon(lat, lon))
    }

    /**
     * รายละเอียดใต้ชื่อ: ตัดชื่อซ้ำ ชื่อประเทศ และรหัสไปรษณีย์ออก เหลือไม่เกิน 3 ระดับแรก
     * "เซ็นทรัลชิดลม, ถนนเพลินจิต, ลุมพินี, เขตปทุมวัน, กรุงเทพมหานคร, 10330, ประเทศไทย"
     *   → "ถนนเพลินจิต, ลุมพินี, เขตปทุมวัน"
     */
    fun detailOf(display: String, name: String): String = display.split(",")
        .map { it.trim() }
        .filterIndexed { i, s -> !(i == 0 && s == name) }
        .filter { it.isNotEmpty() && it != "ประเทศไทย" && it != "Thailand" && !Regex("[0-9]{5}").matches(it) }
        .take(3)
        .joinToString(", ")
}

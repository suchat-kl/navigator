package th.banceum.suchat.navigator.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import th.banceum.suchat.navigator.geo.LatLon
import th.banceum.suchat.navigator.route.Place
import th.banceum.suchat.navigator.route.Profile

/**
 * ข้อมูลที่เก็บในเครื่องอย่างเดียว (SharedPreferences): สถานที่ที่บันทึก, การตั้งค่า, ตำแหน่งแผนที่ล่าสุด
 * ไม่มีบัญชีผู้ใช้และไม่ส่งข้อมูลนี้ออกจากเครื่อง
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("navigator", Context.MODE_PRIVATE)

    var voice: Boolean
        get() = sp.getBoolean("voice", true)
        set(v) = sp.edit().putBoolean("voice", v).apply()

    /** เตือนจุดสำคัญระหว่างนำทาง (ด่านชั่ง ด่านเก็บเงิน กล้องตรวจจับความเร็ว) */
    var alerts: Boolean
        get() = sp.getBoolean("alerts", true)
        set(v) = sp.edit().putBoolean("alerts", v).apply()

    var profile: Profile
        get() = runCatching { Profile.valueOf(sp.getString("profile", null) ?: "") }.getOrDefault(Profile.CAR)
        set(v) = sp.edit().putString("profile", v.name).apply()

    /** จุดกลางแผนที่ล่าสุด ใช้เปิดแผนที่ครั้งถัดไปก่อนได้ตำแหน่ง GPS (ค่าเริ่มต้น = กรุงเทพฯ) */
    var lastCenter: LatLon
        get() = LatLon(
            sp.getFloat("lat", 13.7563f).toDouble(),
            sp.getFloat("lon", 100.5018f).toDouble(),
        )
        set(v) = sp.edit().putFloat("lat", v.lat.toFloat()).putFloat("lon", v.lon.toFloat()).apply()

    var saved: List<Place>
        get() = decode(sp.getString("saved", null))
        set(v) = sp.edit().putString("saved", encode(v)).apply()

    /** ล้างทุกอย่างที่แอปเก็บไว้ในเครื่อง */
    fun clearAll() = sp.edit().clear().apply()

    companion object {
        fun encode(list: List<Place>): String = JSONArray().apply {
            list.forEach {
                put(JSONObject().put("name", it.name).put("detail", it.detail).put("lat", it.pos.lat).put("lon", it.pos.lon))
            }
        }.toString()

        fun decode(json: String?): List<Place> = runCatching {
            val arr = JSONArray(json ?: return emptyList())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Place(o.getString("name"), o.optString("detail"), LatLon(o.getDouble("lat"), o.getDouble("lon")))
            }
        }.getOrDefault(emptyList())
    }
}

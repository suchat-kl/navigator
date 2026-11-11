package th.banceum.suchat.navigator.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import th.banceum.suchat.navigator.BuildConfig
import th.banceum.suchat.navigator.geo.LatLon
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** ข้อผิดพลาดที่แสดงให้ผู้ใช้เห็นได้ตรง ๆ */
class OrsException(message: String) : Exception(message)

/**
 * เรียก OpenRouteService: หาเส้นทาง (directions) และค้นหาสถานที่ (geocode)
 * กุญแจมาจาก BuildConfig.ORS_API_KEY (ตั้งใน local.properties) — ดู docs/play/ตั้งค่า OpenRouteService.md
 * ส่งเฉพาะพิกัดต้นทาง/ปลายทางและคำค้น ไม่ส่งข้อมูลระบุตัวผู้ใช้
 */
object OrsClient {
    private const val BASE = "https://api.openrouteservice.org"
    private val key get() = BuildConfig.ORS_API_KEY

    val configured get() = key.isNotBlank()

    suspend fun route(from: LatLon, to: LatLon, profile: Profile): Route {
        val body = JSONObject()
            .put("coordinates", JSONArray().put(from.toJson()).put(to.toJson()))
            .put("instructions", true)
            .put("units", "m")
        val json = request("$BASE/v2/directions/${profile.ors}/geojson", body.toString())
        return runCatching { OrsParser.route(json, profile) }
            .getOrElse { throw OrsException("อ่านผลเส้นทางไม่ได้") }
    }

    /** ค้นหาสถานที่ในประเทศไทย เรียงจากใกล้ [near] ก่อนถ้ามี */
    suspend fun search(text: String, near: LatLon?): List<Place> {
        val q = buildString {
            append("$BASE/geocode/search?text=").append(enc(text))
            append("&size=10&boundary.country=TH")
            if (near != null) append("&focus.point.lat=${near.lat}&focus.point.lon=${near.lon}")
        }
        return OrsParser.places(request(q, null))
    }

    /** ชื่อสถานที่ ณ จุดที่แตะค้างบนแผนที่ */
    suspend fun reverse(p: LatLon): Place? {
        val q = "$BASE/geocode/reverse?point.lat=${p.lat}&point.lon=${p.lon}&size=1"
        return OrsParser.places(request(q, null)).firstOrNull()
    }

    private suspend fun request(url: String, postBody: String?): String = withContext(Dispatchers.IO) {
        if (!configured) throw OrsException("ยังไม่ได้ใส่กุญแจ OpenRouteService ในแอปรุ่นนี้")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", key)
            setRequestProperty("Accept", "application/json, application/geo+json")
            // ผลค้นหาสถานที่เป็นชื่อภาษาไทย (ถ้าไม่ส่ง ORS ตอบเป็นภาษาอังกฤษ)
            setRequestProperty("Accept-Language", "th")
            if (postBody != null) {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (postBody != null) conn.outputStream.use { it.write(postBody.toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
            else throw OrsException(OrsParser.error(code, conn.errorStream?.bufferedReader()?.use { it.readText() }))
        } catch (e: IOException) {
            throw OrsException("เชื่อมต่ออินเทอร์เน็ตไม่ได้")
        } finally {
            conn.disconnect()
        }
    }

    private fun LatLon.toJson() = JSONArray().put(lon).put(lat)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

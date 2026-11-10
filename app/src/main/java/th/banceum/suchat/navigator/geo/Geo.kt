package th.banceum.suchat.navigator.geo

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** พิกัดละติจูด/ลองจิจูด (องศา) */
data class LatLon(val lat: Double, val lon: Double)

/** ผลการหาจุดบนเส้นทางที่ใกล้ตำแหน่งที่สุด */
data class Snap(
    /** ช่วงที่ i (ระหว่างจุด i กับ i+1) */
    val segment: Int,
    /** สัดส่วนบนช่วงนั้น 0..1 */
    val fraction: Double,
    /** ระยะจากตำแหน่งถึงเส้นทาง (เมตร) */
    val distance: Double,
    val point: LatLon,
)

object Geo {
    private const val EARTH_R = 6_371_000.0

    /** ระยะทางบนผิวโลก (เมตร) แบบ haversine */
    fun distance(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_R * atan2(sqrt(h), sqrt(1 - h))
    }

    /** ระยะสะสมตามเส้น: ผลลัพธ์ index i = ระยะจากจุดแรกถึงจุด i */
    fun cumulative(line: List<LatLon>): DoubleArray {
        val out = DoubleArray(line.size)
        for (i in 1 until line.size) out[i] = out[i - 1] + distance(line[i - 1], line[i])
        return out
    }

    /**
     * จุดบนเส้นที่ใกล้ p ที่สุด ค้นเฉพาะช่วง [from, to) เพื่อไม่ให้กระโดดไปเส้นที่วนกลับมาใกล้กัน
     * ใช้ระนาบแบน (equirectangular) รอบ p ซึ่งแม่นพอสำหรับระยะไม่กี่กิโลเมตร
     */
    fun snap(line: List<LatLon>, p: LatLon, from: Int = 0, to: Int = line.size - 1): Snap? {
        if (line.size < 2) return null
        val start = from.coerceIn(0, line.size - 2)
        val end = to.coerceIn(start + 1, line.size - 1)
        val kx = cos(Math.toRadians(p.lat)) * EARTH_R * Math.PI / 180
        val ky = EARTH_R * Math.PI / 180
        var best: Snap? = null
        for (i in start until end) {
            val a = line[i]
            val b = line[i + 1]
            val ax = (a.lon - p.lon) * kx; val ay = (a.lat - p.lat) * ky
            val bx = (b.lon - p.lon) * kx; val by = (b.lat - p.lat) * ky
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val x = ax + t * dx; val y = ay + t * dy
            val d = sqrt(x * x + y * y)
            if (best == null || d < best.distance) {
                best = Snap(i, t, d, LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t))
            }
        }
        return best
    }

    /** ข้อความระยะทางภาษาไทย เช่น "350 เมตร", "1.2 กม.", "25 กม." */
    fun formatDistance(m: Double): String = when {
        m < 50 -> "${(m / 5).roundToInt() * 5} เมตร"
        m < 1000 -> "${(m / 10).roundToInt() * 10} เมตร"
        m < 10_000 -> String.format(Locale.ROOT, "%.1f กม.", m / 1000)
        else -> "${(m / 1000).roundToInt()} กม."
    }

    /** ระยะสำหรับเสียงพูด (ไม่ใช้ตัวย่อ "กม." เพราะเครื่องอ่านออกเสียงบางเครื่องอ่านไม่ถูก) */
    fun spokenDistance(m: Double): String = when {
        m < 100 -> "${(m / 10).roundToInt().coerceAtLeast(1) * 10} เมตร"
        m < 1000 -> "${(m / 50).roundToInt() * 50} เมตร"
        else -> String.format(Locale.ROOT, "%.1f กิโลเมตร", m / 1000).replace(".0 ", " ")
    }

    /** ข้อความเวลาเดินทาง เช่น "5 นาที", "1 ชม. 20 นาที" */
    fun formatDuration(sec: Double): String {
        val min = (sec / 60).roundToInt().coerceAtLeast(1)
        return if (min < 60) "$min นาที" else {
            val h = min / 60
            val m = min % 60
            if (m == 0) "$h ชม." else "$h ชม. $m นาที"
        }
    }
}

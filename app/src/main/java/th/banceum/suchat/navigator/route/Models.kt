package th.banceum.suchat.navigator.route

import th.banceum.suchat.navigator.geo.LatLon

/** วิธีเดินทาง ตามชื่อ profile ของ OpenRouteService */
enum class Profile(val ors: String, val label: String) {
    CAR("driving-car", "รถยนต์"),
    BIKE("cycling-regular", "จักรยาน"),
    WALK("foot-walking", "เดิน"),
}

/** สถานที่จากการค้นหา/แตะบนแผนที่/ที่บันทึกไว้ */
data class Place(val name: String, val detail: String, val pos: LatLon)

/**
 * หนึ่งช่วงของเส้นทาง: ทำ [type] ที่จุด [start] แล้วเดินทางไปจนถึงจุด [end] (index ใน Route.points)
 * type ตามรหัสของ OpenRouteService (0 = เลี้ยวซ้าย, 1 = เลี้ยวขวา, ... ดู ThaiInstructions)
 */
data class Step(
    val type: Int,
    val name: String,
    val distance: Double,
    val duration: Double,
    val start: Int,
    val end: Int,
    val exit: Int? = null,
)

data class Route(
    val profile: Profile,
    val points: List<LatLon>,
    /** เมตร */
    val distance: Double,
    /** วินาที */
    val duration: Double,
    val steps: List<Step>,
)

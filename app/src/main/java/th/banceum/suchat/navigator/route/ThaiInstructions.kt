package th.banceum.suchat.navigator.route

/**
 * แปลงรหัสการเลี้ยวของ OpenRouteService เป็นคำแนะนำภาษาไทย
 * (ORS ไม่มีภาษาไทยในตัว จึงสร้างข้อความเองจาก type + ชื่อถนน)
 */
object ThaiInstructions {
    const val LEFT = 0
    const val RIGHT = 1
    const val SHARP_LEFT = 2
    const val SHARP_RIGHT = 3
    const val SLIGHT_LEFT = 4
    const val SLIGHT_RIGHT = 5
    const val STRAIGHT = 6
    const val ENTER_ROUNDABOUT = 7
    const val EXIT_ROUNDABOUT = 8
    const val U_TURN = 9
    const val GOAL = 10
    const val DEPART = 11
    const val KEEP_LEFT = 12
    const val KEEP_RIGHT = 13

    /** คำสั่งสั้น ไม่รวมชื่อถนน */
    fun action(step: Step): String = when (step.type) {
        LEFT -> "เลี้ยวซ้าย"
        RIGHT -> "เลี้ยวขวา"
        SHARP_LEFT -> "เลี้ยวซ้ายหักศอก"
        SHARP_RIGHT -> "เลี้ยวขวาหักศอก"
        SLIGHT_LEFT -> "เบี่ยงซ้าย"
        SLIGHT_RIGHT -> "เบี่ยงขวา"
        STRAIGHT -> "ตรงไป"
        ENTER_ROUNDABOUT -> step.exit?.let { "เข้าวงเวียน แล้วออกทางออกที่ $it" } ?: "เข้าวงเวียน"
        EXIT_ROUNDABOUT -> "ออกจากวงเวียน"
        U_TURN -> "กลับรถ"
        GOAL -> "ถึงจุดหมาย"
        DEPART -> "ออกเดินทาง"
        KEEP_LEFT -> "ชิดซ้าย"
        KEEP_RIGHT -> "ชิดขวา"
        else -> "ไปต่อ"
    }

    /** คำแนะนำเต็ม เช่น "เลี้ยวขวา เข้าสู่ ถนนพหลโยธิน" */
    fun text(step: Step): String {
        val road = roadName(step.name) ?: return action(step)
        return when (step.type) {
            GOAL -> action(step)
            DEPART, STRAIGHT, KEEP_LEFT, KEEP_RIGHT -> "${action(step)} ไปตาม $road"
            else -> "${action(step)} เข้าสู่ $road"
        }
    }

    /** ประโยคสำหรับเสียงพูด เช่น "อีก 300 เมตร เลี้ยวขวา เข้าสู่ ถนนพหลโยธิน" */
    fun spoken(step: Step, distanceText: String?): String =
        if (distanceText == null) text(step) else "อีก $distanceText ${text(step)}"

    /** ORS ใช้ "-" แทนถนนไม่มีชื่อ */
    fun roadName(name: String): String? = name.trim().takeIf { it.isNotEmpty() && it != "-" }
}

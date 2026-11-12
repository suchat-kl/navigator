package th.banceum.suchat.navigator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.banceum.suchat.navigator.nav.SpeedLimit
import th.banceum.suchat.navigator.nav.SpeedLimitResolver
import th.banceum.suchat.navigator.nav.SpeedMonitor
import th.banceum.suchat.navigator.nav.SpeedRules
import th.banceum.suchat.navigator.nav.SpeedSource

class SpeedTest {
    private val rules = SpeedRules()
    private val noSigns: (Long) -> Int? = { null }

    /** ผล /reverse ย่อของ Nominatim */
    private fun road(type: String, address: String, id: Long = 1, category: String = "highway") =
        """{"osm_type":"way","osm_id":$id,"category":"$category","type":"$type","address":{$address}}"""

    @Test
    fun bangkokIsUrban() {
        val l = SpeedLimitResolver.resolve(road("primary", """"suburb":"เขตพระนคร","city":"กรุงเทพมหานคร""""), rules, noSigns)
        assertEquals(80, l!!.kmh)
        assertFalse(l.fromSign)
    }

    @Test
    fun municipalityIsUrban() {
        // ตัวอย่างจริง: คลองหลวง ปทุมธานี (town = เทศบาลเมืองคลองหลวง)
        val l = SpeedLimitResolver.resolve(road("residential",
            """"city_district":"ตำบลคลองหนึ่ง","town":"เทศบาลเมืองคลองหลวง","county":"อำเภอคลองหลวง""""), rules, noSigns)
        assertEquals(80, l!!.kmh)
        val pattaya = SpeedLimitResolver.resolve(road("secondary", """"city":"เมืองพัทยา","municipality":"หนองปลาไหล""""), rules, noSigns)
        assertEquals(80, pattaya!!.kmh)
    }

    @Test
    fun townWithoutTessabanPrefixIsUrban() {
        // ตัวอย่างจริง: ถนนมิตรภาพในแก่งคอย (town = "แก่งคอย" ไม่มีคำว่าเทศบาล)
        val l = SpeedLimitResolver.resolve(road("trunk", """"town":"แก่งคอย","county":"อำเภอแก่งคอย""""), rules, noSigns)
        assertEquals(80, l!!.kmh)
        val noPlaceKeys = SpeedRules(urbanPlaceKeys = emptyList())
        assertEquals(90, SpeedLimitResolver.resolve(road("trunk", """"town":"แก่งคอย""""), noPlaceKeys, noSigns)!!.kmh)
    }

    @Test
    fun ruralRoad() {
        val l = SpeedLimitResolver.resolve(road("trunk", """"county":"อำเภอด่านขุนทด","province":"จังหวัดนครราชสีมา""""), rules, noSigns)
        assertEquals(90, l!!.kmh)
    }

    @Test
    fun roadNamedTessabanIsNotUrban() {
        // ซอย/ถนนชื่อ "เทศบาล 1" ไม่ได้แปลว่าอยู่ในเขตเทศบาล
        val l = SpeedLimitResolver.resolve(road("residential", """"road":"เทศบาล 1","county":"อำเภอเมือง""""), rules, noSigns)
        assertEquals(90, l!!.kmh)
    }

    @Test
    fun motorway() {
        val l = SpeedLimitResolver.resolve(road("motorway", """"city":"กรุงเทพมหานคร""""), rules, noSigns)
        assertEquals(120, l!!.kmh)
    }

    @Test
    fun osmSignWins() {
        val signs = mapOf(42L to 60)
        val l = SpeedLimitResolver.resolve(road("motorway", """"city":"กรุงเทพมหานคร"""", id = 42), rules) { signs[it] }
        assertEquals(SpeedLimit(60, true, "ป้ายจำกัดความเร็ว"), l)
    }

    @Test
    fun notARoad() {
        assertNull(SpeedLimitResolver.resolve(road("yes", "", category = "building"), rules, noSigns))
        assertNull(SpeedLimitResolver.resolve(road("footway", """"city":"กรุงเทพมหานคร""""), rules, noSigns))
        assertNull(SpeedLimitResolver.resolve("""{"error":"Unable to geocode"}""", rules, noSigns))
    }

    @Test
    fun rulesFromServerOverrideDefaults() {
        val r = SpeedRules.parse("""{"rural":100,"motorway":110,"urbanPrefixes":["เทศบาล","อบต."]}""")
        assertEquals(100, r.rural)
        assertEquals(80, r.urban)          // ไม่ได้ระบุ = ค่าตั้งต้น
        assertEquals(110, r.motorway)
        assertEquals(listOf("เทศบาล", "อบต."), r.urbanPrefixes)
    }

    @Test
    fun parseSignsFile() {
        val signs = SpeedSource.parseSigns("""{"ways":{"60":[1,2],"TH:urban":[3],"5":[4],"abc":[5]}}""", rules)
        assertEquals(mapOf(1L to 60, 2L to 60, 3L to 80), signs)
    }

    @Test
    fun warnAfterThreeFixesThenCooldown() {
        val m = SpeedMonitor()
        val limit = SpeedLimit(80, false, "")
        // เกินไม่ถึง tolerance (5) ไม่เตือน
        assertNull(m.update(84.0, limit, 5, 0))
        assertFalse(m.over)
        assertNull(m.update(90.0, limit, 5, 1_000))
        assertTrue(m.over)
        assertNull(m.update(91.0, limit, 5, 2_000))
        val w = m.update(92.0, limit, 5, 3_000)
        assertEquals("ความเร็วเกินกำหนด จำกัด 80 กิโลเมตรต่อชั่วโมง", w)
        // ยังเร็วอยู่ภายใน 60 วินาที ไม่พูดซ้ำ
        assertNull(m.update(95.0, limit, 5, 30_000))
        // พ้น 60 วินาที เตือนอีกครั้ง
        assertNotNull(m.update(95.0, limit, 5, 64_000))
    }

    @Test
    fun gpsJumpDoesNotWarn() {
        val m = SpeedMonitor()
        val limit = SpeedLimit(80, false, "")
        assertNull(m.update(120.0, limit, 5, 0))
        assertNull(m.update(70.0, limit, 5, 1_000))
        assertNull(m.update(120.0, limit, 5, 2_000))
        assertNull(m.update(120.0, limit, 5, 3_000))
        assertNotNull(m.update(120.0, limit, 5, 4_000))
    }

    @Test
    fun newLimitWarnsAgainImmediately() {
        val m = SpeedMonitor(needFixes = 1)
        assertNotNull(m.update(100.0, SpeedLimit(90, false, ""), 5, 0))
        // เข้าเขตเมือง อัตราเปลี่ยนเป็น 80 เตือนทันทีแม้ยังไม่พ้น 60 วินาที
        assertEquals("ความเร็วเกินกำหนด จำกัด 80 กิโลเมตรต่อชั่วโมง", m.update(100.0, SpeedLimit(80, false, ""), 5, 5_000))
    }
}

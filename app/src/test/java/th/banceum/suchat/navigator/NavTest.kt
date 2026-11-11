package th.banceum.suchat.navigator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.banceum.suchat.navigator.data.Prefs
import th.banceum.suchat.navigator.geo.Geo
import th.banceum.suchat.navigator.geo.LatLon
import th.banceum.suchat.navigator.nav.NavTracker
import th.banceum.suchat.navigator.route.OrsParser
import th.banceum.suchat.navigator.route.Place
import th.banceum.suchat.navigator.route.Profile
import th.banceum.suchat.navigator.route.Step
import th.banceum.suchat.navigator.route.ThaiInstructions

class NavTest {
    /**
     * เส้นทางจำลองรูปตัว L: ไปทางเหนือ ~1.1 กม. แล้วเลี้ยวขวาไปทางตะวันออก ~1.1 กม.
     * จุดที่ 0..10 ขึ้นเหนือ, 10..20 ไปตะวันออก
     */
    private val orsJson = buildString {
        val coords = (0..10).map { "[100.5, ${13.7 + it * 0.001}]" } + (1..10).map { "[${100.5 + it * 0.001}, 13.71]" }
        append("""{"type":"FeatureCollection","features":[{"type":"Feature","properties":{""")
        append(""""segments":[{"distance":2190.0,"duration":200.0,"steps":[""")
        append("""{"distance":1112.0,"duration":100.0,"type":11,"instruction":"Head north","name":"-","way_points":[0,10]},""")
        append("""{"distance":1078.0,"duration":100.0,"type":1,"instruction":"Turn right","name":"ถนนพหลโยธิน","way_points":[10,20]},""")
        append("""{"distance":0.0,"duration":0.0,"type":10,"instruction":"Arrive","name":"-","way_points":[20,20]}]}],""")
        append(""""summary":{"distance":2190.0,"duration":200.0}},""")
        append(""""geometry":{"type":"LineString","coordinates":[${coords.joinToString(",")}]}}]}""")
    }

    private val route = OrsParser.route(orsJson, Profile.CAR)

    @Test fun parseRoute() {
        assertEquals(21, route.points.size)
        assertEquals(LatLon(13.7, 100.5), route.points.first())
        assertEquals(3, route.steps.size)
        assertEquals(1, route.steps[1].type)
        assertEquals(10, route.steps[1].start)
        assertEquals(2190.0, route.distance, 0.1)
    }

    @Test fun thaiText() {
        assertEquals("เลี้ยวขวา เข้าสู่ ถนนพหลโยธิน", ThaiInstructions.text(route.steps[1]))
        assertEquals("ออกเดินทาง", ThaiInstructions.text(route.steps[0]))
        assertEquals("เข้าวงเวียน แล้วออกทางออกที่ 2",
            ThaiInstructions.text(Step(7, "-", 0.0, 0.0, 0, 1, exit = 2)))
        assertEquals("อีก 300 เมตร เลี้ยวขวา เข้าสู่ ถนนพหลโยธิน",
            ThaiInstructions.spoken(route.steps[1], Geo.spokenDistance(300.0)))
    }

    @Test fun roadNamesAndStraight() {
        assertEquals("ถนนสิงหวัฒน์", ThaiInstructions.roadName("ถนนสิงหวัฒน์, 12"))
        assertEquals("ทางหลวงหมายเลข 3256", ThaiInstructions.roadName("3256"))
        assertNull(ThaiInstructions.roadName("-"))
        // เส้นทางที่จุดเปลี่ยนถัดไปเป็น "ตรงไป" ไม่พูด
        val straight = route.copy(steps = route.steps.mapIndexed { i, s -> if (i == 1) s.copy(type = ThaiInstructions.STRAIGHT) else s })
        val t = NavTracker(straight)
        assertNull(t.update(LatLon(13.705, 100.5)).speech)
        assertFalse(t.intro().contains("ตรงไป"))
    }

    @Test fun formatting() {
        assertEquals("350 เมตร", Geo.formatDistance(347.0))
        assertEquals("1.2 กม.", Geo.formatDistance(1234.0))
        assertEquals("25 กม.", Geo.formatDistance(25_400.0))
        assertEquals("2 กิโลเมตร", Geo.spokenDistance(2010.0))
        assertEquals("5 นาที", Geo.formatDuration(290.0))
        assertEquals("1 ชม. 20 นาที", Geo.formatDuration(4800.0))
    }

    @Test fun trackProgressAndSpeech() {
        val t = NavTracker(route)
        // ต้นทาง: อีก ~1.1 กม. ถึงจุดเลี้ยว ยังไม่ถึงระยะเตือน 800 ม.
        val u0 = t.update(LatLon(13.7, 100.5))
        assertEquals(0, u0.state.stepIndex)
        assertEquals(1112.0, u0.state.toNext, 5.0)
        assertNull(u0.speech)
        // ห่างจุดเลี้ยว ~556 ม. → เตือนระยะ 800 ม.
        val u1 = t.update(LatLon(13.705, 100.5))
        assertTrue(u1.speech!!.startsWith("อีก 550 เมตร เลี้ยวขวา"))
        // ตำแหน่งเดิมไม่พูดซ้ำ
        assertNull(t.update(LatLon(13.7051, 100.5)).speech)
        // ใกล้จุดเลี้ยว ~22 ม. → พูดคำสั่งโดยไม่บอกระยะ
        assertEquals("เลี้ยวขวา เข้าสู่ ถนนพหลโยธิน", t.update(LatLon(13.7098, 100.5)).speech)
        // เลี้ยวแล้ว อยู่ช่วงที่ 2
        val u2 = t.update(LatLon(13.71, 100.503))
        assertEquals(1, u2.state.stepIndex)
        assertFalse(u2.state.arrived)
        // ถึงปลายทาง
        val u3 = t.update(LatLon(13.71, 100.5099))
        assertTrue(u3.state.arrived)
        assertEquals("ถึงจุดหมายแล้ว", u3.speech)
        assertNull(t.update(LatLon(13.71, 100.51)).speech)
    }

    @Test fun offRouteTriggersReroute() {
        val t = NavTracker(route)
        t.update(LatLon(13.702, 100.5))
        val far = LatLon(13.702, 100.503) // ห่างเส้นทาง ~320 ม.
        assertFalse(t.update(far).reroute)
        assertFalse(t.update(far).reroute)
        val u = t.update(far)
        assertTrue(u.state.offRoute)
        assertTrue(u.reroute)
        // กลับเข้าเส้นทาง
        assertFalse(t.update(LatLon(13.703, 100.5)).state.offRoute)
    }

    @Test fun parsePlacesAndErrors() {
        val json = """{"features":[{"geometry":{"coordinates":[100.53,13.74]},
            "properties":{"name":"สยามพารากอน","label":"สยามพารากอน, ปทุมวัน, กรุงเทพมหานคร"}}]}"""
        val p = OrsParser.places(json).single()
        assertEquals("สยามพารากอน", p.name)
        assertEquals("ปทุมวัน, กรุงเทพมหานคร", p.detail)
        assertEquals(13.74, p.pos.lat, 1e-9)
        assertEquals("ปทุมวัน", OrsParser.detailOf("เซ็นทรัลชิดลม, ปทุมวัน, BM, ประเทศไทย", "เซ็นทรัลชิดลม"))
        assertEquals("Ban Rangsit", OrsParser.detailOf("Central, Ban Rangsit, PT, ประเทศไทย", "Central"))
        assertTrue(OrsParser.error(404, """{"error":{"code":2010,"message":"x"}}""").contains("ไม่พบถนน"))
        assertTrue(OrsParser.error(403, null).contains("กุญแจ"))
    }

    @Test fun savedPlacesRoundTrip() {
        val list = listOf(Place("บ้าน", "", LatLon(13.1, 100.2)), Place("ที่ทำงาน", "กรุงเทพฯ", LatLon(13.7, 100.5)))
        assertEquals(list, Prefs.decode(Prefs.encode(list)))
        assertEquals(emptyList<Place>(), Prefs.decode("ไม่ใช่ json"))
        assertNotNull(Geo.snap(route.points, LatLon(13.705, 100.5)))
    }
}

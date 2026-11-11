package th.banceum.suchat.navigator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.banceum.suchat.navigator.alert.AlertKind
import th.banceum.suchat.navigator.alert.AlertParser
import th.banceum.suchat.navigator.alert.AlertPoint
import th.banceum.suchat.navigator.alert.AlertTracker
import th.banceum.suchat.navigator.geo.LatLon
import th.banceum.suchat.navigator.route.Profile
import th.banceum.suchat.navigator.route.Route

class AlertTest {
    /** เส้นทางตรงขึ้นเหนือ ~2.2 กม. ที่ลองจิจูด 100.5 (จุดละ ~111 ม.) */
    private val route = Route(
        profile = Profile.CAR,
        points = (0..20).map { LatLon(13.7 + it * 0.001, 100.5) },
        distance = 2224.0,
        duration = 200.0,
        steps = emptyList(),
    )

    private fun at(lat: Double, lonOffsetM: Double = 0.0) = LatLon(lat, 100.5 + lonOffsetM / 108_000.0)

    @Test fun parseGeoJson() {
        val json = """{"type":"FeatureCollection","features":[
            {"type":"Feature","geometry":{"type":"Point","coordinates":[100.5,13.71]},
             "properties":{"kind":"toll_booth","name":"ด่านดินแดง"}},
            {"type":"Feature","geometry":{"type":"Point","coordinates":[100.6,13.8]},
             "properties":{"kind":"speed_camera","maxspeed":90}},
            {"type":"Feature","geometry":{"type":"Point","coordinates":[100.6,13.8]},
             "properties":{"kind":"unknown"}},
            {"type":"Feature","geometry":{"type":"LineString","coordinates":[[1,2],[3,4]]},
             "properties":{"kind":"weighbridge"}}]}"""
        val list = AlertParser.parse(json)
        assertEquals(2, list.size)
        assertEquals(AlertKind.TOLL_BOOTH, list[0].kind)
        assertEquals("ด่านดินแดง", list[0].name)
        assertEquals(13.71, list[0].pos.lat, 1e-9)
        assertEquals(90, list[1].maxspeed)
        assertNull(list[0].maxspeed)
    }

    @Test fun onlyPointsOnRouteAndMerged() {
        val points = listOf(
            AlertPoint(AlertKind.TOLL_BOOTH, "ด่าน ก", at(13.705, 5.0)),      // บนเส้นทาง ~555 ม.
            AlertPoint(AlertKind.TOLL_BOOTH, "ด่าน ก", at(13.7051, -8.0)),    // ช่องติดกัน รวมกับจุดแรก
            AlertPoint(AlertKind.WEIGHBRIDGE, null, at(13.708, 200.0)),       // ห่างเส้นทาง 200 ม. ไม่นับ
            AlertPoint(AlertKind.WEIGHBRIDGE, null, at(13.709, 20.0)),        // ด่านชั่งฝั่งตรงข้าม (ขวาของทิศขึ้นเหนือ) ไม่นับ
            AlertPoint(AlertKind.SPEED_CAMERA, null, at(13.712, 0.0), 90),    // ~1334 ม.
            AlertPoint(AlertKind.SPEED_CAMERA, null, at(13.9, 0.0)),          // นอกกรอบเส้นทาง
        )
        val t = AlertTracker(route, points)
        assertEquals(listOf(AlertKind.TOLL_BOOTH, AlertKind.SPEED_CAMERA), t.alerts.map { it.point.kind })
        assertEquals(556.0, t.alerts[0].along, 5.0)
    }

    @Test fun speakOnceWhenClose() {
        val points = listOf(
            AlertPoint(AlertKind.TOLL_BOOTH, "ด่านดินแดง", at(13.705)),
            AlertPoint(AlertKind.SPEED_CAMERA, null, at(13.712), 90),
        )
        val t = AlertTracker(route, points)
        assertNull(t.speech(0.0))                         // ไกลกว่า 500 ม.
        assertEquals("อีก 450 เมตร ด่านเก็บเงิน ด่านดินแดง", t.speech(110.0))
        assertNull(t.speech(200.0))                       // พูดแล้ว ไม่ซ้ำ
        val up = t.upcoming(200.0)
        assertNotNull(up)
        assertEquals(AlertKind.TOLL_BOOTH, up!!.point.kind)
        assertEquals(356.0, up.distance, 5.0)
        assertEquals("อีก 450 เมตร มีกล้องตรวจจับความเร็ว จำกัด 90 กิโลเมตรต่อชั่วโมง", t.speech(890.0))
        // ผ่านด่านไปแล้ว จุดถัดไปคือกล้อง
        assertEquals(AlertKind.SPEED_CAMERA, t.upcoming(700.0)!!.point.kind)
        assertNull(t.upcoming(1400.0))
    }

    @Test fun weighbridgeOnLeftSideOnly() {
        // ขับขึ้นเหนือ: ทิศตะวันตก (ลองจิจูดน้อยกว่า) คือซ้าย
        val left = AlertPoint(AlertKind.WEIGHBRIDGE, "ด่านชั่ง ก", at(13.705, -30.0))
        val right = AlertPoint(AlertKind.WEIGHBRIDGE, "ด่านชั่ง ข", at(13.71, 30.0))
        assertEquals(listOf("ด่านชั่ง ก"), AlertTracker(route, listOf(left, right)).alerts.map { it.point.name })
        assertTrue(AlertTracker.isLeft(LatLon(0.0, 0.0), LatLon(1.0, 0.0), LatLon(0.5, -0.1)))
    }

    @Test fun skipPointsAlreadyPassedAtStart() {
        val t = AlertTracker(route, listOf(AlertPoint(AlertKind.WEIGHBRIDGE, null, at(13.701, -10.0))))
        assertNull(t.speech(300.0))                       // เริ่มนำทางหลังผ่านจุดไปแล้ว ไม่พูด
        assertTrue(t.alerts.size == 1)
    }
}

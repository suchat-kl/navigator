package th.banceum.suchat.navigator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import th.banceum.suchat.navigator.geo.LatLon
import th.banceum.suchat.navigator.route.OrsException
import th.banceum.suchat.navigator.route.OsrmParser
import th.banceum.suchat.navigator.route.Profile
import th.banceum.suchat.navigator.route.ThaiInstructions

class OsrmTest {
    /** ผลจำลองรูปแบบ OSRM /route/v1 (steps=true, geometries=geojson) ขึ้นเหนือแล้วเลี้ยวขวา */
    private val json = """
    {"code":"Ok","routes":[{"distance":2190.0,"duration":200.0,"legs":[{"steps":[
      {"distance":1112.0,"duration":100.0,"name":"","ref":"",
       "geometry":{"type":"LineString","coordinates":[[100.5,13.7],[100.5,13.705],[100.5,13.71]]},
       "maneuver":{"type":"depart","modifier":"straight","location":[100.5,13.7]}},
      {"distance":1078.0,"duration":100.0,"name":"ถนนพหลโยธิน","ref":"1;AH2",
       "geometry":{"type":"LineString","coordinates":[[100.5,13.71],[100.505,13.71],[100.51,13.71]]},
       "maneuver":{"type":"turn","modifier":"right","location":[100.5,13.71]}},
      {"distance":0.0,"duration":0.0,"name":"","ref":"",
       "geometry":{"type":"LineString","coordinates":[[100.51,13.71],[100.51,13.71]]},
       "maneuver":{"type":"arrive","location":[100.51,13.71]}}
    ]}]}]}
    """

    @Test fun parseRoute() {
        val r = OsrmParser.route(json, Profile.CAR)
        // ต่อเรขาคณิต: 3 + 2 + 1 จุด (ไม่ซ้ำรอยต่อ)
        assertEquals(6, r.points.size)
        assertEquals(LatLon(13.71, 100.51), r.points.last())
        assertEquals(listOf(ThaiInstructions.DEPART, ThaiInstructions.RIGHT, ThaiInstructions.GOAL), r.steps.map { it.type })
        assertEquals(2, r.steps[1].start)
        assertEquals(4, r.steps[1].end)
        assertEquals(r.points.size - 1, r.steps.last().end)
        assertEquals("ถนนพหลโยธิน, 1", r.steps[1].name)
        assertEquals("เลี้ยวขวา เข้าสู่ ถนนพหลโยธิน", ThaiInstructions.text(r.steps[1]))
        assertEquals(2190.0, r.distance, 0.1)
    }

    @Test fun maneuverMapping() {
        assertEquals(ThaiInstructions.U_TURN, OsrmParser.typeOf("continue", "uturn"))
        assertEquals(ThaiInstructions.KEEP_LEFT, OsrmParser.typeOf("fork", "slight left"))
        assertEquals(ThaiInstructions.SLIGHT_RIGHT, OsrmParser.typeOf("off ramp", "slight right"))
        assertEquals(ThaiInstructions.ENTER_ROUNDABOUT, OsrmParser.typeOf("rotary", "right"))
        assertEquals(ThaiInstructions.STRAIGHT, OsrmParser.typeOf("new name", "straight"))
        assertEquals("ทางหลวงหมายเลข 12", ThaiInstructions.roadName(OsrmParser.nameOf("", "12;AH16")))
    }

    @Test fun errorCodeToThai() {
        try {
            OsrmParser.route("""{"code":"NoSegment","message":"Could not find a matching segment"}""", Profile.CAR)
            fail()
        } catch (e: OrsException) {
            assertTrue(e.message!!.contains("ไม่พบถนน"))
        }
    }
}

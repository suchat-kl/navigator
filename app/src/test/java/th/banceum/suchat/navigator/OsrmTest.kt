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

class NominatimTest {
    @org.junit.Test fun parseSearchAndReverse() {
        val json = """[{"place_id":1,"lat":"13.7443758","lon":"100.5439864","category":"shop","type":"mall",
            "name":"เซ็นทรัลชิดลม",
            "display_name":"เซ็นทรัลชิดลม, ถนนเพลินจิต, ลุมพินี, เขตปทุมวัน, กรุงเทพมหานคร, 10330, ประเทศไทย"},
            {"place_id":2,"lat":"x","lon":"1","name":"ผิด","display_name":"ผิด"}]"""
        val p = th.banceum.suchat.navigator.route.NominatimParser.places(json)
        org.junit.Assert.assertEquals(1, p.size)
        org.junit.Assert.assertEquals("เซ็นทรัลชิดลม", p[0].name)
        org.junit.Assert.assertEquals("ถนนเพลินจิต, ลุมพินี, เขตปทุมวัน", p[0].detail)
        org.junit.Assert.assertEquals(13.7443758, p[0].pos.lat, 1e-9)
        org.junit.Assert.assertNull(th.banceum.suchat.navigator.route.NominatimParser.reverse("""{"error":"Unable to geocode"}"""))
        val r = th.banceum.suchat.navigator.route.NominatimParser.reverse(
            """{"lat":"13.7","lon":"100.5","name":"","display_name":"ซอยสมประสงค์ 5, ถนนเพชรบุรี, ราชเทวี, 10400, ประเทศไทย"}""")
        org.junit.Assert.assertEquals("ซอยสมประสงค์ 5", r!!.name)
        org.junit.Assert.assertEquals("ถนนเพชรบุรี, ราชเทวี", r.detail)
    }

    @org.junit.Test fun addressDetailsAndDedupe() {
        // ผลจริงจากเซิร์ฟเวอร์กรม (ตัดบางช่อง): บ้านเลขที่/ชุมชน/รหัสไปรษณีย์ไม่แสดง, ชื่อซ้ำใกล้กันเหลือรายการเดียว
        val a = """{"house_number":"999/9","road":"ถนนราชดำริ","neighbourhood":"ชุมชน","quarter":"แขวงปทุมวัน",
            "suburb":"เขตปทุมวัน","city":"กรุงเทพมหานคร","postcode":"10330","country":"ประเทศไทย"}"""
        val json = """[{"lat":"13.7466","lon":"100.5393","name":"เซ็นทรัลเวิลด์","display_name":"x","address":$a},
            {"lat":"13.7468","lon":"100.5395","name":"เซ็นทรัลเวิลด์","display_name":"x","address":$a},
            {"lat":"13.8","lon":"100.6","name":"เซ็นทรัลเวิลด์","display_name":"x","address":$a}]"""
        val p = th.banceum.suchat.navigator.route.NominatimParser.places(json)
        org.junit.Assert.assertEquals(2, p.size)
        org.junit.Assert.assertEquals("ถนนราชดำริ, แขวงปทุมวัน, เขตปทุมวัน", p[0].detail)
        val khonKaen = org.json.JSONObject("""{"road":"ซอยมิตรภาพ 12","city_district":"ตำบลในเมือง","city":"เทศบาลนครขอนแก่น",
            "county":"อำเภอเมืองขอนแก่น","province":"จังหวัดขอนแก่น"}""")
        org.junit.Assert.assertEquals("ตำบลในเมือง, อำเภอเมืองขอนแก่น, จังหวัดขอนแก่น",
            th.banceum.suchat.navigator.route.NominatimParser.detailOf(khonKaen, "ซอยมิตรภาพ 12"))
    }
}

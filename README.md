# นำทางไทย OSM (Android / Kotlin)

แอปแผนที่และนำทางด้วยข้อมูล OpenStreetMap เขียนด้วย Kotlin + Jetpack Compose (Material 3)
applicationId `th.banceum.suchat.navigator` · minSdk 26 · targetSdk 36

## ความสามารถ

| ส่วน | รายละเอียด |
|---|---|
| แผนที่ | MapLibre Native + แผนที่ฐาน OpenFreeMap (style `liberty`) ป้ายชื่อใช้ `name:th` ก่อน |
| ค้นหา | ORS Geocode Search จำกัดในประเทศไทย เรียงจากใกล้ตำแหน่งปัจจุบัน; แตะค้างบนแผนที่ = เลือกจุดหมาย (ORS Reverse) |
| หาเส้นทาง | ORS Directions: รถยนต์ / จักรยาน / เดิน แสดงเวลา ระยะทาง และขั้นตอนเป็นภาษาไทย |
| นำทาง | ป้ายคำสั่งด้านบน, เสียงพูดไทย (TextToSpeech), กล้องตามทิศทาง, หาเส้นทางใหม่เมื่อออกนอกเส้นทาง 3 ครั้งติด (ห่างกันอย่างน้อย 15 วินาที), จอไม่ดับ |
| เตือนจุดสำคัญ | ด่านเก็บเงิน / ด่านชั่งน้ำหนัก / กล้องตรวจจับความเร็ว ที่อยู่บนเส้นทาง พูดก่อนถึง 500 ม. (รถยนต์) + ป้ายใต้คำสั่งเลี้ยว + จุดบนแผนที่ ปิดได้ที่ตั้งค่า |
| บันทึกสถานที่ | เก็บใน SharedPreferences ในเครื่องอย่างเดียว |
| โฆษณา | Banner ล่างจอ (ซ่อนระหว่างนำทาง) + Interstitial หลังจบการนำทาง เว้น ≥ 3 นาที |

ไม่มีบัญชีผู้ใช้ ไม่ใช้ตำแหน่งเบื้องหลัง

## โครงโค้ด

```
app/src/main/java/th/banceum/suchat/navigator/
  ads/Ads.kt               AdMob + UMP (แบบเดียวกับ student_score)
  geo/Geo.kt               ระยะทาง, snap ตำแหน่งลงเส้นทาง, ข้อความระยะ/เวลา
  route/                   Models, OrsClient (HTTP), OrsParser (อ่าน JSON), ThaiInstructions (รหัสเลี้ยว → ไทย)
  nav/NavTracker.kt        ติดตามความคืบหน้า เลือกจังหวะพูด ตรวจออกนอกเส้นทาง (ไม่ขึ้นกับ Android ทดสอบได้)
  nav/LocationSource.kt    Fused Location (1 วินาทีตอนนำทาง / 5 วินาทีปกติ)
  nav/Voice.kt             TextToSpeech ภาษาไทย
  data/Prefs.kt            สถานที่ที่บันทึก + การตั้งค่า
  ui/                      MapScreen (MapLibre ใน Compose), AppRoot, Screens, MainViewModel
```

รหัสการเลี้ยวของ ORS (`type`): 0 ซ้าย, 1 ขวา, 2/3 หักศอก, 4/5 เบี่ยง, 6 ตรง, 7/8 วงเวียน, 9 กลับรถ, 10 ถึง, 11 ออกเดินทาง, 12/13 ชิดซ้าย/ขวา
ORS ไม่มีคำแนะนำภาษาไทย แอปจึงสร้างข้อความเองใน `ThaiInstructions.kt`

## จุดเตือน (geofence)

- ข้อมูลตั้งต้น `app/src/main/assets/alerts.geojson` ดึงจาก OpenStreetMap ด้วย `python docs/tools/osm_alerts.py`
  (11 พ.ย. 2569: ด่านเก็บเงิน 455, กล้อง 48, ด่านชั่ง 33 จุด — OSM ในไทยยังไม่ครบ โดยเฉพาะด่านชั่ง)
- รูปแบบ: GeoJSON จุด `properties.kind` = `weighbridge` | `toll_booth` | `speed_camera`, `name`, `maxspeed` (ไม่บังคับ)
- ข้อมูลของหน่วยงาน: ใส่ `alertsUrl=https://...` ใน `local.properties` แอปดาวน์โหลดทุกครั้งที่เปิด เก็บสำรองในเครื่อง
  ดาวน์โหลดไม่ได้ใช้ฉบับเดิม/ฉบับในแอป (ใช้รูปแบบเดียวกัน เช่น API จาก PostGIS ระยะที่สองของ gis-navigation)
- นับว่าอยู่บนเส้นทางเมื่อห่างแนวทาง ≤ 25 ม. (ด่านเก็บเงิน), 30 ม. (กล้อง), 40 ม. และอยู่**ทางซ้าย** (ด่านชั่ง — อยู่ช่องแยกข้างถนนแยกตามทิศ)
  จุดชนิดเดียวกันห่างกัน ≤ 250 ม. รวมเป็นครั้งเดียว (ด่านเก็บเงินหลายช่อง) — ดู `alert/AlertTracker.kt`

ทดสอบบน emulator (รุ่น debug เท่านั้น):
```bash
adb shell am start -n th.banceum.suchat.navigator/.MainActivity --es dest "16.90053,100.206727"   # ตั้งจุดหมาย
adb logcat -s NavVoice   # ประโยคที่แอปพูด (emulator ไม่มีเสียงไทย)
```

## Build

| ค่า | อยู่ที่ | เข้า git |
|---|---|---|
| รหัส AdMob จริง | `gradle.properties` | ใช่ (ไม่ใช่ความลับ) |
| กุญแจ OpenRouteService `orsApiKey` | `local.properties` | **ไม่** |
| กุญแจเซ็น release | `keystore.properties` → `C:\AI\flutter\keystore\upload-keystore.jks` | **ไม่** |

```bash
./gradlew testDebugUnitTest      # unit test (NavTracker, parser, ข้อความไทย)
./gradlew assembleDebug          # รุ่นพัฒนา โฆษณาทดสอบ
./gradlew assembleStaging        # เหมือน release (R8) แต่เซ็น debug + โฆษณาทดสอบ
./gradlew bundleRelease          # ไฟล์ .aab สำหรับ Play
```

ทดสอบนำทางบน emulator: Extended controls → Location → ตั้งจุด หรือ Routes เล่นเส้นทาง (`adb emu geo fix <lon> <lat>`)

## เอกสาร Google Play

`docs/play/` — ขั้นตอนเผยแพร่, ตั้งค่า AdMob, ตั้งค่า OpenRouteService, Data safety, ข้อความหน้าร้าน, privacy-policy.html (สำเนาที่ขึ้นเว็บ https://suchat-kl.github.io/navigator/privacy-policy.html)

## สัญญาอนุญาตข้อมูล

แผนที่ © ผู้ร่วมพัฒนา OpenStreetMap (ODbL) · OpenFreeMap / OpenMapTiles · เส้นทาง © openrouteservice.org by HeiGIT

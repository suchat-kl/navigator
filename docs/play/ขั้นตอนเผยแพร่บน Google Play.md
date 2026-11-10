# ขั้นตอนเผยแพร่บน Google Play

เอกสารสำหรับผู้พัฒนา/ผู้เผยแพร่แอป ทำตามลำดับ

## 0. เตรียมก่อน

| รายการ | สถานะ |
|---|---|
| บัญชี Google Play Console (บัญชีบุคคล ใช้บัญชีเดียวกับสมุดคะแนนนักเรียน) | มีแล้ว |
| AdMob: แอป + Banner + Interstitial | สร้างแล้ว 10 พ.ย. 2569 ใส่ใน `gradle.properties` แล้ว (ดู `ตั้งค่า AdMob.md`) |
| กุญแจ OpenRouteService ใน `local.properties` | **ผู้เผยแพร่ทำเอง** (ดู `ตั้งค่า OpenRouteService.md`) — ไม่มีกุญแจ = ค้นหา/หาเส้นทางไม่ได้ ห้ามอัปโหลดรุ่นนั้น |
| หน้าเว็บนโยบายความเป็นส่วนตัว (ข้อ 4) | ร่างไว้ที่ `docs/play/privacy-policy.html` ต้องนำขึ้นเว็บ |
| ไอคอน 512×512, ภาพหน้าปก 1024×500, ภาพหน้าจอมือถืออย่างน้อย 2 ภาพ | ยังไม่ได้ทำ (ข้อ 5) |

## 1. กุญแจเซ็นแอป (upload key)

ใช้กุญแจชุดเดียวกับ sliptaxMobile และสมุดคะแนนนักเรียน: `C:\AI\flutter\keystore\upload-keystore.jks` (alias `upload`)
SHA-1 `CC:4A:EB:2C:CF:0C:77:A8:89:C6:69:F7:4B:8F:6D:D0:C4:FD:6D:8E`

- `keystore.properties` ที่โฟลเดอร์บนสุดของโปรเจกต์ คัดลอกมาจาก `C:\AI\kotlin\student_score\keystore.properties` (ไม่เข้า git)
- Play App Signing จะเซ็นไฟล์ที่ผู้ใช้ดาวน์โหลดด้วยกุญแจของ Google แยกต่อแอป จึงใช้ upload key ร่วมกันหลายแอปได้
- **ไฟล์ .jks และรหัสหาย = อัปเดตทุกแอปที่ใช้ร่วมกันไม่ได้จนกว่าจะขอ Google รีเซ็ต** — สำรองโฟลเดอร์ `C:\AI\flutter\keystore` ไว้นอกเครื่อง

ตรวจลายเซ็น: `keytool -printcert -jarfile app\build\outputs\bundle\release\app-release.aab`

## 2. ทดสอบแล้วสร้างไฟล์อัปโหลด

1. ทดสอบรุ่น staging (ย่อโค้ดด้วย R8 เหมือน release, เซ็นด้วยกุญแจ debug, โฆษณาทดสอบ, ติดตั้งคู่กับรุ่นจริงได้เพราะ id ลงท้าย `.staging`)
   `./gradlew assembleStaging` → `app/build/outputs/apk/staging/app-staging.apk`
   ลอง: เปิดแผนที่, ค้นหา, แตะค้าง, หาเส้นทางทั้ง 3 แบบ, เริ่มนำทาง (บน emulator ใช้ Extended controls → Location → Routes เล่นเส้นทาง), จบการนำทาง, บันทึก/ลบสถานที่
2. สร้างไฟล์สำหรับ Play: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
3. เพิ่ม `versionCode` ทุกครั้งที่อัปโหลดรุ่นใหม่ (`app/build.gradle.kts`)

> บทเรียนจากสมุดคะแนนนักเรียน: รุ่น release แรกพังเพราะ R8 ตัดคลาสของ WorkManager (มากับ AdMob) กฎแก้ไว้ใน `app/proguard-rules.pro` แล้ว — ทดสอบ staging ทุกครั้งก่อนอัปโหลด

## 3. หน้าเว็บที่ Play บังคับ

GitHub Pages repo `suchat-kl/suchat-kl.github.io` (ต้นฉบับในเครื่อง `C:\AI\kotlin\suchat-kl.github.io`) โฟลเดอร์ `navigator/`

| ใช้ที่ | URL |
|---|---|
| เว็บไซต์ผู้พัฒนา (Store settings → Contact details) | https://suchat-kl.github.io |
| Privacy policy (Play + AdMob) | https://suchat-kl.github.io/navigator/privacy-policy.html |
| Account deletion | ไม่ต้องใช้ — แอปไม่มีบัญชีผู้ใช้ |
| app-ads.txt | https://suchat-kl.github.io/app-ads.txt (ไฟล์เดิม ใช้ร่วม) |

ใส่ URL ใน Play แล้วอย่าย้ายไฟล์อีก

## 4. ตั้งค่าใน Play Console

1. **Create app**: ชื่อ "นำทางไทย OSM", ภาษาเริ่มต้น ไทย, ประเภท App, ฟรี
2. **App signing**: ใช้ Play App Signing (ค่าเริ่มต้น)
3. **App content**
   - Privacy policy: https://suchat-kl.github.io/navigator/privacy-policy.html
   - Ads: **Yes, my app contains ads**
   - App access: **ทุกฟังก์ชันใช้ได้โดยไม่ต้องเข้าสู่ระบบ**
   - Content rating: แบบสอบถาม — หมวด "แอปอื่น ๆ / ยูทิลิตี" ไม่มีความรุนแรง/เนื้อหาผู้ใหญ่ ไม่มีการซื้อในแอป ผู้ใช้สื่อสารกันไม่ได้ **แชร์ตำแหน่งของผู้ใช้กับผู้ใช้อื่น: ไม่**
   - Target audience and content: **18 ปีขึ้นไป** อย่างเดียว; "ดึงดูดเด็กโดยไม่ตั้งใจ": ไม่
   - Data safety: ตอบตาม `docs/play/Data safety.md`
   - Location permissions: ใช้เฉพาะตอนเปิดแอป ไม่มีตำแหน่งเบื้องหลัง
   - Government apps: ไม่ใช่ · Financial features: ไม่มี · Health: ไม่มี
4. **Store listing**: ข้อความจาก `docs/play/ข้อความหน้าร้าน.md` + ภาพจาก `docs/play/graphics/`
   หมวด Maps & Navigation, อีเมลติดต่อ suchat.msit04@gmail.com, เว็บไซต์ https://suchat-kl.github.io
5. **Testing**
   - Internal testing: อัปโหลด .aab ทดสอบกับบัญชีตัวเองก่อน
   - **Closed testing: ผู้ทดสอบอย่างน้อย 12 คน ติดตั้งและเปิดใช้ต่อเนื่อง 14 วัน** (บัญชีนักพัฒนาบุคคล) แล้วจึงขอ Production ได้ — ตรวจเงื่อนไขล่าสุดใน Play Console
   - ระหว่างทดสอบแบบปิด แนะนำผู้ทดสอบให้ลองนำทางจริงอย่างน้อยหนึ่งเส้นทาง และรายงานคำแนะนำภาษาไทยที่อ่านแปลก
6. **Production**: ตอบคำถามช่วงทดสอบแบบปิด แล้วส่งตรวจ

## 5. ภาพหน้าร้าน (ยังไม่ได้ทำ)

- ไอคอน 512×512 PNG จาก `app/src/main/res/drawable/ic_launcher_foreground.xml` (พื้น `#0F7B5F`)
- Feature graphic 1024×500
- ภาพหน้าจอมือถือ 2–8 ภาพ (แนะนำ: ผลค้นหา, เส้นทางพร้อมเวลา, ขั้นตอนการเลี้ยว, หน้าจอนำทาง, สถานที่ที่บันทึก)
- ทำแบบเดียวกับสมุดคะแนนนักเรียน: เก็บภาพดิบจาก emulator ไว้ที่ `docs/play/graphics/raw/` แล้วดัดแปลง `student_score/docs/tools/store_graphics.py`

## 6. หลังเผยแพร่

- AdMob → Apps → Link กับ Google Play
- ดูโควตา OpenRouteService ใน Dashboard ช่วงแรกทุกสัปดาห์

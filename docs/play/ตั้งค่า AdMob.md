# ตั้งค่า AdMob สำหรับแอปนำทางไทย OSM

ทำที่ https://admob.google.com (บัญชีเดียวกับแอปสมุดคะแนนนักเรียน Publisher ID `pub-6256004372132515`)

## 1. แอปและหน่วยโฆษณา (สร้างแล้ว 10 พ.ย. 2569)

| รายการ | รหัส | ใช้ที่ |
|---|---|---|
| App ID | `ca-app-pub-6256004372132515~4568558013` | `admobAppId` |
| Banner | `ca-app-pub-6256004372132515/9629313008` | `admobBannerId` — ล่างจอ (adaptive anchored) ซ่อนระหว่างนำทาง |
| Interstitial (คั่นระหว่างหน้า) | `ca-app-pub-6256004372132515/1559251293` | `admobInterstitialId` — หลังกด "จบ/เสร็จ" การนำทาง เว้นอย่างน้อย 3 นาที |

ใส่ไว้ใน `gradle.properties` แล้ว ใช้เฉพาะรุ่น release; รุ่น debug/staging ใช้รหัสทดสอบของ Google เสมอ
ค่าเหล่านี้ไม่ใช่ความลับ (อยู่ในไฟล์แอปที่ผู้ใช้ดาวน์โหลด) เข้า git ได้

ตอนสร้างแอปใน AdMob เลือก "ยังไม่อยู่ใน store" — เผยแพร่บน Play แล้วให้กลับมา **Link** กับ Google Play (package `th.banceum.suchat.navigator`)

## 2. นโยบายโฆษณาในแอปนำทาง

- **ไม่แสดงโฆษณาใด ๆ ระหว่างนำทาง** (แบนเนอร์ซ่อน และไม่มีโฆษณาเต็มจอ) เพื่อไม่รบกวนผู้ขับขี่ — อย่าเปลี่ยน
- Interstitial แสดงเฉพาะหลังผู้ใช้จบการนำทางเอง (จุดพักตามธรรมชาติ) ไม่แสดงตอนเปิดแอป
- ตั้ง Frequency capping ใน AdMob ให้ตรงกันได้ (เช่น 1 ครั้งต่อ 3 นาที)

## 3. ควบคุมเนื้อหาโฆษณา (Blocking controls)

- ในแอปจำกัด **Max ad content rating = PG** ตั้งที่ AdMob ให้ตรงกัน (Blocking controls → Content rating → PG)
- Sensitive categories: พิจารณาบล็อก การพนัน, หาคู่ ตามความเหมาะสม

## 4. Privacy & messaging

แอปเชื่อม UMP ไว้แล้ว ต้องสร้างข้อความใน AdMob จึงจะแสดง
- **European regulations (GDPR)**: Create message → ผูกกับแอปนี้ → Publish
- URL นโยบายความเป็นส่วนตัว: https://suchat-kl.github.io/navigator/privacy-policy.html

## 5. app-ads.txt

ใช้ไฟล์เดิมที่ https://suchat-kl.github.io/app-ads.txt (บัญชี AdMob เดียวกัน ไม่ต้องแก้)
ต้องใส่เว็บไซต์ผู้พัฒนา `https://suchat-kl.github.io` ในหน้าร้าน Play ของแอปนี้ด้วย AdMob จึงจับคู่ได้

## 6. ระหว่างทดสอบ

- **ห้ามกดโฆษณาของตัวเอง** — บัญชีอาจถูกระงับ
- เพิ่มมือถือที่ทดสอบเป็น Test device ใน AdMob ก่อนติดตั้งรุ่น release
- ทดสอบโฆษณาด้วยรุ่น debug หรือ staging (ขึ้นป้าย "Test Ad")

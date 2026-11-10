# กฎเพิ่มเติมสำหรับรุ่น release (R8)
# ทดสอบด้วย build type "staging" (ย่อโค้ดเหมือน release) ก่อนเผยแพร่ทุกครั้ง

# WorkManager (มากับ AdMob) สร้างฐานข้อมูล Room ด้วย reflection (ชื่อคลาส + "_Impl")
# ไม่มีกฎนี้แอปพังตั้งแต่เปิด: "Failed to create an instance of androidx.work.impl.WorkDatabase"
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

package th.banceum.suchat.navigator

import android.app.Application
import org.maplibre.android.MapLibre

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // MapLibre ต้องเริ่มก่อนสร้าง MapView (ไม่ใช้กุญแจ เพราะแผนที่ฐานมาจาก OpenFreeMap)
        MapLibre.getInstance(this)
    }
}

package th.banceum.suchat.navigator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import th.banceum.suchat.navigator.ads.Ads
import th.banceum.suchat.navigator.geo.LatLon
import th.banceum.suchat.navigator.route.Place
import th.banceum.suchat.navigator.ui.MainViewModel
import th.banceum.suchat.navigator.ui.AppRoot
import th.banceum.suchat.navigator.ui.NavigatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // ถ่ายภาพหน้าจอสำหรับหน้าร้าน Play โดยไม่มีแบนเนอร์ "Test Ad" (เฉพาะรุ่น debug)
        // adb shell am start -n th.banceum.suchat.navigator/.MainActivity --ez screenshots true
        val screenshots = BuildConfig.DEBUG && intent.getBooleanExtra("screenshots", false)
        if (!screenshots) Ads.start(this)
        setContent { NavigatorTheme { AppRoot() } }
        // ทดสอบบน emulator (เฉพาะรุ่น debug): ตั้งจุดหมายจากคำสั่ง --es dest "lat,lon"
        if (BuildConfig.DEBUG) intent.getStringExtra("dest")?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }
            ?.takeIf { it.size == 2 }
            ?.let { (lat, lon) ->
                ViewModelProvider(this)[MainViewModel::class.java].select(Place("จุดทดสอบ", "", LatLon(lat, lon)))
            }
    }
}

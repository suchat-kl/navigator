package th.banceum.suchat.navigator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import th.banceum.suchat.navigator.BuildConfig
import th.banceum.suchat.navigator.geo.Geo
import th.banceum.suchat.navigator.route.NavServer
import th.banceum.suchat.navigator.route.Place

const val PRIVACY_URL = "https://suchat-kl.github.io/navigator/privacy-policy.html"

@Composable
fun SavedScreen(vm: MainViewModel, onBack: () -> Unit, onPick: (Place) -> Unit) {
    var confirm by remember { mutableStateOf<Place?>(null) }
    Surface(Modifier.fillMaxSize()) {
        Column {
            BackBar("สถานที่ที่บันทึก", onBack)
            if (vm.saved.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "ยังไม่มีสถานที่ที่บันทึก\nเลือกจุดหมายแล้วแตะไอคอนบุ๊กมาร์กเพื่อบันทึก",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.navigationBarsPadding()) {
                    items(vm.saved) { p ->
                        PlaceRow(
                            p,
                            distance = vm.here?.let { Geo.formatDistance(Geo.distance(it, p.pos)) },
                            trailing = { IconButton(onClick = { confirm = p }) { Icon(Icons.Filled.Delete, "ลบ") } },
                        ) { onPick(p) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("ลบ \"${p.name}\"?") },
            confirmButton = { TextButton(onClick = { vm.deleteSaved(p); confirm = null }) { Text("ลบ") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("ยกเลิก") } },
        )
    }
}

@Composable
fun AboutScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    Surface(Modifier.fillMaxSize()) {
        Column {
            BackBar("ตั้งค่าและเกี่ยวกับแอป", onBack)
            Column(
                Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("เสียงพูดนำทาง", style = MaterialTheme.typography.titleMedium)
                        Text("ใช้เสียงอ่านภาษาไทยของเครื่อง", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = vm.voiceOn, onCheckedChange = { vm.toggleVoice() })
                }
                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("เตือนจุดสำคัญ", style = MaterialTheme.typography.titleMedium)
                        Text("ด่านชั่งน้ำหนัก ด่านเก็บเงิน กล้องตรวจจับความเร็ว บนเส้นทางที่นำทาง " +
                            "(ข้อมูลจาก OpenStreetMap อาจไม่ครบหรือไม่เป็นปัจจุบัน)",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = vm.alertsOn, onCheckedChange = { vm.toggleAlerts() })
                }
                HorizontalDivider()

                if (vm.speedSupported) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("เตือนความเร็วเกินกำหนด", style = MaterialTheme.typography.titleMedium)
                            Text("โหมดรถยนต์ระหว่างนำทาง ใช้ป้ายจำกัดความเร็วใน OpenStreetMap ถ้ามี " +
                                "ไม่มีใช้อัตราตามกฎหมาย: ในกรุงเทพฯ เมืองพัทยา เขตเทศบาล / นอกเขตเมือง / ทางหลวงพิเศษ " +
                                "ใช้ประกอบเท่านั้น ให้ยึดป้ายจราจรจริงเสมอ",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = vm.speedOn, onCheckedChange = { vm.toggleSpeedWarn() })
                    }
                    HorizontalDivider()
                }

                Text("ข้อตกลงการใช้งาน", style = MaterialTheme.typography.titleMedium)
                Text(DISCLAIMER, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()

                Text("วิธีใช้", style = MaterialTheme.typography.titleMedium)
                Text(
                    "1. ค้นหาชื่อสถานที่ หรือแตะค้างบนแผนที่เพื่อเลือกจุดหมาย\n" +
                        "2. เลือกวิธีเดินทาง (รถยนต์ จักรยาน เดิน) แล้วกด \"หาเส้นทาง\"\n" +
                        "3. กด \"เริ่มนำทาง\" แอปจะบอกทางด้วยข้อความและเสียง และหาเส้นทางใหม่เองเมื่อออกนอกเส้นทาง\n" +
                        "ควรติดตั้งมือถือไว้กับที่ยึด และไม่ใช้งานมือถือขณะขับขี่ ปฏิบัติตามป้ายจราจรเสมอ",
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider()

                Text("แหล่งข้อมูล", style = MaterialTheme.typography.titleMedium)
                Text(
                    "แผนที่ จุดเตือน และป้ายจำกัดความเร็ว © ผู้ร่วมพัฒนา OpenStreetMap (ODbL) · " +
                        (if (NavServer.configured) "แผนที่ฐานจากเซิร์ฟเวอร์กรมทางหลวง (OpenMapTiles รูปแบบจาก OpenFreeMap) สำรองด้วย OpenFreeMap\n"
                        else "แผนที่ฐาน OpenFreeMap / OpenMapTiles\n") +
                        when {
                            NavServer.searchEnabled -> "หาเส้นทางและค้นหาสถานที่: เซิร์ฟเวอร์ระบบนำทางของกรมทางหลวง (OSRM, Nominatim) " +
                                "สำรองด้วย openrouteservice.org by HeiGIT เมื่อเซิร์ฟเวอร์กรมใช้งานไม่ได้"
                            NavServer.configured -> "หาเส้นทาง: เซิร์ฟเวอร์ระบบนำทางของกรมทางหลวง (OSRM) · ค้นหาสถานที่ © openrouteservice.org by HeiGIT"
                            else -> "หาเส้นทางและค้นหาสถานที่ © openrouteservice.org by HeiGIT"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { open("https://www.openstreetmap.org/copyright") }) { Text("สัญญาอนุญาต OpenStreetMap") }
                if (!vm.orsReady) {
                    Text(
                        "แอปรุ่นนี้ยังไม่ได้ใส่กุญแจ OpenRouteService จึงค้นหาและหาเส้นทางไม่ได้",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider()

                Text("ความเป็นส่วนตัว", style = MaterialTheme.typography.titleMedium)
                Text(
                    "แอปไม่มีบัญชีผู้ใช้ สถานที่ที่บันทึกและการตั้งค่าอยู่ในเครื่องนี้เท่านั้น " +
                        when {
                            NavServer.searchEnabled -> "ตำแหน่งต้นทาง/ปลายทางและคำค้นส่งไปเซิร์ฟเวอร์ระบบนำทางของกรมทางหลวงเพื่อหาเส้นทางและค้นหาสถานที่เท่านั้น " +
                                "(ช่วงที่เซิร์ฟเวอร์กรมใช้งานไม่ได้ ส่งไป OpenRouteService แทน) " +
                                "เมื่อเปิดเตือนความเร็ว ระหว่างนำทางด้วยรถยนต์จะส่งพิกัดปัจจุบันไปเซิร์ฟเวอร์กรมทุกราว 250 เมตร เพื่อหาอัตราจำกัดความเร็วของถนน"
                            NavServer.configured -> "ตำแหน่งต้นทาง/ปลายทางส่งไปเซิร์ฟเวอร์ระบบนำทางของกรมทางหลวงเพื่อหาเส้นทาง " +
                                "และคำค้นส่งไป OpenRouteService เพื่อค้นหาสถานที่เท่านั้น"
                            else -> "ตำแหน่งต้นทาง/ปลายทางและคำค้นจะส่งไป OpenRouteService เพื่อหาเส้นทางเท่านั้น"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { open(PRIVACY_URL) }) { Text("นโยบายความเป็นส่วนตัว") }
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("ล้างข้อมูลทั้งหมดในเครื่อง")
                }
                HorizontalDivider()
                Text("รุ่น ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("ล้างข้อมูลทั้งหมด?") },
            text = { Text("สถานที่ที่บันทึกและการตั้งค่าจะถูกลบ และกู้คืนไม่ได้") },
            confirmButton = { TextButton(onClick = { vm.clearAllData(); confirmClear = false }) { Text("ล้าง") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("ยกเลิก") } },
        )
    }
}

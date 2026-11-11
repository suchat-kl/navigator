package th.banceum.suchat.navigator

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import th.banceum.suchat.navigator.route.Failover
import th.banceum.suchat.navigator.route.OrsException
import th.banceum.suchat.navigator.route.ServerUnavailable

class FailoverTest {
    private var now = 0L
    private val f = Failover(cooldownMs = 120_000) { now }
    private var primaryCalls = 0
    private var serverUp = true

    private val primary: suspend () -> String = {
        primaryCalls++
        if (serverUp) "กรม" else throw ServerUnavailable("ล่ม")
    }
    private val backup: suspend () -> String = { "ORS" }

    @Test fun switchToBackupThenBackAfterCooldown() = runBlocking {
        assertEquals("กรม", f.run(primary, backup))
        serverUp = false
        assertEquals("ORS", f.run(primary, backup))           // ล่ม → ใช้สำรองในคำขอเดียวกัน
        assertTrue(f.primaryDown)
        val calls = primaryCalls
        now += 60_000
        assertEquals("ORS", f.run(primary, backup))           // ระหว่างพัก ไม่เรียกเครื่องกรม
        assertEquals(calls, primaryCalls)
        serverUp = true
        now += 61_000
        assertEquals("กรม", f.run(primary, backup))           // ครบเวลาพัก ลองเครื่องกรมอีกครั้ง → กลับมาใช้
        assertFalse(f.primaryDown)
    }

    @Test fun realRoutingErrorIsNotFailover() = runBlocking {
        try {
            f.run<String>({ throw OrsException("ไม่พบถนน") }, backup)
            fail()
        } catch (e: OrsException) {
            assertEquals("ไม่พบถนน", e.message)
        }
        assertFalse(f.primaryDown)
    }

    @Test fun noBackupThrowsServerError() = runBlocking {
        serverUp = false
        try {
            f.run(primary, null)
            fail()
        } catch (e: ServerUnavailable) {
            assertEquals("ล่ม", e.message)
        }
        // ไม่มีสำรอง: ไม่พักเครื่องกรม เรียกใหม่ได้ทุกครั้ง
        serverUp = true
        assertEquals("กรม", f.run(primary, null))
    }

    @Test fun backupOnlyWhenNoPrimary() = runBlocking {
        assertEquals("ORS", f.run(null, backup))
    }
}

package com.noir.companion.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoYoungProtocolTest {
    @Test fun commandPacketHasObservedHeaderLengthAndOpcode() {
        assertArrayEquals(
            byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x05, 0x61),
            MoYoungProtocol.buildCommand(MoYoungProtocol.CMD_FIND_MY_WATCH),
        )
        assertArrayEquals(
            byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x07, 0x43, 0x01, 0x02),
            MoYoungProtocol.buildCommand(0x43, byteArrayOf(1, 2)),
        )
    }

    @Test fun settingIntegersPreserveDocumentedAsymmetricEndianness() {
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), MoYoungProtocol.u32be(0x01020304))
        assertEquals(0x04030201L, MoYoungProtocol.readU32le(byteArrayOf(1, 2, 3, 4)))
        assertNull(MoYoungProtocol.readU32le(byteArrayOf(1, 2, 3)))
    }

    @Test fun healthSentinelIsUnavailableRatherThan255() {
        val invalid = MoYoungProtocol.parseSingleByteMeasurement("SpO2", byteArrayOf(0xFF.toByte()), "%")
        assertFalse(invalid.valid)
        assertNull(invalid.value)
        assertTrue(invalid.note!!.contains("sentinel"))
        val valid = MoYoungProtocol.parseSingleByteMeasurement("HR", byteArrayOf(72), "bpm")
        assertTrue(valid.valid)
        assertEquals(72, valid.value)
    }

    @Test fun guardBlocksOnlyUnverifiedGalleryTransferPaths() {
        val service = "0000feea-0000-1000-8000-00805f9b34fb"
        val prep = byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x09, 0x74, 0, 0, 0, 1)
        val apply = byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x06, 0x19, 0x0D)
        assertTrue(WatchFaceUploadGuard.shouldBlock(service, "fee2", prep))
        assertTrue(WatchFaceUploadGuard.shouldBlock(service, "0000fee6-0000-1000-8000-00805f9b34fb", byteArrayOf(1, 2)))
        assertTrue(WatchFaceUploadGuard.shouldBlock(service, "0xFEE2", apply))
        assertFalse(WatchFaceUploadGuard.shouldBlock(service, "fee2", MoYoungProtocol.buildCommand(0x61)))
        assertFalse(WatchFaceUploadGuard.shouldBlock("180f", "fee6", byteArrayOf(1)))
    }
}

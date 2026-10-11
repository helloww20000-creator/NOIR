package com.noir.companion.core

import java.util.UUID

/** Verified UUID constants and conservative parsers for the observed MoYoung V2 service. */
object MoYoungProtocol {
    val SERVICE_FEEA = UUID.fromString("0000FEEA-0000-1000-8000-00805F9B34FB")
    val FEE1 = UUID.fromString("0000FEE1-0000-1000-8000-00805F9B34FB")
    val FEE2 = UUID.fromString("0000FEE2-0000-1000-8000-00805F9B34FB")
    val FEE3 = UUID.fromString("0000FEE3-0000-1000-8000-00805F9B34FB")
    val FEE4 = UUID.fromString("0000FEE4-0000-1000-8000-00805F9B34FB")
    val FEE5 = UUID.fromString("0000FEE5-0000-1000-8000-00805F9B34FB")
    val FEE6 = UUID.fromString("0000FEE6-0000-1000-8000-00805F9B34FB")
    val SERVICE_FEE7 = UUID.fromString("0000FEE7-0000-1000-8000-00805F9B34FB")
    val FEC9 = UUID.fromString("0000FEC9-0000-1000-8000-00805F9B34FB")
    val FEA1 = UUID.fromString("0000FEA1-0000-1000-8000-00805F9B34FB")
    val BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    val DEVICE_INFO_SERVICE = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")

    data class Measurement(val kind: String, val value: Int?, val unit: String, val rawHex: String, val valid: Boolean, val note: String? = null)

    /** Common 0xFF sentinel readings are explicitly treated as unavailable. */
    fun parseSingleByteMeasurement(kind: String, bytes: ByteArray, unit: String): Measurement {
        val raw = bytes.joinToString("-") { "%02X".format(it) }
        val value = bytes.firstOrNull()?.toInt()?.and(0xFF)
        val valid = value != null && value != 0xFF
        return Measurement(kind, if (valid) value else null, unit, raw, valid,
            if (value == 0xFF) "Watch returned 0xFF sentinel; not a real measurement" else null)
    }

    fun formatHex(bytes: ByteArray) = bytes.joinToString("-") { "%02X".format(it) }
}

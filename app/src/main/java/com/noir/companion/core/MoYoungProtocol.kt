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

    // Command IDs transcribed from the supplied DaFit Canvas source.
    const val CMD_FIND_MY_WATCH = 0x61
    const val CMD_FIND_MY_PHONE = 0x62
    const val CMD_TRIGGER_HEART_RATE = 0x6D
    const val CMD_TRIGGER_BLOOD_PRESSURE = 0x69
    const val CMD_TRIGGER_SPO2 = 0x6B
    const val CMD_SEND_MESSAGE = 0x41
    const val CMD_SYNC_TIME = 0x31
    const val CMD_SET_WEATHER_TODAY = 0x43
    const val CMD_SET_STEP_GOAL = 0x16
    const val CMD_QUERY_STEP_GOAL = 0x26
    const val CMD_SET_TIME_SYSTEM = 0x17
    const val CMD_SET_METRIC_SYSTEM = 0x1A
    const val CMD_SET_DEVICE_LANGUAGE = 0x1B
    const val CMD_SET_WATCH_FACE = 0x19

    /** DaFit/Gadgetbridge wall-clock payload: u32 BE seconds, timezone metadata 8. */
    fun buildSyncTimePayload(nowMillis: Long = System.currentTimeMillis()): ByteArray {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        val localAsUtc = java.time.LocalDateTime.of(
            c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.HOUR_OF_DAY),
            c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND)
        ).toEpochSecond(java.time.ZoneOffset.UTC)
        val seconds = (localAsUtc - 8L * 3600L).toInt()
        return byteArrayOf(
            (seconds ushr 24).toByte(), (seconds ushr 16).toByte(),
            (seconds ushr 8).toByte(), seconds.toByte(), 8
        )
    }

    enum class Weather(val wireCode: Int) {
        CLOUDY(0), FOGGY(1), OVERCAST(2), RAINY(3), SNOWY(4),
        SUNNY(5), SANDSTORM(6), HAZE(7)
    }

    /** Weather: PM2.5 flag, condition, signed Celsius, UTF-16BE city. */
    fun buildWeatherPayload(condition: Weather, tempC: Int, city: String): ByteArray {
        val cityBytes = ByteArray(city.length * 2)
        city.forEachIndexed { i, ch ->
            cityBytes[i * 2] = (ch.code ushr 8).toByte()
            cityBytes[i * 2 + 1] = ch.code.toByte()
        }
        val temp = tempC.coerceIn(-128, 127)
        return byteArrayOf(0, condition.wireCode.toByte(), temp.toByte()) + cityBytes
    }

    /** Outgoing setting integers use big-endian in the supplied implementation. */
    fun u32be(value: Long): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(),
        (value ushr 8).toByte(), value.toByte()
    )

    /** Query response integers use little-endian in the supplied implementation. */
    fun readU32le(bytes: ByteArray): Long? {
        if (bytes.size < 4) return null
        return (bytes[0].toLong() and 0xff) or
            ((bytes[1].toLong() and 0xff) shl 8) or
            ((bytes[2].toLong() and 0xff) shl 16) or
            ((bytes[3].toLong() and 0xff) shl 24)
    }

    /** Complete control packet for FEE2. */
    fun buildCommand(opcode: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        val total = payload.size + 5
        return byteArrayOf(
            0xFE.toByte(), 0xEA.toByte(),
            (0x20 + ((total shr 8) and 0xff)).toByte(),
            (total and 0xff).toByte(), opcode.toByte()
        ) + payload
    }

    fun buildNotificationPayload(type: Int, message: String): ByteArray {
        val text = message.toByteArray(Charsets.UTF_8)
        return byteArrayOf(type.toByte()) + text
    }

    fun buildStepGoalPayload(goal: Long): ByteArray = byteArrayOf(
        (goal ushr 24).toByte(), (goal ushr 16).toByte(),
        (goal ushr 8).toByte(), goal.toByte()
    )

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

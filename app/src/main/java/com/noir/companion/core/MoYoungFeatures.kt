package com.noir.companion.core

/**
 * Native high-level feature adapter ported from DaFit Canvas' MoYoung V2 protocol.
 * Commands are explicit and auditable; callers should only expose features verified
 * against the connected firmware.
 */
class MoYoungFeatures(private val ble: BleDeviceRepository) {
    fun subscribeToReplies() = ble.subscribeToMoYoungReplies()

    fun findMyWatch() = ble.sendMoYoungCommand(MoYoungProtocol.CMD_FIND_MY_WATCH)
    fun findMyPhone() = ble.sendMoYoungCommand(MoYoungProtocol.CMD_FIND_MY_PHONE)

    fun syncTime(nowMillis: Long = System.currentTimeMillis()) =
        ble.sendMoYoungCommand(MoYoungProtocol.CMD_SYNC_TIME, MoYoungProtocol.buildSyncTimePayload(nowMillis))

    fun requestHeartRate() = ble.sendMoYoungCommand(MoYoungProtocol.CMD_TRIGGER_HEART_RATE)
    fun requestBloodPressure() = ble.sendMoYoungCommand(MoYoungProtocol.CMD_TRIGGER_BLOOD_PRESSURE)
    fun requestSpO2() = ble.sendMoYoungCommand(MoYoungProtocol.CMD_TRIGGER_SPO2)

    fun setStepGoal(goal: Long) {
        require(goal in 1..200_000) { "Step goal must be between 1 and 200000" }
        ble.sendMoYoungCommand(MoYoungProtocol.CMD_SET_STEP_GOAL, MoYoungProtocol.u32be(goal))
    }

    fun queryStepGoal() = ble.sendMoYoungCommand(MoYoungProtocol.CMD_QUERY_STEP_GOAL)
    fun set12HourClock(enabled: Boolean) =
        ble.sendMoYoungCommand(MoYoungProtocol.CMD_SET_TIME_SYSTEM, byteArrayOf(if (enabled) 1 else 0))
    fun setMetricUnits(enabled: Boolean) =
        ble.sendMoYoungCommand(MoYoungProtocol.CMD_SET_METRIC_SYSTEM, byteArrayOf(if (enabled) 1 else 0))
    fun setDeviceLanguage(languageCode: Int) {
        require(languageCode in 0..255) { "Language code must fit in one byte" }
        ble.sendMoYoungCommand(MoYoungProtocol.CMD_SET_DEVICE_LANGUAGE, byteArrayOf(languageCode.toByte()))
    }

    fun sendNotification(type: NotificationType, text: String) {
        require(text.isNotBlank()) { "Notification text cannot be empty" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 480) { "Notification too long; keep it under 480 UTF-8 bytes" }
        ble.sendMoYoungCommand(MoYoungProtocol.CMD_SEND_MESSAGE, byteArrayOf(type.wireValue.toByte()) + bytes)
    }

    fun sendWeather(condition: MoYoungProtocol.Weather, temperatureC: Int, city: String) {
        require(city.length <= 40) { "City name too long" }
        ble.sendMoYoungCommand(
            MoYoungProtocol.CMD_SET_WEATHER_TODAY,
            MoYoungProtocol.buildWeatherPayload(condition, temperatureC, city)
        )
    }

    enum class NotificationType(val wireValue: Int) {
        CALL_OFF_HOOK(-1), CALL(0), SMS(1), WECHAT(2), QQ(3), FACEBOOK(4),
        TWITTER(5), INSTAGRAM(6), SKYPE(7), WHATSAPP(8), LINE(9), KAKAO(10), OTHER(11)
    }
}

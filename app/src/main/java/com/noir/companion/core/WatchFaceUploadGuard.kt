package com.noir.companion.core

/**
 * Blocks only the DaFit Canvas Gallery watch-face upload/apply paths whose
 * handshake is not verified on the NSW-309 firmware. This is a safety gate,
 * not a claim that other FEEA writes are compatible.
 */
object WatchFaceUploadGuard {
    private const val MOYOUNG_SERVICE = "feea"
    private const val CONTROL_CHARACTERISTIC = "fee2"
    private const val FILE_CHARACTERISTIC = "fee6"

    fun shouldBlock(serviceUuid: String, characteristicUuid: String, bytes: ByteArray): Boolean {
        if (shortUuid(serviceUuid) != MOYOUNG_SERVICE) return false
        return when (shortUuid(characteristicUuid)) {
            FILE_CHARACTERISTIC -> true // file payload chunks
            CONTROL_CHARACTERISTIC -> isGalleryPreparation(bytes) || isGalleryApply(bytes)
            else -> false
        }
    }

    private fun isGalleryPreparation(bytes: ByteArray): Boolean =
        bytes.size >= 5 && bytes[0].u8() == 0xFE && bytes[1].u8() == 0xEA &&
            bytes[2].u8() == 0x20 && bytes[3].u8() == 0x09 && bytes[4].u8() == 0x74

    private fun isGalleryApply(bytes: ByteArray): Boolean =
        bytes.size >= 6 && bytes[0].u8() == 0xFE && bytes[1].u8() == 0xEA &&
            bytes[2].u8() == 0x20 && bytes[3].u8() == 0x06 &&
            bytes[4].u8() == 0x19 && bytes[5].u8() == 0x0D

    private fun Byte.u8(): Int = toInt() and 0xFF

    private fun shortUuid(value: String): String {
        val normalized = value.trim().lowercase().removePrefix("0x").replace("-", "")
        return when {
            normalized.length == 4 -> normalized
            normalized.length == 32 && normalized.startsWith("0000") -> normalized.substring(4, 8)
            else -> normalized
        }
    }
}

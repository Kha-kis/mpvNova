package app.mpvnova.player

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Reads a font's real family name so the picker shows it and sub-font matches.
internal object SubtitleFontTable {
    private const val TAG_TTCF = 0x74746366 // 'ttcf'
    private const val TAG_NAME = 0x6E616D65 // 'name'
    private const val MASK16 = 0xFFFF
    private const val TTC_FIRST_OFFSET_POS = 12
    private const val TABLE_RECORD_SKIP = 6
    private const val NAME_ID_FAMILY = 1
    private const val NAME_ID_TYPO_FAMILY = 16
    private const val PLATFORM_UNICODE = 0
    private const val PLATFORM_WINDOWS = 3
    private const val SCORE_TYPO = 2
    private const val SCORE_WINDOWS = 1

    fun familyName(file: File): String? = runCatching { parse(file.readBytes()) }.getOrNull()

    private fun parse(data: ByteArray): String? {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        bb.position(tableDirectoryOffset(bb))
        bb.int // sfntVersion
        val numTables = bb.short.toInt() and MASK16
        bb.position(bb.position() + TABLE_RECORD_SKIP) // searchRange, entrySelector, rangeShift
        val nameOffset = findNameTableOffset(bb, numTables) ?: return null
        return readBestFamily(data, bb, nameOffset)
    }

    private fun tableDirectoryOffset(bb: ByteBuffer): Int {
        return if (bb.int == TAG_TTCF) {
            bb.position(TTC_FIRST_OFFSET_POS) // tag(4) + version(4) + numFonts(4)
            bb.int
        } else {
            0
        }
    }

    private fun findNameTableOffset(bb: ByteBuffer, numTables: Int): Int? {
        repeat(numTables) {
            val tag = bb.int
            bb.int // checksum
            val offset = bb.int
            bb.int // length
            if (tag == TAG_NAME) return offset
        }
        return null
    }

    private fun readBestFamily(data: ByteArray, bb: ByteBuffer, nameOffset: Int): String? {
        bb.position(nameOffset)
        bb.short // format
        val count = bb.short.toInt() and MASK16
        val stringOffset = bb.short.toInt() and MASK16
        var best: String? = null
        var bestScore = -1
        repeat(count) {
            val platformId = bb.short.toInt() and MASK16
            bb.short // encodingId
            bb.short // languageId
            val nameId = bb.short.toInt() and MASK16
            val length = bb.short.toInt() and MASK16
            val offset = bb.short.toInt() and MASK16
            if (nameId == NAME_ID_FAMILY || nameId == NAME_ID_TYPO_FAMILY) {
                val text = decodeName(data, nameOffset + stringOffset + offset, length, platformId)
                val score = scoreName(nameId, platformId)
                if (text != null && score > bestScore) {
                    best = text
                    bestScore = score
                }
            }
        }
        return best?.trim()?.ifEmpty { null }
    }

    private fun decodeName(data: ByteArray, start: Int, length: Int, platformId: Int): String? {
        if (length <= 0 || start < 0 || start + length > data.size) return null
        val bytes = data.copyOfRange(start, start + length)
        return when (platformId) {
            PLATFORM_WINDOWS, PLATFORM_UNICODE -> String(bytes, Charsets.UTF_16BE)
            else -> String(bytes, Charsets.ISO_8859_1)
        }
    }

    private fun scoreName(nameId: Int, platformId: Int): Int {
        var score = 0
        if (nameId == NAME_ID_TYPO_FAMILY) score += SCORE_TYPO
        if (platformId == PLATFORM_WINDOWS) score += SCORE_WINDOWS
        return score
    }
}

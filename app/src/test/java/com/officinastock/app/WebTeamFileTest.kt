package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * 4.15: Ubicora Web reads the team files of the phones with its own code. This file was written
 * by that code (web/core.mjs, teamEncrypt): if the phone's cipher opens it, the two speak the same
 * format (salt, nonce, authenticated data, frames, end marker, gzip).
 */
class WebTeamFileTest {
    private fun fixture() = javaClass.classLoader!!.getResourceAsStream("team_file_from_web.bin")!!.use { it.readBytes() }

    @Test fun fileWrittenByTheBrowserOpensWithThePhoneCipher() {
        val bytes = fixture()
        assertTrue("more than one 64 KiB frame", bytes.size > 70_000)
        val text = GZIPInputStream(BackupCipher.decrypt(ByteArrayInputStream(bytes), "squadra-2026")).use { it.readBytes() }.toString(Charsets.UTF_8)
        assertTrue(text.startsWith("{\"format\":\"UBICORA_SYNC_STATE\",\"version\":1,\"note\":\"fixture scritta da Ubicora Web\""))
        assertTrue(text.endsWith("\"}"))
    }

    @Test fun wrongPasswordIsRefused() {
        try {
            GZIPInputStream(BackupCipher.decrypt(ByteArrayInputStream(fixture()), "sbagliata-2026")).use { it.readBytes() }
            fail("aperto con la password sbagliata")
        } catch (expected: java.io.IOException) {}
    }
}

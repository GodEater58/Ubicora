package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** 4.21.2: full backups are written as B3 (600,000 rounds); B2 files (old backups, team sync) still open. */
class BackupCipherFormatTest {
    private fun roundTrip(strong: Boolean): Pair<String, ByteArray> {
        val data = ByteArray(70_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        BackupCipher.encrypt(out, "password-di-prova", strong).use { it.write(data) }
        val file = out.toByteArray()
        val back = BackupCipher.decrypt(ByteArrayInputStream(file), "password-di-prova").use { it.readBytes() }
        assertArrayEquals(data, back)
        return String(file.copyOf(8), Charsets.US_ASCII) to file
    }

    @Test fun strongBackupsAreB3AndTeamFilesStayB2() {
        assertEquals("OSTOCKB3", roundTrip(strong = true).first)
        assertEquals("OSTOCKB2", roundTrip(strong = false).first)
    }

    @Test fun aB3FileDoesNotOpenAsB2() {
        val file = roundTrip(strong = true).second
        "OSTOCKB2".toByteArray(Charsets.US_ASCII).copyInto(file)
        assertTrue(runCatching { BackupCipher.decrypt(ByteArrayInputStream(file), "password-di-prova").use { it.readBytes() } }.isFailure)
    }
}

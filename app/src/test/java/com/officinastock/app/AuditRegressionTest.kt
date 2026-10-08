package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AuditRegressionTest {
    @Test fun pinchKeepsAnchorAndClampsZoom() {
        val before=PlanViewport(900f,700f,12f,8f)
        val anchor=PointMetres(450f,350f)
        val world=before.toWorld(anchor)
        val after=before.transformed(2f,anchor,PointMetres(0f,0f))
        assertEquals(2f,after.zoom,0f)
        assertEquals(world.x,after.toWorld(anchor).x,.001f)
        assertEquals(world.y,after.toWorld(anchor).y,.001f)
        assertEquals(4f,after.transformed(100f,anchor,PointMetres(100000f,-100000f)).zoom,0f)
    }
    @Test fun labelFitsEvenWhenViewportIsShorterThanBadge() {
        val v=PlanViewport(200f,10f,12f,8f)
        assertEquals(5f,v.insetCoordinate(200f,10f,24f),0f)
        assertEquals(0f,v.insetCoordinate(-20f,0f,24f),0f)
    }
    private fun encrypted():ByteArray = ByteArrayOutputStream().also { out ->
        BackupCipher.encrypt(out,"password-test").use {it.write(ByteArray(140000){(it%251).toByte()})}
    }.toByteArray()
    private fun decrypt(bytes:ByteArray,password:String="password-test") =
        BackupCipher.decrypt(ByteArrayInputStream(bytes),password).use{it.readBytes()}
    private fun rejects(block:()->Unit) { try{block();fail("Invalid backup accepted")}catch(expected:Exception){} }
    @Test fun encryptedBackupRoundTripsMultipleFrames() {
        assertArrayEquals(ByteArray(140000){(it%251).toByte()},decrypt(encrypted()))
    }
    @Test fun backupRejectsWrongPasswordTamperingTruncationAndTrailingBytes() {
        val bytes=encrypted()
        rejects {decrypt(bytes,"incorrect-password")}
        rejects {decrypt(bytes.copyOf(bytes.size-1))}
        rejects {decrypt(bytes+byteArrayOf(1))}
        rejects {decrypt(bytes.clone().also{it[50]=(it[50].toInt() xor 1).toByte()})}
    }
}

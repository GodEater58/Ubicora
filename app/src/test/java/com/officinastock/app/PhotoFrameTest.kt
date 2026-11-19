package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test

/** 4.21: what is inside the camera frame is exactly the photo of the article card. */
class PhotoFrameTest {
    @Test fun theFrameHasTheShapeOfTheCardPhoto() {
        val (w, h) = PhotoFiles.frameSize(1080f, 2160f)
        assertEquals(PhotoFiles.CARD_ASPECT, w / h, 0.001f)
        assertTrue(w <= 1080f && h <= 2160f)
        // A very low preview (landscape): the frame shrinks to fit, same shape.
        val (lw, lh) = PhotoFiles.frameSize(2000f, 600f)
        assertEquals(PhotoFiles.CARD_ASPECT, lw / lh, 0.001f)
        assertTrue(lh <= 600f * 0.7f + 0.01f)
    }

    @Test fun theCutIsTheFramedPartOfThePhotoCentred() {
        // Portrait photo 3000x4000 on a 1080x2160 preview that fills the screen (centre crop).
        val (fw, fh) = PhotoFiles.frameSize(1080f, 2160f)
        val c = PhotoFiles.frameCrop(3000, 4000, 1080f, 2160f, fw, fh)
        val scale = maxOf(1080f / 3000, 2160f / 4000)
        assertEquals(fw / scale, c[2].toFloat(), 2f)
        assertEquals(fh / scale, c[3].toFloat(), 2f)
        assertTrue(kotlin.math.abs(3000 - (c[0] * 2 + c[2])) <= 1)   // centred horizontally
        assertTrue(kotlin.math.abs(4000 - (c[1] * 2 + c[3])) <= 1)   // and vertically
        assertEquals(PhotoFiles.CARD_ASPECT, c[2].toFloat() / c[3], 0.01f)
    }

    @Test fun theCutNeverLeavesThePhoto() {
        val c = PhotoFiles.frameCrop(100, 50, 1000f, 1000f, 990f, 990f)
        assertTrue(c[0] >= 0 && c[1] >= 0 && c[0] + c[2] <= 100 && c[1] + c[3] <= 50)
    }
}

package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test

class PlanGeometryTest {
    @Test fun viewportRoundTripAfterZoomPan() {
        val v=PlanViewport(1080f,720f,12f,8f,2.2f,95f,-35f)
        val point=PointMetres(3.75f,5.25f)
        val result=v.toWorld(v.toScreen(point))
        assertEquals(point.x,result.x,.001f)
        assertEquals(point.y,result.y,.001f)
    }
    @Test fun gridAndMagneticWallSnap() {
        assertEquals(2.25f,PlanGeometry.snap(2.33f),.001f)
        val walls=listOf(Wall(1,1,0f,0f,4f,0f,"manual"))
        assertEquals(PointMetres(4f,0f),PlanGeometry.snapToWallEndpoint(PointMetres(4.1f,.08f),walls))
    }
    @Test fun rotationHitTest() {
        val fixture=Placement(1,1,"Scaffale","A",5f,4f,false,2f,.8f,90f)
        assertTrue(PlanGeometry.contains(PointMetres(5f,4.9f),fixture))
        assertFalse(PlanGeometry.contains(PointMetres(6.7f,4f),fixture))
    }
    @Test fun dimensionsAndOrthogonal() {
        val fixed=PlanGeometry.orthogonal(PointMetres(2f,3f),PointMetres(6f,3.2f))
        assertEquals(3f,fixed.y,.001f)
        assertFalse(PlanGeometry.validWall(PointMetres(1f,1f),PointMetres(1.03f,1.03f)))
    }
}

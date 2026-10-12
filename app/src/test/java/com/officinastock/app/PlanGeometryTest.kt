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
    @Test fun wallNearStartCornerClosesSquare() {
        val walls=listOf(Wall(1,1,1f,1f,2f,1f,"manual"),Wall(2,1,2f,1f,2f,2f,"manual"),Wall(3,1,2f,2f,1f,2f,"manual"))
        val end=PlanGeometry.wallPoint(PointMetres(1.08f,1.1f),PointMetres(1f,2f),walls,.3f,true,true)
        assertEquals(WallSnapKind.VERTEX,end.kind)
        assertEquals(PointMetres(1f,1f),end.point)
        assertTrue(end.closes)
    }
    @Test fun cornerBeatsOrthogonalSoTrianglesClose() {
        val walls=listOf(Wall(1,1,0f,0f,3f,0f,"manual"),Wall(2,1,3f,0f,3f,2f,"manual"))
        val end=PlanGeometry.wallPoint(PointMetres(.1f,.12f),PointMetres(3f,2f),walls,.3f,false,true)
        assertEquals(PointMetres(0f,0f),end.point)
        assertTrue(end.closes)
    }
    @Test fun wallJustDrawnDoesNotCaptureItsOwnStart() {
        val walls=listOf(Wall(1,1,0f,0f,1f,0f,"manual"))
        val end=PlanGeometry.wallPoint(PointMetres(.15f,.05f),PointMetres(1f,0f),walls,.3f,false,false)
        assertFalse(end.closes)
    }
    @Test fun strokeEndingOnAWallHooksOntoIt() {
        val walls=listOf(Wall(1,1,0f,0f,0f,6f,"manual"))
        val end=PlanGeometry.wallPoint(PointMetres(.12f,2.6f),PointMetres(2f,2.5f),walls,.3f,false,true)
        assertEquals(WallSnapKind.SEGMENT,end.kind)
        assertEquals(0f,end.point.x,.001f)
        assertEquals(2.5f,end.point.y,.001f)
    }
    @Test fun farFromGeometryKeepsGridAndChainOpen() {
        val walls=listOf(Wall(1,1,0f,0f,1f,0f,"manual"))
        val end=PlanGeometry.wallPoint(PointMetres(3.1f,3.4f),PointMetres(1f,0f),walls,.3f,true,false)
        assertEquals(PointMetres(3f,3.5f),end.point)
        assertFalse(end.closes)
    }
    @Test fun rotationHitTest() {
        val fixture=Placement(1,1,"Scaffale","A",5f,4f,false,2f,.8f,90f)
        assertTrue(PlanGeometry.contains(PointMetres(5f,4.9f),fixture))
        assertFalse(PlanGeometry.contains(PointMetres(6.7f,4f),fixture))
    }
    @Test fun rotatedFixtureBoundsStayInsideRoom() {
        val straight=PlanGeometry.boundedPlacementCenter(0f,0f,2f,.8f,0f,12f,8f)
        assertEquals(1f,straight.x,.001f)
        assertEquals(.4f,straight.y,.001f)
        val rotated=PlanGeometry.boundedPlacementCenter(0f,0f,2f,.8f,90f,12f,8f)
        assertEquals(.4f,rotated.x,.001f)
        assertEquals(1f,rotated.y,.001f)
        val skew=PlanGeometry.boundedPlacementCenter(12f,8f,2f,1f,45f,12f,8f)
        val half=3f*kotlin.math.sqrt(.5f)/2f
        assertEquals(12f-half,skew.x,.001f)
        assertEquals(8f-half,skew.y,.001f)
        assertTrue(PlanGeometry.placementFitsRoom(
            Placement(1,1,"Scaffale","A",skew.x,skew.y,false,2f,1f,45f),12f,8f))
    }
    @Test fun oversizedFixtureIsRecognizedAsNotFitting() {
        val center=PlanGeometry.boundedPlacementCenter(-3f,50f,15f,10f,0f,2f,3f)
        assertEquals(1f,center.x,.001f)
        assertEquals(1.5f,center.y,.001f)
        assertFalse(PlanGeometry.placementFitsRoom(
            Placement(1,1,"Area","Gigante",center.x,center.y,false,15f,10f,0f),2f,3f))
        assertTrue(runCatching{PlanGeometry.boundedPlacementCenter(Float.NaN,0f,1f,1f,0f,12f,8f)}.isFailure)
    }
    @Test fun dimensionsAndOrthogonal() {
        val fixed=PlanGeometry.orthogonal(PointMetres(2f,3f),PointMetres(6f,3.2f))
        assertEquals(3f,fixed.y,.001f)
        assertFalse(PlanGeometry.validWall(PointMetres(1f,1f),PointMetres(1.03f,1.03f)))
    }
}

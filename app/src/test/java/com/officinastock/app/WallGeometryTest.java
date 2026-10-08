package com.officinastock.app;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;

public class WallGeometryTest {
    @Test public void collinearWallsMerge() {
        var one = new WallGeometry.Segment(0,0,2,0);
        var two = new WallGeometry.Segment(2.08f,.06f,4,.06f);
        assertEquals(1,WallGeometry.mergeSegments(Arrays.asList(one,two)).size());
    }
    @Test public void parallelWallsStaySeparate() {
        var one = new WallGeometry.Segment(0,0,2,0);
        var two = new WallGeometry.Segment(0,1.2f,2,1.2f);
        assertEquals(2,WallGeometry.mergeSegments(Arrays.asList(one,two)).size());
    }
    @Test public void tinyWallsAreRejected() {
        assertNull(WallGeometry.widestProjectedSegment(new float[]{0,0,.05f,.02f}));
    }
}

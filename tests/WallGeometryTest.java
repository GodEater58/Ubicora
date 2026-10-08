import com.officinastock.app.WallGeometry;
import java.util.List;
public class WallGeometryTest {
    static void yes(boolean b){if(!b)throw new AssertionError("Wall geometry failed");}
    public static void main(String[] args){
        var first=new WallGeometry.Segment(0f,0f,2f,0f);
        var second=new WallGeometry.Segment(2.05f,0.04f,4f,0.04f);
        var combined=WallGeometry.mergeSegments(List.of(first,second));
        yes(combined.size()==1);
        yes(Math.abs(WallGeometry.totalMeters(combined)-4f)<.2f);
        yes(WallGeometry.mergeSegments(List.of(first,new WallGeometry.Segment(0f,2f,2f,2f))).size()==2);
        yes(WallGeometry.widestProjectedSegment(new float[]{0,0,2,0,1,0})!=null);
        yes(WallGeometry.widestProjectedSegment(new float[]{0,0,.01f,0})==null);
        System.out.println("5/5 test geometria pareti superati");
    }
}

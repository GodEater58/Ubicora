package com.officinastock.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Geometria metrica per pareti AR. Indipendente da Android, testabile con javac. */
public final class WallGeometry {
    private WallGeometry() { }
    public static final class Segment {
        public final float x1, z1, x2, z2;
        public Segment(float x1,float z1,float x2,float z2) {
            if(!Float.isFinite(x1)||!Float.isFinite(z1)||!Float.isFinite(x2)||!Float.isFinite(z2))
                throw new IllegalArgumentException("coordinate non finite");
            this.x1=x1;this.z1=z1;this.x2=x2;this.z2=z2;
        }
        public float length() { return (float)Math.hypot(x2-x1,z2-z1); }
    }
    private static float distance(float x1,float y1,float x2,float y2) {
        return (float)Math.hypot(x1-x2,y1-y2);
    }
    /** Estrae l'estensione orizzontale di un poligono di piano verticale proiettato su X/Z. */
    public static Segment widestProjectedSegment(float[] xz) {
        if(xz==null || xz.length<4 || xz.length%2!=0) return null;
        double best=0;int aa=0,bb=0;
        for(int a=0;a<xz.length;a+=2)for(int b=a+2;b<xz.length;b+=2){
            if(!Float.isFinite(xz[a])||!Float.isFinite(xz[a+1])||!Float.isFinite(xz[b])||!Float.isFinite(xz[b+1])) continue;
            double d=Math.hypot(xz[a]-xz[b],xz[a+1]-xz[b+1]);
            if(d>best){best=d;aa=a;bb=b;}
        }
        if(best<.40 || best>100) return null;
        return new Segment(xz[aa],xz[aa+1],xz[bb],xz[bb+1]);
    }
    /** Unisce tratti dello stesso muro senza collegare pareti parallele separate o aperture ampie. */
    public static List<Segment> mergeSegments(Collection<Segment> incoming) {
        List<Segment> result=new ArrayList<>();
        if(incoming==null) return result;
        for(Segment segment:incoming){
            if(segment==null || segment.length()<.40f)continue;
            Segment active=segment;
            boolean found;
            do{
                found=false;
                for(int i=0;i<result.size();i++){
                    Segment joined=joinIfCompatible(active,result.get(i));
                    if(joined!=null){active=joined;result.remove(i);found=true;break;}
                }
            }while(found);
            result.add(active);
        }
        return result;
    }
    private static Segment joinIfCompatible(Segment a, Segment b) {
        float al=a.length(),bl=b.length();
        if(al<.4f||bl<.4f)return null;
        float dx=(a.x2-a.x1)/al,dz=(a.z2-a.z1)/al;
        float bx=(b.x2-b.x1)/bl,bz=(b.z2-b.z1)/bl;
        if(Math.abs(dx*bx+dz*bz)<Math.cos(Math.toRadians(12)))return null;
        // Distanza reciproca delle rette (non unisce muri sfalsati/paralleli).
        float off1=Math.abs((b.x1-a.x1)*(-dz)+(b.z1-a.z1)*dx);
        float off2=Math.abs((b.x2-a.x1)*(-dz)+(b.z2-a.z1)*dx);
        if(Math.max(off1,off2)>.28f)return null;
        float b1=(b.x1-a.x1)*dx+(b.z1-a.z1)*dz;
        float b2=(b.x2-a.x1)*dx+(b.z2-a.z1)*dz;
        float lo=Math.min(0,Math.min(b1,b2)),hi=Math.max(al,Math.max(b1,b2));
        float intervalGap=Math.max(0,Math.max(Math.min(b1,b2)-al,-Math.max(b1,b2)));
        if(intervalGap>.50f)return null;
        return new Segment(a.x1+dx*lo,a.z1+dz*lo,a.x1+dx*hi,a.z1+dz*hi);
    }
    public static float totalMeters(Collection<Segment> segments) {
        float sum=0;for(Segment s:segments)sum+=s.length();return sum;
    }
}

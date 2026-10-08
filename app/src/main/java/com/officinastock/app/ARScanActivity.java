package com.officinastock.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Rilevamento AR reale: usa tracking ARCore + piani VERTICALI.
 * Non si spaccia una traiettoria IMU per un muro: salva solo superfici AR tracciate.
 * La scansione produce segmenti di parete in metri, non una pianta certificata.
 */
public class ARScanActivity extends Activity implements GLSurfaceView.Renderer {
    private static final int REQUEST_CAMERA=778;
    private GLSurfaceView cameraSurface;
    private ScanMiniMap miniMap;
    private TextView statusText, detailText;
    private Button saveButton;
    private volatile Session session;
    private boolean installRequested=false;
    private volatile boolean surfaceRunning=false, activityResumed=false;
    private volatile int viewWidth, viewHeight;
    private int glTexture=0, glProgram=0, vertexAttribute=-1, uvAttribute=-1, texUniform=-1;
    private long lastUpdateMs=0, lastReportMs=0, lastFrameTimestamp=-1;
    private Session textureBoundSession=null;
    private int textureBoundName=-1, configuredRotation=-1, configuredWidth=-1, configuredHeight=-1;
    private final Object lock=new Object();
    private final java.util.HashMap<Plane,WallGeometry.Segment> detected=new java.util.HashMap<>();
    private final List<float[]> path=new ArrayList<>();
    private List<WallGeometry.Segment> merged=new ArrayList<>();
    private volatile boolean hasTracking=false;
    private final float[] vertexCoords={-1f,-1f, 1f,-1f, -1f,1f, 1f,1f};
    private final float[] uvCoords=new float[8];
    private FloatBuffer vertexBuffer,uvBuffer;

    private static int px(Activity a,int dp){return (int)(dp*a.getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String message,int size){TextView v=new TextView(this);v.setText(message);v.setTextColor(Color.WHITE);v.setTextSize(size);return v;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(android.os.Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.rgb(9,18,27));
        frame.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;
        });
        cameraSurface=new GLSurfaceView(this);
        cameraSurface.setEGLContextClientVersion(2);
        cameraSurface.setPreserveEGLContextOnPause(true);
        cameraSurface.setRenderer(this);
        cameraSurface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        frame.addView(cameraSurface,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setPadding(px(this,16),px(this,30),px(this,16),px(this,12));top.setOrientation(LinearLayout.VERTICAL);
        top.setBackgroundColor(0xB9081320);
        TextView heading=label("Mappatura ambiente · AR",21);heading.setTypeface(null,android.graphics.Typeface.BOLD);top.addView(heading);
        statusText=label("Avvio fotocamera e riconoscimento superfici…",14);statusText.setPadding(0,px(this,7),0,px(this,4));top.addView(statusText);
        detailText=label("Cammina lentamente e inquadra le pareti, mantenendo il telefono stabile.",12);top.addView(detailText);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);frame.addView(top,tp);

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(px(this,12),px(this,12),px(this,12),px(this,24));bottom.setBackgroundColor(0xDA081320);
        miniMap=new ScanMiniMap();miniMap.setMinimumHeight(px(this,170));
        bottom.addView(miniMap,new LinearLayout.LayoutParams(-1,px(this,170)));
        TextView hint=label("Linea verde: parete rilevata  ·  Traccia chiara: percorso",12);
        hint.setPadding(0,px(this,9),0,px(this,10));bottom.addView(hint);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel=new Button(this);cancel.setText("Annulla");cancel.setOnClickListener(v->finish());
        actions.addView(cancel,new LinearLayout.LayoutParams(0,px(this,52),1));
        saveButton=new Button(this);saveButton.setText("Salva mappa rilevata");saveButton.setEnabled(false);
        saveButton.setOnClickListener(v->saveScan());
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,px(this,52),2);bp.leftMargin=px(this,8);
        actions.addView(saveButton,bp);bottom.addView(actions);
        FrameLayout.LayoutParams btp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);frame.addView(bottom,btp);
        setContentView(frame);frame.requestApplyInsets();
    }

    @Override protected void onResume(){
        super.onResume();
        activityResumed=true;startSessionIfReady();
    }
    private void startSessionIfReady(){
        if(!activityResumed || isFinishing() || surfaceRunning)return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA);return;
        }
        try{
            if(session==null){
                ArCoreApk.Availability availability=ArCoreApk.getInstance().checkAvailability(this);
                if(availability.isTransient()){
                    setStatus("Verifica compatibilità ARCore…");
                    ArCoreApk.getInstance().checkAvailabilityAsync(this,a->startSessionIfReady());return;
                }
                if(!availability.isSupported()){setStatus("Scansione AR non disponibile su questo dispositivo ("+availability.name()+").");return;}
                ArCoreApk.InstallStatus status=ArCoreApk.getInstance().requestInstall(this,!installRequested);
                if(status==ArCoreApk.InstallStatus.INSTALL_REQUESTED){installRequested=true;setStatus("Completa l'installazione di Google Play Services per AR.");return;}
                Session ar=new Session(this);
                Config config=new Config(ar);
                config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
                // La Depth API non è necessaria al rilievo dei piani: non richiede hardware ToF.
                ar.configure(config);session=ar;
            }
            session.resume();cameraSurface.onResume();surfaceRunning=true;
        }catch(Exception e){setStatus("AR non disponibile: "+friendlyError(e));saveButton.setEnabled(false);}
    }
    private String friendlyError(Exception e){String c=e.getClass().getSimpleName();
        if(c.contains("UnavailableDeviceNotCompatible"))return "telefono non compatibile con ARCore";
        if(c.contains("UnavailableUserDeclined"))return "installazione servizi AR rifiutata";
        if(c.contains("CameraNotAvailable"))return "fotocamera occupata da un'altra app";
        return c+(e.getMessage()==null?"":": "+e.getMessage());
    }
    private void setStatus(String message){runOnUiThread(()->{if(!isFinishing()&&statusText!=null)statusText.setText(message);});}
    @Override protected void onPause(){
        activityResumed=false;surfaceRunning=false;if(cameraSurface!=null)cameraSurface.onPause();
        Session s=session;if(s!=null)try{s.pause();}catch(Exception ignored){}
        super.onPause();
    }
    @Override protected void onDestroy(){
        Session s=session;session=null;if(s!=null)s.close();super.onDestroy();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==REQUEST_CAMERA){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startSessionIfReady();
            else setStatus("La scansione AR richiede il permesso fotocamera.");
        }
    }

    private static FloatBuffer buffer(float[] data){FloatBuffer b=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(data).position(0);return b;}
    private static int compile(int kind,String source){int shader=GLES20.glCreateShader(kind);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);
        int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]!=GLES20.GL_TRUE)throw new IllegalStateException("OpenGL shader: "+GLES20.glGetShaderInfoLog(shader));return shader;}
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
        GLES20.glClearColor(0.03f,.07f,.11f,1);
        textureBoundSession=null;textureBoundName=-1;configuredRotation=-1;
        int[] texture=new int[1];GLES20.glGenTextures(1,texture,0);glTexture=texture[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,glTexture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        int vs=compile(GLES20.GL_VERTEX_SHADER,"attribute vec2 aPos; attribute vec2 aUv; varying vec2 uv; void main(){gl_Position=vec4(aPos,0.,1.);uv=aUv;}");
        int fs=compile(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES tex; varying vec2 uv; void main(){gl_FragColor=texture2D(tex,uv);}");
        glProgram=GLES20.glCreateProgram();GLES20.glAttachShader(glProgram,vs);GLES20.glAttachShader(glProgram,fs);GLES20.glLinkProgram(glProgram);
        int[] ok=new int[1];GLES20.glGetProgramiv(glProgram,GLES20.GL_LINK_STATUS,ok,0);
        if(ok[0]!=GLES20.GL_TRUE)throw new IllegalStateException("OpenGL link: "+GLES20.glGetProgramInfoLog(glProgram));
        GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
        vertexAttribute=GLES20.glGetAttribLocation(glProgram,"aPos");uvAttribute=GLES20.glGetAttribLocation(glProgram,"aUv");texUniform=GLES20.glGetUniformLocation(glProgram,"tex");
        vertexBuffer=buffer(vertexCoords);uvBuffer=buffer(uvCoords);
    }
    @Override public void onSurfaceChanged(GL10 gl,int width,int height){viewWidth=width;viewHeight=height;GLES20.glViewport(0,0,width,height);}
    @Override public void onDrawFrame(GL10 gl){
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        Session s=session;if(!surfaceRunning||s==null||glTexture==0||viewWidth<=0||viewHeight<=0)return;
        try{
            if(textureBoundSession!=s || textureBoundName!=glTexture){s.setCameraTextureNames(new int[]{glTexture});textureBoundSession=s;textureBoundName=glTexture;}
            int rotation=getWindowManager().getDefaultDisplay().getRotation();
            if(configuredRotation!=rotation||configuredWidth!=viewWidth||configuredHeight!=viewHeight){
                s.setDisplayGeometry(rotation,viewWidth,viewHeight);configuredRotation=rotation;configuredWidth=viewWidth;configuredHeight=viewHeight;
            }
            Frame frame=s.update();Camera cam=frame.getCamera();
            if(frame.getTimestamp()>0){
                frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,vertexCoords,Coordinates2d.TEXTURE_NORMALIZED,uvCoords);
                drawVideo();
            }
            hasTracking=cam.getTrackingState()==TrackingState.TRACKING;
            if(!hasTracking){long now=android.os.SystemClock.uptimeMillis();if(now-lastReportMs>400){lastReportMs=now;setStatus("Tracciamento perso: "+cam.getTrackingFailureReason()+". Torna lentamente verso la zona rilevata.");runOnUiThread(()->saveButton.setEnabled(false));}return;}
            if(frame.getTimestamp()==lastFrameTimestamp)return;lastFrameTimestamp=frame.getTimestamp();
            long time=android.os.SystemClock.uptimeMillis();
            if(time-lastUpdateMs>180){lastUpdateMs=time;extractWallsAndPath(s,cam);}
            if(time-lastReportMs>400){lastReportMs=time;updateOverlay();}
        }catch(Exception e){if(android.os.SystemClock.uptimeMillis()-lastReportMs>1500){lastReportMs=android.os.SystemClock.uptimeMillis();setStatus("In attesa del tracciamento AR: "+friendlyError(e));}}
    }
    private void drawVideo(){
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glUseProgram(glProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,glTexture);
        vertexBuffer.position(0);uvBuffer.position(0);uvBuffer.put(uvCoords).position(0);
        GLES20.glVertexAttribPointer(vertexAttribute,2,GLES20.GL_FLOAT,false,0,vertexBuffer);
        GLES20.glVertexAttribPointer(uvAttribute,2,GLES20.GL_FLOAT,false,0,uvBuffer);
        GLES20.glEnableVertexAttribArray(vertexAttribute);GLES20.glEnableVertexAttribArray(uvAttribute);
        GLES20.glUniform1i(texUniform,0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(vertexAttribute);GLES20.glDisableVertexAttribArray(uvAttribute);
    }
    private void extractWallsAndPath(Session s,Camera cam){
        Pose pose=cam.getPose();float[] cameraPos=pose.getTranslation();
        synchronized(lock){
            if(path.isEmpty() || Math.hypot(path.get(path.size()-1)[0]-cameraPos[0],path.get(path.size()-1)[1]-cameraPos[2])>.30){
                if(path.size()<700)path.add(new float[]{cameraPos[0],cameraPos[2]});
            }
            java.util.HashMap<Plane,WallGeometry.Segment> next=new java.util.HashMap<>(detected);
            for(Plane plane:s.getAllTrackables(Plane.class)){
                if(plane.getSubsumedBy()!=null){next.remove(plane);continue;}
                if(plane.getType()!=Plane.Type.VERTICAL||plane.getTrackingState()!=TrackingState.TRACKING)continue;
                FloatBuffer polygon=plane.getPolygon();if(polygon==null||polygon.remaining()<4)continue;
                float[] xz=new float[polygon.remaining()];Pose center=plane.getCenterPose();
                // plane polygon = coppie [localX, localZ] nel riferimento del piano.
                for(int i=0;polygon.hasRemaining();i+=2){float[] pt=center.transformPoint(new float[]{polygon.get(),0,polygon.get()});xz[i]=pt[0];xz[i+1]=pt[2];}
                WallGeometry.Segment seg=WallGeometry.widestProjectedSegment(xz);
                if(seg!=null&&(next.containsKey(plane)||next.size()<200))next.put(plane,seg);
            }
            detected.clear();detected.putAll(next);
            merged=WallGeometry.mergeSegments(next.values());
        }
    }
    private void updateOverlay(){
        final List<WallGeometry.Segment> walls;final int steps;
        synchronized(lock){walls=new ArrayList<>(merged);steps=path.size();}
        final float meters=WallGeometry.totalMeters(walls);
        runOnUiThread(()->{
            if(isFinishing())return;
            statusText.setText(walls.isEmpty()?"Muoviti lentamente e inquadra una parete con buona luce.":"Pareti riconosciute: "+walls.size()+"  ·  Tratti visibili: "+String.format(Locale.ITALY,"%.1f m",meters));
            detailText.setText("Tracciamento: "+(hasTracking?"attivo":"in ricerca")+"  ·  Punti di percorso: "+steps+". Misure indicative, controlla gli angoli.");
            saveButton.setEnabled(!walls.isEmpty());miniMap.invalidate();
        });
    }
    private void saveScan(){
        List<WallGeometry.Segment> walls;List<float[]> route;
        synchronized(lock){walls=new ArrayList<>(merged);route=new ArrayList<>(path);}
        if(!hasTracking){setStatus("Attendi il recupero del tracciamento prima di salvare.");return;}
        if(walls.isEmpty()){setStatus("Nessuna parete ancora individuata.");return;}
        try{
            JSONObject data=new JSONObject();data.put("format","OFFICINA_STOCK_AR_WALLS");data.put("version",1);
            data.put("site",getIntent().getStringExtra("site"));data.put("profileId",getIntent().getStringExtra("profileId"));
            data.put("scanAt",System.currentTimeMillis());
            JSONArray lines=new JSONArray();for(WallGeometry.Segment line:walls){JSONArray pair=new JSONArray();pair.put(line.x1);pair.put(line.z1);pair.put(line.x2);pair.put(line.z2);lines.put(pair);}data.put("walls",lines);
            JSONArray track=new JSONArray();for(float[] p:route){JSONArray point=new JSONArray();point.put(p[0]);point.put(p[1]);track.put(point);}data.put("route",track);
            Intent result=new Intent();result.putExtra("map_json",data.toString());setResult(RESULT_OK,result);finish();
        }catch(Exception e){setStatus("Errore nel salvataggio del rilievo: "+e.getMessage());}
    }
    private final class ScanMiniMap extends View {
        private final Paint paint=new Paint(3);
        ScanMiniMap(){super(ARScanActivity.this);setBackgroundColor(0xEE112132);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
            List<WallGeometry.Segment> walls;List<float[]> route;
            synchronized(lock){walls=new ArrayList<>(merged);route=new ArrayList<>(path);}
            if(walls.isEmpty()&&route.isEmpty())return;
            float minX=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,minZ=Float.MAX_VALUE,maxZ=-Float.MAX_VALUE;
            for(WallGeometry.Segment s:walls){minX=Math.min(minX,Math.min(s.x1,s.x2));maxX=Math.max(maxX,Math.max(s.x1,s.x2));minZ=Math.min(minZ,Math.min(s.z1,s.z2));maxZ=Math.max(maxZ,Math.max(s.z1,s.z2));}
            for(float[] p:route){minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minZ=Math.min(minZ,p[1]);maxZ=Math.max(maxZ,p[1]);}
            float spanX=Math.max(1,maxX-minX),spanZ=Math.max(1,maxZ-minZ);
            float scale=Math.min((getWidth()-40)/spanX,(getHeight()-30)/spanZ);
            float ox=(getWidth()-spanX*scale)*.5f-minX*scale,oy=(getHeight()-spanZ*scale)*.5f-minZ*scale;
            paint.setStrokeWidth(px(ARScanActivity.this,2));paint.setColor(0xFF9EB0C8);
            for(int i=1;i<route.size();i++){float[] a=route.get(i-1),b=route.get(i);canvas.drawLine(ox+a[0]*scale,oy+a[1]*scale,ox+b[0]*scale,oy+b[1]*scale,paint);}
            paint.setStrokeWidth(px(ARScanActivity.this,4));paint.setColor(0xFF21D7A0);
            for(WallGeometry.Segment s:walls)canvas.drawLine(ox+s.x1*scale,oy+s.z1*scale,ox+s.x2*scale,oy+s.z2*scale,paint);
            if(!route.isEmpty()){float[] last=route.get(route.size()-1);paint.setColor(Color.WHITE);canvas.drawCircle(ox+last[0]*scale,oy+last[1]*scale,px(ARScanActivity.this,5),paint);}
        }
    }
}

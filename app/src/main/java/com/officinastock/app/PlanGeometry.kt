package com.officinastock.app

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.hypot

/** The entire plan is defined in metres, independent of device pixels or screen ratio. */
data class PointMetres(val x: Float, val y: Float)
data class PlanViewport(
    val widthPx: Float, val heightPx: Float,
    val roomWidth: Float, val roomHeight: Float,
    val zoom: Float=1f, val panX: Float=0f, val panY: Float=0f,
    val marginPx: Float=34f
) {
    val scale: Float get() = minOf((widthPx - 2*marginPx)/roomWidth,(heightPx - 2*marginPx)/roomHeight).coerceAtLeast(.1f)*zoom
    val originX: Float get() = (widthPx-roomWidth*scale)/2f + panX
    val originY: Float get() = (heightPx-roomHeight*scale)/2f + panY
    fun toScreen(p:PointMetres) = PointMetres(originX+p.x*scale,originY+p.y*scale)
    fun toWorld(p:PointMetres):PointMetres = PointMetres((p.x-originX)/scale,(p.y-originY)/scale)
    fun inside(p:PointMetres) = p.x in 0f..roomWidth && p.y in 0f..roomHeight
    fun bounded(p:PointMetres) = PointMetres(p.x.coerceIn(0f,roomWidth),p.y.coerceIn(0f,roomHeight))
    /** Keep part of the plan visible while allowing navigation at high zoom. */
    fun panLimitX(): Float = ((roomWidth*scale-widthPx)/2f).coerceAtLeast(0f) + widthPx*.16f
    fun panLimitY(): Float = ((roomHeight*scale-heightPx)/2f).coerceAtLeast(0f) + heightPx*.16f
    fun clampedPan(x:Float,y:Float) = PointMetres(x.coerceIn(-panLimitX(),panLimitX()),y.coerceIn(-panLimitY(),panLimitY()))

    fun transformed(factor:Float, anchor:PointMetres, translation:PointMetres):PlanViewport {
        val world=toWorld(anchor)
        val next=copy(zoom=(zoom*factor).coerceIn(.7f,4f))
        val projected=next.toScreen(world)
        val pan=next.clampedPan(panX+anchor.x+translation.x-projected.x,panY+anchor.y+translation.y-projected.y)
        return next.copy(panX=pan.x,panY=pan.y)
    }

    fun insetCoordinate(value:Float,extent:Float,inset:Float):Float =
        if(extent<=2*inset) extent/2f else value.coerceIn(inset,extent-inset)

}
object PlanGeometry {
    fun snap(value:Float,spacing:Float=.25f):Float = round(value/spacing)*spacing
    fun snap(p:PointMetres, grid:Float=.25f) = PointMetres(snap(p.x,grid),snap(p.y,grid))
    fun distance(a:PointMetres,b:PointMetres):Float = hypot(a.x-b.x,a.y-b.y)
    fun snapToWallEndpoint(p:PointMetres,walls:List<Wall>,tolerance:Float=.22f):PointMetres {
        val endpoints=walls.flatMap { listOf(PointMetres(it.x1,it.y1),PointMetres(it.x2,it.y2)) }
        return endpoints.minByOrNull{distance(it,p)}?.takeIf{distance(it,p)<=tolerance}?:p
    }
    fun orthogonal(start:PointMetres,end:PointMetres):PointMetres = if(abs(start.x-end.x)>abs(start.y-end.y)) PointMetres(end.x,start.y) else PointMetres(start.x,end.y)
    fun validWall(a:PointMetres,b:PointMetres):Boolean = distance(a,b)>=.2f
    fun defaultSize(kind:String):Pair<Float,Float> = when(kind){
        "Scaffale" -> 2.0f to .75f
        "Armadio" -> 1.25f to .65f
        "Cassetto" -> .9f to .65f
        "Banco" -> 2.0f to .9f
        "Area" -> 2.6f to 1.8f
        "Porta" -> .9f to .2f
        "Colonna" -> .4f to .4f
        "Corridoio" -> 3.0f to 1.2f
        else -> 1.5f to 1f
    }
    /** Restrict the whole rotated footprint to the room, not just its center.
     * Oversize structures are centered: they cannot fully fit without resizing. */
    fun boundedPlacementCenter(x:Float,y:Float,width:Float,depth:Float,rotation:Float,
                               roomWidth:Float,roomHeight:Float):PointMetres {
        require(listOf(x,y,width,depth,rotation,roomWidth,roomHeight).all{it.isFinite()} &&
            width>0f && depth>0f && roomWidth>0f && roomHeight>0f) { "Dimensioni struttura non valide" }
        val angle=Math.toRadians(rotation.toDouble())
        val extentX=((abs(cos(angle))*width+abs(sin(angle))*depth)/2).toFloat()
        val extentY=((abs(sin(angle))*width+abs(cos(angle))*depth)/2).toFloat()
        fun clamp(v:Float,room:Float,extent:Float):Float =
            if(extent*2>room) room/2f else v.coerceIn(extent,room-extent)
        return PointMetres(clamp(x,roomWidth,extentX),clamp(y,roomHeight,extentY))
    }
    fun placementFitsRoom(p:Placement,roomWidth:Float,roomHeight:Float):Boolean {
        val angle=Math.toRadians(p.rotation.toDouble())
        val extentX=((abs(cos(angle))*p.width+abs(sin(angle))*p.depth)/2).toFloat()
        val extentY=((abs(sin(angle))*p.width+abs(cos(angle))*p.depth)/2).toFloat()
        return extentX*2<=roomWidth && extentY*2<=roomHeight &&
            p.x>=extentX-0.0001f && p.x<=roomWidth-extentX+0.0001f &&
            p.y>=extentY-0.0001f && p.y<=roomHeight-extentY+0.0001f
    }
    /** Rotated rectangle hit test in world metres. */
    fun contains(point:PointMetres,p:Placement,padding:Float=.16f):Boolean {
        val dx=point.x-p.x;val dy=point.y-p.y
        val theta=Math.toRadians(p.rotation.toDouble())
        val localX=dx*cos(theta)+dy*sin(theta)
        val localY=-dx*sin(theta)+dy*cos(theta)
        return abs(localX)<=(p.width/2+padding) && abs(localY)<=(p.depth/2+padding)
    }

    /** Half extents of the rotated footprint on the room axes. */
    fun extents(p:Placement):PointMetres {
        val angle=Math.toRadians(p.rotation.toDouble())
        return PointMetres(((abs(cos(angle))*p.width+abs(sin(angle))*p.depth)/2).toFloat(),
            ((abs(sin(angle))*p.width+abs(cos(angle))*p.depth)/2).toFloat())
    }
    private fun corners(p:Placement):List<PointMetres> {
        val t=Math.toRadians(p.rotation.toDouble());val c=cos(t).toFloat();val s=sin(t).toFloat()
        val hw=p.width/2;val hd=p.depth/2
        return listOf(-hw to -hd,hw to -hd,hw to hd,-hw to hd).map{(lx,ly)->
            PointMetres(p.x+lx*c-ly*s,p.y+lx*s+ly*c)
        }
    }
    /** Separating-axis test on rotated rectangles. Touching edges are not an overlap. */
    fun overlaps(a:Placement,b:Placement,tolerance:Float=.01f):Boolean {
        if(a.id==b.id) return false
        val ca=corners(a);val cb=corners(b)
        val axes=listOf(a.rotation,a.rotation+90f,b.rotation,b.rotation+90f).map{
            val t=Math.toRadians(it.toDouble());PointMetres(cos(t).toFloat(),sin(t).toFloat())
        }
        for(axis in axes){
            val pa=ca.map{it.x*axis.x+it.y*axis.y};val pb=cb.map{it.x*axis.x+it.y*axis.y}
            if(pa.max()<=pb.min()+tolerance || pb.max()<=pa.min()+tolerance) return false
        }
        return true
    }
    /** Structures (doors and corridors excluded) whose footprint intersects [p]. */
    fun collisions(p:Placement,others:List<Placement>):List<Placement> =
        if(p.kind=="Porta"||p.kind=="Corridoio") emptyList()
        else others.filter{it.id!=p.id && it.kind!="Porta" && it.kind!="Corridoio" && overlaps(p,it)}

    /**
     * Magnetic alignment: moves the centre so an edge touches or aligns with a nearby structure or
     * wall, within [tolerance] metres. Only for axis-aligned footprints (0/90/180/270 degrees).
     */
    fun snapToNeighbors(p:Placement,others:List<Placement>,walls:List<Wall>,tolerance:Float=.15f):PointMetres {
        if(((p.rotation%90f)+90f)%90f>.5f) return PointMetres(p.x,p.y)
        val e=extents(p)
        val xs=mutableListOf<Float>();val ys=mutableListOf<Float>()
        for(o in others) {
            if(o.id==p.id) continue
            val oe=extents(o)
            val left=o.x-oe.x;val right=o.x+oe.x;val top=o.y-oe.y;val bottom=o.y+oe.y
            xs+=listOf(left-e.x,right+e.x,left+e.x,right-e.x,o.x)
            ys+=listOf(top-e.y,bottom+e.y,top+e.y,bottom-e.y,o.y)
        }
        for(w in walls) {
            if(abs(w.x1-w.x2)<.01f) xs+=listOf(w.x1+e.x,w.x1-e.x)
            if(abs(w.y1-w.y2)<.01f) ys+=listOf(w.y1+e.y,w.y1-e.y)
        }
        fun best(v:Float,candidates:List<Float>)=candidates.minByOrNull{abs(it-v)}?.takeIf{abs(it-v)<=tolerance}?:v
        return PointMetres(best(p.x,xs),best(p.y,ys))
    }

    /** "Scaffale A" -> "Scaffale B", "S9" -> "S10", otherwise "Nome 2". */
    fun nextLabel(label:String):String {
        val trimmed=label.trim()
        Regex("^(.*?)([0-9]+)$").matchEntire(trimmed)?.let { m ->
            val digits=m.groupValues[2]
            val next=(digits.toLong()+1).toString().padStart(digits.length,'0')
            return m.groupValues[1]+next
        }
        Regex("^(.*?[^A-Za-z])?([A-Y])$").matchEntire(trimmed)?.let { m ->
            return m.groupValues[1]+(m.groupValues[2][0]+1)
        }
        Regex("^(.*?[^A-Za-z])?([a-y])$").matchEntire(trimmed)?.let { m ->
            return m.groupValues[1]+(m.groupValues[2][0]+1)
        }
        return "$trimmed 2"
    }
    /** Centres for a row of [count] copies after [source], spaced by [gap] metres along x or y. */
    fun rowCentres(source:Placement,count:Int,gap:Float,vertical:Boolean):List<PointMetres> {
        require(count in 1..50 && gap.isFinite() && gap in 0f..20f) { "Fila non valida: 1–50 copie, distanza 0–20 m" }
        val e=extents(source)
        val step=if(vertical) e.y*2+gap else e.x*2+gap
        return (1..count).map { i ->
            if(vertical) PointMetres(source.x,source.y+step*i) else PointMetres(source.x+step*i,source.y)
        }
    }
    fun wallLength(w:Wall):Float = distance(PointMetres(w.x1,w.y1),PointMetres(w.x2,w.y2))
    /** Distance in metres from [p] to the wall segment, used to select a wall by tapping. */
    fun distanceToWall(p:PointMetres,w:Wall):Float {
        val dx=w.x2-w.x1;val dy=w.y2-w.y1
        val len2=dx*dx+dy*dy
        if(len2<=0f) return distance(p,PointMetres(w.x1,w.y1))
        val t=(((p.x-w.x1)*dx+(p.y-w.y1)*dy)/len2).coerceIn(0f,1f)
        return distance(p,PointMetres(w.x1+t*dx,w.y1+t*dy))
    }
    /** Keeps the start point and direction, changes the length (metres). */
    fun withLength(w:Wall,length:Float):FloatArray {
        val current=wallLength(w)
        require(length.isFinite() && length>=.2f && current>0f) { "Lunghezza non valida" }
        val k=length/current
        return floatArrayOf(w.x1,w.y1,w.x1+(w.x2-w.x1)*k,w.y1+(w.y2-w.y1)*k)
    }
}

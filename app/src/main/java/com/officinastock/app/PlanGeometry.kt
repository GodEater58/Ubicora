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
}

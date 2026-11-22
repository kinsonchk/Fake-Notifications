package com.codejam.taptation.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.codejam.taptation.logic.meadow.MeadowDiscovery
import com.codejam.taptation.logic.meadow.MeadowState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Reused Android drawing primitives keep this scene allocation-light at animation-frame frequency. */
internal class MeadowPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private lateinit var c: Canvas
    private var green = 0; private var deep = 0; private var cream = 0; private var gold = 0; private var earth = 0; private var light = 0
    private var night = false
    fun palette(green: Color, deep: Color, cream: Color, gold: Color, earth: Color, night: Boolean) {
        this.green = green.toArgb(); this.deep = deep.toArgb(); this.cream = cream.toArgb()
        this.gold = gold.toArgb(); this.earth = earth.toArgb(); this.night = night
        this.light = lerp(green, cream, .14f).toArgb()
    }
    private fun fill(color: Int) { paint.color = color; paint.style = Paint.Style.FILL; paint.alpha = 255 }
    private fun oval(x: Float, y: Float, rx: Float, ry: Float, color: Int, angle: Float = 0f) {
        fill(color); c.save(); c.rotate(angle, x, y); c.drawOval(x-rx,y-ry,x+rx,y+ry,paint); c.restore()
    }
    private fun line(x: Float, y: Float, xx: Float, yy: Float, color: Int, width: Float = 1f) {
        paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(x,y,xx,yy,paint)
    }
    private fun shape(color: Int, build: Path.() -> Unit) { path.reset(); path.build(); fill(color); c.drawPath(path,paint) }
    private fun stem(color: Int, width: Float = 1.4f, build: Path.() -> Unit) {
        path.reset(); path.build(); paint.color=color; paint.style=Paint.Style.STROKE; paint.strokeWidth=width; paint.strokeCap=Paint.Cap.ROUND; c.drawPath(path,paint)
    }
    fun landmarks(canvas: Canvas, width: Float, ground: Float, world: MeadowState, time: Float) {
        c=canvas
        if(world.acornStage==0)return
        val x=width*.48f; val y=ground+7
        if(world.acornStage==1){oval(x,y,4f,6f,gold,25f);oval(x-2,y-4,5f,2f,earth,25f);return}
        val h=if(world.acornStage==2)53f else 121f
        val sway=sin(time*1.2f)*2
        stem(earth,if(world.acornStage==2)2f else 4f){moveTo(x,y);quadTo(x-3,y-h*.45f,x+sway,y-h)}
        line(x,y-h*.5f,x-15,y-h*.72f,earth,2f);line(x,y-h*.62f,x+17,y-h*.83f,earth,2f)
        if(world.acornStage==2){oval(x-11+sway,y-36,11f,4f,green,-30f);oval(x+11+sway,y-45,11f,4f,green,30f);oval(x+sway,y-55,7f,4f,green)}
        else {
            // Tapered trunk with a root flare, then a shaded crown: shadow clumps low, sunlit clumps high-right.
            shape(earth){moveTo(x-7,y);quadTo(x-3,y-3,x-3.5f,y-18);lineTo(x-2+sway*.5f,y-h*.8f);lineTo(x+2+sway*.5f,y-h*.8f);lineTo(x+3.5f,y-18);quadTo(x+3,y-3,x+7,y);close()}
            val cx=x+sway; val cy=y-h
            clumps(OAK_SHADOW,cx,cy,deep); clumps(OAK_MAIN,cx,cy,green); clumps(OAK_LIGHT,cx,cy,light)
        }
    }
    fun life(canvas: Canvas, width: Float, ground: Float, health: Int, world: MeadowState, effects: MeadowEffects, motion: Boolean) {
        c=canvas; val t=if(motion)effects.time else 0f
        val count=if(world.flowers>0)world.flowers else if(health==100)3 else 0
        for(i in 0 until count){val u=FLOWER_X[i];val x=u*width;val h=FLOWER_H[i];val bend=sin(t*1.35f-u*4)*7
            val fx=x+bend;val fy=ground-h
            stem(deep){moveTo(x,ground);cubicTo(x-4,ground-h*.3f,x+bend*.2f,fy+15,fx,fy)}
            oval(x+bend*.1f-6,ground-h*.4f,8f,2.7f,green,-37f)
            if(world.flowers==0){oval(fx,fy,3f,5f,gold);continue}
            for(p in 0..4){val a=p*2*PI.toFloat()/5+sin(t+i)*.1f;oval(fx+sin(a)*5.2f,fy-cos(a)*5.2f,3.1f,6f,cream,a*180/PI.toFloat())}
            oval(fx,fy,2.6f,2.6f,gold)
        }
        if(MeadowDiscovery.Pond in world.discoveries){val x=width*.81f;val y=ground+19
            // Grass-edged pond with a muddy bank and a sky glint, rather than a brown dish.
            oval(x,y,35f,14.5f,deep);oval(x,y+1,32.5f,12f,earth);oval(x,y,30f,10f,if(night)0xff42635e.toInt() else 0xff8fc0bd.toInt())
            fill(cream);paint.alpha=if(night)45 else 90;c.drawOval(x-21,y-6,x-3,y-3,paint)
            for(i in 0..2){paint.color=cream;paint.alpha=100;paint.style=Paint.Style.STROKE;paint.strokeWidth=.7f;val r=7+(t*5+i*7)%21;c.drawOval(x-r-4,y-r*.23f-1,x+r-4,y+r*.23f-1,paint)}
            oval(x+13,y+2,7f,3f,green,-9f)
        }
        if(MeadowDiscovery.Hedgehog in world.discoveries)hedgehog(width*.16f,ground+12,t)
        for((u,found) in listOf(.16f to MeadowDiscovery.Hedgehog,.81f to MeadowDiscovery.Pond))if(found !in world.discoveries){fill(gold);paint.alpha=90;c.drawCircle(width*u,ground-5,2f,paint)}
        nest(width*.75f,ground-56,world.nestStage)
        if(world.butterfly||effects.butterflyUntil>effects.time){val swirling=effects.vortexUntil>effects.time
            val x=if(swirling)effects.vortex.x+cos(t*3)*42 else width*.45f+sin(t*.6f)*width*.26f
            val y=if(swirling)effects.vortex.y+sin(t*3)*25 else 60+sin(t*1.4f)*17
            butterfly(x,y,t)
        }
        if(world.robin){val hop=if(effects.birdUntil>effects.time)kotlin.math.abs(sin(t*8))*10 else 0f
            robin(if(world.nestStage>=2)width*.75f+20 else width*.36f,if(world.nestStage>=2)ground-59-hop else ground+8-hop)
        }
        if(world.acornStage>0&&effects.squirrelUntil>effects.time){val age=8-(effects.squirrelUntil-effects.time);val target=width*.48f
            val x=if(!motion)target else if(age<2)-25+(target+25)*age/2 else if(age<5)target else target+(width-target+30)*(age-5)/3
            squirrel(x,ground+8)
        }
        if(world.fireflies&&night){for(i in 0..8){val x=(i*67+t*7)%width;val y=48+(i*37)%120+sin(t+i)*13
            fill(gold);paint.alpha=(50+100*(.5f+.5f*sin(t*2+i))).toInt();c.drawCircle(x,y,4f,paint);oval(x,y,1.2f,1.2f,cream)
        }}
        if(effects.seedsUntil>effects.time){val elapsed=3-(effects.seedsUntil-effects.time);for(i in 0..11){val a=i*2.4f;val x=effects.seeds.x+cos(a)*12*elapsed+elapsed*8;val y=effects.seeds.y-(15+sin(i*3f)*12)*elapsed
            line(x,y+4,x,y-2,cream);line(x-3,y-4,x,y-2,cream);line(x+3,y-4,x,y-2,cream)
        }}
        if(effects.vortexUntil>effects.time){val v=effects.vortex;paint.color=green;paint.alpha=65;paint.style=Paint.Style.STROKE;paint.strokeWidth=1f
            for(j in 0..2){path.reset();for(i in 0..69){val a=i*.12f+t*3+j*2.1f;val r=5+i*.55f;val x=v.x+cos(a)*r;val y=v.y+sin(a)*r*.6f;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};c.drawPath(path,paint)}
        }
        if(effects.rainUntil>effects.time){for(i in 0..44){val x=((i*41-t*25)%width+width)%width;val y=(i*79+t*220)%(ground+40);line(x,y,x-3,y+10,green,.6f)}}
    }
    private fun butterfly(x:Float,y:Float,t:Float){val flap=.25f+kotlin.math.abs(sin(t*9))*.75f
        for(sign in listOf(-1,1)){oval(x+sign*6*flap,y-3,7*flap,9f,gold,sign*23f);oval(x+sign*4*flap,y+5,4*flap,5f,gold,-sign*17f)};line(x,y-6,x,y+9,deep,1.6f)
    }
    private fun robin(x:Float,y:Float){oval(x,y-8,10f,7f,deep,-11f);oval(x+6,y-15,6f,6f,deep);oval(x+6,y-9,5f,6f,0xffbd7953.toInt(),-11f)
        shape(gold){moveTo(x+11,y-16);lineTo(x+17,y-14);lineTo(x+11,y-12);close()};oval(x+8,y-17,1f,1f,cream);line(x-3,y-3,x-5,y+2,deep);line(x+3,y-3,x+3,y+2,deep)
    }
    private fun nest(x:Float,y:Float,stage:Int){if(stage==0)return;stem(earth,2f){moveTo(x+14,y+60);quadTo(x+8,y+22,x,y+5)};line(x+7,y+31,x-10,y+18,earth,2f);line(x+9,y+36,x+21,y+21,earth,2f)
        if(stage>=2){oval(x,y,21f,8f,earth);for(i in 0..4){paint.color=gold;paint.style=Paint.Style.STROKE;paint.strokeWidth=1f;c.drawOval(x-18+i,y-7+i*.5f,x+18-i,y+4+i*.5f,paint)};if(stage>=3){oval(x-6,y-4,4f,6f,0xffd2e0d2.toInt(),11f);oval(x+5,y-4,4f,6f,0xffd2e0d2.toInt(),11f)}}else line(x-14,y+2,x+15,y-3,earth,2f)
    }
    private fun hedgehog(x:Float,y:Float,t:Float){oval(x,y-5,17f,9+sin(t*1.7f)*.3f,earth);for(i in 0..12){val a=PI.toFloat()+i*PI.toFloat()/12;line(x+cos(a)*13,y-4+sin(a)*8,x+cos(a)*19,y-4+sin(a)*13,deep)};oval(x+14,y-3,7f,5f,gold);stem(deep){moveTo(x+15,y-5);quadTo(x+17,y-3,x+19,y-5)};oval(x+21,y-2,1.8f,1.8f,deep)}
    private fun squirrel(x:Float,y:Float){shape(earth){moveTo(x-8,y-4);cubicTo(x-35,y-4,x-35,y-38,x-21,y-34);cubicTo(x-9,y-31,x-15,y-20,x-19,y-22);cubicTo(x-19,y-12,x-10,y-17,x-8,y-4);close()};oval(x,y-8,11f,8f,earth,-11f);oval(x+9,y-15,6f,6f,earth);shape(earth){moveTo(x+6,y-19);lineTo(x+7,y-27);lineTo(x+11,y-19);close()};oval(x+3,y-7,5f,4f,gold);oval(x+11,y-16,1.1f,1.1f,deep)}

    fun cameoRobin(canvas:Canvas,x:Float,y:Float,time:Float,flying:Boolean){c=canvas;c.save();c.translate(x,y);c.scale(1.4f,1.4f);robin(0f,0f)
        if(flying){val flap=sin(time*17);shape(green){moveTo(-4f,-10f);quadTo(-15f,-26*flap-10,-23f,-18*flap-5);quadTo(-18f,0f,-4f,-5f);close()}}
        c.restore()
    }
    fun cameoSquirrel(canvas:Canvas,x:Float,y:Float,time:Float,digging:Boolean){c=canvas;c.save();c.translate(x,y);c.scale(1.3f,1.3f);if(digging)c.rotate(sin(time*15)*4);squirrel(0f,0f);c.restore()}
    fun cameoHedgehog(canvas:Canvas,x:Float,y:Float,time:Float){c=canvas;c.save();c.translate(x,y);c.scale(1.3f,1.3f);hedgehog(0f,0f,time);fill(earth);paint.textSize=10f;c.drawText("z",5f,-24f-sin(time)*2,paint);paint.textSize=8f;c.drawText("z",15f,-31f-sin(time)*2,paint);c.restore()}

    fun underground(canvas:Canvas,width:Float,world:MeadowState,effects:MeadowEffects,motion:Boolean){c=canvas;val t=if(motion)effects.time else 0f
        fill(if(night)0xff302b22.toInt() else 0xff69583e.toInt());c.drawRect(0f,0f,width,230f,paint)
        for(i in 0..74){fill(gold);paint.alpha=30;c.drawCircle((i*67+13)%width,((i*41+9)%230).toFloat(),1f+(i%3),paint)}
        for(i in 0..8){val x=(i+.35f)/9*width;val depth=45+(i*29)%85
            stem(0xffaa9670.toInt(),1f){moveTo(x,0f);cubicTo(x+8,depth*.2f,x-12,depth*.65f,x+7,depth.toFloat())}
            for(j in 1..3){val y=depth*j/4f;val sign=if(j%2==0)-1 else 1;stem(0xffaa9670.toInt(),.6f){moveTo(x,y);quadTo(x+sign*11,y+8,x+sign*18,y+22)}}
        }
        if(world.acornStage>0){val x=width*.48f;val depth=when(world.acornStage){1->22f;2->93f;else->157f};stem(0xfff0d6a5.toInt(),2f){moveTo(x,0f);cubicTo(x+7,depth*.3f,x-12,depth*.6f,x,depth)};for(i in 1..4){val y=depth*i/5;val sign=if(i%2==0)-1 else 1;stem(0xfff0d6a5.toInt(),1f){moveTo(x,y);quadTo(x+sign*25,y+12,x+sign*(30+i*5),y+30)}}}
        val bx=width*.76f+if(effects.beetleUntil>effects.time)sin(t*2)*13 else 0f;val by=115f;oval(width*.76f,by,37f,24f,if(night)0xff24271f.toInt() else 0xff544c36.toInt())
        for(i in -1..1){line(bx-6,by+i*4,bx-13,by+i*7,0xffd3bc85.toInt());line(bx+6,by+i*4,bx+13,by+i*7,0xffd3bc85.toInt())};oval(bx,by,8f,11f,0xff8caa7b.toInt());line(bx,by-10,bx,by+10,0xff40553e.toInt());oval(bx,by-12,4f,4f,0xff3b4c36.toInt())
        for(i in 0..2){val x=width*floatArrayOf(.15f,.23f,.38f)[i];val y=floatArrayOf(184f,192f,161f)[i];val s=if(i==0)1f else .7f
            if(effects.glowUntil>effects.time){fill(0xffc2d29c.toInt());paint.alpha=30;c.drawCircle(x,y-18*s,25*s,paint)}
            stem(0xffe9dcad.toInt(),3*s){moveTo(x,y);quadTo(x+3,y-10*s,x,y-20*s)};oval(x,y-21*s,13*s,5*s,0xffc2d29c.toInt());for(j in -1..1)oval(x+j*6*s,y-23*s,1.2f*s,1.2f*s,0xfffff2c5.toInt())
        }
        stem(0xff54452f.toInt(),14f){moveTo(width*.48f,195f);cubicTo(width*.64f,164f,width*.85f,220f,width,185f)}
        val ax=width*.55f+((t*.035f)%1)*width*.4f;val ay=191+sin(ax*.035f)*8
        for(i in -1..1){line(ax+i*4,ay,ax+i*5-3,ay-5,0xffead6ab.toInt(),.8f);line(ax+i*4,ay,ax+i*5-2,ay+5,0xffead6ab.toInt(),.8f)}
        oval(ax-5,ay,3f,3f,0xffd9bc87.toInt());oval(ax,ay,2f,2f,0xffd9bc87.toInt());oval(ax+5,ay,2.5f,2.5f,0xffd9bc87.toInt())
        when(effects.antParcel){0->oval(ax+2,ay-12,12f,5f,0xff9fbf80.toInt(),-37f);1->oval(ax+2,ay-11,6f,6f,0xffd29c7b.toInt());else->line(ax-8,ay-16,ax+12,ay-8,0xffd9bc87.toInt(),2f)}
    }
    private fun clumps(spec: FloatArray, x: Float, y: Float, color: Int) {
        var i = 0; while (i < spec.size) { oval(x + spec[i], y + spec[i + 1], spec[i + 2], spec[i + 2] * .92f, color); i += 3 }
    }
    companion object {
        /** Oak crown clumps as (dx, dy, radius) from the crown centre, drawn shadow, main, then light. */
        private val OAK_SHADOW = floatArrayOf(-22f,12f,21f, 21f,14f,22f, 0f,20f,24f)
        private val OAK_MAIN = floatArrayOf(-20f,-2f,20f, 20f,0f,21f, 0f,-16f,24f, -6f,6f,22f, 10f,8f,20f)
        private val OAK_LIGHT = floatArrayOf(8f,-24f,13f, 22f,-8f,10f)
        val FLOWER_X=floatArrayOf(.16f,.33f,.49f,.67f,.81f,.24f,.73f);val FLOWER_H=floatArrayOf(70f,93f,63f,82f,59f,52f,101f) }
}

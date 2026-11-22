package com.codejam.taptation.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.codejam.taptation.logic.meadow.MeadowDiscovery
import com.codejam.taptation.logic.meadow.MeadowState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Reused Android drawing primitives keep this scene allocation-light at animation-frame frequency. */
internal class MeadowPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private lateinit var c: Canvas
    private var green = 0; private var deep = 0; private var cream = 0; private var gold = 0; private var earth = 0
    private var night = false
    fun palette(green: Color, deep: Color, cream: Color, gold: Color, earth: Color, night: Boolean) {
        this.green = green.toArgb(); this.deep = deep.toArgb(); this.cream = cream.toArgb()
        this.gold = gold.toArgb(); this.earth = earth.toArgb(); this.night = night
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
        // The grown oak is a valley landmark now; the foreground keeps the sapling so it never hides the view.
        val h=53f
        val sway=sin(time*1.2f)*2
        stem(earth,2f){moveTo(x,y);quadTo(x-3,y-h*.45f,x+sway,y-h)}
        line(x,y-h*.5f,x-15,y-h*.72f,earth,2f);line(x,y-h*.62f,x+17,y-h*.83f,earth,2f)
        oval(x-11+sway,y-36,11f,4f,green,-30f);oval(x+11+sway,y-45,11f,4f,green,30f);oval(x+sway,y-55,7f,4f,green)
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
            val level=1f-.5f*((60-health.coerceIn(0,100))/60f).coerceIn(0f,1f)
            oval(x,y,35f,14.5f,deep);oval(x,y+1,32.5f,12f,earth);oval(x,y+.5f,30f*level,10f*level,if(night)0xff42635e.toInt() else 0xff8fc0bd.toInt())
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

    /**
     * Cross-section under the meadow, in soil coordinates: x is the meadow's logical x, y = 0 is the
     * grass line. The meadow canvas draws the top slice (grass line to its bottom edge) and the
     * underground canvas the rest, with the same function and scale, so the two meet seamlessly and
     * roots, the pond, the burrow and the anthill sit under what is above them.
     */
    fun soil(canvas: Canvas, width: Float, bottom: Float, health: Int, world: MeadowState, effects: MeadowEffects,
        motion: Boolean, blades: Array<MeadowMotion.Blade>) { c = canvas; val t = if (motion) effects.time else 0f
        val s = if (night) NIGHT_SOIL else DAY_SOIL
        val growth = health.coerceIn(0, 100) / 100f
        // Below 60 health the soil dries out: paler, dustier layers, cracks from the surface and a lower pond.
        val dry = ((60 - health.coerceIn(0, 100)) / 60f).coerceIn(0f, 1f)
        fun dusty(color: Int) = androidx.core.graphics.ColorUtils.blendARGB(color, s[DUST], dry * .4f)
        // Strata with wavy boundaries; the top edge follows the mound the blades stand on.
        path.reset(); path.moveTo(-2f, bottom + 4)
        var x = -2f; while (x <= width + 2) { path.lineTo(x, mound(x, width)); x += 4f }
        path.lineTo(width + 2, bottom + 4); path.close(); fill(dusty(s[TOP])); c.drawPath(path, paint)
        stratum(width, bottom, 62f, 5f, .4f, dusty(s[SUB])); stratum(width, bottom, 128f, 6f, 1.7f, dusty(s[CLAY])); stratum(width, bottom, 200f, 7f, 2.9f, s[ROCK])
        if (dry > 0f) cracks(width, dry, s)
        for (i in 0..69) { val px = (i * 67 + 13) % width; val py = 20f + (i * 41 + 9) % 280f; val r = .8f + (i % 4) * .6f
            fill(if (i % 3 == 0) s[PEBBLE_DARK] else s[PEBBLE]); paint.alpha = 140; c.save(); c.rotate((i % 5) * 23f, px, py); c.drawOval(px - r * 1.4f, py - r, px + r * 1.4f, py + r, paint); c.restore() }
        // An old shell in the clay.
        paint.color = s[SHELL]; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.6f; path.reset()
        var a = 0f; while (a < 16.3f) { val r = 1 + a * 1.25f; val sx = width * .58f + cos(a) * r; val sy = 168 + sin(a) * r * .85f; if (a == 0f) path.moveTo(sx, sy) else path.lineTo(sx, sy); a += .15f }
        c.drawPath(path, paint)
        // Hair roots under the living blades: deeper and denser when healthy.
        val active = 8f + growth * (MeadowMotion.COUNT - 8)
        for (b in blades) { if (b.activationRank > active) continue; val bx = b.u * width; val top = mound(bx, width) + 2; val len = 10 + growth * 26 * b.heightFactor
            path.reset(); path.moveTo(bx, top); path.quadTo(bx + sin(b.phase) * 5, top + len * .6f, bx + sin(b.phase * 2) * 7, top + len)
            paint.color = s[HAIR]; paint.alpha = 140; paint.style = Paint.Style.STROKE; paint.strokeWidth = .7f; c.drawPath(path, paint) }
        // Taproot under the sapling.
        if (world.acornStage > 0) { val rx = width * .48f; val depth = when (world.acornStage) { 1 -> 22f; 2 -> 93f; else -> 157f }
            stem(s[ROOT], 2.2f) { moveTo(rx, 4f); cubicTo(rx + 7, depth * .3f, rx - 12, depth * .6f, rx, depth) }
            for (i in 1..4) { val y = depth * i / 5; val sign = if (i % 2 == 0) -1 else 1; stem(s[ROOT], 1f) { moveTo(rx, y); quadTo(rx + sign * 25, y + 12, rx + sign * (30 + i * 5), y + 30) } } }
        // Hedgehog burrow under the hedgehog.
        if (MeadowDiscovery.Hedgehog in world.discoveries) { val hx = width * .16f
            stem(s[TUNNEL], 7f) { moveTo(hx + 12, 10f); quadTo(hx + 26, 30f, hx + 6, 42f) }
            oval(hx - 4, 46f, 18f, 9f, s[TUNNEL]); for (i in 0..5) oval(hx - 14 + i * 4, 50f, 4f, 1.8f, if (i % 2 == 0) 0xFFC79A4D.toInt() else 0xFFB2763F.toInt(), i * 28f) }
        // Pond bowl and its fish, right under the pond.
        if (MeadowDiscovery.Pond in world.discoveries) { val px = width * .81f; val py = 22f
            fill(s[BANK]); c.drawArc(px - 33, py - 27, px + 33, py + 27, 0f, 180f, true, paint)
            // Drought lowers the water inside the bowl.
            val level = 1f - .5f * dry
            fill(s[WATER]); c.drawArc(px - 30 * level, py - 24 * level + 24 * (1 - level), px + 30 * level, py + 24, 0f, 180f, true, paint)
            for (i in 0..4) oval(px - 18 + i * 9, py + 21 - kotlin.math.abs(i - 2) * 2, 3.4f, 2.2f, s[PEBBLE])
            val fx = px + sin(t * .9f) * 14; val dir = if (cos(t * .9f) >= 0) 1 else -1
            oval(fx, py + 10, 5.5f, 3f, gold); shape(gold) { moveTo(fx - dir * 5, py + 10); lineTo(fx - dir * 9, py + 7); lineTo(fx - dir * 9, py + 13); close() }
            oval(fx + dir * 2.5f, py + 9, .8f, .8f, deep)
            val age = t - effects.bubbleAt; if (age in 0f..2f) for (i in 0..2) { fill(s[WATER_LIGHT]); paint.alpha = ((1 - age / 2) * 255).toInt(); c.drawCircle(fx + dir * 4 + i * 2, py + 7 - age * 8 - i * 4, 1.2f + i * .3f, paint) } }
        // Anthill tunnels under the mound, with the ant and its parcel.
        val ax = width * .64f
        shape(s[MOUND]) { moveTo(ax - 12, 8f); quadTo(ax, -6f, ax + 12, 8f); close() }
        val tunnel = floatArrayOf(ax, 6f, ax + 10, 34f, ax - 6, 58f, ax + 18, 84f, ax + 34, 92f)
        stem(s[TUNNEL], 4f) { moveTo(tunnel[0], tunnel[1]); var k = 2; while (k < tunnel.size) { lineTo(tunnel[k], tunnel[k + 1]); k += 2 } }
        oval(tunnel[4], tunnel[5], 9f, 5f, s[TUNNEL]); oval(tunnel[8], tunnel[9], 9f, 5f, s[TUNNEL])
        val u = if (motion) (t * .07f) % 1f else .5f; val seg = minOf(3, (u * 4).toInt()); val k = u * 4 - seg
        val antX = tunnel[seg * 2] + (tunnel[seg * 2 + 2] - tunnel[seg * 2]) * k; val antY = tunnel[seg * 2 + 1] + (tunnel[seg * 2 + 3] - tunnel[seg * 2 + 1]) * k
        for (i in -1..1) oval(antX + i * 2.4f, antY, if (i == 0) 1.1f else 1.3f, if (i == 0) 1.1f else 1.3f, s[ANT])
        when (effects.antParcel) { 0 -> oval(antX + 1, antY - 5, 5f, 2f, 0xFF9FBF80.toInt(), -34f); 1 -> oval(antX + 1, antY - 4, 2.6f, 2.6f, 0xFFD29C7B.toInt()); else -> line(antX - 4, antY - 6, antX + 6, antY - 3, 0xFFD9BC87.toInt(), 1.4f) }
        // Beetle chamber.
        val bx = width * .76f + if (effects.beetleUntil > effects.time) sin(t * 2) * 10 else 0f; val by = 110f
        oval(width * .76f, by, 26f, 15f, s[TUNNEL]); oval(bx, by, 6f, 8f, 0xFF8CAA7B.toInt()); line(bx, by - 7, bx, by + 7, 0xFF40553E.toInt(), .8f); oval(bx, by - 9, 3f, 3f, 0xFF3B4C36.toInt())
        for (side in listOf(-1, 1)) for (i in -1..1) line(bx + side * 5, by + i * 3, bx + side * 10, by + i * 5, 0xFFD3BC85.toInt(), .8f)
        // Worm in the topsoil.
        val wiggle = if (effects.wormUntil > effects.time) 3f else 1.2f
        stem(s[WORM], 3f) { for (i in 0..12) { val wx = width * .40f + i * 2.2f; val wy = 40 + sin(i * .8f + t * 3) * wiggle; if (i == 0) moveTo(wx, wy) else lineTo(wx, wy) } }
        // Fungus threads join the mushrooms to the roots; a tapped mushroom sends light along its thread.
        for (i in 0..2) { val (mx, my) = mushroom(i, width)
            paint.color = s[MYCELIUM]; paint.alpha = 115; paint.style = Paint.Style.STROKE; paint.strokeWidth = .6f
            path.reset(); path.moveTo(mx, my - 4); path.cubicTo(mx + 14, my - 40, width * .48f - 30, 70f, width * .48f - 4, 40f); c.drawPath(path, paint) }
        val pulse = t - effects.pulseAt
        if (effects.pulseFrom in 0..2 && pulse in 0f..1.6f) { val (mx, my) = mushroom(effects.pulseFrom, width); val q = pulse / 1.6f
            fun bez(p0: Float, p1: Float, p2: Float, p3: Float) = (1 - q).pow(3) * p0 + 3 * (1 - q).pow(2) * q * p1 + 3 * (1 - q) * q * q * p2 + q.pow(3) * p3
            val px = bez(mx, mx + 14, width * .48f - 30, width * .48f - 4); val py = bez(my - 4, my - 40, 70f, 40f)
            fill(s[MUSHROOM]); paint.alpha = 64; c.drawCircle(px, py, 6f, paint); oval(px, py, 2f, 2f, s[MYCELIUM]) }
        for (i in 0..2) { val (mx, my) = mushroom(i, width); val sc = MUSHROOM_S[i]
            if (effects.glowUntil > effects.time) { fill(s[MUSHROOM]); paint.alpha = (46 + 20 * sin(t * 4)).toInt(); c.drawCircle(mx, my - 18 * sc, 24 * sc, paint) }
            stem(s[MUSHROOM_STEM], 3 * sc) { moveTo(mx, my); quadTo(mx + 3, my - 10 * sc, mx, my - 20 * sc) }
            oval(mx, my - 21 * sc, 13 * sc, 5 * sc, s[MUSHROOM]); for (j in -1..1) oval(mx + j * 6 * sc, my - 23 * sc, 1.2f * sc, 1.2f * sc, 0xFFFFF2C5.toInt()) }
    }
    /** Dry-soil cracks: jagged dark lines from the surface, longer and more numerous as [dry] grows, each with a pale lip. */
    private fun cracks(width: Float, dry: Float, s: IntArray) {
        val shown = (CRACK_X.size * (.35f + .65f * dry)).toInt()
        for (i in 0 until shown) { val x0 = CRACK_X[i] * width; val len = (18f + 52f * ((i * 37) % 10) / 10f) * dry
            if (len < 4f) continue
            path.reset(); path.moveTo(x0, mound(x0, width) + 1); var x = x0; var y = mound(x0, width) + 1; var step = 0
            while (y < mound(x0, width) + len) { step++; x += (if (step % 2 == 0) 1 else -1) * (2f + (i + step) % 3); y += 5f + (step % 3); path.lineTo(x, y)
                // A short side branch on some steps.
                if (step == 2 && i % 2 == 0) { path.moveTo(x, y); path.lineTo(x + 6, y + 7); path.moveTo(x, y) } }
            paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            paint.color = s[DUST]; paint.alpha = (90 * dry).toInt(); paint.strokeWidth = 2.6f; c.drawPath(path, paint)
            paint.color = s[CRACK]; paint.alpha = (110 + 145 * dry).toInt(); paint.strokeWidth = 1.2f; c.drawPath(path, paint) }
    }

    /** Soil-coordinate hit centres for the underground's tap targets. */
    fun mushroom(i: Int, width: Float) = (MUSHROOM_X[i] * width) to MUSHROOM_Y[i]
    private fun mound(x: Float, width: Float) = 6f - sin(x / width * PI.toFloat()) * 8f
    private fun stratum(width: Float, bottom: Float, base: Float, amp: Float, phase: Float, color: Int) {
        path.reset(); path.moveTo(-2f, bottom + 4); var x = -2f
        while (x <= width + 2) { val u = x / width * 2 * PI.toFloat(); path.lineTo(x, base + amp * sin(u * 1.4f + phase) + amp * .5f * sin(u * 3.1f + phase * 2)); x += 4f }
        path.lineTo(width + 2, bottom + 4); path.close(); fill(color); c.drawPath(path, paint)
    }

    companion object {
        private const val TOP = 0; private const val SUB = 1; private const val CLAY = 2; private const val ROCK = 3; private const val PEBBLE = 4
        private const val PEBBLE_DARK = 5; private const val SHELL = 6; private const val HAIR = 7; private const val ROOT = 8; private const val TUNNEL = 9
        private const val BANK = 10; private const val WATER = 11; private const val WATER_LIGHT = 12; private const val MOUND = 13; private const val ANT = 14
        private const val WORM = 15; private const val MYCELIUM = 16; private const val MUSHROOM = 17; private const val MUSHROOM_STEM = 18
        private const val DUST = 19; private const val CRACK = 20
        private val CRACK_X = floatArrayOf(.07f, .27f, .43f, .55f, .7f, .93f, .2f, .6f, .36f, .88f)
        private val DAY_SOIL = intArrayOf(0xFF7C6647.toInt(), 0xFF8E7856.toInt(), 0xFFA2885F.toInt(), 0xFF5F503B.toInt(), 0xFFB9A27C.toInt(),
            0xFF826F55.toInt(), 0xFFD9C79E.toInt(), 0xFFD9C49A.toInt(), 0xFFE8D3A8.toInt(), 0xFF5E4C33.toInt(), 0xFF6C5A40.toInt(), 0xFF6EAEC7.toInt(),
            0xFFAAD6E2.toInt(), 0xFF8E7451.toInt(), 0xFF3E3022.toInt(), 0xFFD89A86.toInt(), 0xFFF4EBD2.toInt(), 0xFFC2D29C.toInt(), 0xFFE9DCAD.toInt(),
            0xFFCDB68A.toInt(), 0xFF3D2F20.toInt())
        private val NIGHT_SOIL = intArrayOf(0xFF3A3127.toInt(), 0xFF433829.toInt(), 0xFF4E4231.toInt(), 0xFF29231B.toInt(), 0xFF5C5040.toInt(),
            0xFF40382D.toInt(), 0xFF9F906F.toInt(), 0xFF9C8962.toInt(), 0xFFBFA97C.toInt(), 0xFF231D16.toInt(), 0xFF2E271E.toInt(), 0xFF2D5B6B.toInt(),
            0xFF4F8696.toInt(), 0xFF4A3F30.toInt(), 0xFFE8D5AB.toInt(), 0xFFB57E6E.toInt(), 0xFFE8F0C8.toInt(), 0xFFC2D29C.toInt(), 0xFFE9DCAD.toInt(),
            0xFF6E5F45.toInt(), 0xFF15110C.toInt())
        private val MUSHROOM_X = floatArrayOf(.12f, .2f, .31f); private val MUSHROOM_Y = floatArrayOf(176f, 184f, 160f); private val MUSHROOM_S = floatArrayOf(1f, .7f, .75f)
        val FLOWER_X=floatArrayOf(.16f,.33f,.49f,.67f,.81f,.24f,.73f);val FLOWER_H=floatArrayOf(70f,93f,63f,82f,59f,52f,101f) }
}

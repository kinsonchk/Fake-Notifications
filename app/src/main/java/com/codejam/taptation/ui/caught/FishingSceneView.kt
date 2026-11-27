package com.codejam.taptation.ui.caught

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import com.codejam.taptation.R
import com.codejam.taptation.logic.meadow.MeadowCameo
import com.codejam.taptation.ui.home.MeadowPainter
import com.codejam.taptation.ui.theme.*
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*

/** Shared by Compose and TYPE_APPLICATION_OVERLAY. Pure decoration; never calls a game action. */
internal class FishingSceneView(context:Context):View(context) {
    private val renderer=FishingRenderer(context)
    private var binding:String?=null
    private var started=0L
    private var animateIntro=true
    private var touchAt=-20f
    private var downY=0f
    private var attached=false
    private val rect=Rect()
    private val scrollListener=ViewTreeObserver.OnScrollChangedListener { startFrames() }
    private val frames=object:Runnable {
        override fun run() {
            if(!attached||windowVisibility!=VISIBLE||!isShown||!getGlobalVisibleRect(rect))return
            invalidate()
            if(ValueAnimator.areAnimatorsEnabled())postOnAnimation(this)
        }
    }
    init { isClickable=true;isFocusable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES }

    fun bind(key:String,cameo:MeadowCameo,appLabel:String?,dark:Boolean,animate:Boolean) {
        if(binding!=key){binding=key;started=SystemClock.elapsedRealtime();animateIntro=animate;touchAt=-20f}
        renderer.configure(cameo,appLabel,dark)
        contentDescription=context.getString(when(cameo){
            MeadowCameo.Fish->R.string.caught_scene_fish
            MeadowCameo.Robin->R.string.caught_scene_robin
            MeadowCameo.Squirrel->R.string.caught_scene_squirrel
            MeadowCameo.Hedgehog->R.string.caught_scene_hedgehog
        })
        invalidate();startFrames()
    }
    private fun startFrames(){removeCallbacks(frames);if(attached)postOnAnimation(frames)}
    override fun onAttachedToWindow(){super.onAttachedToWindow();attached=true;viewTreeObserver.addOnScrollChangedListener(scrollListener);startFrames()}
    override fun onDetachedFromWindow(){attached=false;removeCallbacks(frames);if(viewTreeObserver.isAlive)viewTreeObserver.removeOnScrollChangedListener(scrollListener);super.onDetachedFromWindow()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)startFrames()else removeCallbacks(frames)}
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int){super.onLayout(changed,left,top,right,bottom);startFrames()}
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);val density=resources.displayMetrics.density;val age=(SystemClock.elapsedRealtime()-started)/1000f
        val motion=ValueAnimator.areAnimatorsEnabled();canvas.save();canvas.scale(density,density);canvas.clipRect(0f,0f,width/density,height/density)
        renderer.draw(canvas,width/density,height/density,if(motion)age else 0f,if(animateIntro&&motion)(age/4.8f).coerceIn(0f,1f)else 1f,if(motion)age-touchAt else 10f)
        canvas.restore()
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        when(event.actionMasked){MotionEvent.ACTION_DOWN->{downY=event.y;return true};MotionEvent.ACTION_UP->{if(abs(event.y-downY)<24*resources.displayMetrics.density)performClick();return true};MotionEvent.ACTION_CANCEL->return true}
        return true
    }
    override fun performClick():Boolean{super.performClick();touchAt=(SystemClock.elapsedRealtime()-started)/1000f;invalidate();startFrames();return true}
}

private class FishingRenderer(private val context:Context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private val animal=MeadowPainter()
    private var cameo=MeadowCameo.Fish
    private var appLabel=""
    private var bg=0;private var ink=0;private var muted=0;private var green=0;private var paper=0;private var water=0;private var gold=0;private var body=0;private var patch=0;private var earth=0;private var red=0
    private lateinit var c:Canvas
    private var w=300f
    fun configure(cameo:MeadowCameo,label:String?,dark:Boolean){this.cameo=cameo;appLabel=label?:context.getString(R.string.caught_scene_notification)
        bg=(if(dark)DarkBackground else LightBackground).toArgb();ink=(if(dark)DarkText else LightText).toArgb();muted=(if(dark)DarkTextMuted else LightTextMuted).toArgb()
        green=(if(dark)GrassDarkHighlight else GrassBlade).toArgb();paper=(if(dark)DarkCard else LightCard).toArgb()
        water=if(dark)0xff111c26.toInt()else 0xffdbe5ee.toInt();gold=MeadowGold.toArgb();earth=(if(dark)GrassDarkDry else GrassDry).toArgb()
        body=if(dark)0xffefdfa6.toInt()else 0xffdfc682.toInt();patch=(if(dark)GrassDarkBlade else GrassHighlight).toArgb();red=if(dark)0xffd58c7e.toInt()else 0xffad5a48.toInt()
        animal.palette(if(dark)GrassDarkHighlight else GrassHighlight,if(dark)GrassDarkBlade else GrassBlade,if(dark)MeadowNightPetal else MeadowPetal,MeadowGold,if(dark)GrassDarkDry else GrassDry,dark)
    }
    private fun fill(color:Int){p.color=color;p.style=Paint.Style.FILL;p.alpha=255}
    private fun oval(x:Float,y:Float,rx:Float,ry:Float,color:Int,angle:Float=0f){fill(color);c.save();c.rotate(angle,x,y);c.drawOval(x-rx,y-ry,x+rx,y+ry,p);c.restore()}
    private fun shape(color:Int,build:Path.()->Unit){path.reset();path.build();fill(color);c.drawPath(path,p)}
    private fun stroke(color:Int,width:Float=1f,build:Path.()->Unit){path.reset();path.build();p.color=color;p.style=Paint.Style.STROKE;p.strokeWidth=width;p.strokeCap=Paint.Cap.ROUND;c.drawPath(path,p)}
    private fun notification(x:Float,y:Float,rotation:Float=0f){val width=min(185f,w*.76f);c.save();c.translate(x,y);c.rotate(rotation)
        fill(paper);c.drawRoundRect(-width/2,-23.5f,width/2,23.5f,11f,11f,p)
        p.color=earth;p.alpha=65;p.style=Paint.Style.STROKE;p.strokeWidth=.7f;c.drawRoundRect(-width/2,-23.5f,width/2,23.5f,11f,11f,p)
        fill(green);c.drawRoundRect(-width/2+9,-11f,-width/2+32,12f,7f,7f,p)
        val hx=-width/2+20
        shape(bg){moveTo(hx,7f);cubicTo(hx-12,-1f,hx-6,-8f,hx,-2f);cubicTo(hx+6,-8f,hx+12,-1f,hx,7f);close()}
        fill(ink);p.textAlign=Paint.Align.LEFT;p.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD);p.textSize=11f
        val name=android.text.TextUtils.ellipsize(appLabel,android.text.TextPaint(p),width-49,android.text.TextUtils.TruncateAt.END).toString()
        c.drawText(name,-width/2+40,-3f,p);fill(muted);p.typeface=Typeface.DEFAULT;p.textSize=10f
        c.drawText(context.getString(R.string.caught_scene_bait),-width/2+40,13f,p)
        oval(width/2-3,-20f,6f,6f,red);fill(bg);p.textSize=8f;p.textAlign=Paint.Align.CENTER;c.drawText("1",width/2-3,-17f,p);c.restore()
    }
    private fun fish(x:Float,y:Float,angle:Float,time:Float,scale:Float=1f){c.save();c.translate(x,y);c.rotate(angle);c.scale(scale,scale)
        c.save();c.translate(-27f,0f);c.rotate(sin(time*13)*13)
        shape(gold){moveTo(4f,0f);cubicTo(-8f,-2f,-21f,-20f,-23f,-16f);quadTo(-17f,0f,-23f,16f);cubicTo(-15f,16f,-5f,2f,4f,0f);close()};c.restore()
        shape(gold){moveTo(-10f,-10f);quadTo(3f,-29f,14f,-13f);close()};shape(gold){moveTo(1f,9f);quadTo(-4f,30f,-14f,15f);close()}
        shape(body){moveTo(33f,0f);cubicTo(20f,-23f,-17f,-23f,-29f,0f);cubicTo(-16f,21f,20f,22f,33f,0f);close()}
        oval(-8f,-9f,10f,8f,patch,-30f);oval(6f,10f,10f,7f,patch,-20f);oval(21f,-6f,4f,4f,bg);oval(22f,-6f,1.8f,1.8f,ink)
        stroke(earth){moveTo(29f,2f);quadTo(33f,5f,38f,1f);moveTo(29f,2f);quadTo(33f,9f,37f,7f)};c.restore()
    }
    private fun guilty(x:Float,y:Float,time:Float){c.save();c.translate(x,y)
        for(sign in intArrayOf(-1,1)){shape(gold){moveTo(sign*18f,1f);quadTo(sign*(38+sin(time*10)*2),-12f,sign*32f,12f);quadTo(sign*25f,18f,sign*18f,6f);close()}}
        oval(0f,1f,24f,26f,body);oval(-10f,-15f,9f,9f,patch,-20f)
        val blink=time%5>4.82f;for(eye in intArrayOf(-1,1)){oval(eye*13f,-6f,8f,if(blink)1f else 9f,bg);if(!blink)oval(eye*10f,-5f,3.1f,3.1f,ink)}
        stroke(earth,1.5f){addOval(-5f,9f,5f,15f,Path.Direction.CW)};c.restore()
    }
    private fun splash(phase:Float,at:Float,waterY:Float){if(phase<at)return;val q=((phase-at)/.3f).coerceIn(0f,1f)
        p.color=green;p.alpha=((1-q)*100).toInt();p.style=Paint.Style.STROKE;p.strokeWidth=1f;c.drawOval(w*.5f-15-q*w*.36f,waterY+4-q*12,w*.5f+15+q*w*.36f,waterY+10+q*12,p)
        for(i in 0..14){val a=i*2.4f;val x=w*.5f+cos(a)*(15+q*55);val y=waterY-abs(sin(a))*55*q+45*q*q
            fill(patch);p.alpha=((1-q)*170).toInt();c.drawOval(x-1.5f,y-3,x+1.5f,y+3,p)}
    }
    fun draw(canvas:Canvas,width:Float,height:Float,time:Float,phase:Float,tapAge:Float){c=canvas;w=width;val ground=height*.76f
        val gradient=RadialGradient(w*.5f,ground+11,w*.55f,intArrayOf(water,bg),null,Shader.TileMode.CLAMP)
        fill(water);p.shader=gradient;c.drawOval(1f,ground-20,w-1,ground+42,p);p.shader=null
        for(i in 0..3){p.color=green;p.alpha=50;p.style=Paint.Style.STROKE;p.strokeWidth=1f;val r=35+i*24+sin(time*2-i)*3;c.drawOval(w*.5f-r,ground+3-i*2,w*.5f+r,ground+9+i*2,p)}
        for(u in floatArrayOf(.1f,.14f,.87f))stroke(patch,1.6f){moveTo(w*u,ground+13);quadTo(w*u-7,ground+3,w*u+sin(time+u)*3,ground-20)}
        when(cameo){
            MeadowCameo.Fish->fishStory(phase,ground,time,tapAge)
            MeadowCameo.Robin->robinStory(phase,ground,time,tapAge)
            MeadowCameo.Squirrel->squirrelStory(phase,ground,time,tapAge)
            MeadowCameo.Hedgehog->hedgehogStory(phase,ground,time)
        }
    }
    private fun fishStory(q:Float,y:Float,t:Float,tap:Float){var x=w*.45f+sin(t*.9f)*w*.13f;var fy=y+sin(t*2)*4;var angle=0f
        if(q<.23f){x=w*(.13f+q/.23f*.21f);fy=y+4}
        else if(q<.44f){val p=(q-.23f)/.21f;x=w*.34f;fy=y-sin(p*PI.toFloat()*.5f)*(y-67);angle=-7-sin(p*PI.toFloat())*25}
        else if(q<.68f){val p=(q-.44f)/.24f;x=w*(.34f+p*.12f);fy=67+p*p*(y+64-67);angle=7+sin(p*PI.toFloat())*40}
        stroke(green,2.5f){moveTo(w+13,66f);quadTo(w*.97f,-6f,w*.76f,9f)}
        stroke(earth,.8f){moveTo(w*.76f,9f);quadTo(w*.7f,15f,w*.51f+if(q>.44f)sin(t*7)*7 else 0f,if(q<.44f)36f else 38f)}
        if(q<.68f){c.save();if(q>.44f)c.clipRect(0f,0f,w,y+12);val p=((q-.44f)/.24f).coerceAtLeast(0f);notification(if(q<.44f)w*.51f else x+12,if(q<.44f)49+sin(t*2)*1.5f else fy-21,if(q<.44f)sin(t*2)*1.4f else sin(p*PI.toFloat())*14);c.restore();fish(x,fy,angle,t)}
        else if(q>.82f){val p=((q-.82f)/.18f).coerceAtMost(1f);val jump=if(tap in 0f.. .8f)sin(tap/.8f*PI.toFloat())*28 else 0f;guilty(w*.5f,y+26-p*27+sin(t*2)*2-jump,t)}
        splash(q,.58f,y)
    }
    private fun robinStory(q:Float,y:Float,t:Float,tap:Float){if(q<.7f)fish(w*.33f,y+4,0f,t)else guilty(w*.35f,y+2,t)
        if(q<.28f){notification(w*.51f,49f);val p=q/.28f;animal.cameoRobin(c,-25+w*.51f*p,42-sin(p*PI.toFloat())*12,t,true)}
        else if(q<.73f){val p=(q-.28f)/.45f;val x=w*.43f+p*w*.85f;val yy=42-sin(p*PI.toFloat())*20;stroke(earth,.7f){moveTo(x+23,yy-19);lineTo(x+37,yy+4)};notification(x+37,yy+27,sin(t*4)*5);animal.cameoRobin(c,x,yy,t,true)}
        else{stroke(earth,2f){moveTo(w*.83f,y+14);lineTo(w*.83f,y-15);lineTo(w*.72f,y-15)};animal.cameoRobin(c,w*.81f,y-18,t,tap<.8f);oval(w*.81f+25,y-36,3f,3f,red)}
    }
    private fun squirrelStory(q:Float,y:Float,t:Float,tap:Float){fish(w*.23f,y+4,0f,t,.8f)
        // A grassy bank, not an animal sitting in the water.
        oval(w*.75f,y+15,w*.24f,18f,earth);oval(w*.75f,y+9,w*.23f,8f,patch)
        if(q<.32f){notification(w*.51f,49+q*100,1f);animal.cameoSquirrel(c,w+32-(w*.24f+32)*(q/.32f),y+6,t,false)}
        else if(q<.76f){val p=(q-.32f)/.44f;c.save();c.clipRect(0f,0f,w,y+10);notification(w*.66f,80+p*p*100,p*17);c.restore();animal.cameoSquirrel(c,w*.79f,y+6,t,true)
            for(i in 0..7)oval(w*.7f-(t*25+i*11)%40,y-4-abs(sin(t*8+i))*13,1.5f,1.5f,earth)
        }else{oval(w*.67f,y+8,18f,4f,earth);animal.cameoSquirrel(c,w*.82f,y+6,t,tap<.8f);stroke(patch,1.5f){moveTo(w*.67f,y+5);lineTo(w*.67f,y-8)};oval(w*.67f-5,y-6,7f,2.5f,patch,-30f);oval(w*.67f+5,y-6,7f,2.5f,patch,30f);oval(w*.67f,y-10,2.5f,2.5f,red)}
    }
    private fun hedgehogStory(q:Float,y:Float,t:Float){oval(w*.18f,y+18,w*.2f,13f,earth);oval(w*.18f,y+14,w*.19f,6f,patch);animal.cameoHedgehog(c,w*.18f,y+12,t)
        if(q<.7f){val p=((q-.2f)/.5f).coerceAtLeast(0f);val fy=if(q<.2f)y else y-sin(p*PI.toFloat())*(y-67);fish(w*.6f,fy,if(q>.45f)23f else -12f,t);c.save();if(q>.45f)c.clipRect(0f,0f,w,y+10);notification(w*.56f,if(q<.45f)49f else fy-18);c.restore()}else guilty(w*.65f,y+2,t)
        splash(q,.53f,y)
    }
}

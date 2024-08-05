package dev.legendsayantan.extendroid.data

import android.view.MotionEvent

/**
 * @author legendsayantan
 */
data class TouchMotionEvent(val downTime:Long, val eventTime:Long,val action:Int,val x:Float,val y:Float){

    companion object{
        fun MotionEvent.asTouchEvent(scale:Float=1f): TouchMotionEvent {
            return TouchMotionEvent(downTime,eventTime,action,x*scale,y*scale)
        }
        fun TouchMotionEvent.asMotionEvent(): MotionEvent {
            return MotionEvent.obtain(downTime,eventTime,action,x,y,0)
        }
    }
}
package org.tensorflow.lite.examples.audio

import android.os.Handler
import android.os.Looper

class Timer(listener: OnTimerTickListener){

    interface OnTimerTickListener{
        fun onTimerTick()
    }
    
    private var handler: Handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable
    
    private var duration = 0L
    private var delay = 100L

    init {
        runnable = Runnable{
            //This runnable is what is run every 100 miliseconds, and its created when the timer is created
            duration += delay
            handler.postDelayed(runnable, delay)
            listener.onTimerTick()
        }

    }

    fun start(){
        handler.postDelayed(runnable, delay)
        //when this function is called, the handler starts the runnable in 100 ml
    }

    fun stop(){
        handler.removeCallbacks(runnable)
        duration = 0L
    }

    //every 100 ms update the text view




}
package com.example.audiorecorder

import android.os.Handler
import android.os.Looper
import java.time.Duration

class Timer(listener: OnTimerTickListener){

    interface OnTimerTickListener{
        fun onTimerTick(duration: String)
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
            listener.onTimerTick(convertDurationToReadable())
        }

    }

    fun start(){
        handler.postDelayed(runnable, delay)
        //when this function is called, the handler starts the runnable in 100 ml
    }

    fun pause(){
        handler.removeCallbacks(runnable)


    }

    fun stop(){
        handler.removeCallbacks(runnable)
        duration = 0L
    }

    private fun convertDurationToReadable(): String{
        var millis = duration % 1000
        val seconds = (duration / 1000) % 60
        val minutes = (duration / (1000*60)) % 60
        val hours = (duration / (1000*60*60))

        val formattedString: String = if(hours > 0)
            "%02d:%02d:%02d.%02d".format(hours, minutes, seconds, millis/10)
            else
            "%02d:%02d.%02d".format(minutes, seconds, millis/10)

        return formattedString

    }


    //every 100 ms update the text view




}
package com.example.audiorecorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.provider.CalendarContract.Colors
import android.util.AttributeSet
import android.util.Log
import android.view.View

class WaveformView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    //main method is draw method

    private var paint = Paint()
    private var amplitudes = ArrayList<Float>()
    private var spikes = ArrayList<RectF>()

    private var radius = 6f
    private var spikeWidth = 9f
    private var distanceBetweenSpikes = 6f

    //right to left stuff
    private var screenWidth = 0f
    private var screenHeight = 400f

    private var maxNumberOfSpikesInWaveformView = 0


    init {
        paint.color = Color.rgb(244, 81, 40)

        screenWidth = resources.displayMetrics.widthPixels.toFloat()

        maxNumberOfSpikesInWaveformView = (screenWidth/(spikeWidth+distanceBetweenSpikes)).toInt()
    }

    fun addAmplitude(amp: Float){
        Log.d("WaveformView", "Add Amplitude Called with amplitude $amp")
        //each time you get a new amplitude you store it in a list

        //normalize - idk why divide by 7
        var norm = Math.min(amp.toInt()/20, 400).toFloat()


        amplitudes.add(norm)

        spikes.clear()

        var amps = amplitudes.takeLast(maxNumberOfSpikesInWaveformView)


        for(i in amps.indices){
            //i is the amplitide index
            //create a rect
            //var left = screenWidth - (i*(spikeWidth+distanceBetweenSpikes))
            var left = (i*distanceBetweenSpikes) + ((i-1)*spikeWidth)
            //screenHeigh/2 => center line
            var top = (screenHeight/2)  - amps[i]/2
            //amps[i]/2 is half of height of of rectangle
            var right = left + spikeWidth
            //bottom = height of waveform which is amplitude
            var bottom = top + amps[i]

            spikes.add(RectF(left, top, right, bottom))
        }
        //Draw is only called once on creation
        //add amplitude needs to call it each time it is called though
        //this is done with the following method
        invalidate()
    }

    override fun draw(canvas: Canvas?) {
        super.draw(canvas)

        spikes.forEach{
            canvas?.drawRoundRect(it, radius, radius, paint)
        }
        //spikes.clear()
    }

}


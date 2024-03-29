/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.audio

/* Copied from model personalization */
/* End copy */

/* My own */
/* End my own */

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioRecord
import android.media.audiofx.Visualizer
import android.media.audiofx.Visualizer.OnDataCaptureListener
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.examples.audio.fragments.AudioClassificationListener
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit


class AudioClassificationHelper(
    val context: Context,
    val listener: AudioClassificationListener,
    var currentModel: String = YAMNET_MODEL,
    var classificationThreshold: Float = DISPLAY_THRESHOLD,
    var overlap: Float = DEFAULT_OVERLAP_VALUE,
    var numOfResults: Int = DEFAULT_NUM_OF_RESULTS,
    var currentDelegate: Int = 0,
    var numThreads: Int = 2
) {
    private var interpreter: Interpreter? = null
    private lateinit var classifier: AudioClassifier
    private lateinit var tensorAudio: TensorAudio
    private lateinit var recorder: AudioRecord
    private lateinit var executor: ScheduledThreadPoolExecutor

    private var trainingExecutor: ExecutorService? = null

    private val dataBuffer: MutableList<TrainingSample> = mutableListOf()
    // To guarantee that only one thread is performing training or inference at any time
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())

    private val id2lblMap = mapOf<Int, String>(
        0 to "Appliances",
        1 to "Baby Cry",
        2 to "Car Honk",
        3 to "Cat Meow",
        4 to "Dog Bark",
        5 to "Doorbell",
        6 to "Fire Alarm",
        7 to "Knocking",
        8 to "Siren",
        9 to "Water Running"
    )

    private var rmsThreshold = 0.01f
    private var isTraining = false

    private val classifyRunnable = Runnable {
        classifyAudio()
    }

    init {
        initClassifier()
        Log.d("AudioClassificationHelper.kt", "We are Entering the ClassificationHelper.kt - 2")
    }

    fun initClassifier() {
        try {
            val interpreterOptions = Interpreter.Options()
            interpreterOptions.numThreads = numThreads
            val modelFile = FileUtil.loadMappedFile(context, "sc_model.tflite")
            interpreter =  Interpreter(modelFile, interpreterOptions)
            startAudioClassification()
        } catch (e: IllegalStateException) {
            listener.onError(
                "Audio Classifier failed to initialize. See error logs for details"
            )

            Log.e("AudioClassification", "TFLite failed to load with error: " + e.message)
        }
    }

    fun calculateRMS(audioTensor: FloatArray): Double {
        val squaredSum = audioTensor.fold(0.0) { accumulator, sample ->
            accumulator + sample * sample
        }
        val meanSquared = squaredSum / audioTensor.size
        val rms = Math.sqrt(meanSquared)
        return rms
    }

    @SuppressLint("MissingPermission")
    fun startAudioClassification() {


        //Format of the AudioClassifier
        val format = TensorAudio.TensorAudioFormat.builder()
            .setChannels(1)
            .setSampleRate(44100)
            .build()
        tensorAudio = TensorAudio.create(format, 44100)



        //create an recorder of type AudioRecord
        recorder = AudioRecord(
            6, // audioSource, //
            44100, // sampleRateInHz, // change to 44100
            16, // channelConfig, // CHANNEL_IN_MONO
            4, // audioFormat, // ENCODING_PCM_16BIT
            44100 // bufferSizeInBytes // I DON'T KNOW 31200 when sr=16000
            //AudioRecord.getMinBufferSize(44100, 16, 4)
        )

//        var bufferSizeInBytes = AudioRecord.getMinBufferSize(44100, 16, 4)
//        Log.d("MinBufferSize", "Buffer Size in Bytes: ${bufferSizeInBytes}")
//        Log.d("Min Buffer Size", "Buffer Size in Frames w 44100 Buffer Size: ${recorder.bufferSizeInFrames}")
//
//
//        var bufferSizeInBytes44100 = 44100
//        Log.d("Current Buffer Size", "Buffer Size in Bytes: ${bufferSizeInBytes44100}")
//        //Log.d("Current Buffer Size", "Buffer Size in Frames w 44100 Buffer Size: ${recorder.bufferSizeInFrames}")


        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            return
        }

        recorder.startRecording()




        Log.d("AudioRecorder", "${recorder.recordingState}")

        //ALSO STARTING MEDIA RECORDER AS AN INEFFICIENT WORKAROUND FOR WAVEFORM VISUALIZATION... to see if hacky solution works

        //Create a Media Recorder in the AudioFragment.kt


        executor = ScheduledThreadPoolExecutor(1)
        // Each model will expect a specific audio recording length. This formula calculates that
        // length using the input buffer size and tensor format sample rate.
        // For example, YAMNET expects 0.975 second length recordings.
        // This needs to be in milliseconds to avoid the required Long value dropping decimals.
        // val lengthInMilliSeconds = ((classifier.requiredInputBufferSize * 1.0f) /
        // classifier.requiredTensorAudioFormat.sampleRate) * 1000

        //Do something here


        val lengthInMilliSeconds = 1000 // one second

        // val interval = (lengthInMilliSeconds * (1 - overlap)).toLong()
        val interval = (1000).toLong()

        //What is this?
        executor.scheduleAtFixedRate(
            classifyRunnable,
            0,
            interval,
            TimeUnit.MILLISECONDS)
    }

    private fun classifyAudio() {
        tensorAudio.load(recorder) // 1, 15600(0.975*sr)


        //listener.getMaxAmplitudeArray(tensorAudio.getTensorBuffer().getFloatArray())
        //Log.d("AudioClassificationHelper", "Sent Waveform Audio to Buffer")

        //Call the listener over here

        //EVERY one second it wants to classify audio -> but can I get the floatArray
        //every 1ms get the max amplitude




        synchronized(lock) {
            val rms = calculateRMS(tensorAudio.getTensorBuffer().getFloatArray())
            // Log.d("AudioClassificationHelper", "rms: " + rms)
            if (rms > rmsThreshold){ // TODO: the method to define the threshold for sound happening
                val sr = recorder.getSampleRate()
                var inferenceTime = SystemClock.uptimeMillis()

                val inputs: MutableMap<String, Any> = HashMap()
                inputs["x"] = tensorAudio.getTensorBuffer().buffer

                val outputs: MutableMap<String, Any> = HashMap()
                val lbl = LongBuffer.allocate(1)
                outputs["class"] = lbl
                val probs = FloatBuffer.allocate(10)
                outputs["output"] = probs

                try {
                    interpreter!!.runSignature(inputs, outputs, "inference")
                } catch (e: IllegalArgumentException) {
                    listener.onError(
                        "Classification process failed. See error logs for details"
                    )

                    Log.e("AudioClassification", "Model failed to inference with error: " + e.message)
                }

                val class_probs = FloatArray(10) {0f}
                for (i in 0 until 10) {
                    class_probs[i] = probs[i]
                }

                inferenceTime = SystemClock.uptimeMillis() - inferenceTime
                listener.onResult(tensorAudio.getTensorBuffer().getFloatArray(),arrayOf(lbl[0].toFloat()).toFloatArray(),id2lblMap[lbl[0].toInt()].toString(), class_probs, inferenceTime)
            }
            else { // no sound
                listener.onResult(tensorAudio.getTensorBuffer().getFloatArray(),arrayOf(1f).toFloatArray(),"silence", floatArrayOf(0f), 0)
            }
        }

    }

    fun stopAudioClassification() {
        recorder.stop()
        executor.shutdownNow()
    }

    /* On-edge training */
    // Change label to categorical
    fun toCategoricalLabel(label: FloatArray, numClasses: Int): FloatArray {
        val categoricalLabel = FloatArray(numClasses) { 0f }
        categoricalLabel[label.get(0).toInt()] = 1f
        return categoricalLabel
    }


    // Add data to the data buffer
    fun collectSample(audio: FloatArray, label: FloatArray) {
        synchronized(lock) {
            dataBuffer.add(
                TrainingSample(audio, toCategoricalLabel(label,10))
            )
        }
        // Log.d("AudioClassificationHelper","audio: "+audio.contentToString())
        // Log.d("AudioClassificationHelper","label: "+toCategoricalLabel(label,10).contentToString())
        Log.d("AudioClassificationHelper","buffer size: "+dataBuffer.size)
    }

    // Check if data buffer is full
    fun isBufferFull(): Boolean {
        if (dataBuffer.size < BATCH_SIZE) {
            return false
        } else {
            return true
        }
    }

    // Check if it's training
    fun isModelTraining(): Boolean {
        return isTraining
    }

    // Running the interpreter's signature function
    private fun trainOneStep(
        x: MutableList<FloatArray>, y: MutableList<FloatArray>
    ): Float {
        val inputs: MutableMap<String, Any> = HashMap()
        inputs["x"] = x.toTypedArray()
        inputs["y"] = y.toTypedArray()

        val outputs: MutableMap<String, Any> = HashMap()
        val loss = FloatBuffer.allocate(1)
        outputs["loss"] = loss

        interpreter!!.runSignature(inputs, outputs, "finetune")
        return loss.get(0)
    }

    // Run fine-tuning with the data in the buffer
    suspend fun fineTuning() {
        if (dataBuffer.size < BATCH_SIZE) {
            throw RuntimeException(
                String.format(
                    "Too few samples to start training: need %d, got %d",
                    BATCH_SIZE, dataBuffer.size
                )
            )
        }

        Log.d("AudioClassificationHelper","Start fine-tuning")

        isTraining = true
        var meanLoss: Float = 1000f
        var numIterations = 0
        while (numIterations < 10 && meanLoss > 1) {
            var totalLoss = 0f
            // training
            dataBuffer.shuffle() // might not need to do this

            val trainingBatchAudios =
                MutableList(BATCH_SIZE) { FloatArray(44100) }

            val trainingBatchLabels =
                MutableList(BATCH_SIZE) { FloatArray(10) }

            dataBuffer.forEachIndexed { index, sample ->
                trainingBatchAudios[index] = sample.audio
                trainingBatchLabels[index] = sample.label
            }

            Log.d("AudioClassificationHelper","audio: "+trainingBatchAudios.toString())
            Log.d("AudioClassificationHelper","label: "+trainingBatchLabels.toString())

            val loss = trainOneStep(trainingBatchAudios,trainingBatchLabels)
            numIterations++

            totalLoss += loss
            meanLoss = loss
            handler.post {
                listener.onTrainResult(loss, numIterations)
            }
        }
        dataBuffer.clear()
        isTraining = false
        val outfile: File = File(context.getFilesDir(), "sc_model.tflite")
        Log.d("AudioClassificationHelper",outfile.getAbsolutePath())
        val inputs: MutableMap<String, Any> = HashMap()
        inputs["checkpoint_path"] = outfile.getAbsolutePath()
        val outputs: MutableMap<String, Any> = HashMap()
        interpreter!!.runSignature(inputs, outputs, "save")
        Log.d("AudioClassificationHelper","Finish fine-tuning")
    }

    // stop training the model
    fun stopTraining() {
        Log.d("AudioClassificationHelper","Stop fine-tuning")
        // empty the data buffer
        dataBuffer.clear()
        trainingExecutor!!.shutdownNow()
    }

    // update model
    fun updateModel() {
        Log.d("AudioClassificationHelper","Saving model")
        // call the signature function to restore model weights
        // no need to reload model to the interpreter
        val outfile: File = File(context.getFilesDir(), "sc_model.tflite")
        Log.d("AudioClassificationHelper",outfile.getAbsolutePath())
        val inputs: MutableMap<String, Any> = HashMap()
        inputs["checkpoint_path"] = outfile.getAbsolutePath()
        val outputs: MutableMap<String, Any> = HashMap()
        interpreter!!.runSignature(inputs, outputs, "save")
        Log.d("AudioClassificationHelper","model saved")
        // Log.d("AudioClassificationHelper",outfile.lastModified().toString())
    }



    /* End of on-edge training */

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_NNAPI = 1
        const val DISPLAY_THRESHOLD = 0.3f
        const val DEFAULT_NUM_OF_RESULTS = 2
        const val DEFAULT_OVERLAP_VALUE = 0.5f
        const val YAMNET_MODEL = "yamnet.tflite"
        const val SPEECH_COMMAND_MODEL = "speech.tflite"
        const val BATCH_SIZE = 10
    }

    data class TrainingSample(val audio: FloatArray, val label: FloatArray)
}
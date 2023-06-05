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

import android.content.Context
import android.media.AudioRecord
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
/* Copied from model personalization */
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
/* End copy */

/* My own */
import java.lang.IllegalArgumentException
import java.nio.LongBuffer
/* End my own */

import org.tensorflow.lite.examples.audio.fragments.AudioClassificationListener
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.task.core.BaseOptions




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

    private val id2lblMap = mapOf<Int, String>(
        0 to "appliances",
        1 to "baby cry",
        2 to "car honk",
        3 to "cat meow",
        4 to "dog bark",
        5 to "doorbell",
        6 to "fire alarm",
        7 to "knocking",
        8 to "siren",
        9 to "water running"
        )

    private val classifyRunnable = Runnable {
        classifyAudio()
    }

    init {
        initClassifier()
    }

    fun initClassifier() {
        // Set general detection options, e.g. number of used threads
        val baseOptionsBuilder = BaseOptions.builder()
            .setNumThreads(numThreads)

        // Use the specified hardware for running the model. Default to CPU.
        // Possible to also use a GPU delegate, but this requires that the classifier be created
        // on the same thread that is using the classifier, which is outside of the scope of this
        // sample's design.
        when (currentDelegate) {
            DELEGATE_CPU -> {
                // Default
            }
            DELEGATE_NNAPI -> {
                baseOptionsBuilder.useNnapi()
            }
        }

        // Configures a set of parameters for the classifier and what results will be returned.
        val options = AudioClassifier.AudioClassifierOptions.builder()
            .setScoreThreshold(classificationThreshold)
            .setMaxResults(numOfResults)
            .setBaseOptions(baseOptionsBuilder.build())
            .build()

        try {
            // Create the classifier and required supporting objects
            // My customized classifer should be able to create TensorAudio and AudioRecord
            val interpreterOptions = Interpreter.Options()
            interpreterOptions.numThreads = numThreads
            val modelFile = FileUtil.loadMappedFile(context, "sc_model.tflite")
            interpreter =  Interpreter(modelFile, interpreterOptions)
            // classifier = AudioClassifier.createFromFileAndOptions(context, currentModel, options)
            // tensorAudio = classifier.createInputTensorAudio()
            val format = TensorAudio.TensorAudioFormat.builder()
                .setChannels(1)
                .setSampleRate(44100)
                .build()
            tensorAudio = TensorAudio.create(format, 44100)
            // recorder = classifier.createAudioRecord()
            recorder = AudioRecord(
                6, // audioSource, //
                44100, // sampleRateInHz, // change to 44100
                16, // channelConfig, // CHANNEL_IN_MONO
                4, // audioFormat, // ENCODING_PCM_16BIT
                44100 // bufferSizeInBytes // I DON'T KNOW 31200 when sr=16000
            )
            // get some stats for environment noise
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

    fun startAudioClassification() {
        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            return
        }
        recorder.startRecording()
        executor = ScheduledThreadPoolExecutor(1)
        // Each model will expect a specific audio recording length. This formula calculates that
        // length using the input buffer size and tensor format sample rate.
        // For example, YAMNET expects 0.975 second length recordings.
        // This needs to be in milliseconds to avoid the required Long value dropping decimals.
        // val lengthInMilliSeconds = ((classifier.requiredInputBufferSize * 1.0f) /
                // classifier.requiredTensorAudioFormat.sampleRate) * 1000

        val lengthInMilliSeconds = 1000 // one second 

        val interval = (lengthInMilliSeconds * (1 - overlap)).toLong()

        executor.scheduleAtFixedRate(
            classifyRunnable,
            0,
            interval,
            TimeUnit.MILLISECONDS)
    }

    private fun classifyAudio() {
        tensorAudio.load(recorder) // 1, 15600(0.975*sr)
        
        val rms = calculateRMS(tensorAudio.getTensorBuffer().getFloatArray())

        println(rms)

        if (rms > 0.01){ // TODO: the method to define the threshold for sound happening
            val sr = recorder.getSampleRate()
            var inferenceTime = SystemClock.uptimeMillis()
            // println(tensorAudio.getTensorBuffer().getShape().contentToString())
            // println(interpreter!!.getSignatureInputs("inference").contentToString())
            // println(interpreter!!.getSignatureOutputs("inference").contentToString())
            
            val inputs: MutableMap<String, Any> = HashMap()
                inputs["x"] = tensorAudio.getTensorBuffer().buffer

                val outputs: MutableMap<String, Any> = HashMap()
                // val output = TensorBuffer.createFixedSize(
                //     intArrayOf(1, 10),
                //     DataType.FLOAT32
                // )
                // outputs["output"] = output.buffer
                val lbl = LongBuffer.allocate(1)
                outputs["class"] = lbl

            try {
                interpreter!!.runSignature(inputs, outputs, "inference")
                // println(lbl.array().contentToString())
                // println(id2lblMap[lbl[0].toInt()])
                // println(output.getFloatArray().contentToString())
            } catch (e: IllegalArgumentException) {
                listener.onError(
                    "Classification process failed. See error logs for details"
                )

                Log.e("AudioClassification", "Model failed to inference with error: " + e.message)
            }
            

            // val output = classifier.classify(tensorAudio)
            inferenceTime = SystemClock.uptimeMillis() - inferenceTime

            // val src = recorder.getAudioSource()
            // val format = recorder.getAudioFormat()
            // val channel = recorder.getChannelConfiguration ()
            // val buffer = recorder.getBufferSizeInFrames()

            // listener.onResult(output[0].categories, inferenceTime, sr, tensorAudio.getTensorBuffer())
            listener.onResult(id2lblMap[lbl[0].toInt()].toString(), inferenceTime)
        } 
        else { // no sound
            listener.onResult("silence", 0)
        }
        
    }

    fun stopAudioClassification() {
        recorder.stop()
        executor.shutdownNow()
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_NNAPI = 1
        const val DISPLAY_THRESHOLD = 0.3f
        const val DEFAULT_NUM_OF_RESULTS = 2
        const val DEFAULT_OVERLAP_VALUE = 0.5f
        const val YAMNET_MODEL = "yamnet.tflite"
        const val SPEECH_COMMAND_MODEL = "speech.tflite"
    }
}

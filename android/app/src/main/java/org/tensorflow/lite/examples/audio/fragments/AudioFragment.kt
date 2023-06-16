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

package org.tensorflow.lite.examples.audio.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import org.tensorflow.lite.examples.audio.AudioClassificationHelper
import org.tensorflow.lite.examples.audio.R
import org.tensorflow.lite.examples.audio.databinding.FragmentAudioBinding
//import org.tensorflow.lite.examples.audio.ui.ProbabilitiesAdapter
import org.tensorflow.lite.support.label.Category
/* My own */
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.os.Handler
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
/* End my own */

interface AudioClassificationListener {
    fun onError(error: String)
    fun onResult(audio: FloatArray, lbl: FloatArray, output: String, probs: FloatArray, inferenceTime: Long)
    fun onTrainResult(loss: Float, numIter: Int)
}

class AudioFragment : Fragment() {
    private var _fragmentBinding: FragmentAudioBinding? = null
    private val fragmentAudioBinding get() = _fragmentBinding!!

    private val handler = Handler()

    private lateinit var audioHelper: AudioClassificationHelper
    private lateinit var resultTextView: TextView
    private lateinit var correctButton: Button
    private lateinit var incorrectButton: Button

    private val lbl2idMap = mapOf<String, Int>( // I know there're better ways to do this, but...
        "Appliances" to 0,
        "Baby Cry" to 1,
        "Car Honk" to 2,
        "Cat Meow" to 3,
        "Dog Bark" to 4,
        "Doorbell" to 5,
        "Fire Alarm" to 6,
        "Knocking" to 7,
        "Siren" to 8,
        "Water Running" to 9
        )

    private var isClicked = false

    private val audioClassificationListener = object : AudioClassificationListener {
        override fun onResult(audio: FloatArray, lbl: FloatArray, output: String, probs: FloatArray, inferenceTime: Long) {
            requireActivity().runOnUiThread {
                resultTextView.text = String.format(output)

                if (output != "silence") {
                    audioHelper.stopAudioClassification()
                    
                    showButtons()
                    correctButton.setOnClickListener {
                        isClicked = true
                        hideButtons()

                        correctButtonClicked(audio, lbl)
                    }
                    incorrectButton.setOnClickListener {
                        isClicked = true
                        hideButtons()

                        incorrectButtonClicked(audio)
                    }
                    handler.removeCallbacks(hideButtonsRunnable)
                    handler.postDelayed(hideButtonsRunnable, 1000000L)
                } else {
                    hideButtons()
                }
                
            }
        }

        override fun onTrainResult(loss: Float, numIter: Int) {
            Log.d("AudioFragment","loss: " + loss.toString())
            // if loss is lower than something, stop training
            // if (loss < 0.01 || numIter > 5) {
            //     audioHelper.stopTraining()
            //     audioHelper.updateModel()
            // }
        }

        override fun onError(error: String) {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                //adapter.categoryList = emptyList()
                //adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentBinding = FragmentAudioBinding.inflate(inflater, container, false)
        return fragmentAudioBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        //Setup the UI Elements
        resultTextView = fragmentAudioBinding.root.findViewById(R.id.resultTextView)
        correctButton = fragmentAudioBinding.root.findViewById(R.id.correctButton)
        incorrectButton = fragmentAudioBinding.root.findViewById(R.id.incorrectButton)


        val sounds = resources.getStringArray(R.array.sounds)
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, sounds)
        fragmentAudioBinding.autoCompleteTxt.setAdapter(arrayAdapter)


        //Hide all needed UI Elements on Display
        hideUIElements()

        audioHelper = AudioClassificationHelper(
            requireContext(),
            audioClassificationListener
        )
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(AudioFragmentDirections.actionAudioToPermissions())
        }

        if (::audioHelper.isInitialized ) {
            audioHelper.startAudioClassification()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::audioHelper.isInitialized ) {
            audioHelper.stopAudioClassification()
        }
    }

    override fun onDestroyView() {
        _fragmentBinding = null
        super.onDestroyView()
    }
    private fun hideIncorrectView(){
        hideSurety()
        hideDropDown()
    }

    val hideButtonsRunnable = Runnable {
        if (!isClicked) {
            hideButtons()
            audioHelper.startAudioClassification()
        }
    }

    private fun hideButtons(){
        correctButton.visibility = View.GONE
        incorrectButton.visibility = View.GONE
    }

    private  fun showButtons(){
        isClicked = false
        correctButton.visibility = View.VISIBLE
        incorrectButton.visibility = View.VISIBLE
    }

    private fun showDropDown(){
        fragmentAudioBinding.TextInputLayout.visibility = View.VISIBLE
    }

    private fun hideDropDown(){
        fragmentAudioBinding.TextInputLayout.visibility = View.GONE
    }

    private fun hideUIElements(){
        hideButtons()
        hideDropDown()
        hideSurety()
    }

    private fun correctButtonClicked(audio: FloatArray, lbl:FloatArray){
        //1. Show Dropdown of Possible Sounds
        Log.d("AudioFragment", "Correct Button Clicked")
        
        if (audioHelper.isModelTraining() == false) {
            audioHelper.collectSample(audio, lbl)

            if (audioHelper.isBufferFull()) {
                CoroutineScope(Dispatchers.Default).launch{
                    audioHelper.fineTuning()
                }
            }
        }
        
        audioHelper.startAudioClassification()
    }

    private fun hideSurety(){
        fragmentAudioBinding.textView2.visibility = View.GONE
        fragmentAudioBinding.oneBtn.visibility = View.GONE
        fragmentAudioBinding.twoBtn.visibility = View.GONE
        fragmentAudioBinding.threeBtn.visibility = View.GONE
        fragmentAudioBinding.fourBtn.visibility = View.GONE
        fragmentAudioBinding.fiveBtn.visibility = View.GONE
    }

    private fun showSurety(){
        fragmentAudioBinding.textView2.visibility = View.VISIBLE
        fragmentAudioBinding.oneBtn.visibility = View.VISIBLE
        fragmentAudioBinding.twoBtn.visibility = View.VISIBLE
        fragmentAudioBinding.threeBtn.visibility = View.VISIBLE
        fragmentAudioBinding.fourBtn.visibility = View.VISIBLE
        fragmentAudioBinding.fiveBtn.visibility = View.VISIBLE
    }

    private fun incorrectButtonClicked(audio: FloatArray){
        //1. Show Dropdown of Possible Sounds
        Log.d("AudioFragment", "Incorrect Button Clicked")
        showDropDown()

        // var selectedItem: String = ""

        fragmentAudioBinding.autoCompleteTxt.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            val selectedItem = parent.getItemAtPosition(position) as String

            showSurety()

            var suretyScore = 0

            fragmentAudioBinding.oneBtn.setOnClickListener {
                suretyScore = 1;
                hideIncorrectView()
                Log.d("AudioFragment", (selectedItem + ":" + suretyScore))
                audioHelper.startAudioClassification()
            }

            fragmentAudioBinding.twoBtn.setOnClickListener {
                suretyScore = 2;
                hideIncorrectView()
                Log.d("AudioFragment", (selectedItem + ":" + suretyScore))
                audioHelper.startAudioClassification()
            }

            fragmentAudioBinding.threeBtn.setOnClickListener {
                suretyScore = 3;
                hideIncorrectView()
                Log.d("AudioFragment", (selectedItem + ":" + suretyScore))
                audioHelper.startAudioClassification()
            }

            fragmentAudioBinding.fourBtn.setOnClickListener {
                suretyScore = 4;
                hideIncorrectView()
                if (selectedItem != "Others") {
                    if (audioHelper.isModelTraining() == false) {
                        audioHelper.collectSample(audio, arrayOf(lbl2idMap[selectedItem]!!.toFloat()).toFloatArray())
            
                        if (audioHelper.isBufferFull()) {
                            CoroutineScope(Dispatchers.Default).launch{
                                audioHelper.fineTuning()
                            }
                        }
                    }
                }
                Log.d("AudioFragment", (selectedItem + ":" + suretyScore))
                audioHelper.startAudioClassification()
            }

            fragmentAudioBinding.fiveBtn.setOnClickListener {
                suretyScore = 5;
                hideIncorrectView()
                if (selectedItem != "Others") {
                    if (audioHelper.isModelTraining() == false) {
                        audioHelper.collectSample(audio, arrayOf(lbl2idMap[selectedItem]!!.toFloat()).toFloatArray())
            
                        if (audioHelper.isBufferFull()) {
                            CoroutineScope(Dispatchers.Default).launch{
                                audioHelper.fineTuning()
                            }
                        }
                    }
                }
                Log.d("AudioFragment", (selectedItem + ":" + suretyScore))
                audioHelper.startAudioClassification()
            }
        }

    }
}

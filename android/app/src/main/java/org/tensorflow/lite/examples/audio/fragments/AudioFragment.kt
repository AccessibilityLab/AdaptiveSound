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

//import org.tensorflow.lite.examples.audio.ui.ProbabilitiesAdapter
/* My own */

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.RangeSlider
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.examples.audio.AudioClassificationHelper
import org.tensorflow.lite.examples.audio.R
import org.tensorflow.lite.examples.audio.databinding.FragmentAudioBinding
import java.sql.Time
import java.util.SortedMap
import java.util.Timer
import kotlin.math.roundToInt
import kotlin.concurrent.schedule


/* End my own */

interface AudioClassificationListener {
    fun onError(error: String)
    fun onResult(audio: FloatArray, lbl: FloatArray, output: String, probs: FloatArray, inferenceTime: Long)
    fun onTrainResult(loss: Float, numIter: Int)
}

class AudioFragment : Fragment() {

    private var dialogBoxShown = false

    private var _fragmentBinding: FragmentAudioBinding? = null
    private val fragmentAudioBinding get() = _fragmentBinding!!
    private val handler = Handler()
    private lateinit var audioHelper: AudioClassificationHelper
    private lateinit var resultTextView: TextView


    private lateinit var dialog: BottomSheetDialog


    //Results Page
    private lateinit var predictionLabel: TextView
    private lateinit var confidenceLabel: TextView
    private lateinit var thumbsUpButton: Button
    private lateinit var thumbsDownButton: Button

    //Correction Page
    private lateinit var whatSoundLabel: TextView
    private lateinit var optBtnOne: Button
    private lateinit var optBtnTwo: Button
    private lateinit var optBtnThree: Button
    private lateinit var optBtnFour: Button
    private lateinit var optBtnFive: Button
    private lateinit var autoCompleteTextView: AutoCompleteTextView
    private lateinit var textInputLayout: TextInputLayout

    //Surety Page
    private lateinit var suretyLabel: TextView
    private lateinit var suretySlider: RangeSlider


    //Updating Page
    private lateinit var updatingLabel: TextView

    private var counter = 0


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


    private val audioClassificationListener = object : AudioClassificationListener {
        override fun onResult(audio: FloatArray, lbl: FloatArray, output: String, probs: FloatArray, inferenceTime: Long) {
            if (output != "silence"){
                Log.d("onResult", output)

                audioHelper.stopAudioClassification()

                val probabilityMap = getProbabilityMap(probs)

                requireActivity().runOnUiThread {
                    displayBottomSheet(probabilityMap)
                }
            }
            else{
                Log.d("onResult", "Silence")
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
        //val view = inflater.inflate(R.layout.fragment_audio, container, false)

        _fragmentBinding = FragmentAudioBinding.inflate(inflater, container, false)

        return fragmentAudioBinding.root

        //return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        resultTextView = fragmentAudioBinding.root.findViewById(R.id.resultTextView)

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
        Log.d("AudioFragment", "View is Paused")
        super.onPause()
        if (::audioHelper.isInitialized ) {
            audioHelper.stopAudioClassification()
        }
    }

    override fun onDestroyView() {
        _fragmentBinding = null
        super.onDestroyView()
    }
    private fun correctButtonClicked(audio: FloatArray, lbl:FloatArray){
        //1. Show Dropdown of Possible Sounds
        Log.d("AudioFragment", "Correct Button Clicked")
        audioHelper.collectSample(audio, lbl)

        if (audioHelper.isBufferFull()) {
            CoroutineScope(Dispatchers.Default).launch{
                audioHelper.fineTuning()
            }
        }

        audioHelper.startAudioClassification()
    }

    private fun incorrectButtonClicked(audio: FloatArray){
        //1. Show Dropdown of Possible Sounds
        Log.d("AudioFragment", "Incorrect Button Clicked")
    }

    private fun displayBottomSheet(probabilityMap: Map<String, Float>){
        counter++

        dialog = BottomSheetDialog(requireContext())

        val view=layoutInflater.inflate(R.layout.dialog_layout,null)

        //1. Setup View
        setupView(view)

        //2. Update Results Page
        displayResults(probabilityMap)


        //IN SCREEN 1
        thumbsUpButton.setOnClickListener(){
            //LEO WRITE CORRECT FINE TUNING CODE HERE
        }
        thumbsDownButton.setOnClickListener(){

            //ASK LEO TO DO FINETUNING

            setupCorrectionViewWithData(probabilityMap)

            transitionToCorrectionView()
        }


            //Correction Page
        dialog.setContentView(view)
        dialog.setCancelable(false)

        //Help
        resultTextView.text = ""
        Log.d("Bottom Sheet", "Displayed " + counter + " time(s)")
        dialog.show()
    }

    private fun setupCorrectionViewWithData(probabilityMap: Map<String, Float>){
        //Create an Array of Sounds

        val LIMIT = 5
        var curr = 0

        var buttonArray = arrayOf(optBtnOne, optBtnTwo, optBtnThree, optBtnFour, optBtnFive)
        var othersArray = emptyArray<String>()

        //Update Button Text
        for (item in probabilityMap){
            if(curr != 0){ //Skip first item in list
                if(curr <= LIMIT){
                    buttonArray[curr-1].text = item.key
                }
                else{
                    othersArray += item.key
                }
            }
            curr++
        }

        othersArray += "Other Sound"

        //Update Dropdown Options
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, othersArray)
        autoCompleteTextView.setAdapter(arrayAdapter)

    }

    private fun setupView(view: View){
        setupResultsPage(view)
        setupCorrectionPage(view)
        setupSuretyPage(view)
    }


    //Transition Functions
    private fun transitionToCorrectionView(){
        hideResultsPage()
        showCorrectionPage()

        optBtnOne.setOnClickListener(){
            transitionToSuretyView(optBtnOne.text as String)
        }

        optBtnTwo.setOnClickListener(){
            transitionToSuretyView(optBtnTwo.text as String)
        }

        optBtnThree.setOnClickListener(){
            transitionToSuretyView(optBtnThree.text as String)
        }

        optBtnFour.setOnClickListener(){
            transitionToSuretyView(optBtnFour.text as String)
        }

        optBtnFive.setOnClickListener(){
            transitionToSuretyView(optBtnFive.text as String)
        }

        autoCompleteTextView.onItemClickListener =
            OnItemClickListener { parent, view, position, rowId ->
                val selection = parent.getItemAtPosition(position) as String
                transitionToSuretyView(selection)
            }
    }

    private fun transitionToSuretyView(correctLabel: String){
        hideCorrectionPage()
        showSuretyPage()
        suretyListener(correctLabel)
    }

    private fun suretyListener(correctLabel: String){

        var zeVal: Double = 0.0

        suretySlider.addOnSliderTouchListener(object : RangeSlider.OnSliderTouchListener {

            @SuppressLint("RestrictedApi")
            override fun onStartTrackingTouch(slider: RangeSlider) {
                // Responds to when slider's touch event is being started
            }

            @SuppressLint("RestrictedApi")
            override fun onStopTrackingTouch(slider: RangeSlider) {
                Log.d("Slider", "Stopped With Value" + zeVal)

                //Transition To Finished Fragment
                Log.d("FEEDBACK", correctLabel + " : " + zeVal)

                fineTune(correctLabel, zeVal.toInt())

            }
        })

        suretySlider.addOnChangeListener { rangeSlider, value, fromUser ->
            // Responds to when slider's value is changed
            zeVal = value.toDouble()
        }
    }

    //LEO WRITE FINETUNING CODE HERE
    private fun fineTune(correctLabel: String, surety: Int){
        //Runs if incorrect...
    }


    //Setup View Functions
    private fun setupResultsPage(view: View){
        predictionLabel = view.findViewById<TextView>(R.id.predictionLabel)
        confidenceLabel = view.findViewById<TextView>(R.id.confidenceLabel)
        thumbsUpButton = view.findViewById<Button>(R.id.thumbsUpButton)
        thumbsDownButton = view.findViewById<Button>(R.id.thumbsDownButton)
        showResultsPage()
    }
    private fun setupCorrectionPage(view: View){
        whatSoundLabel = view.findViewById<TextView>(R.id.whatSoundWasIt)
        optBtnOne = view.findViewById<Button>(R.id.button)
        optBtnTwo = view.findViewById<Button>(R.id.button3)
        optBtnThree = view.findViewById<Button>(R.id.button4)
        optBtnFour = view.findViewById<Button>(R.id.button5)
        optBtnFive = view.findViewById<Button>(R.id.button6)
        textInputLayout = view.findViewById<TextInputLayout>(R.id.tInputLayout)
        autoCompleteTextView = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteTextView)
        setupDropdown()
        hideCorrectionPage()
    }
    private fun setupSuretyPage(view: View){
        suretyLabel = view.findViewById(R.id.howSureAreYou)
        suretySlider = view.findViewById(R.id.slidee)
        hideSuretyPage();
    }

    private fun setupUpdatingPage(view: View){
        updatingLabel = view.findViewById(R.id.updating)
        hideUpdating()
    }

    private fun hideUpdating(){
        updatingLabel.visibility = View.GONE
    }

    private fun showUpdating(){
        Log.d("Show Updating", "Shown")
        updatingLabel.visibility = View.VISIBLE
    }

    private fun setupDropdown(){
        val arrayOfSounds = arrayOf("Cat Meow", "Car Honk", "Appliance Noise", "Dog Barking", "Other", "Whistling")
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, arrayOfSounds)
        autoCompleteTextView.setAdapter(arrayAdapter)
    }

    //Show and Hide Functions
    private fun hideResultsPage(){
        predictionLabel.visibility = View.GONE
        confidenceLabel.visibility = View.GONE
        thumbsDownButton.visibility = View.GONE
        thumbsUpButton.visibility = View.GONE
    }
    private fun showResultsPage(){
        predictionLabel.visibility = View.VISIBLE
        confidenceLabel.visibility = View.VISIBLE
        thumbsDownButton.visibility = View.VISIBLE
        thumbsUpButton.visibility = View.VISIBLE
    }
    private fun hideCorrectionPage(){
        whatSoundLabel.visibility = View.GONE
        optBtnOne.visibility = View.GONE
        optBtnTwo.visibility = View.GONE
        optBtnThree.visibility = View.GONE
        optBtnFour.visibility = View.GONE
        optBtnFive.visibility = View.GONE
        textInputLayout.visibility = View.GONE

    }
    private fun showCorrectionPage(){
        whatSoundLabel.visibility = View.VISIBLE
        optBtnOne.visibility = View.VISIBLE
        optBtnTwo.visibility = View.VISIBLE
        optBtnThree.visibility = View.VISIBLE
        optBtnFour.visibility = View.VISIBLE
        optBtnFive.visibility = View.VISIBLE
        textInputLayout.visibility = View.VISIBLE
    }
    private fun hideSuretyPage(){
        suretyLabel.visibility = View.GONE
        suretySlider.visibility = View.GONE
    }
    private fun showSuretyPage(){
        suretyLabel.visibility = View.VISIBLE
        suretySlider.visibility = View.VISIBLE
    }

    private fun displayResults(data: Map<String, Float>){
        val firstEntry = data.entries.firstOrNull()

        if (firstEntry != null) {
            val output = firstEntry.key
            var confidence = firstEntry.value

            predictionLabel.text = output
            confidence*=100
            confidenceLabel.text = confidence.roundToInt().toString() + "% Sure"
        }
    }



    private fun getProbabilityMap(probs: FloatArray) : Map<String, Float>{

        var sortedMap: SortedMap<String, Float> = sortedMapOf()

        var index = 0
        for (probability in probs){
            val label = getLabelFromIndex(index)
            sortedMap[label] = probability
            index++
        }

        val sortedByValueMap = sortedMap.toList().sortedByDescending { (_, value) -> value }.toMap()
        return sortedByValueMap
    }

    private fun getLabelFromIndex(index: Int) : String? {
        val lbl2idMap = mapOf<Int, String>( // I know there're better ways to do this, but...
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
        return lbl2idMap[index]
    }
}



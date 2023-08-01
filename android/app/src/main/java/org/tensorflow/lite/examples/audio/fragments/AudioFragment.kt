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
import android.os.Looper
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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import org.tensorflow.lite.examples.audio.AudioClassificationHelper
import org.tensorflow.lite.examples.audio.R
import org.tensorflow.lite.examples.audio.databinding.FragmentAudioBinding
import java.util.SortedMap
import kotlin.math.roundToInt


/* End my own */

interface AudioClassificationListener {
    fun onError(error: String)
    fun onResult(audio: FloatArray, lbl: FloatArray, output: String, probs: FloatArray, inferenceTime: Long)
    fun onTrainResult(loss: Float, numIter: Int)
}

class AudioFragment : Fragment() {

    //region Declarations



    private var _fragmentBinding: FragmentAudioBinding? = null
    private val fragmentAudioBinding get() = _fragmentBinding!!
    private val handler = Handler()
    private lateinit var audioHelper: AudioClassificationHelper
    private lateinit var listeningLabel: TextView


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

    //Fine-Tuning Global Variables
    private lateinit var audioGlobal: FloatArray
    private lateinit var lblGlobal: FloatArray

    private var isClicked = false

    //Boolean Variable to keep track of dialogbox state - whether it is shown or not
    private var dialogBoxIsShown = false
    private var userHasFineTuningEnabled = true




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

    //endregion

    //region AudioClassificationListener Methods

    //On Result Method - This Method gets triggered whenever the AudioClassificationHelper.kt
    //makes a prediction based on audio sound

    private val audioClassificationListener = object : AudioClassificationListener {
        override fun onResult(audio: FloatArray, lbl: FloatArray, output: String, probs: FloatArray, inferenceTime: Long) {

            //audio of type FloatArray is the audio file that was processed by the model
            //I don't exactly know what lbl is (USED FOR RL)
            //output is the string label of the most confident prediction of the model
            //probs is FloatArray which deals with probabilities of different labels
            //inference time i I don't exactly remember what it is

            //If the Output is not silence, it is a valid prediction
            if (output != "silence"){


                //Stop Classificying Audio -> Stops Listening for new sounds
                audioHelper.stopAudioClassification()

                //Updates the Global Audio Variable with the correct audio
                audioGlobal = audio

                //Updates the Global lbl variable with the correct lbl
                lblGlobal = lbl

                //Probability Map is a Map of <String, Float> where:
                    //String is the audio class label i.e Knocking or Car Honk or Doorbell etc.
                    //Float is the confidence of that prediction i.e 0.97 -> 97% confidence
                val probabilityMap = getProbabilityMap(probs)


                //From this point we have only UI - front end updates which need to be run on the
                //UI thread to make sure low latency for front end
                requireActivity().runOnUiThread {

                    //Log Statements for Debugging
                    Log.d("On Result", "Prediction: " + output)
                    Log.d("On Result", "Dialog Box Shown: " + dialogBoxIsShown)

                    //If Dialogbox is not shown (fix for a bug where dialog box shows twice)
                    if(!dialogBoxIsShown){
                        //Debug Statement
                        Log.d("On Result", "Calling Function Display Bottom Sheet")

                        //Mark DialogBox as Shown
                        dialogBoxIsShown = true

                        //Calls function to display Bottom Sheet (since we have a valid prediction)
                        displayBottomSheet(probabilityMap)
                    }
                }
            }
            else{
                //Debug Purposes
                Log.d("On Result", "Silence is Prediction")
            }

        }

        //On Train Result - Listener Method for RL (See AudioClassificationHelper.kt) for more
        override fun onTrainResult(loss: Float, numIter: Int) {
            Log.d("AudioFragment","loss: " + loss.toString())
            // if loss is lower than something, stop training
            // if (loss < 0.01 || numIter > 5) {
            //     audioHelper.stopTraining()
            //     audioHelper.updateModel()
            // }
        }

        //On Error - Listener Method for RL (See AudioClassificationHelper.kt) for more
        override fun onError(error: String) {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                //adapter.categoryList = emptyList()
                //adapter.notifyDataSetChanged()
            }
        }
    }

    //endregion

    //region FragmentLifeCycle Methods

    // onCreateView - See Fragment Lifecycles documentation - https://developer.android.com/guide/fragments/lifecycle
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

    //On View Created - See Fragment Lifecycles documentation - https://developer.android.com/guide/fragments/lifecycle
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Setup listeningLabel by linking it to element in fragment_audio.xml
        listeningLabel = fragmentAudioBinding.root.findViewById(R.id.resultTextView)


        //Setup fineTuningSwitch Text View by linking it to element in fragment_audio.xml
        //Fine Tuning Switch used to turn on and off fine tuning
        var fineTuneSwitch = fragmentAudioBinding.root.findViewById<SwitchMaterial>(R.id.switchFineTune)

        //UI feature
        fineTuneSwitch.isUseMaterialThemeColors = true

        //This On CheckedCgangeListener is triggered wheever the switch is clicked
        fineTuneSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                // Code to execute when the switch is checked - Fine tuning is On
                userHasFineTuningEnabled = true
                Log.d("Switch", "Switch is checked")
            } else {
                // Code to execute when the switch is unchecked - Fine tuning is off
                userHasFineTuningEnabled = false
                Log.d("Switch", "Switch is unchecked")
            }
        }


        //AudioHelper Runnable - Not Sure What Exactly this is used for
        audioHelper = AudioClassificationHelper(
            requireContext(),
            audioClassificationListener
        )
    }

    //More Lifecyle Methods - Need to figure out for 2nd version of App
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

    //More Lifecyle Methods - Need to figure out for 2nd version of App
    override fun onPause() {
        super.onPause()
        if (::audioHelper.isInitialized ) {
            audioHelper.stopAudioClassification()
        }
    }

    //More Lifecyle Methods - Need to figure out for 2nd version of App
    override fun onDestroyView() {
        _fragmentBinding = null
        super.onDestroyView()
    }

    //endregion

    //region Display Bottom Sheet

    //Display Bottom Sheet Method - Function that encapsulates displaying prediction information
    //to the user
    private fun displayBottomSheet(probabilityMap: Map<String, Float>){

        //setup our Bottom Sheet Dialog
        dialog = BottomSheetDialog(requireContext())


        val view=layoutInflater.inflate(R.layout.dialog_layout,null)

        //1. Setup View
        setupView(view)

        //2. Update Results Page
        displayResults(probabilityMap)



        //When FineTuning if the User Clicks the Thumbs up Button
        thumbsUpButton.setOnClickListener(){

            //isClicked = true because feedback is given
            isClicked = true
            fineTune("NONE", -1)
        }

        //When FineTuning if the User Clicks the Thumbs up Button
        thumbsDownButton.setOnClickListener(){

            //isClicked = true because feedback is given
            isClicked = true

            //If thumbs down selected -> user needs to fine tune model
            //Correction View Setup
            setupCorrectionViewWithData(probabilityMap)

            //Transition from Results View to Correction View
            transitionToCorrectionView()
        }


        dialog.setContentView(view)

        //Prevent User from Clikcing Out of the DialogBox
        //-> prevents unnesecary start stop timing delays and bugs
        dialog.setCancelable(false)
        dialog.show()

        //Delay for is user doesn't want to give feedback.
        //If the User Does not provide feedback within five seconds, run the noResponseRunnable
        Handler(Looper.getMainLooper()).removeCallbacks(noResponseRunnable)
        Handler(Looper.getMainLooper()).postDelayed(noResponseRunnable, 5000)
    }

    //Runnable run if no response to prediction
    val noResponseRunnable = Runnable{

        if(!isClicked){
            //If feedback is not given
            //Calls fineTune method with a no click flag (i.e. feedback not given flag)
            fineTune("No Click", -2)
        }
    }

    //endregion

    //region Setup Views

    //Setup the (2nd View) Correction View with Data (different buttons and slider)
    private fun setupCorrectionViewWithData(probabilityMap: Map<String, Float>){

        /*This Iteration of Adaptive Sound presents users with buttons as well as a drop down of
        * different sounds the user can select from to pick the correct sound. Buttons are ordered
        * in order of descending probability. I.E buttons with labels that had higher confidence are
        * the top few buttons the user can select from to give feedback. This is the main reason
        * behind large amounts of data processing in this function*/



        val LIMIT = 5
        var curr = 0

        //A Button Array of the five buttons
        var buttonArray = arrayOf(optBtnOne, optBtnTwo, optBtnThree, optBtnFour, optBtnFive)

        //Other Sounds that arn't displayed by the buttons to be displayed in the dropDown
        var othersArray = emptyArray<String>()

        //Update Button Text
        //Yes this is a map, and yes we are looping through it using a for loop
        //This map is ordered -> for more info look at the function getProbabilityMap

        for (item in probabilityMap){
            if(curr != 0){ //Skip first item in list because item at index 0 is
                // the main prediction which the user has already seen
                if(curr <= LIMIT){
                    buttonArray[curr-1].text = item.key
                    //Set buttonArray[0] to label at probaMap[1] and so until we hit out limit of 5
                    //buttons
                }
                else{
                    othersArray += item.key
                    //If we can't populate our buttons anymore,
                    // add the labels to the other sounds array
                }
            }
            curr++
            //increment curr
        }

        //Add the Other Sound option in case the actual sound wasn't any of our provided sounds.
        othersArray += "Other Sound"

        //Update Dropdown Options
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, othersArray)
        autoCompleteTextView.setAdapter(arrayAdapter)

    }


    //TODO: Finish Commenting The Following Functions Down Below
    private fun setupView(view: View){
        setupResultsPage(view)
        setupCorrectionPage(view)
        setupSuretyPage(view)
        setupUpdatingView(view)
    }

    private fun setupUpdatingView(view: View){
        updatingLabel = view.findViewById<TextView>(R.id.updating)
        hideUpdatingPage()
    }


    private fun setupResultsPage(view: View){
        isClicked
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


    private fun setupDropdown(){
        val arrayOfSounds = arrayOf("Cat Meow", "Car Honk", "Appliance Noise", "Dog Barking", "Other", "Whistling")
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, arrayOfSounds)
        autoCompleteTextView.setAdapter(arrayAdapter)
    }

    //endregion

    //region Transition Functions
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

    //endregion

    private fun suretyListener(correctLabel: String){

        var zeVal: Double = 0.0

        suretySlider.addOnSliderTouchListener(object : RangeSlider.OnSliderTouchListener {

            @SuppressLint("RestrictedApi")
            override fun onStartTrackingTouch(slider: RangeSlider) {
                // Responds to when slider's touch event is being started
            }

            @SuppressLint("RestrictedApi")
            override fun onStopTrackingTouch(slider: RangeSlider) {


                fineTune(correctLabel, zeVal.toInt())

            }
        })

        suretySlider.addOnChangeListener { rangeSlider, value, fromUser ->
            // Responds to when slider's value is changed
            zeVal = value.toDouble()
        }
    }
    private fun fineTune(correctLabel: String, surety: Int){
        Log.d("FineTune", "Entered Fine Tuning Front-End")
        Log.d("FineTune", "Dialog Box Shown: " + dialogBoxIsShown)
        if (surety == -1){
            //Correct Sound
            if(audioHelper.isModelTraining() == false){
                audioHelper.collectSample(audioGlobal, lblGlobal)

                if (audioHelper.isBufferFull()){
                    CoroutineScope(Dispatchers.Default).launch {
                        audioHelper.fineTuning()
                    }
                }
            }
        }
        else if(surety == 4 || surety == 5){
            //Incorrect Sound with high surety
            if(correctLabel != "Other Sound"){
                if (audioHelper.isModelTraining() == false) {
                    audioHelper.collectSample(audioGlobal, arrayOf(lbl2idMap[correctLabel]!!.toFloat()).toFloatArray())
                    if (audioHelper.isBufferFull()) {
                        CoroutineScope(Dispatchers.Default).launch{
                            audioHelper.fineTuning()
                        }
                    }
                }
            }
        }


        dialog.dismiss()
        Log.d("FineTune", "Dialog Box Dismissed")
        dialogBoxIsShown = false
        Log.d("FineTune", "Dialog Box Shown: " + dialogBoxIsShown)




        audioHelper.startAudioClassification()
    }


    //region Show and Hide Functions
    private fun hideResultsPage(){
        predictionLabel.visibility = View.GONE
        confidenceLabel.visibility = View.GONE
        thumbsDownButton.visibility = View.GONE
        thumbsUpButton.visibility = View.GONE
    }
    private fun showResultsPage(){
        isClicked = false
        predictionLabel.visibility = View.VISIBLE
        confidenceLabel.visibility = View.VISIBLE
        if(userHasFineTuningEnabled){
            thumbsDownButton.visibility = View.VISIBLE
            thumbsUpButton.visibility = View.VISIBLE
        }
        else{
            thumbsDownButton.visibility = View.GONE
            thumbsUpButton.visibility = View.GONE
        }
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

    private fun hideUpdatingPage(){
        updatingLabel.visibility = View.GONE
    }

    private fun showUpdatingPage(){
        updatingLabel.visibility = View.GONE
    }

    //endregion

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



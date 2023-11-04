package com.example.john_shots

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.john_shots.model.UIState

class CountViewModel(application: Application) : AndroidViewModel(application) {

    var uiState by mutableStateOf(UIState())
    private var gunshotNumber = 0

    private val audioClassificationListener = object : AudioClassifierHelper.AudioClassifierListener {
        override fun onResult(resultBundle: AudioClassifierHelper.ResultBundle) {
            if (resultBundle.results.isNotEmpty()) {
                resultBundle.results[0].classificationResults().first()
                    .classifications()?.get(1)?.categories()?.let {
                        // If index is the gunshot.
                        try {
                            Log.v("gun", it[0].categoryName().toString())
                            if (it[0].index() == 1) {
                                gunshotNumber++
                            }
                            uiState = uiState.copy(gunshotNumber = gunshotNumber.toString())
                        }catch (e: Exception) {
                            Log.e("Error", e.toString())
                        }
                    }
            }
        }

        override fun onError(error: String) {
            Log.v("gun", error)
        }
    }
    private val audioClassificationHelper = AudioClassifierHelper(
        context = application,
        listener = audioClassificationListener
    )

    init {
        audioClassificationHelper.initClassifier()
    }

    override fun onCleared() {
        super.onCleared()
        audioClassificationHelper.stopAudioClassification()
    }

    companion object {

    }
}

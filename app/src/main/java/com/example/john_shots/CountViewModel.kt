package com.example.john_shots

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel

class CountViewModel(application: Application) : AndroidViewModel(application) {

    private val audioClassificationListener = object : AudioClassifierHelper.AudioClassifierListener {
        override fun onResult(resultBundle: AudioClassifierHelper.ResultBundle) {
            if (resultBundle.results.isNotEmpty()) {
                resultBundle.results[0].classificationResults().first()
                    .classifications()?.get(0)?.categories()?.let {
                        Log.v("gun", it.toString())
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

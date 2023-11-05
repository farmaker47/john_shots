package com.example.john_shots

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AudioClassifierHelper(
    private val context: Context,
    private var classificationThreshold: Float = DISPLAY_THRESHOLD,
    private var numOfResults: Int = DEFAULT_NUM_OF_RESULTS,
    private var runningMode: RunningMode = RunningMode.AUDIO_STREAM,
    private var listener: AudioClassifierListener? = null,
) {

    private var recorder: AudioRecord? = null
    private var executor: ScheduledThreadPoolExecutor? = null
    private var audioClassifier: AudioClassifier? = null
    private val classifyRunnable = Runnable {
        recorder?.let { classifyAudioAsync(it) }
    }

    fun initClassifier() {
        // Set general detection options, e.g. number of used threads
        val baseOptionsBuilder = BaseOptions.builder()

        baseOptionsBuilder.setModelAssetPath(GENERAL_MODEL)

        try {
            // Configures a set of parameters for the classifier and what results will be returned.
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder =
                AudioClassifier.AudioClassifierOptions.builder()
                    .setScoreThreshold(classificationThreshold)
                    .setMaxResults(numOfResults)
                    .setBaseOptions(baseOptions)
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.AUDIO_STREAM) {
                optionsBuilder
                    .setResultListener(this::streamAudioResultListener)
                    .setErrorListener(this::streamAudioErrorListener)
            }

            val options = optionsBuilder.build()

            // Create the classifier and required supporting objects
            audioClassifier =
                AudioClassifier.createFromOptions(context, options)
            if (runningMode == RunningMode.AUDIO_STREAM) {
                recorder = audioClassifier?.createAudioRecord(
                    AudioFormat.CHANNEL_IN_DEFAULT,
                    SAMPLING_RATE_IN_HZ,
                    BUFFER_SIZE_IN_BYTES.toInt())

                startAudioClassification()
            }
        } catch (e: IllegalStateException) {
            listener?.onError(
                "Audio Classifier failed to initialize. See error logs for details"
            )

            Log.e(
                TAG, "MP task failed to load with error: " + e.message
            )
        } catch (e: RuntimeException) {
            listener?.onError(
                "Audio Classifier failed to initialize. See error logs for details"
            )

            Log.e(
                TAG, "MP task failed to load with error: " + e.message
            )
        }
    }

    private fun startAudioClassification() {
        if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            return
        }

        recorder?.startRecording()
        executor = ScheduledThreadPoolExecutor(1)

        // Each model will expect a specific audio recording length. This formula calculates that
        // length using the input buffer size and tensor format sample rate.
        // For example, YAMNET expects 0.975 second length recordings.
        // This needs to be in milliseconds to avoid the required Long value dropping decimals.
        val lengthInMilliSeconds =
            ((REQUIRE_INPUT_BUFFER_SIZE * 1.0f) / SAMPLING_RATE_IN_HZ) * 1000

        val interval = (lengthInMilliSeconds * DEFAULT_OVERLAP).toLong()

        executor?.scheduleAtFixedRate(
            classifyRunnable,
            0,
            interval,
            TimeUnit.MILLISECONDS
        )
    }

    private fun classifyAudioAsync(audioRecord: AudioRecord) {
        val audioData = AudioData.create(
            AudioData.AudioDataFormat.create(recorder?.format),  /* sampleCounts= */SAMPLING_RATE_IN_HZ
        )
        audioData.load(audioRecord)

        val inferenceTime = SystemClock.uptimeMillis()
        audioClassifier?.classifyAsync(audioData, inferenceTime)
    }

    fun stopAudioClassification() {
        executor?.shutdownNow()
        audioClassifier?.close()
        audioClassifier = null
        recorder?.stop()
    }

    /*fun isClosed(): Boolean {
        return audioClassifier == null
    }*/

    private fun streamAudioResultListener(resultListener: AudioClassifierResult) {
        listener?.onResult(
            ResultBundle(listOf(resultListener), 0)
        )
    }

    private fun streamAudioErrorListener(e: RuntimeException) {
        listener?.onError(e.message.toString())
    }

    // Wraps results from inference, the time it takes for inference to be
    // performed.
    data class ResultBundle(
        val results: List<AudioClassifierResult>,
        val inferenceTime: Long,
    )

    companion object {
        private const val TAG = "AudioClassifierHelper"
        const val DISPLAY_THRESHOLD = 0.6f
        const val DEFAULT_NUM_OF_RESULTS = 1
        const val DEFAULT_OVERLAP = 0.5f
        const val GENERAL_MODEL = "gunshot_model.tflite"

        private const val SAMPLING_RATE_IN_HZ = 16000
        private const val BUFFER_SIZE_FACTOR: Int = 2
        private const val EXPECTED_INPUT_LENGTH = 0.975F
        private const val REQUIRE_INPUT_BUFFER_SIZE =
            SAMPLING_RATE_IN_HZ * EXPECTED_INPUT_LENGTH

        /**
         * Size of the buffer where the audio data is stored by Android
         */
        private const val BUFFER_SIZE_IN_BYTES =
            REQUIRE_INPUT_BUFFER_SIZE * Float.SIZE_BYTES * BUFFER_SIZE_FACTOR
    }

    interface AudioClassifierListener {
        fun onError(error: String)
        fun onResult(resultBundle: ResultBundle)
    }
}

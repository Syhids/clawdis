package com.steipete.clawdis.node.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VoiceWakeManager(
  private val context: Context,
  private val scope: CoroutineScope,
  private val onCommand: suspend (String) -> Unit,
) {
  companion object {
    private const val TAG = "VoiceWake"
  }

  private val mainHandler = Handler(Looper.getMainLooper())

  private val _isListening = MutableStateFlow(false)
  val isListening: StateFlow<Boolean> = _isListening

  private val _statusText = MutableStateFlow("Off")
  val statusText: StateFlow<String> = _statusText

  var triggerWords: List<String> = emptyList()
    private set

  private var recognizer: SpeechRecognizer? = null
  private var restartJob: Job? = null
  private var lastDispatched: String? = null
  private var stopRequested = false

  fun setTriggerWords(words: List<String>) {
    triggerWords = words
    Log.i(TAG, "setTriggerWords: ${words.joinToString()}")
  }

  fun start() {
    Log.i(TAG, "start() called")
    mainHandler.post {
      if (_isListening.value) {
        Log.v(TAG, "start: already listening, skipping")
        return@post
      }
      stopRequested = false

      if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        _isListening.value = false
        _statusText.value = "Speech recognizer unavailable"
        Log.w(TAG, "start: SpeechRecognizer not available on this device")
        return@post
      }

      try {
        Log.v(TAG, "start: creating SpeechRecognizer")
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(listener) }
        startListeningInternal()
        Log.i(TAG, "start: SpeechRecognizer started successfully")
      } catch (err: Throwable) {
        _isListening.value = false
        _statusText.value = "Start failed: ${err.message ?: err::class.simpleName}"
        Log.e(TAG, "start: failed to create SpeechRecognizer", err)
      }
    }
  }

  fun stop(statusText: String = "Off") {
    Log.i(TAG, "stop() called, statusText='$statusText'")
    stopRequested = true
    restartJob?.cancel()
    restartJob = null
    mainHandler.post {
      _isListening.value = false
      _statusText.value = statusText
      recognizer?.cancel()
      recognizer?.destroy()
      recognizer = null
      Log.v(TAG, "stop: SpeechRecognizer destroyed")
    }
  }

  private fun startListeningInternal() {
    val r = recognizer ?: run {
      Log.w(TAG, "startListeningInternal: recognizer is null, aborting")
      return
    }
    Log.v(TAG, "startListeningInternal: configuring intent with LANGUAGE_MODEL_FREE_FORM")
    val intent =
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
      }

    _statusText.value = "Listening"
    _isListening.value = true
    r.startListening(intent)
    Log.i(TAG, "startListeningInternal: startListening() invoked")
  }

  private fun scheduleRestart(delayMs: Long = 350) {
    if (stopRequested) {
      Log.v(TAG, "scheduleRestart: stopRequested=true, skipping")
      return
    }
    Log.v(TAG, "scheduleRestart: scheduling restart in ${delayMs}ms")
    restartJob?.cancel()
    restartJob =
      scope.launch {
        delay(delayMs)
        mainHandler.post {
          if (stopRequested) {
            Log.v(TAG, "scheduleRestart: stopRequested after delay, skipping")
            return@post
          }
          try {
            Log.v(TAG, "scheduleRestart: restarting recognizer")
            recognizer?.cancel()
            startListeningInternal()
          } catch (err: Throwable) {
            Log.w(TAG, "scheduleRestart: restart failed", err)
            // Will be picked up by onError and retry again.
          }
        }
      }
  }

  private fun handleTranscription(text: String) {
    Log.v(TAG, "handleTranscription: raw='$text'")
    val command = VoiceWakeCommandExtractor.extractCommand(text, triggerWords)
    if (command == null) {
      Log.v(TAG, "handleTranscription: no trigger word matched")
      return
    }
    if (command == lastDispatched) {
      Log.v(TAG, "handleTranscription: duplicate command, ignoring")
      return
    }
    lastDispatched = command
    Log.i(TAG, "handleTranscription: trigger detected! command='$command'")

    scope.launch { onCommand(command) }
    _statusText.value = "Triggered"
    scheduleRestart(delayMs = 650)
  }

  private val listener =
    object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) {
        Log.i(TAG, "RecognitionListener: onReadyForSpeech")
        _statusText.value = "Listening"
      }

      override fun onBeginningOfSpeech() {
        Log.v(TAG, "RecognitionListener: onBeginningOfSpeech - user started speaking")
      }

      override fun onRmsChanged(rmsdB: Float) {
        // Log.v(TAG, "RecognitionListener: onRmsChanged rmsdB=$rmsdB") // Too verbose
      }

      override fun onBufferReceived(buffer: ByteArray?) {
        Log.v(TAG, "RecognitionListener: onBufferReceived size=${buffer?.size ?: 0}")
      }

      override fun onEndOfSpeech() {
        Log.i(TAG, "RecognitionListener: onEndOfSpeech - user stopped speaking")
        scheduleRestart()
      }

      override fun onError(error: Int) {
        val errorName = when (error) {
          SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
          SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
          SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
          SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
          SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
          SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
          SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
          SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
          SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
          else -> "UNKNOWN($error)"
        }
        Log.w(TAG, "RecognitionListener: onError code=$error ($errorName)")

        if (stopRequested) {
          Log.v(TAG, "RecognitionListener: onError ignored (stopRequested=true)")
          return
        }
        _isListening.value = false
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
          _statusText.value = "Microphone permission required"
          Log.e(TAG, "RecognitionListener: microphone permission missing!")
          return
        }

        _statusText.value =
          when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "Listening"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening"
            else -> "Speech error ($error)"
          }
        scheduleRestart(delayMs = 600)
      }

      override fun onResults(results: Bundle?) {
        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        Log.i(TAG, "RecognitionListener: onResults count=${list.size} first='${list.firstOrNull()}'")
        list.firstOrNull()?.let(::handleTranscription)
        scheduleRestart()
      }

      override fun onPartialResults(partialResults: Bundle?) {
        val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        Log.v(TAG, "RecognitionListener: onPartialResults count=${list.size} first='${list.firstOrNull()}'")
        list.firstOrNull()?.let(::handleTranscription)
      }

      override fun onEvent(eventType: Int, params: Bundle?) {
        Log.v(TAG, "RecognitionListener: onEvent type=$eventType")
      }
    }
}

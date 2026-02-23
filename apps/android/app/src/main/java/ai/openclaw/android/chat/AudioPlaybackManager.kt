package ai.openclaw.android.chat

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays audio received inline in agent events (base64-encoded).
 *
 * When the gateway's TTS tool generates audio, the gateway reads the local
 * file, encodes it to base64, and includes it in the WebSocket agent event.
 * This manager decodes the audio and plays it via Android's MediaPlayer.
 */
class AudioPlaybackManager(
  private val scope: CoroutineScope,
  private val cacheDir: File,
) {
  companion object {
    private const val TAG = "AudioPlayback"
  }

  private var player: MediaPlayer? = null
  private var currentJob: Job? = null
  private var tempFile: File? = null

  /**
   * Play base64-encoded audio. Cancels any in-progress playback.
   *
   * @param base64 The base64-encoded audio data.
   * @param mimeType The MIME type (e.g. "audio/ogg", "audio/mpeg").
   */
  fun play(base64: String, mimeType: String) {
    stop()
    currentJob =
      scope.launch {
        try {
          playInternal(base64, mimeType)
        } catch (err: Throwable) {
          Log.w(TAG, "playback failed: ${err.message ?: err::class.simpleName}")
        }
      }
  }

  /** Stop current playback and release resources. */
  fun stop() {
    currentJob?.cancel()
    currentJob = null
    releasePlayer()
    cleanupTempFile()
  }

  private suspend fun playInternal(base64: String, mimeType: String) {
    val bytes =
      withContext(Dispatchers.IO) {
        Base64.decode(base64, Base64.DEFAULT)
      }
    if (bytes.isEmpty()) {
      Log.w(TAG, "decoded audio is empty")
      return
    }

    // Write to a temp file because MediaPlayer.setDataSource needs a file
    // descriptor or URI — feeding raw bytes requires a temp file.
    val ext = extensionForMime(mimeType)
    val file =
      withContext(Dispatchers.IO) {
        val f = File.createTempFile("tts_audio_", ".$ext", cacheDir)
        FileOutputStream(f).use { it.write(bytes) }
        f
      }
    tempFile = file

    val mp = MediaPlayer()
    mp.setAudioAttributes(
      AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .build(),
    )

    withContext(Dispatchers.IO) {
      mp.setDataSource(file.absolutePath)
      mp.prepare()
    }

    player = mp
    Log.d(TAG, "playing ${bytes.size} bytes mimeType=$mimeType")

    val done = kotlinx.coroutines.CompletableDeferred<Unit>()
    mp.setOnCompletionListener { done.complete(Unit) }
    mp.setOnErrorListener { _, what, extra ->
      done.completeExceptionally(
        IllegalStateException("MediaPlayer error what=$what extra=$extra"),
      )
      true
    }
    mp.start()

    try {
      done.await()
    } finally {
      releasePlayer()
      cleanupTempFile()
    }
    Log.d(TAG, "playback complete")
  }

  private fun releasePlayer() {
    try {
      player?.stop()
    } catch (_: Throwable) {
      // ignore if not started
    }
    player?.release()
    player = null
  }

  private fun cleanupTempFile() {
    tempFile?.delete()
    tempFile = null
  }

  private fun extensionForMime(mime: String): String =
    when {
      mime.contains("ogg") || mime.contains("opus") -> "ogg"
      mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
      mime.contains("wav") -> "wav"
      mime.contains("m4a") || mime.contains("mp4") -> "m4a"
      mime.contains("aac") -> "aac"
      mime.contains("webm") -> "webm"
      mime.contains("flac") -> "flac"
      else -> "ogg"
    }
}

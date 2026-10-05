package com.chattlyx.feature.chats

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import timber.log.Timber

/** Recorded voice note ready for the MED-03 send pipeline. */
data class VoiceRecording(
    val file: File,
    val durationMs: Long,
)

/**
 * MED-03 voice capture: one-shot MediaRecorder wrapper (record -> stop ->
 * hand file to the attachment pipeline). All calls are main-thread safe;
 * MediaRecorder itself blocks only on stop().
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L

    val isRecording: Boolean
        get() = recorder != null

    /** Starts an MPEG-4/AAC capture into the app cache dir. */
    fun start(): Boolean {
        if (recorder != null) return false
        val output = File(context.cacheDir, "voice-${java.util.UUID.randomUUID()}.m4a")
        val candidate = createRecorder(context)
        try {
            candidate.setAudioSource(MediaRecorder.AudioSource.MIC)
            candidate.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            candidate.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            candidate.setAudioSamplingRate(44_100)
            candidate.setAudioEncodingBitRate(128_000)
            candidate.setOutputFile(output.absolutePath)
            candidate.prepare()
            candidate.start()
        } catch (e: Exception) {
            Timber.w(e, "Voice recording failed to start")
            candidate.release()
            if (!output.delete()) output.deleteOnExit()
            return false
        }
        recorder = candidate
        outputFile = output
        startedAt = System.currentTimeMillis()
        return true
    }

    /** Stops and returns the recorded file, or null when nothing was captured. */
    fun stop(): VoiceRecording? {
        val active = recorder ?: return null
        val output = outputFile
        recorder = null
        outputFile = null
        try {
            active.stop()
        } catch (e: IllegalStateException) {
            Timber.w(e, "Recorder stopped without capturing")
            active.release()
            if (output != null && !output.delete()) output.deleteOnExit()
            return null
        }
        active.release()
        if (output == null || !output.isFile || output.length() == 0L) {
            if (output != null && !output.delete()) output.deleteOnExit()
            return null
        }
        return VoiceRecording(output, System.currentTimeMillis() - startedAt)
    }

    /** Discards the in-progress recording without producing a file. */
    fun cancel() {
        val active = recorder
        val output = outputFile
        recorder = null
        outputFile = null
        if (active != null) {
            try {
                active.stop()
            } catch (e: IllegalStateException) {
                // Not started far enough to stop; release is enough.
            }
            active.release()
        }
        if (output != null && !output.delete()) output.deleteOnExit()
    }

    private fun createRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
}

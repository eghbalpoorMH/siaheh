package ir.siaheh.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0L

    val isRecording: Boolean get() = recorder != null
    val currentFile: File? get() = outputFile
    val elapsedMillis: Long get() = if (isRecording) System.currentTimeMillis() - startTime else 0L

    fun start(): File {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        startTime = System.currentTimeMillis()
        return file
    }

    fun stop(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputFile
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            outputFile?.delete()
            null
        }
    }

    fun cancel() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            recorder?.release()
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

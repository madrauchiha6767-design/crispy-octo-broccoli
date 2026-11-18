package org.godotengine.plugin.android.template

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot

class GodotAndroidPlugin(godot: Godot) : GodotPlugin(godot) {

    override fun getPluginName(): String = "RXRecorder"

    companion object {
        private const val REQUEST_CODE = 4567
        private const val STOP_ACTION = "org.godotengine.plugin.android.template.STOP_RECORDING"
    }

    @UsedByGodot
    fun requestRecordingPermission() {
        // GodotPlugin exposes runOnUiThread(); runOnHostThread() is not a Godot API.
        runOnUiThread {
            val hostActivity = getActivity() ?: return@runOnUiThread
            val manager = hostActivity.getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager
            hostActivity.startActivityForResult(
                manager.createScreenCaptureIntent(),
                REQUEST_CODE
            )
        }
    }

    override fun onMainActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onMainActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_CODE || resultCode != Activity.RESULT_OK || data == null) {
            return
        }

        val hostActivity = getActivity() ?: return
        val serviceIntent = Intent(hostActivity, ScreenRecordService::class.java).apply {
            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hostActivity.startForegroundService(serviceIntent)
        } else {
            hostActivity.startService(serviceIntent)
        }
    }

    @UsedByGodot
    fun stopRecording() {
        runOnUiThread {
            val hostActivity = getActivity() ?: return@runOnUiThread
            val stopIntent = Intent(hostActivity, ScreenRecordService::class.java).apply {
                action = STOP_ACTION
            }
            hostActivity.startService(stopIntent)
        }
    }

    @UsedByGodot
    fun isRecording(): Boolean = ScreenRecordService.isRecording

    @UsedByGodot
    fun getLastRecordingPath(): String = ScreenRecordService.lastRecordingPath
}

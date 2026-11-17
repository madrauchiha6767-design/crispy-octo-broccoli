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

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    companion object {
        private const val REQUEST_CODE = 4567
    }

    @UsedByGodot
    fun requestRecordingPermission() {
        runOnHostThread {
            val currentActivity = activity ?: return@runOnHostThread

            val manager = currentActivity.getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

            currentActivity.startActivityForResult(
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

        if (requestCode == REQUEST_CODE &&
            resultCode == Activity.RESULT_OK &&
            data != null
        ) {
            val currentActivity = activity ?: return

            val intent = Intent(
                currentActivity,
                ScreenRecordService::class.java
            ).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                currentActivity.startForegroundService(intent)
            } else {
                currentActivity.startService(intent)
            }
        }
    }

    @UsedByGodot
    fun stopRecording() {
        runOnHostThread {
            val currentActivity = activity ?: return@runOnHostThread

            val intent = Intent(
                currentActivity,
                ScreenRecordService::class.java
            ).apply {
                action = "STOP_RECORDING"
            }

            currentActivity.startService(intent)
        }
    }

    @UsedByGodot
    fun isRecording(): Boolean {
        return ScreenRecordService.isRecording
    }

    @UsedByGodot
    fun getLastRecordingPath(): String {
        return ScreenRecordService.lastRecordingPath
    }
}

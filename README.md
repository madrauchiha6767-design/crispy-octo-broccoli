# RX Recorder – Godot Android Plugin

This project builds a Godot 4.7.2 Android plugin AAR for screen-only recording.
It is designed for Android 10+ devices, including Android 12 TV boxes.

## Build

The GitHub Actions workflow (`.github/workflows/build-android-plugin.yml`) builds the debug and release AARs and uploads a ready-to-copy `RXRecorder` Godot add-on as an artifact.

You can also build locally with JDK 17 and Gradle 8.14.3:

```bash
gradle :plugin:prepareGodotAddon
```

The prepared add-on is written to:

`plugin/build/godot-addon/RXRecorder/`

Copy that `RXRecorder` folder into your Godot project's `addons/` directory, enable it in Project Settings → Plugins, and export the Android project with Gradle Build enabled.

## GDScript usage

```gdscript
var rx_recorder

func _ready() -> void:
    if Engine.has_singleton("RXRecorder"):
        rx_recorder = Engine.get_singleton("RXRecorder")

func start_recording() -> void:
    if rx_recorder:
        rx_recorder.requestRecordingPermission()

func stop_recording() -> void:
    if rx_recorder:
        rx_recorder.stopRecording()

func recording_active() -> bool:
    return rx_recorder != null and rx_recorder.isRecording()

func last_recording_uri() -> String:
    return "" if rx_recorder == null else rx_recorder.getLastRecordingPath()
```

## Output and current limitations

- Video files are saved under `Movies/RXRecorder` using Android MediaStore.
- The recorder uses the device's actual screen resolution and H.264 video at 30 FPS.
- This first build records video only. Internal game audio and microphone audio are not implemented yet.
- Android's screen-capture consent dialog must be accepted each time recording starts.

This source bundle has not been compiled on a real Android toolchain in this environment. The GitHub Actions workflow must complete successfully before treating the AAR as build-verified.

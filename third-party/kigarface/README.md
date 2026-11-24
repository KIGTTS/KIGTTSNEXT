# KIGARFACE Android 1.1.8

AARs and upstream notices were supplied in `KIGARFACE-Android-Integration-1.1.8-3.51MiB.zip`.
Archive SHA-256 is `2c6c89cec62b5efb20a01174a99e24c1ac20a82fa5a740e2fc3f0af0a4cea4a2`.

The tracker AAR preserves its three models and runtime metadata. Four original indexed
PNG assets were removed. Their matching SVG paths live in `app/src/main/assets/camera-expressions`.
Unreferenced producer IDs were removed from the SVGs to make the files valid for the
host's restricted renderer. Native OpenCV and ONNX Runtime archives are unchanged. The original license files are
packaged under `app/src/main/assets/licenses/kigarface`.

The KIGTTS camera uses the tracking API on ARM64. Android's other packaged ABIs use the
existing camera. The optional analysis stream falls back on cameras that cannot bind it.

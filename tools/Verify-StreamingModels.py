"""Real CPU online-decoder smoke test. This is not an Android DEX/JNI device test."""
import argparse
import hashlib
import json
import pathlib
import tarfile
import time
import wave
import zipfile

import numpy as np
import sherpa_onnx


def check_archive(root, name, language, fixture_override=None):
    archive = root / "downloads" / (name + ".tar.bz2")
    destination = root / "models" / "verification"
    destination.mkdir(parents=True, exist_ok=True)
    if not (destination / name / "tokens.txt").is_file():
        with tarfile.open(archive, "r:bz2") as package:
            for entry in package.getmembers():
                target = (destination / entry.name).resolve()
                if not target.is_relative_to(destination.resolve()) or not (entry.isfile() or entry.isdir()):
                    raise ValueError("Unsafe model archive")
            package.extractall(destination, filter="data")
    folder = destination / name

    def model(prefix):
        return sorted(folder.glob(prefix + "*.onnx"), key=lambda p: ("int8" not in p.name, p.name))[0]

    files = {key: model(key) for key in ("encoder", "decoder", "joiner")}
    files["tokens"] = folder / "tokens.txt"
    manifest = {"format": 1, "name": name, "languages": language.split(","), "architecture": "TRANSDUCER",
                "files": {key: value.name for key, value in files.items()}, "sampleRate": 16000}
    output = root / "downloads" / (name + ".kigasr")
    if not output.is_file():
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as package:
            package.writestr("asr-pack.json", json.dumps(manifest, ensure_ascii=False))
            for file in files.values():
                package.write(file, file.name)
            for file in folder.rglob("*"):
                if file.is_file() and file.name.lower().startswith(("license", "notice", "readme")):
                    package.write(file, str(file.relative_to(folder)))

    fixture = pathlib.Path(fixture_override) if fixture_override else sorted((folder / "test_wavs").glob("*.wav"))[0]
    assert fixture.resolve().is_relative_to(root)
    with wave.open(str(fixture), "rb") as audio:
        assert audio.getnchannels() == 1 and audio.getframerate() == 16000 and audio.getsampwidth() == 2
        samples = np.frombuffer(audio.readframes(audio.getnframes()), dtype="<i2").astype(np.float32) / 32768.0
    recognizer = sherpa_onnx.OnlineRecognizer.from_transducer(
        **{key: str(value) for key, value in files.items()}, num_threads=2, sample_rate=16000,
        feature_dim=80, enable_endpoint_detection=True, decoding_method="greedy_search",
        rule1_min_trailing_silence=2.4, rule2_min_trailing_silence=1.4, rule3_min_utterance_length=20)
    stream = recognizer.create_stream()
    partials, finals = [], []
    previous, first_partial_at = "", None
    begin = time.perf_counter()
    # Two utterances with silence demonstrate ongoing sessions and endpoint reset.
    input_samples = np.concatenate((samples, np.zeros(32000, np.float32), samples))
    for offset in range(0, len(input_samples), 1600):
        stream.accept_waveform(16000, input_samples[offset:offset + 1600])
        while recognizer.is_ready(stream):
            recognizer.decode_stream(stream)
        text = recognizer.get_result(stream).strip()
        if recognizer.is_endpoint(stream):
            if text:
                finals.append(text)
            recognizer.reset(stream)
            previous = ""
        elif text and text != previous:
            if first_partial_at is None:
                first_partial_at = (offset + 1600) / 16000
            partials.append(text)
            previous = text
    assert partials, "No hypothesis appeared before EOF"
    stream.accept_waveform(16000, np.zeros(4800, np.float32))
    stream.input_finished()
    while recognizer.is_ready(stream):
        recognizer.decode_stream(stream)
    text = recognizer.get_result(stream).strip()
    if text:
        finals.append(text)
    assert len(finals) >= 2, "Endpoint/EOF did not finalize both utterances"
    elapsed = time.perf_counter() - begin
    return {"model": name, "language": language, "archive_sha256": hashlib.file_digest(archive.open("rb"), "sha256").hexdigest(),
            "package": str(output), "fixture": str(fixture), "partials_before_eof": len(partials),
            "first_partial_audio_seconds": first_partial_at, "finals": finals, "decode_seconds": round(elapsed, 3),
            "audio_seconds": len(input_samples) / 16000, "runtime": sherpa_onnx.__version__, "platform": "Windows CPU"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", type=pathlib.Path, required=True)
    args = parser.parse_args()
    root = args.data_dir.resolve()
    assert root.parts[-4:] == ("Kigtts", "plugin", "plugindata", "engine.streaming-asr")
    results = [check_archive(root, "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23", "zh"),
               check_archive(root, "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17", "en")]
    for fixture in [item["fixture"] for item in results]:
        results.append(check_archive(root, "sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16", "zh,en", fixture))
    report = root / "config" / "streaming-verification.json"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(report.read_text(encoding="utf-8"))

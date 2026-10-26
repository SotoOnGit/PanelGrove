"""Generate original test pages and archive fixtures. Requires Pillow; RAR fixtures use an installed rar CLI."""
from pathlib import Path
import io
import json
import subprocess
import zipfile
import tarfile
import shutil

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "app/src/androidTest/assets/fixtures"
WORK = ROOT / ".tooling/fixture-pages"

def main():
    from PIL import Image, ImageDraw, JpegImagePlugin, WebPImagePlugin
    DEST.mkdir(parents=True, exist_ok=True)
    WORK.mkdir(parents=True, exist_ok=True)
    names = ["001.png", "002.png", "003.png", "010.png"]
    for i, name in enumerate(names):
        image = Image.new("RGB", (1125, 1600), (250 - i * 12, 247 - i * 8, 235 - i * 8))
        draw = ImageDraw.Draw(image)
        draw.rectangle((75, 75, 1050, 1525), outline=(30, 50, 35), width=8)
        draw.rectangle((100, 200, 1025, 760), fill=(50 + 20 * i, 80, 65))
        draw.rectangle((100, 800, 550, 1450), outline=(30, 50, 35), width=8)
        draw.rectangle((590, 800, 1025, 1450), outline=(30, 50, 35), width=8)
        draw.text((120, 110), f"PANELGROVE ORIGINAL TEST PAGE {i + 1}", fill="black", font_size=34)
        draw.text((130, 850), "One more page.", fill="black", font_size=32)
        image.save(WORK / name)
    # Keep the original fixture series stable for retained RAR/CBZ metadata assertions.
    manifest = {"series": "Readly Originals", "title": "A Quiet Afternoon", "number": "1", "kind": "volume",
                "reading_direction": "right-to-left", "completeness": "complete",
                "pages": [{"archive_name": name, "source_id": f"test/{i + 1}", "chapter": str(1 + i // 2), "dimensions": [1125, 1600]} for i, name in enumerate(names)]}
    (WORK / "translation-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    with zipfile.ZipFile(DEST / "sample.cbz", "w", zipfile.ZIP_DEFLATED) as archive:
        for name in reversed(names): archive.write(WORK / name, name)
        archive.write(WORK / "translation-manifest.json", "translation-manifest.json")
        archive.writestr("__MACOSX/._001.png", b"ignored")
    with zipfile.ZipFile(DEST / "unsafe.cbz", "w") as archive: archive.writestr("../outside.png", (WORK / names[0]).read_bytes())
    with zipfile.ZipFile(DEST / "thousand.cbz", "w", zipfile.ZIP_DEFLATED) as archive:
        page = (WORK / names[0]).read_bytes()
        for i in range(1000): archive.writestr(f"{i + 1}.png", page)
    with zipfile.ZipFile(DEST / "mixed-images.cbz", "w", zipfile.ZIP_DEFLATED) as archive:
        for i, (name, format) in enumerate([("1.jpg", "JPEG"), ("2.webp", "WEBP"), ("3.png", "PNG")]):
            image = Image.new("RGB", (1600, 900) if i == 1 else (900, 1600), (55 + i * 20, 80, 65))
            output = io.BytesIO(); image.save(output, format=format); image.close()
            archive.writestr(name, output.getvalue())
    images = [Image.open(WORK / name).convert("RGB") for name in names]
    images[0].save(DEST / "sample.pdf", save_all=True, append_images=images[1:])
    for image in images: image.close()
    rar = Path(r"C:\Program Files\WinRAR\Rar.exe")
    if rar.exists():
        for name, switches in [("rar4.cbr", ["-ma4"]), ("rar5.cbr", ["-ma5"]), ("solid-rar5.cbr", ["-ma5", "-s"]), ("encrypted.cbr", ["-ma5", "-hppanelgrove-test"])]:
            result = subprocess.run([str(rar), "a", "-idq", "-y", "-m3", *switches, str(DEST / name), *names, "translation-manifest.json"], cwd=WORK, capture_output=True, text=True)
            if result.returncode: raise RuntimeError(result.stdout + result.stderr)
    shutil.copyfile(DEST / "sample.cbz", DEST / "zip-as-cbr.cbr")
    (DEST / "corrupt.cbr").write_bytes(b"Rar!\x1a\x07\x01\x00corrupt")
    if rar.exists():
        unicode_work = WORK / "unicode"
        unicode_work.mkdir(exist_ok=True)
        for i, name in enumerate(names):
            shutil.copyfile(WORK / name, unicode_work / f"日本語🌙-{i + 1:03}.png")
        subprocess.run([str(rar), "a", "-idq", "-y", "-ma5", "-s", str(DEST / "unicode.rar"),
                        *[path.name for path in sorted(unicode_work.glob("*.png"))]], cwd=unicode_work, check=True)
    # Preserve the original vendored license alongside generated notices.
    with tarfile.open(ROOT / "gradle/libarchive-3.8.10.tar.xz") as archive:
        license_text = archive.extractfile("libarchive-3.8.10/COPYING").read().decode()
    (ROOT / "LICENSES").mkdir(exist_ok=True)
    (ROOT / "LICENSES/libarchive.txt").write_text(license_text, encoding="utf-8")
    print("Generated fixtures:", ", ".join(path.name for path in DEST.iterdir()))

if __name__ == "__main__": main()

"""Generate original 7Z fixtures from sample.cbz. Requires a local 7-Zip CLI."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "app/src/androidTest/assets/fixtures"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seven-zip", default=shutil.which("7z") or r"C:\Program Files\7-Zip\7z.exe")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="panelgrove-7z-") as directory:
        work = Path(directory)
        with zipfile.ZipFile(DEST / "sample.cbz") as sample:
            for name in ["001.png", "002.png", "003.png", "010.png", "translation-manifest.json"]:
                (work / name).write_bytes(sample.read(name))
        names = ["010.png", "003.png", "002.png", "001.png", "translation-manifest.json"]

        def archive(name, files=names, method="LZMA2", *switches):
            target = DEST / name
            target.unlink(missing_ok=True)
            subprocess.run([args.seven_zip, "a", "-t7z", "-bd", "-y", "-mtc=off", "-mta=off",
                            "-mtr=off", f"-m0={method}", *switches, str(target), *files],
                           cwd=work, check=True, stdout=subprocess.DEVNULL)

        archive("solid-lzma2.cb7", names, "LZMA2", "-ms=on", "-md=1m")
        archive("lzma.7z", names, "LZMA", "-ms=off", "-md=1m")
        archive("stored.7z", names, "Copy", "-ms=off")
        archive("deflate.7z", names, "Deflate", "-ms=off")
        archive("ppmd.7z", names, "PPMd", "-ms=on", "-mmem=1m")
        archive("bzip2.7z", names, "BZip2", "-ms=off")
        archive("encrypted-header.7z", names, "LZMA2", "-ptest-only", "-mhe=on", "-md=1m")
        archive("encrypted-data.cb7", names, "LZMA2", "-ptest-only", "-mhe=off", "-md=1m")

        unicode_names = ["日本語🌙/10.png", "日本語🌙/2.png", "日本語🌙/1.png"]
        for name, source in zip(unicode_names, names):
            (work / name).parent.mkdir(exist_ok=True)
            (work / name).write_bytes((work / source).read_bytes())
        (work / "ComicInfo.xml").write_text("<ComicInfo><Series>Seven Zip</Series><Manga>YesAndRightToLeft</Manga></ComicInfo>")
        archive("unicode.7z", unicode_names + ["ComicInfo.xml"], "LZMA2", "-md=1m")
        (work / "translation-manifest.json").write_text("{broken")
        archive("invalid-metadata.cb7", ["010.png", "002.png", "001.png", "translation-manifest.json"], "LZMA2", "-md=1m")
        (work / "outside.png").write_bytes((work / "001.png").read_bytes())
        archive("unsafe.7z", ["outside.png"], "Copy")
        subprocess.run([args.seven_zip, "rn", "-bd", str(DEST / "unsafe.7z"), "outside.png", "../outside.png"],
                       check=True, stdout=subprocess.DEVNULL)
        # Large advertised entry, tiny compressed fixture: rejected before extraction.
        with (work / "oversized.png").open("wb") as output:
            output.truncate(128 * 1024 * 1024 + 1)
        archive("oversized.7z", ["oversized.png"], "LZMA2", "-md=1m")
        (work / "note.txt").write_text("No image pages")
        archive("empty.7z", ["note.txt"], "Copy")
        data = (DEST / "solid-lzma2.cb7").read_bytes()
        (DEST / "corrupt.7z").write_bytes(data[:40])
        # Stored page bytes are easy to corrupt without changing the archive index.
        data = bytearray((DEST / "stored.7z").read_bytes())
        data[40] ^= 0xFF
        (DEST / "bad-crc.7z").write_bytes(data)
    print("Generated CB7/7Z fixtures")


if __name__ == "__main__":
    main()

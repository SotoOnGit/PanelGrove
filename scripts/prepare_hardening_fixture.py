"""Generate a huge original grayscale page without allocating the full image in memory."""
import struct
import zlib
import zipfile
from pathlib import Path


def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))


destination = Path(__file__).resolve().parents[1] / 'app/src/androidTest/assets/fixtures/huge-page.cbz'
compressor = zlib.compressobj(9)
row = b'\0' + b'\x90' * 20_000
compressed = bytearray()
for _ in range(20_000):
    compressed.extend(compressor.compress(row))
compressed.extend(compressor.flush())
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 20_000, 20_000, 8, 0, 0, 0, 0))
png += chunk(b'IDAT', compressed) + chunk(b'IEND', b'')
with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_STORED) as archive:
    entry = zipfile.ZipInfo('huge.png', date_time=(2026, 1, 1, 0, 0, 0))
    archive.writestr(entry, png)
print(f'{destination}: {destination.stat().st_size} bytes; 20,000 x 20,000 pixels')

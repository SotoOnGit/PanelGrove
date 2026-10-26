"""Verify native ELF LOAD alignment and absence of private manga in a built APK."""
import argparse
from pathlib import Path
import struct
import zipfile

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    args = parser.parse_args()
    with zipfile.ZipFile(args.apk) as archive:
        libraries = [name for name in archive.namelist() if name.endswith('.so')]
        assert libraries, 'Expected native archive libraries'
        for name in libraries:
            data = archive.read(name)
            assert data[:4] == b'\x7fELF'
            if data[4] == 2:
                fields = struct.unpack_from('<HHIQQQIHHHHHH', data, 16)
                offset, size, count = fields[4], fields[8], fields[9]
                headers = [struct.unpack_from('<IIQQQQQQ', data, offset + i * size) for i in range(count)]
                alignments = [entry[7] for entry in headers if entry[0] == 1]
            else:
                fields = struct.unpack_from('<HHIIIIIHHHHHH', data, 16)
                offset, size, count = fields[4], fields[8], fields[9]
                headers = [struct.unpack_from('<IIIIIIII', data, offset + i * size) for i in range(count)]
                alignments = [entry[7] for entry in headers if entry[0] == 1]
            assert alignments and all(value >= 16384 and value % 16384 == 0 for value in alignments), (name, alignments)
            print(name, 'LOAD alignment:', ', '.join(hex(value) for value in alignments))
        assert not any(name.lower().endswith(('.cbz', '.cbr', '.pdf', '.rar')) for name in archive.namelist()), 'A document was bundled in the application'
        print('No manga/document archives are bundled in the APK.')

if __name__ == '__main__': main()

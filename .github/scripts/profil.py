"""Ispisuje šta nosi profil za brži start u APK-u (assets/dexopt/baseline.prof): po dex fajlu broj
klasa i veličinu spiska metoda. Pada ako profila nema. Poziv: python3 profil.py <putanja do APK-a>"""
import struct
import sys
import zipfile
import zlib

data = zipfile.ZipFile(sys.argv[1]).read("assets/dexopt/baseline.prof")
if data[:4] != b"pro\0":
    sys.exit("baseline.prof nije u očekivanom formatu")
version = data[4:7].decode()
print(f"baseline.prof: {len(data)} bajtova, verzija {version}")
if version != "010":
    sys.exit(0)  # drugačiji zapis: dovoljno je da profil postoji
dex_count = data[8]
raw_size, packed_size = struct.unpack_from("<II", data, 9)
raw = zlib.decompress(data[17:17 + packed_size])
pos, classes, methods = 0, 0, 0
for _ in range(dex_count):
    key_size, class_count, hot_size, _checksum, method_ids = struct.unpack_from("<HHIII", raw, pos)
    key = raw[pos + 16:pos + 16 + key_size].decode(errors="replace")
    pos += 16 + key_size
    classes += class_count
    methods += hot_size
    print(f"  {key or 'classes.dex'}: klasa {class_count}, spisak metoda {hot_size} bajtova, metoda u dex-u {method_ids}")
print(f"ukupno: klasa {classes}, spisak metoda {methods} bajtova, raspakovano {raw_size} bajtova")

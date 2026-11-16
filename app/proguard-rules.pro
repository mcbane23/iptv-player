# Pravila za R8 (release build). Biblioteke donose svoja pravila; ovde je samo ono što je naše.

# Kod je naš i nema šta da se krije: imena ostaju čitljiva, pa su i prijave grešaka čitljive,
# a serijalizacija i rute navigacije ne zavise od preimenovanja. R8 i dalje izbacuje nekorišćen kod.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# Navodi se samo imenom u AndroidManifest.xml (meta-data), a Cast ga pravi refleksijom.
-keep class com.bane23.iptvplayer.cast.CastOptionsProvider { <init>(); }

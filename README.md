# Suara — pemutar musik lokal

- Package: `com.za.sidqi.suara`
- PRD: `/home/trfth/Desktop/PRD_Suara.md`
- Project: `/home/trfth/Desktop/SuaraApp`

## Build

```bash
cd /home/trfth/Desktop/SuaraApp
JAVA_HOME=~/jdk21 ANDROID_HOME=~/Android/Sdk ./gradlew assembleRelease --no-daemon
```

Gunakan `with-build-lock.sh` saat bot Telegram aktif:

```bash
/home/trfth/simpanbot/scripts/with-build-lock.sh env \
  JAVA_HOME=~/jdk21 ANDROID_HOME=~/Android/Sdk ./gradlew assembleRelease --no-daemon
```

Hasil: `app/build/outputs/apk/release/app-release.apk`

## Signing

- Keystore: `suara-release.jks` (tidak di-commit)
- Store password: `suara123`
- Alias: `suara`
- Key password: `suara123`

Jangan ganti key setelah APK dibagikan; kalau berbeda, user harus uninstall dulu.

## Sumber musik

- MediaStore audio di `Music/` (termasuk `Music/Telegram`) dan `Download/` (termasuk `Download/Simpan`).
- File di `Android/data/...` tidak terbaca aplikasi lain; simpan lewat Telegram "Save to Music".

## Status

Debug build sukses. Belum diuji di HP (tidak ada emulator).

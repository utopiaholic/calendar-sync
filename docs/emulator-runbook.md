# Emulator Build, Install, and Fixture Data

Run these from WSL in `C:\code\Personal Calendar\calendar-sync`.

## Build

```bash
./gradlew :app:assembleDebug
```

## Windows ADB From WSL

Use the Windows SDK `adb.exe`; WSL `adb` may fail to start its server.

```bash
powershell.exe -NoProfile -Command "& 'C:\Users\alber\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices -l"
```

## Install and Launch

```bash
powershell.exe -NoProfile -Command "& 'C:\Users\alber\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 install -r 'C:\code\Personal Calendar\calendar-sync\app\build\outputs\apk\debug\app-debug.apk'"
powershell.exe -NoProfile -Command "& 'C:\Users\alber\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 shell monkey -p com.calmerge.app -c android.intent.category.LAUNCHER 1"
```

## Load Sample ICS Feeds

Start the local fixture server and keep it running:

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\code\Personal Calendar\calendar-sync\scripts\serve-fixtures.ps1"
```

Map emulator localhost to the host fixture server:

```bash
powershell.exe -NoProfile -Command "& 'C:\Users\alber\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 reverse tcp:8800 tcp:8800"
```

Then open the Feeds tab in the app and add:

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\code\Personal Calendar\calendar-sync\scripts\add-feed.ps1" -FeedName "Work A" -FeedUrl "http://localhost:8800/worka.ics"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\code\Personal Calendar\calendar-sync\scripts\add-feed.ps1" -FeedName "Work B" -FeedUrl "http://localhost:8800/workb.ics"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\code\Personal Calendar\calendar-sync\scripts\add-feed.ps1" -FeedName "Personal" -FeedUrl "http://localhost:8800/personal.ics"
```

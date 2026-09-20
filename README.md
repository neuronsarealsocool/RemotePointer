# RemotePointer

RemotePointer lets an Android phone control a computer's mouse and keyboard, display a digital laser pointer, and send scanned barcode or QR code data.

This monorepo combines the Android client and cross-platform desktop server while preserving the history of both projects.

## Projects

- [`android/`](android/) - Android client, including the `devDebug` build with development-only feature access.
- [`windows/`](windows/) - Python/PyQt desktop server for Windows, Linux, and macOS.

## Android development build

Set `sdk.dir` in `android/local.properties`, then build from the repository root:

```powershell
cd android
.\gradlew.bat :app:assembleDevDebug
```

The APK is written to `android/app/build/outputs/apk/dev/debug/remotepointer.apk` and uses the separate package ID `systems.sieber.remotespotlight.dev`.

## Windows development build

```powershell
cd windows
python -m venv venv
.\venv\Scripts\pip.exe install -r requirements.txt pyinstaller
lrelease .\lang\de.ts
.\venv\Scripts\pyinstaller.exe --clean --noconfirm RemotePointerServer.windows.spec
```

The `lrelease` command is supplied by Qt and generates the translation catalog required by the PyInstaller spec. The application is written to `windows/dist/RemotePointerServer/RemotePointerServer.exe`.

See the README and license in each project directory for upstream project details.

# Security notes

- **Root:** the app runs root shell commands to write LED sysfs nodes and to set the
  RECORD_AUDIO permission for the music visualizer. Review `Glyph.kt`, `MusicVisualizer.kt`
  and `GestureServiceControl.kt` before installing.
- **Broadcast API:** `GlyphReceiver` accepts intents only with the per-install token stored
  in the app's private preferences. The token is shown in Settings; treat it like a password.
- **Backups:** `allowBackup` is disabled so tokens and motion templates are not exported.
- **No network:** the app declares no INTERNET permission.
- **Signing:** `app/debug.keystore` in this repository is a debug-only key for stable CI
  signing. It must not be used to sign a public release. Use your own release key.

To report a vulnerability, open a private security advisory on the GitHub repository.

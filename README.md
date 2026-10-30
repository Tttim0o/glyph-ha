# Glyphy

Control the Nothing Phone (1) glyph interface and camera flash from an Android app.

- Requires root (Magisk or KernelSU). Writes to `/sys/class/leds/aw210xx_led/`.
- Min Android 10 (API 29). Tested on LineageOS 24 (Android 17).
- Built with Kotlin, Jetpack Compose (Material 3) and [libsu](https://github.com/topjohnwu/libsu).
- Written with the help of AI coding tools and tested on a real device.

## Build

```
gradle assembleRelease   (Gradle 8.9, JDK 17)
```

Release builds are unsigned unless you add your own signing config. F-Droid builds and signs
the app with its own key.

## Security

See [SECURITY.md](SECURITY.md).

## License

GPL-3.0-or-later, see [LICENSE](LICENSE).

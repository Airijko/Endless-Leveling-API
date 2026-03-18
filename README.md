# Endlessleveling

## Building

```bash
./gradlew clean build
```

Or on Windows:

```cmd
.\\gradlew.bat clean build
```

## Development

### Run Server with Plugin

```bash
./gradlew runServer
```

This will build your plugin, copy it to the server's mods folder, and start the Hytale server.

### Install Plugin Only (Hot Reload)

```bash
./gradlew installPlugin
```

This builds and copies the plugin to the server without starting it.

## Requirements

- **JDK 25** - Required for Gradle and compilation
- Gradle wrapper (included, Gradle 9.2.1), or a Java-25-compatible global Gradle install
- Hytale Server installation

The Hytale installation path is configured in gradle.properties.

If your IDE reports a Gradle JVM mismatch, make sure the project is using the bundled Gradle 9.2.1 wrapper and that your Gradle JVM points to a Java 25 JDK before syncing.

## Git Bash note

In Git Bash, run wrapper scripts with ./ prefix:

```bash
./gradlew clean build
./gradlew.bat clean build
```

Do not use .\ in Git Bash. That syntax is for cmd/PowerShell.

## License

MIT

## Author

Airijko

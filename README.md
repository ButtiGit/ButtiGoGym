# ButtiGoGym · PULSE Workout Android

App Android nativa per gestire le schede e registrare gli allenamenti.

- Kotlin + Jetpack Compose + Material 3
- Nessun login o backend, dati salvati localmente
- Tre giorni personalizzati: lunedì spalle e braccia, mercoledì gambe e addome, venerdì petto e schiena
- Registrazione di serie, kg, ripetizioni e storico
- Progetto Figma: https://www.figma.com/design/hJMXltGv34gI2cVSTpTKqV

## Scaricare l'APK

Vai in **Actions → PULSE Android APK → ultima esecuzione riuscita → Artifacts**.
Lì trovi l'archivio contenente `app-debug.apk`, installabile sui dispositivi Android 8.0 o successivi.

La compilazione è automatica ad ogni push su `main`.

## Sviluppo

Apri questa repository in Android Studio, attendi il sync delle dipendenze Gradle
e avvia il modulo `app`. Per la build locale usa Gradle 8.9, Java 17 e Android SDK 35.

Il build automatico può aggiornare il repository con i sorgenti completi,
estratti dall'archivio iniziale `pulse-build/source.tar.xz.b64`.

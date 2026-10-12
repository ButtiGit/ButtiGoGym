# ButtiGoGym · PULSE Workout Android

App nativa Android in Kotlin + Jetpack Compose (Android 8.0+), con allenamenti,
kg, ripetizioni e storico salvati sul dispositivo.

## Scheda allenamento modificabile senza aggiornare l'APK

**File da modificare:** [workouts/plan.json](workouts/plan.json).

L'app legge una scheda JSON statica dal repository pubblico, cioè una semplice API via HTTPS:

```
https://raw.githubusercontent.com/ButtiGit/ButtiGoGym/main/workouts/plan.json
```

Per cambiare scheda modifica il JSON su `main`. Al prossimo avvio dell'app,
o premendo **Scheda → Aggiorna scheda da GitHub**, verrà caricata la versione aggiornata
(potrebbero servire alcuni minuti per la cache di GitHub). Non serve ricompilare
l'app quando si modificano esercizi, serie, recuperi, nome o GIF.

Il client verifica `schemaVersion=1`, presenza dei giorni e limiti minimi prima di
accettare modifiche. Se la connessione manca o il file non è valido, usa la
**precedente scheda valida in cache**; su prima installazione senza internet usa
la scheda inclusa nell'APK (`app/src/main/assets/workout-plan.json`).

**Attenzione all'identità degli esercizi:** mantieni gli `id` stabili quando modifichi
serie o nomi, perché lo storico è associato a questi identificatori. Se sostituisci
un esercizio con uno realmente diverso, assegnagli un nuovo `id`.

### Struttura della scheda

- 3 sedute principali: lunedì spalle/braccia + richiami, mercoledì gambe/core,
  venerdì petto/schiena + richiami
- 1 seduta bonus **facoltativa**, solo se sufficientemente recuperato
- Ogni esercizio definisce `sets`, `target` (range ripetizioni), `restSeconds`,
  `rir`, `equipment`, `note`, `alternative` e `gifUrl`
- `optional=true` identifica le sedute che non contano nelle tre principali

Le GIF vengono **caricate da archivi esterni soltanto su richiesta** durante la seduta.
`tools/resolve_gifs.py` cerca animazioni dal dataset esterno exercise-library,
senza copiare i file nel repository. Le animazioni mancanti vengono lasciate
vuote se non ci sono corrispondenze credibili: le istruzioni rimangono visibili.
Controlla sempre i diritti d'uso dei contenuti di terzi prima di distribuire pubblicamente l'app.

### Protezione dei progressi

- Una sessione in corso conserva uno *snapshot* della sua scheda originale.
- Lo storico conserva nomi, esercizi e serie della seduta originale.
- Gli aggiornamenti GitHub **non sovrascrivono** cronologia e carichi locali.
- Nessun account né server per i tuoi dati di allenamento.

## Scaricare l'APK

Vai su **Actions → PULSE Android APK → ultima esecuzione riuscita → Artifacts**
e scarica lo ZIP contenente `app-debug.apk`.

## Sviluppo e design

Per compilare localmente: Android Studio, Android SDK 35, Gradle 8.9, Java 17.
Il workflow GitHub Actions produce automaticamente un APK a ogni push su `main`.

Figma: https://www.figma.com/design/hJMXltGv34gI2cVSTpTKqV

## Nota sugli attrezzi

La palestra Revolution Fitness Club Cuneo dichiara un'ampia sala pesi e numerosi
macchinari, ma l'inventario aggiornato delle specifiche postazioni non è pubblico.
Per questo la scheda propone attrezzatura comune con alternative da verificare in sede.

## Dashboard V0.3 · Widget style bianco/verde

Palette chiara con accento **#106119**, quattro metriche streak e monitoraggio locale:

- **Generale (giorni):** 6.000 passi e (allenamento registrato oppure riposo confermato) nello stesso giorno.
- **Allenamenti (settimane):** tre sedute obbligatorie distinte (ID `mon`, `wed`, `fri`) nella stessa settimana; il bonus non conta. La settimana ancora in corso non azzera la precedente.
- **Riposo (giorni):** giorni segnati come recupero consecutivo; le giornate di allenamento sono ignorate, non interrompono la serie.
- **Passi (giorni):** minimo **6.000** passi giornalieri. Non viene perso lo streak appena ci si sveglia: se oggi è incompleto viene considerato ieri.

I contatori usano soltanto i dati reali registrati nel dispositivo. I passi
sono **inseriti manualmente come totale della giornata** (0–200.000), per ora
senza Health Connect o lettura del contapassi Android. Il bottone riposo è
reversibile. I dati sono salvati localmente in SharedPreferences e limitati
a due anni per contenere le dimensioni.

Gli streak NON sono salvati come numeri precompilati: vengono ricalcolati
da allenamenti, passi e riposo. I test automatizzati si trovano in
`app/src/test/java/it/pulse/workout/data/StreakCalculatorTest.kt`.

Il nuovo design è ispirato alle superfici arrotondate e ai riquadri di iOS:
non aggiunge ancora widget alla schermata Home del launcher Android.

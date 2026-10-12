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

## V0.4 · Contapassi e allenamenti guidati

### Contapassi Android automatico

L'app richiede il permesso **Attività fisica** (`ACTIVITY_RECOGNITION` su Android 10+)
e legge il sensore a basso consumo `TYPE_STEP_COUNTER`, se presente sul dispositivo.
Dopo la concessione del permesso, il riepilogo passi si aggiorna automaticamente
alla ricezione degli eventi del sensore. Non è necessario premere "Registra passi".

**Limite tecnico importante:** il sensore restituisce un totale dal riavvio,
non il totale di oggi. La prima lettura imposta un riferimento iniziale e non
può ricostruire i passi precedenti. Se l'app non viene aperta per più giorni,
non può ricostruire precisamente a quale giorno appartengano i passi effettuati
a cavallo di mezzanotte: in quel caso prende una nuova base. Non funziona su
smartphone privi di `TYPE_STEP_COUNTER` o se il permesso viene rifiutato.
L'inserimento manuale rimane come alternativa/correzione.

Per uno storico giornaliero completo e indipendente dall'apertura dell'app è
prevista come evoluzione l'integrazione con **Health Connect**, con consenso
esplicito e una sorgente dati compatibile.

### Sessione guidata

Il pulsante **Inizia allenamento** crea una sessione e avvia il cronometro.
A ogni passo l'app mostra un singolo esercizio, il numero della serie e due
campi (peso in kg e ripetizioni). La casella **Serie di riscaldamento** registra
una serie aggiuntiva separata da quelle allenanti e non diminuisce il conteggio
delle serie del programma.

Dopo aver registrato una serie parte il timer di recupero, calcolato dal campo
`restSeconds` presente nella scheda remota; per il riscaldamento il massimo è
90 secondi. Il recupero può essere accorciato, allungato o saltato.
Alla serie successiva il procedimento si ripete.

La sessione rimane su disco, incluse serie, riscaldamenti, esercizio corrente,
prossima serie e scadenza del recupero. Tornando nell'app il timer recupera
il tempo trascorso. Premendo **Salva allenamento** si registra anche la durata
effettiva in secondi e si possono vedere i risultati nello storico.

Le serie già registrate e lo storico precedente restano disponibili. Nella
prima versione guidata la schermata modifica il percorso una serie alla volta;
la modifica retroattiva delle singole serie è prevista in un secondo passaggio.

## V0.5 — Passi da Samsung Health tramite Health Connect

La schermata **Oggi → Movimento quotidiano** può collegarsi a Health Connect
e leggere, con il permesso `android.permission.health.READ_STEPS`, i passi
giornalieri provenienti da **Samsung Health**, Galaxy Watch e altre sorgenti
consentite dall'utente. Non richiediamo alcun permesso di scrittura.

### Collegare Samsung Health

1. Aggiorna Samsung Health (e Health Connect, se il telefono lo richiede).
2. Apri **Samsung Health → Impostazioni → Health Connect**, completa la
   configurazione e concedi a Samsung Health **Scrivi passi**.
3. Apri **ButtiGoGym → Oggi → Collega Health Connect** e autorizza
   **Leggi passi**. Le autorizzazioni di Samsung Health e ButtiGoGym sono separate.
4. Torna alla dashboard e usa **Aggiorna passi da Health Connect** se vuoi
   forzare una rilettura. L'app sincronizza anche all'avvio, alla ripresa e
   ogni circa 60 secondi mentre è in primo piano.

Se i passi del Galaxy Watch non appaiono, riapri o sincronizza Samsung Health
sul telefono; gli aggiornamenti del Watch possono arrivare con ritardo.

### Accuratezza e compatibilità

- Si usa `StepsRecord.COUNT_TOTAL` con `aggregateGroupByPeriod` della libreria
  AndroidX Health Connect **1.1.0**. Health Connect applica le priorità delle
  sorgenti ed evita il conteggio duplicato di telefono e Watch.
- Il totale letto da Health Connect **sostituisce, non si somma** al totale
  del sensore `TYPE_STEP_COUNTER` o a quello inserito manualmente.
- Vengono sincronizzati oggi e i 27 giorni precedenti, senza permessi
  di lettura storica aggiuntivi; ciò aggiorna anche lo streak passi.
- Se Health Connect manca o il permesso non è concesso, l'app continua
  a usare il sensore locale o il conteggio manuale.
- Revocando il permesso, la cache Health Connect viene rimossa al successivo
  rilevamento nell'app. I dati dei tuoi allenamenti non sono toccati.
- **I dati di salute restano sul dispositivo**, senza invii a GitHub,
  server o terze parti. L'app usa Internet soltanto per la scheda remota
  e per caricare le GIF, non per inviare i passi.

Health Connect è integrato nel sistema Android 14+; su Android 13 o inferiori
potrebbe essere necessario installare l'app Health Connect. Alcuni telefoni
o versioni potrebbero non supportarlo.

## Myself — muscoli, recupero, streak e livelli (V0.6)

La nuova scheda **Myself** è una vista muscolare interattiva di fronte/retro
con 16 gruppi anatomici selezionabili. Il manichino è disegnato direttamente
con Android Compose Canvas, ispirato alle mappe muscolari del fitness ma
non copiato da artwork di terze parti.

**Colori dei gruppi:**
- verde intenso: allenato oggi;
- arancione: recupero in corso;
- verde chiaro: pronto secondo la finestra temporale stimata;
- grigio: nessuna serie allenante registrata.

**Recupero:** indicatore euristico di circa 36–72 ore a seconda del gruppo
muscolare; il coinvolgimento secondario prevede metà del tempo. Non è una
misurazione medica né una garanzia di recupero reale.

**Streak muscolare:** numero di settimane consecutive in cui hai completato
almeno una serie allenante per il gruppo (primario o secondario). La settimana
attuale ancora incompleta non interrompe la precedente.

**XP e livelli:** basati sui dati reali, NON su benchmark clinici di forza.
Per ogni serie allenante:
`XP = round(reps × (1 + sqrt(min(kg, 400))/4) × ruolo)`;
`ruolo = 1.0` per il muscolo primario, `0.35` per quello secondario.
Ripetizioni conteggiate fino a 40 per serie per ridurre incentivi a inserimenti
non realistici. Solo serie completate; **riscaldamento escluso**.
Soglia del livello `n` = `150 × (n - 1)^1.55` XP; livelli dal 1 in poi.
Il livello complessivo normalizza gli XP di tutti i gruppi. Ogni scheda
mostra XP accumulati, progresso e XP mancanti al prossimo livello.
I livelli sono gamificazione, non stimano la massa muscolare.

**Esercizi / muscoli**: la scheda remota `workouts/plan.json` ora include
`primaryMuscles` e `secondaryMuscles`: quando cambi gli esercizi su GitHub,
puoi correggere anche questi gruppi senza aggiornare l'APK. Gli storici più
vecchi sono coperti da una tabella fallback in `ExerciseMuscleMapping`.

Myself legge lo **storico locale già registrato**: non inventa forza, volume,
streak o progressi iniziali. Le sessioni in corso compaiono quando vengono
salvate, per evitare di assegnare XP per serie non ancora confermate.

## Myself V2 — anatomia reale con template open source (v0.7)

La sezione **Myself** non usa più i poligoni disegnati a mano in Compose.
Il corpo anteriore e posteriore, le immagini fotografiche e i tracciati
individuali dei muscoli sono importati da
[Jsplice/MuscleMap](https://github.com/Jsplice/MuscleMap), versione
`170cfacda79376dd59ac0cf2f7dfab8ff9f4443a`, **licenza MIT**.
I crediti e la licenza completa sono contenuti nell'APK in
`app/src/main/assets/MUSCLEMAP-LICENSE.txt`.

La mappa viene costruita da `tools/build_muscle_map.py` nella pipeline CI:
importa l'illustrazione anatomica tracciata da MuscleMap (senza modificarne
la geometria), le immagini male-front/male-back, genera un HTML locale che
colorizza i gruppi in base a recupero/allenamento, e li include offline nel
pacchetto Android.

Le superfici muscolari sono selezionabili al tocco via WebView, con una
JavaScript bridge limitata agli ID dei 16 muscoli supportati dalla scheda.
**Il WebView non effettua richieste Internet**. Streak, livello, XP e recupero
restano implementati in Kotlin con dati locali.

Myself v0.7.1 rende neutre (scala di grigi) le immagini base del template:
i colori verde/arancione sono applicati esclusivamente ai muscoli secondo
i dati reali di allenamento e recupero.

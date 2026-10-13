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

## V0.7.2 — Correzione mappa muscoli bianca

Alcuni telefoni mostravano un pannello completamente bianco nella sezione
Myself nonostante gli asset fossero presenti nell'APK. La causa esatta
dell'incompatibilità WebView sul dispositivo non è verificabile senza
logcat, ma ora **WebView è stato rimosso** dalla mappa dei muscoli.

`NativeMuscleMapView.kt` visualizza le immagini anatomiche frontale e posteriore
del template MIT Jsplice/MuscleMap tramite Android Canvas nativo. I percorsi
anatomici originali vengono importati come `muscle-map-regions.json` e
interpretati da `androidx.core.graphics.PathParser`; non sono poligoni
disegnati manualmente. L'interazione usa Android `Region.contains` per i tocchi.

La mappa funziona completamente offline, senza HTML, JavaScript, WebView né
download. La foto base viene convertita in scala di grigi e ricoperta dalle
regioni effettivamente allenate (verde), in recupero (arancio) e pronte
(verde chiaro). Gli streak/XP restano invariati.

## V0.8 — Lean bulk personalizzato con focus avambracci

Scheda sostituita: tre giorni obbligatori (lunedì spalle, braccia e avambracci;
mercoledì gambe e addominali; venerdì petto, schiena e avambracci) + sabato
bonus facoltativo. Parametri indicati: 168 cm, 58 kg e aumento di peso controllato
0,1–0,2 kg/settimana, con obiettivo iniziale di 100–120 g proteine/giorno.

Serie previste: lun 22 + 2 richiamo facoltativo, mer 21, ven 19 + 2 richiamo
facoltativo, bonus 8. Avambracci: 8 serie dirette fra lunedì e venerdì.
Ogni seduta mostra il proprio protocollo di camminata finale.

Il piano remoto `workouts/plan.json` rimane aggiornabile senza APK.
Le sessioni attive conservano una snapshot della vecchia scheda sino al
termine, per evitare di perdere serie già registrate; le sessioni nuove
ricevono automaticamente il programma scaricato.

Le nuove proprietà `optional`, `timeBased`, `group` degli esercizi e
`cooldown` della giornata vengono validate, serializzate, salvate negli
storici e visualizzate nell'allenamento guidato. I richiami facoltativi
possono essere saltati senza falsare le statistiche. Il farmer's walk
registra kg e *secondi*, non ripetizioni: la conversione per gli XP muscolari
è normalizzata per non premiare artificialmente la durata come se fosse
un grande numero di ripetizioni.

La pagina **Scheda** può ora espandere l'elenco di tutti gli esercizi, con
serie, range, recuperi e richiami distinti, prima di iniziare il timer.
La verifica automatizzata `tools/validate_lean_bulk_plan.py` controlla
il programma effettivo prima di ogni build.

## V0.8.1 — Nuovo logo del launcher

La vecchia icona a bilanciere è stata sostituita dalla mascotte del logo
ButtiGoGym fornito dal proprietario: capelli rosa, struttura verde e
mascotte muscolosa. Il ritaglio centrale è ottimizzato per le icone
Android adaptive/round; la scritta originale è volutamente esclusa
per renderla leggibile anche a dimensioni molto piccole. Il file WebP
è incorporato offline nell’APK sotto `res/drawable-nodpi`.

## V0.9 — nuovo design, microanimazioni e widget di progresso

- Mascotte ButtiGoGym nelle intestazioni Oggi, Scheda, Storico e Myself
- Hero con verde sfumato, gerarchia tipografica e CTA primaria
- Widget Oggi basati esclusivamente sullo storico: sedute fisse della
  settimana (0–3), serie completate, minuti allenati, muscoli pronti
  (stima, non misurazione fisiologica)
- Animazione della fiamma per **3 secondi**, una sola volta quando si
  apre la pagina o cambia la streak, senza loop infiniti
- Barre di progresso di passi, allenamenti e livelli con transizioni brevi
- Esporta/ripristina backup locali dall'interfaccia Oggi mediante il
  selettore file di Android (senza permessi di archiviazione né cloud)

## Aggiornamenti Android — certificato permanente

**Limite di Android, da non aggirare:** APK firmati con certificati diversi
NON possono aggiornarsi reciprocamente senza disinstallazione, né basta
cambiare il numero di versione o il manifest. Le vecchie APK di GitHub
Actions erano firmate con **chiavi debug temporanee**: il certificato della
V0.8.1 installata sul telefono non può essere recuperato dal solo APK.
Questa modifica non promette un aggiornamento diretto retroattivo.

### 1. Configurare una sola volta una chiave privata stabile

Generare la chiave **sul proprio PC** (non caricarla nel repository):
```bash
keytool -genkeypair -v -keystore buttigogym-release.jks -alias buttigogym -keyalg RSA -keysize 3072 -validity 10000
```

Convertire la JKS in Base64 (PowerShell Windows):
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("buttigogym-release.jks")) | Set-Clipboard
```

In GitHub > repository > Settings > Secrets and variables > Actions,
aggiungere i repository secrets:
- `BUTTIGOGYM_KEYSTORE_BASE64`: Base64 della JKS
- `BUTTIGOGYM_STORE_PASSWORD`: password della JKS
- `BUTTIGOGYM_KEY_ALIAS`: `buttigogym`
- `BUTTIGOGYM_KEY_PASSWORD`: password della chiave

La pipeline genera quindi **assembleRelease** con lo stesso certificato
per ogni versione, verificando la firma con `apksigner`.
Non committare JKS, password o valori segreti nella repository.
Conserva una copia sicura della JKS e delle password: se le perdi,
gli aggiornamenti diretti potranno non essere più possibili.

In assenza dei secrets, la CI produce ancora un **APK debug** per test,
segnalandolo con un warning: **questo APK NON garantisce l'aggiornamento
di un'installazione già presente**.

### 2. Migrare una vecchia APK debug senza perdere lo storico

Se l'app già installata non supporta ancora il nuovo export:

1. **PRIMA DI DISINSTALLARE**, collega il telefono al PC con debug USB
   attivo; installa Android SDK Platform Tools (`adb`), sblocca il
   telefono e autorizza il computer.
2. Dal clone della repository, esegui
   `python tools/backup_existing_debug_android.py`. Il comando
   legge le SharedPreferences tramite `adb run-as` (supportato sulle
   vecchie build **debuggable**) e crea
   `ButtiGoGym-backup-migrazione.json` sul PC.
3. Apri il JSON e verifica che contenga i dati attesi. Conservane
   almeno una copia. Se il comando non riesce, **non disinstallare**.
4. Dopo avere salvato il backup e verificato il contenuto, disinstalla
   la vecchia APK, installa la versione con certificato stabile e, in
   **Oggi > I tuoi dati > Ripristina un backup**, scegli il JSON.

Da quel momento, le versioni rilasciate con la **stessa chiave stabile**
e un `versionCode` superiore si installeranno come normali aggiornamenti.

Per le versioni dalla V0.9 in poi, la funzione **Esporta backup in JSON**
è disponibile direttamente nell'app, senza ADB. Il JSON è in chiaro:
contiene dati degli allenamenti e potenzialmente passi; trattalo come
un documento personale e non condividerlo pubblicamente.

## v0.9.1 - Installazione pulita e aggiornamenti affidabili

La v0.9.1 avvia una nuova catena di firma: installare una volta con certificato
persistente (dopo aver disinstallato una precedente build debug con firma
incompatibile). Per le versioni seguenti aumentare sempre `versionCode`.

**La CI su main ora rifiuta di generare APK debug effimeri** se mancano
`BUTTIGOGYM_KEYSTORE_BASE64`, `BUTTIGOGYM_STORE_PASSWORD`,
`BUTTIGOGYM_KEY_ALIAS` e `BUTTIGOGYM_KEY_PASSWORD`. La pipeline
firmando `assembleRelease` riutilizza la stessa chiave; l'APK di avvio
firmato localmente usa lo stesso certificato da importare nei Secrets.
Conservare il file `.jks` e le relative credenziali fuori dalla repository.

## V0.9.2 — Foto e recap per ogni sessione di allenamento

Nella sezione Storico, apri una sessione per **allegare una foto dalla galleria**
o **scattare una foto a risoluzione piena con la fotocamera di sistema**.
Le immagini sono ridimensionate e salvate in `filesDir/session_photos/`,
private e senza upload online; rimangono presenti durante i normali aggiornamenti
Android firmati con il medesimo certificato.

**Recap WO** genera e salva un PNG 1080×1350 con la foto,
il titolo/data della sessione e 3 statistiche:
- Volume (kg): somma su tutte le **serie allenanti completate**
  di `peso registrato × ripetizioni registrate`. Riscaldamento escluso;
  il farmer's walk misura secondi e non contribuisce al volume.
- Durata: `durationSeconds` realmente registrati, con fallback
  a `durationMinutes * 60` per storici precedenti.
- Calorie: **stima indicativa**, non sensore, MET 5,0 e 58 kg:
  `5 × 3,5 × 58 / 200 × minuti`. Gli allenamenti meno o più intensi
  possono consumare significativamente meno o più di questa cifra.

Le foto sono legate all'ID della sessione e memorizzate come nomi di file
nel JSON dello storico. Non vengono attualmente incluse negli export JSON
delle preferenze: salva separatamente le foto/recap prima di disinstallare
l'app. Per l'aggiornamento normale invece i file privati si conservano.

`versionCode=14`, `versionName=0.9.2`. Il workflow usa il certificato
permanente dai Secrets GitHub: deve corrispondere alla firma della v0.9.1
STABLE già installata.

## V0.9.3 — Interfaccia essenziale e timer notifiche

- Dashboard semplificata: meno descrizioni duplicate, streak compatte,
  card avanzate "Backup" e indicazioni Health Connect espandibili.
- Scheda: aggiornamento rapido e linee guida espandibili, senza testi lunghi
  subito visibili; interfaccia coerente con il logo.
- Sessione guidata: meno testo, timer principale e recupero più immediati.
- **Notifica persistente soltanto quando una sessione è in corso**:
  il cronometro nativo conta il tempo trascorso anche con lo schermo bloccato.
  Durante un intervallo di riposo mostra invece il conto alla rovescia.
  Quando il recupero termina compare una notifica di avviso separata.
  Toccando la notifica torni all'allenamento.
- Richiede `POST_NOTIFICATIONS` su Android 13+; il permesso viene chiesto
  alla prima sessione, non all'apertura dell'app. Negandolo, Android potrebbe
  nascondere la notifica dalla tendina pur mostrando il servizio in Gestione
  attività. Si può abilitarlo in Impostazioni > App > ButtiGoGym > Notifiche.
- Su Android 14+ il servizio foreground usa il tipo `specialUse` per
  cronometro e recupero di una sessione avviata dall'utente. Si arresta
  automaticamente al salvataggio dell'allenamento.
- Il cambio dell'APK usa lo stesso certificato permanente dei Secrets
  (non un certificato debug), `versionCode=15`.
- Gli avvisi di fine riposo sono notifiche normalmente temporizzate dal
  servizio in foreground. Alcune ottimizzazioni aggressive della batteria
  del produttore possono ritardarli: non garantire precisione al secondo
  in sospensione profonda.

## V0.9.4 — Reset sessione e notifiche verificabili

- **Reset allenamento** sempre accessibile nell'intestazione della sessione.
  Chiede conferma; cancella tutte le serie del draft (anche i valori
  precompilati), riscaldamenti, indice esercizio e recupero, e riavvia
  il cronometro al momento del reset. Lo storico già salvato rimane intatto.
- Nuovi canali Android separati `Timer allenamento` e
  `Avvisi fine recupero`, importanza **DEFAULT** (v2).
  I canali precedenti v1 avevano importanza LOW e potevano essere nascosti
  su alcuni launcher Samsung; cambiare ID permette di creare nuovi canali,
  senza provare a forzare le impostazioni di un canale già configurato
  dall'utente.
- Durante l'allenamento una card indica lo stato del servizio; se il
  permesso, l'app o il canale sono disabilitati sono disponibili
  **Abilita**, **Impostazioni**, **Riprova**.
- **Invia notifica di prova**: avviso immediato sul canale fine recupero,
  per distinguere un problema di permessi da quello del servizio.
- Il servizio cattura eventuali errori di creazione della notifica
  e li espone alla schermata invece di fallire silenziosamente.
- `POST_NOTIFICATIONS` va autorizzato su Android 13+; in caso di
  blocco permanente aprire Impostazioni > App > ButtiGoGym > Notifiche.
- La notifica persistente richiede che la sessione sia attiva;
  per il recupero segue il timer di sistema. I produttori possono
  comunque ritardare avvisi con risparmio energetico aggressivo.
- Versione `0.9.4`, codice 16, certificato release stabile invariato.

L'integrazione e la compatibilità sono verificate dai test JVM, dall'APK
firmato e dal manifest. La ricezione effettiva delle notifiche deve
essere testata su un telefono reale: usa il pulsante di prova.

## V0.9.5 — Workout UI e promemoria quotidiani

### Schermata esercizi
- Scheda esercizio con testata verde sfumata: nome, muscolo, target,
  RIR, recupero e serie già completate mostrati come indicatori;
  note tecniche, alternative e GIF restano accessibili ma non ingombranti.
- Editor della singola serie con campi grandi, controllo rapido dei carichi
  (**−2,5/+2,5 kg**) e ripetizioni (**−1/+1**), riscaldamento separato,
  pulsante di completamento più chiaro.
- Recupero in una card verde dedicata con countdown, barra animata,
  −15/+15 secondi e pulsante per saltarlo.

### Notifiche acqua e movimento
- **Acqua:** tutti i giorni, orari locali 08:30, 09:00, 09:30, ... 18:00.
- **Movimento:** tutti i giorni dalle 09:30 alle 17:30, ogni ora.
  Il primo invito a muoversi arriva un'ora dopo l'inizio della fascia 08:30.
- Due **Switch** autonomi in Home > Promemoria, persistenti dopo riavvio
  e normale aggiornamento APK. Entrambi sono attivi per default.
- Si richiede `POST_NOTIFICATIONS` su Android 13+ al primo avvio
  della versione con promemoria. Puoi negare il permesso e riattivarlo
  in seguito dalle impostazioni Android, senza compromettere l'app.
- Scheduler Android Jetpack WorkManager, senza servizio sempre in esecuzione:
  notifica *inexact*, potenzialmente ritardata o saltata da Doze,
  ottimizzazioni del produttore o batteria. Gli slot rimangono allineati
  all'orologio locale, non a quando viene aperta l'app.
- Nessun promemoria notturno, nessuna notifica retroattiva se WorkManager
  riparte molto tardi. Notifiche separate per acqua e movimento.
- Gli orari sono applicati **anche il sabato e la domenica**.
  Attualmente non esiste l'impostazione "solo giorni lavorativi".
- Installazione come aggiornamento con `versionCode=17`, `versionName=0.9.5`
  e firma stabile già configurata su GitHub Actions.

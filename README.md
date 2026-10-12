# Ubicora — Android nativo 4.12.0

> **4.12.0:** archivio cifrato sul telefono (SQLCipher AES-256, chiave nel portachiavi sicuro di Android) con migrazione automatica verificata. Dettagli: [CHANGELOG 4.12.0](docs/CHANGELOG_4_12_0.md).
>
> **4.11.0:** QR di collegamento con codice usa e getta, password della squadra nel portachiavi sicuro di Android, limiti sulla rete del centrale. Dettagli: [CHANGELOG 4.11.0](docs/CHANGELOG_4_11_0.md).
>
> **4.10.3:** il pulsante «Scansiona QR» non copre più l'ultimo elemento delle liste. Dettagli: [CHANGELOG 4.10.3](docs/CHANGELOG_4_10_3.md).
>
> **4.10.2:** prelievo a raffica che resta attivo, contatore sincronizzazione dopo gli spostamenti, carichi e prelievi più veloci. Dettagli: [CHANGELOG 4.10.2](docs/CHANGELOG_4_10_2.md).
>
> **4.10.1:** barre di sistema coerenti con il tema chiaro; prelievo a raffica con giacenze sotto 1 unità. Dettagli: [CHANGELOG 4.10.1](docs/CHANGELOG_4_10_1.md).
>
> **4.10.0:** collegamento con QR, sincronizza dalla Panoramica, prelievo a raffica, lista da ordinare da condividere, modalità guanti, tema chiaro, preferiti, screenshot consentibili. Dettagli: [CHANGELOG 4.10.0](docs/CHANGELOG_4_10_0.md).
>
> **4.9.1:** correzioni alla sincronizzazione: lettera prenotata al primo collegamento, password controllata dal centrale, articoli non validi come conflitti. Dettagli: [CHANGELOG 4.9.1](docs/CHANGELOG_4_9_1.md).
>
> **4.9.0:** sincronizzazione automatica: il telefono centrale apre una sua rete (Bluetooth + Wi-Fi diretto) e i satelliti vicini si collegano da soli, con animazione; reparti per operatore (categorie assegnate dal centrale). Dettagli: [CHANGELOG 4.9.0](docs/CHANGELOG_4_9_0.md).
>
> **4.8.1:** sincronizzazione più robusta: satellite rifiutato scollegabile e ricollegabile, nessun blocco con copie del magazzino già sul telefono o codici di versioni vecchie, dati articolo protetti sui satelliti. Dettagli: [CHANGELOG 4.8.1](docs/CHANGELOG_4_8_1.md).
>
> **4.8.0:** sincronizzazione tra telefoni senza server: un telefono centrale e più satelliti si scambiano file cifrati; giacenze unite sommando i movimenti, importazioni ripetibili senza doppioni. Dettagli: [CHANGELOG 4.8.0](docs/CHANGELOG_4_8_0.md).

> **4.7.5:** anagrafica iniziale da Excel: modello con menù a tendina e importazione CSV che accetta codici vuoti, file di Excel e righe vuote. Dettagli: [CHANGELOG 4.7.5](docs/CHANGELOG_4_7_5.md).

> **4.7.4:** unità di misura scelta da un menù, con le unità adatte alla categoria in cima. Dettagli: [CHANGELOG 4.7.4](docs/CHANGELOG_4_7_4.md).

> **4.7.3 / Web 1.0.6:** debug completo: Annulla della mappa sicuro (niente più elementi fuori pianta che bloccavano il ripristino del backup), pareti veloci senza tocchi persi, CSV Web sempre compatibile con Android. Dettagli: [CHANGELOG 4.7.3](docs/CHANGELOG_4_7_3.md).

> **4.7.2:** ordinamento articoli, ubicazione nella riga, carico/prelievo rapido ±1, barra «Annulla» dopo i movimenti, tasto Indietro tra le sezioni. Dettagli: [CHANGELOG 4.7.2](docs/CHANGELOG_4_7_2.md).

> **4.7.1 / Web 1.0.5:** le pareti si agganciano agli angoli e chiudono la forma da sole (quadrati, triangoli, colonne), CSV con ripiano e posizione, schede articolo a pagina intera. Dettagli: [CHANGELOG 4.7.1](docs/CHANGELOG_4_7_1.md).

> **4.7.0 / Web 1.0.4:** mappa con ripiani e posizioni, QR delle strutture, annulla/ripeti, viste a colori, PDF della pianta; editor mappa nel browser. Dettagli: [CHANGELOG 4.7.0](docs/CHANGELOG_4_7_0.md).

> **4.6.3 / Web 1.0.3:** correzioni di affidabilità e sicurezza (feed di sincronizzazione, blocco PIN progressivo, login web, CSV). Dettagli e limiti: [CHANGELOG 4.6.3](docs/CHANGELOG_4_6_3.md).

App locale per inventario di più magazzini e profili, con codici automatici per categoria, etichette QR, scanner QR, fotografie, movimenti, prestiti, planimetria metrica, statistiche manageriali e backup completo cifrato. L'app non richiede un account cloud per il funzionamento dell'inventario. La scansione AR utilizza Google Play Services per AR su dispositivi compatibili.

**Stato attuale:** versione di test. La cronologia movimenti è interrogabile a pagine da 100 record con filtri eseguiti direttamente in SQLite, senza troncare la ricerca alle ultime 250 registrazioni. La home conserva una lista recente ridotta per evitare di appesantire l'avvio. La versione 4.2.0 richiede una firma APK compatibile con la versione installata; le build debug GitHub usano certificati potenzialmente diversi a ogni run e non vanno considerate aggiornamenti sicuri dei dati esistenti. Non è legalmente certificata né pronta alla distribuzione commerciale. “Ubicora” è il nome visualizzato nell’app, ma la registrabilità del marchio deve essere ancora verificata; l'identificativo Android è ora `applicationId=com.ubicora.app`: viene installata come nuova app, separata dalla precedente. Trasferire dati tramite esportazione e ripristino backup cifrato.

## Enterprise 4.4 (offline-first, server-ready)
In modalità Aziendale: anagrafiche di centri di costo, fornitori, macchinari e rubrica ruoli informativa, acquisti collegati e ricevimento parziale. SQLite v8 e coda locale transazionale per le nuove anagrafiche. **Nessuna sincronizzazione automatica o autenticazione cloud è attiva.** Le predisposizioni per collegare un futuro servizio HTTPS e PostgreSQL sono descritte in [Enterprise 4.4 e server](docs/ENTERPRISE_4_4_SERVER.md). La rubrica ruoli non conferisce autorizzazioni senza server.

## Gestione operativa 4.5 e tracciabilità 4.6
Scorte riservate e quantità disponibili, prenotazioni con movimenti atomici, manutenzione per impianto e costi, lotti con scadenza, numeri di serie e scarichi tracciati. Schema SQLite v10, backup cifrato compatibile con versioni precedenti. Guida e limiti: [Pianificazione e tracciabilità 4.6](docs/PLANNING_4_6.md). La sincronizzazione multi-dispositivo e l'autenticazione centralizzata non sono ancora operative.

## Indicatori operativi 4.6
In Aziendale → Decisioni: stato completo degli acquisti, scorte disponibili dopo le prenotazioni, lotti scaduti/in scadenza, budget mensile su tutte le sedi del profilo, costi di manutenzione per impianto (7/30/90 giorni), quota dei conteggi confermati senza scostamenti. I grafici aprono la sezione corrispondente. I totali SQL non dipendono dalle ultime 250 righe delle liste. Prezzi mancanti sono segnalati, non sostituiti con costi inventati. Dettagli: [Indicatori manageriali](docs/MANAGER_INDICATORI_4_6.md).

## Backend e limiti attuali
Il codice FastAPI/PostgreSQL con OIDC è nel repository e ha test dedicati. Non è distribuito: login Android, sync bidirezionale dell'inventario, provisioning tenant e configurazione server restano da completare. Vedi [server](server/README.md). La firma release permanente e l'aggiornamento su telefono richiedono un keystore compatibile e una prova reale; nessun APK debug è presentato come aggiornamento sicuro.

## Build

- Android Gradle Plugin 8.10.1, Kotlin 2.1.20, Java 17, target/compile SDK 36, min SDK 26.
- `./gradlew testDebugUnitTest assembleDebug`
- Artefatto: `app/build/outputs/apk/debug/app-debug.apk`.
- CI: `.github/workflows/android-build.yml`, APK e distinta dipendenze runtime negli artifacts delle Actions.

## Informazioni legali

- **Componenti terzi:** `THIRD_PARTY_NOTICES.md` e `LICENSES/Apache-2.0.txt`, inoltre nella sezione in-app «Privacy e licenze».
- **ARCore:** condizioni Google indipendenti da Apache 2.0; prima scansione con disclosure obbligatoria.
- **Privacy:** `docs/PRIVACY_POLICY_DRAFT_IT.md` contiene SEGNAPOSTO da compilare prima della pubblicazione; non è un'informativa definitiva.
- **Provenienza:** `docs/PROVENANCE_AUDIT.md` documenta rischi e controlli mancanti.
- **Rilascio commerciale:** `docs/COMMERCIAL_RELEASE_CHECKLIST.md` elenca i passaggi obbligatori.

Nessuna licenza sul codice proprietario del prodotto viene concessa implicitamente con il file `LICENSES/Apache-2.0.txt`: la licenza Apache riguarda esclusivamente i componenti di terze parti applicabili. La titolarità dei sorgenti del progetto va formalmente confermata.

## Beta

Vedi [criteri di rilascio e firma](docs/BETA_RELEASE_GATE.md). Fino ai test su telefono la versione rimane sperimentale.

## Moduli manageriali 4.3.0 Alpha

In **Impostazioni → Modalità d'uso** è possibile attivare `Aziendale` per il singolo profilo. La home apre un nuovo **Centro operativo**: gestione delle richieste d'acquisto, conferma degli arrivi, inventari ciclici con approvazione, trasferimenti spedizione/ricevimento tra magazzini dello stesso profilo, interventi di manutenzione e consumo ricambi, budget mensile, stato fornitori, log locale e esportazione CSV/PDF. Il database è migrato in modo additivo da schema 6 a schema 7; il backup cifrato conserva i nuovi dati e può leggere gli archivi legacy.

Queste funzioni non offrono ruoli autenticati fra dipendenti diversi né una sincronizzazione multi-dispositivo. La versione 4.3 è in Alpha e necessita ancora di test su telefono e APK firmato stabilmente per aggiornamenti senza disinstallazione. Dettagli e limiti nella [guida manager](docs/MANAGER_4_3_GUIDA.md).

## Aggiornare Ubicora senza disinstallarla

Per gli aggiornamenti **non utilizzare** gli APK debug di GitHub Actions, perché i certificati possono cambiare tra esecuzioni. La pipeline di compilazione li etichetta esplicitamente come **non utilizzabili per gli aggiornamenti**.

Il workflow manuale [Ubicora Stable Signed Update](.github/workflows/ubicora-signed-update.yml) produce APK release firmati con una chiave privata persistente custodita nei GitHub Actions Secrets e ne verifica certificato, identificativo e versionCode prima della pubblicazione. Occorre configurare i secrets prima del primo rilascio: il workflow **non genera nuove chiavi**. Una nuova chiave non può aggiornare in-place un APK già installato con una firma diversa.

Leggi la [guida per firma permanente, verifica sul telefono e migrazione sicura](docs/AGGIORNAMENTI_APK_SENZA_DISINSTALLARE.md). Non disinstallare l'app attualmente in uso senza avere verificato un backup cifrato.

# Ubicora Web per PC

È disponibile [Ubicora Web](web/README.md): aprire `web/Ubicora.html` in Edge/Chrome per lavorare in locale, oppure usare il portale servito dal backend per uno spazio condiviso autenticato tra browser PC e telefono. Import CSV Android, inventario, planimetria 2D, acquisti, prenotazioni, manutenzioni e indicatori. L'archivio SQLite dell'APK resta separato; attivazione HTTPS/OIDC e parità completa sono requisiti distinti.


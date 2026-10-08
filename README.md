# Officina Stock 4.0 · Native Alpha

**Progetto Android nativo scritto in Kotlin/Jetpack Compose + Java per ARCore.**
Non contiene alcuna WebView, HTML, JavaScript o server web. Il progetto non modifica né importa i dati di Officina Stock 3.x.

## Funzioni implementate

- Creazione di profili locali con PIN 4–8 cifre (PBKDF2-SHA256 con salt, 150.000 iterazioni).
- Scelta del profilo ad ogni apertura e cambio profilo dalle impostazioni.
- Più magazzini per ogni profilo, con nome e località.
- Inventario SQLite: codice univoco per magazzino, categorie, giacenza, unità, minima, note e ricerca.
- Movimenti di carico e prelievo transazionali: impossibile portare la giacenza sotto zero.
- Prestiti ad operatori e restituzione con movimenti di magazzino corrispondenti.
- Dashboard Compose con scorte critiche, KPI e movimenti recenti.
- Mappa Compose Canvas: griglia, pareti manuali, trascinamento delle strutture, blocco, vista zoom/pan, ricerca contenuti, associazione articoli e rimozione.
- Scansione ARCore opzionale: rilevamento di piani verticali dal feed fotocamera, geometria proiettata su XZ, minimappa, salvataggio nel magazzino scelto. Il codice ARCore/OpenGL proviene dalla versione Android nativa 3.0.1 ed è stato adattato al nuovo package.
- Esportazione CSV tramite Storage Access Framework, prevenzione delle formule CSV.
- Dati demo *solo su comando dell'utente* in un magazzino vuoto.
- Interfaccia Material 3 dark completamente nativa; senza rete e permesso Internet.

## Limitazioni dichiarate

- **Non esiste ancora un APK 4.0 compilato e testato in questo pacchetto.** Nell'ambiente di generazione mancavano Android SDK/Gradle e la connessione al repository Maven/Gradle non era disponibile. I test di dominio, geometria e schema SQLite sono stati eseguiti senza SDK; compilazione Android, test strumentati e installazione fisica sono ancora da eseguire.
- Scanner barcode tramite fotocamera, fotografie, allegati e backup cifrato completo **non sono stati ancora migrati**. Il CSV è soltanto l'esportazione di inventario, non un backup totale.
- Il PIN è un controllo applicativo, **non cifra il database**. Il database resta nel sandbox privato di Android, mentre il file CSV esportato è in chiaro.
- La geometria ARCore è approssimativa: può non rilevare certe pareti; i piani non rappresentano automaticamente una planimetria chiusa e le scansioni multiple non vengono allineate nello stesso riferimento metrico.
- In v4 le coordinate di mappa sono normalizzate 0–100 (percentuale canvas), non misure certificate in metri. La scansione sostituisce soltanto le pareti precedentemente rilevate da ARCore, non le manuali.
- Non è una release di produzione: non utilizzarla per registrazioni industriali critiche senza collaudi in azienda.

## Compilare l'APK (Android Studio)

1. Installa Android Studio aggiornato, Android SDK Platform 35 e Android Build Tools 35.0.0.
2. Apri la cartella principale `OfficinaStock4Native` come progetto Android Studio.
3. Lascia completare **Gradle Sync**. Occorre una connessione Internet per scaricare le dipendenze la prima volta.
4. Usa **Build > Build APK(s)** oppure nel terminale `./gradlew :app:assembleDebug`.
5. APK creato in `app/build/outputs/apk/debug/app-debug.apk`. Si installa come **app separata** da Officina Stock 3.x (applicationId `com.officinastock.app`).
6. Per test: `./gradlew :app:testDebugUnitTest`. Test ARCore su telefono Android compatibile e con Google Play Services per AR installato.

### Compilazione automatizzata

Contiene `.github/workflows/android-build.yml`, eseguibile in **un repository GitHub dedicato**, per produrre un artifact `officina-stock-4-native-debug-apk` tramite GitHub Actions. Non copiare il progetto in repository non correlati.

## Tecnologie

- Kotlin 2.1.20, Jetpack Compose BOM 2025.04.01, Material 3
- AGP 8.9.2, Gradle 8.11.1, Java 17, minSdk 26, targetSdk 35
- SQLiteOpenHelper con foreign keys, transazioni e vincoli (in questa alpha più semplice di Room)
- ARCore 1.56.0, OpenGL ES 2.0, Android camera permission solo per AR

## Codice e punti di estensione

- `StockDb.kt`: schema SQLite, transazioni, inventario, prestiti, planimetrie, esportazione e demo.
- `StockViewModel.kt`: stato ed esecuzione background di operazioni sul DB.
- `Screens.kt`: onboarding, profili, dashboard, inventario, attività, impostazioni.
- `MapScreen.kt`: planimetria Compose nativa con gesti e strutture.
- `ARScanActivity.java`, `WallGeometry.java`: scanner ARCore nativo e geometria.
- `tests/`: test indipendenti dall'SDK; `app/src/test/`: test JVM da Gradle.

## Sicurezza e distribuzione

Versione e package nuovi: **nessuna dipendenza dalla firma delle 3.x e nessuna disinstallazione necessaria**. L'APK debug viene firmato da Android Studio o dalla pipeline CI. Non distribuire la variante debug come release pubblica senza ulteriori test, gestione della firma e revisione dei dati.

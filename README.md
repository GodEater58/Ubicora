# Ubicora — Android nativo 4.1.9 (build di test)

App locale per inventario di più magazzini e profili, con codici automatici per categoria, etichette QR, scanner QR, fotografie, movimenti, prestiti, planimetria metrica, statistiche manageriali e backup completo cifrato. L'app non richiede un account cloud per il funzionamento dell'inventario. La scansione AR utilizza Google Play Services per AR su dispositivi compatibili.

**Stato attuale:** versione di test. La cronologia movimenti è interrogabile a pagine da 100 record con filtri eseguiti direttamente in SQLite, senza troncare la ricerca alle ultime 250 registrazioni. La home conserva una lista recente ridotta per evitare di appesantire l'avvio. La versione 4.1.9 richiede una firma APK compatibile con la versione installata; le build debug GitHub usano certificati potenzialmente diversi a ogni run e non vanno considerate aggiornamenti sicuri dei dati esistenti. Non è legalmente certificata né pronta alla distribuzione commerciale. “Ubicora” è il nome visualizzato nell’app, ma la registrabilità del marchio deve essere ancora verificata; l'identificativo Android è ora `applicationId=com.ubicora.app`: viene installata come nuova app, separata dalla precedente. Trasferire dati tramite esportazione e ripristino backup cifrato.

## Build

- Android Gradle Plugin 8.9.2, Kotlin 2.1.20, Java 17, target/compile SDK 35, min SDK 26.
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

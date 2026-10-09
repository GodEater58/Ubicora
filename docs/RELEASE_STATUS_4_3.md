# Ubicora 4.3 Manager | stato di rilascio

**Base unica:** `main`; codice versione 430; ID Android `com.ubicora.app`; migrazione SQLite dalla 6 alla 7 senza reset intenzionale. Il ramo Manager è stato integrato nella PR #7 soltanto dopo una build CI verde.

## Un solo percorso di rilascio

1. Nuove funzioni e correzioni sempre su una branch da `main`, con un'unica PR aperta per la stessa area, mai sulla build debug da distribuire.
2. Eseguire lint, test JVM debug/release, controlli di compatibilità, prove su backup legacy e su cambio schema. Integrare in `main` **soltanto** dopo esito `success` dei check correnti. Non considerare un run annullato come verde.
3. `versionCode` deve aumentare a ogni nuovo APK distribuito. `applicationId` rimane invariato.
4. Usare soltanto `Ubicora Stable Signed Update` con keystore permanente esistente e fingerprint verificata. Gli APK `debug` di GitHub Actions non sono aggiornamenti affidabili. Nessuna nuova chiave improvvisata per installazioni già esistenti.
5. Prima di installare su un telefono con dati reali: backup cifrato verificato altrove, confronto del certificato e controllo della migrazione del database. Non disinstallare l'app vecchia.
6. Test manuali Android 17: login e profili, header/notch, mappa/zoom, QR, ARCore e fallback 2D, foto, CSV, ordini, trasferimenti, rettifica, esportazione/importazione backup. Anche Android 15/16 necessitano verifica.

## Test che NON equivalgono a certificazione

- CI `lint` + test JVM + assemble ≠ test automatici eseguiti su telefono fisico.
- Le workflow sperimentali di emulatori Android 15/16 nella PR #5 si sono interrotte senza report diagnostici sufficienti. Non sono state integrate e non certificano quei sistemi.
- Le nuove prove Robolectric (PR #10) verificano la migrazione schema, archivi legacy cifrati e restituzione delle foto; non sostituiscono una prova di aggiornamento con firma reale.
- Profilo locale offline ≠ account verificato su più dispositivi. Le approvazioni manageriali non forniscono controllo dei ruoli centralizzato.
- Le operazioni manageriali non comprendono ancora acquisti con consegne parziali, approvazioni con utenti verificati o sincronizzazione cloud.

**Blocchi per Beta:** [issue #4](https://github.com/GodEater58/OfficinaStock-Android/issues/4). Per gli APK aggiornabili consultare [la procedura](AGGIORNAMENTI_APK_SENZA_DISINSTALLARE.md).

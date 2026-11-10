# Stato dei test · Ubicora 4.15.0

Aggiornato il 10 novembre 2026. Sostituisce il rapporto della 4.0, conservato in fondo.

## Verificato in automatico (GitHub Actions, a ogni pull request)

| Controllo | Esito sulla 4.15.0 |
|---|---|
| Controlli di struttura in Python (`tests/`) | Passano |
| Lint Android | Nessun errore |
| Test JVM/Robolectric, varianti debug e release (180 casi `@Test` nel sorgente) | Passano |
| Compilazione APK debug, release e test strumentali | Riuscita |

Coperti da test nella 4.15: quantità e letture ripetute del prelievo a raffica, prelievo guidato (ordine, guida sulla mappa, conferma con etichetta), prestiti di squadra, lettera del telefono sui movimenti, sostituzione del centrale, testo dei tag NFC, verifica scaffale.

## Non verificato

Nessuna prova su un telefono è registrata per la 4.15. Restano da collaudare a mano:

- fotocamera integrata, torcia, suono e vibrazione nel prelievo a raffica e nella verifica scaffale;
- scorrimento sulle righe, pressione lunga e pannello dal basso, anche con i guanti;
- doppio tocco, modalità giro e tratto del percorso sulla mappa;
- lettura e scrittura dei tag NFC, in particolare su utensili metallici;
- scambio tra telefoni reali con prestiti, e sostituzione del centrale;
- i criteri di `BETA_RELEASE_GATE.md`: tre dispositivi, ripristino di un backup su un secondo telefono, 14 giorni di uso.

I due test strumentali (`EncryptedArchiveTest`, `ProfileGateUiTest`) vengono compilati; l'archivio cifrato è eseguito su emulatore dal workflow dedicato.

---

## Rapporto storico · Officina Stock 4.0 Native Alpha

Data: 8 ottobre 2026.

## Verifiche eseguite in questo ambiente

- [PASS] Test di dominio compilati ed eseguiti con `kotlinc` e `java`: 8 controlli su SKU, quantità, clamp coordinate, segmenti degeneri.
- [PASS] Test di geometria compilati ed eseguiti con `javac` e `java`: 5 controlli su fusione segmenti, separazione muri paralleli, segmenti troppo brevi, sviluppo lineare.
- [PASS] Test Python su SQLite con schema DDL estratto dal sorgente Kotlin: 7 tabelle, 4 indici, blocco duplicati, vincoli quantità, cascata e SET NULL su ubicazioni.
- [PASS] Kotlin parser: nessun errore di sintassi (`expecting`/`unexpected tokens`) rilevato durante analisi di file `.kt` senza librerie Android.
- [PASS] Esame del manifest: nuovo applicationId, ARCore optional, nessun `INTERNET`, nessuna WebView.

## Verifiche da fare quando è disponibile Android SDK

- [NOT RUN] `:app:assembleDebug` e test JVM Gradle; tentativo bloccato da impossibilità di risolvere `services.gradle.org`.
- [NOT RUN] Installazione APK fisica e test di navigazione Compose.
- [NOT RUN] Stress test inserimento 10.000 articoli in ambiente Android e verifica rendering/memoria.
- [NOT RUN] Prova reale ARCore: permessi, texture, tracking, rotazione, salvataggio ed errore di camera occupata.
- [NOT RUN] Accessibilità, performance e stato dopo interruzione processo.
- [NOT RUN] Verifica visuale Android 15/16/17 con gesture navigation e tastiera.

**Esito: sorgenti alpha implementati; NON qualificati come APK collaudato.**

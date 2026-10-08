# Test report · Officina Stock 4.0 Native Alpha

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

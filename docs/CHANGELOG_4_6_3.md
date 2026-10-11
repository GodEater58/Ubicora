# Ubicora 4.6.3 / Web 1.0.3 — Correzioni di affidabilità e sicurezza

Data: 11/10/2026. Base: 4.6.2, commit `7574f19`. Nessuna funzione nuova: solo difetti corretti e test di regressione.

## Riepilogo

| # | Parte | Priorità | Difetto | Correzione | Verifica |
|---|---|---|---|---|---|
| 1 | Server | **P1** | Il feed `/v1/sync/changes` poteva **saltare per sempre** un evento. `accepted_at` era `now()`, cioè l'ora di *inizio* transazione: un writer partito prima ma confermato dopo finiva dietro il cursore di un dispositivo che aveva già letto. | `accepted_at` = `clock_timestamp()` preso **dopo** il lock consultivo per tenant: ordine di commit = ordine del cursore. | `test_late_commit_is_never_skipped_by_change_feed`: fallisce con il codice 4.6.2, passa con 4.6.3 (PostgreSQL 16 reale). |
| 2 | Server | **P1** | `POST /v1/sync/events` e `PUT /v1/web/workspace` erano `async` ma eseguivano psycopg sincrono sull'event loop: una query lenta bloccava **tutte** le richieste del server. | Lavoro DB spostato in `run_in_threadpool`; logica estratta in `store_batch()`. | `test_push_does_not_block_event_loop` + suite completa. |
| 3 | Server | P2 | Si poteva eliminare un centro di costo ancora collegato a macchinari (`center_uuid` orfano). | `409 Cost center still referenced by assets`. | `test_cost_center_with_assets_cannot_be_deleted`. |
| 4 | Server | P3 | Cursore con offset `+00:00` non codificato nell'URL (il `+` diventa spazio) → `400`. | `parse_cursor()` tollerante; cursore limitato a 128 caratteri. | `test_cursor_accepts_unencoded_plus_sign`. |
| 5 | Server | P3 | Mancavano HSTS, `frame-ancestors`, `Permissions-Policy`. `Content-Length` del workspace non pre-verificato. | Header aggiunti (non toccano gli script: la CSP con hash resta nella pagina). Rifiuto anticipato `413`. | Suite + test browser. |
| 6 | Web | **P1** | Un login aziendale **annullato o fallito** (`?error=…`, token non JSON) faceva mostrare «Impossibile aprire l'archivio» e disattivava i pulsanti: anche l'archivio **locale** diventava inutilizzabile. | Errore del callback OIDC isolato: messaggio e archivio locale intatto. | `resilience-browser.cjs` (Playwright/Chromium): fallisce con 1.0.2, passa con 1.0.3. |
| 7 | Web | P2 | Un salvataggio condiviso rifiutato in modo definitivo (403 ruolo revocato, 400, 413) restava «in sospeso» per sempre: «Risolvi salvataggio» riprovava all'infinito. | Questi codici sono trattati come conflitto: esporta la copia non confermata e ricarica dal server (sola lettura se il ruolo è cambiato). | `resilience-browser.cjs`. |
| 8 | Web + Android | P2 | L'apostrofo anti-formula aggiunto all'export CSV (`'-10% sconto`) restava nei dati dopo l'import: ogni giro Android ↔ Web corrompeva note, fornitori, ubicazioni e descrizioni. | Rimosso all'import solo se seguito da `= + - @ TAB CR`; un apostrofo normale resta. | Web: test core. Android: `CsvImportTest.formulaApostropheAddedOnExportIsRemovedOnImport` (**da eseguire in CI**). |
| 9 | Android | **P1** | Blocco PIN fisso di 1 minuto dopo 5 errori = 7.200 tentativi al giorno: un PIN a 4 cifre cadeva in circa un giorno e mezzo. | Attesa crescente per blocchi consecutivi: 1, 2, 4, 8, 16, 32, poi 60 minuti. Il PIN corretto azzera. Con 5 tentativi l'ora servono mesi. | `PinThrottleTest` (**da eseguire in CI**); `DatabaseAuditTest` invariato. |
| 10 | Web | P3 | Il backup web si chiamava sempre `Ubicora_Web_1.0.1.json`. | Nome con data: `Ubicora_Web_backup_AAAA-MM-GG.json`. | — |

## Esiti dei test in questa sessione

| Suite | Esito |
|---|---|
| Server pytest con PostgreSQL 16 reale (fixture identica alla CI) | **36 passati, 0 falliti, 0 saltati** (erano 32, di cui 3 saltati senza database) |
| Web `core.test.mjs` | **25 passati** |
| Web browser Playwright: `browser.cjs`, `shared-browser.cjs`, `resilience-browser.cjs` | **3/3 passati** |
| 12 script Python di contratto statico | **12/12 passati** |
| Android Gradle (lint, unit test, build APK) | **NON eseguito**: in questa sessione l'SDK Android e i repository Google/Maven non erano raggiungibili. |

## Limiti, da leggere

- Le modifiche Kotlin (`PinThrottle.kt`, `StockDb.authenticate`, `CsvImport.kt`) sono state revisionate ma **non compilate**. Prima di distribuire: workflow `android-build.yml` oppure `gradlew lint testDebugUnitTest assembleDebug` in locale.
- **Nessun nuovo APK** nel pacchetto: quello incluso è il 4.6.2 debug originale. Per un APK 4.6.3 che aggiorna senza perdere dati serve il workflow «Ubicora Stable Signed Update» con la chiave originale (vedi `AGGIORNAMENTI_APK_SENZA_DISINSTALLARE.md`).
- I database esistenti non vanno migrati: le correzioni del server usano le tabelle invariate; gli eventi già registrati mantengono il loro `accepted_at`.
- Restano aperti i limiti elencati in `AUDIT_2026-10-08.md` (database Android non cifrato a riposo, test su dispositivo, ecc.).

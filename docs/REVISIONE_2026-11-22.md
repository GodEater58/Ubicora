# Revisione completa 22/11/2026 (versione 4.24.0)

## Verdetto
Tenere l'architettura con correzioni mirate: **non serve reinstallare da zero**. I backup delle versioni vecchie (4.1.2+) si ripristinano sulla 4.24 (test automatici presenti). Corretto sul branch `fix/4.24.1-backup`: il backup ora salva anche le modifiche alle schede non validate; il ripristino svuota i prestiti visti dagli altri telefoni.

## Architettura e database (agente android-architect)

| # | Problema | Rischio | Intervento minimo | Compatibilità |
|---|---|---|---|---|
| 1 | Backup: limite 32 MiB / 300.000 righe per tabella (`BackupArchive.kt:36-37`); il ripristino carica tutto in memoria | Con l'uso, la tabella movimenti supera il limite e il backup smette di funzionare (la 4.22 permette di cancellare i movimenti con più di 2 anni) | Tabelle grandi spezzate in più parti nello stesso file, lette un pezzo alla volta | I backup nuovi non si aprono sulle versioni vecchie |
| 2 | Schema fermo alla versione 12 dalla 4.8; le aggiunte successive girano in `onOpen` fuori da transazione (`StockDb.kt:76-86`, `TeamSyncSchema.kt`) | Il numero di versione non descrive lo schema vero; nessuna migrazione non additiva possibile | Versione 13, comandi in `if(oldVersion<13)`, `onOpen` in transazione; regola: ogni modifica = +1 | Nessuna migrazione dei dati |
| 3 | La regola "cambia la giacenza" è copiata in 5 punti (`StockDb.kt:544,555`, `OperationsDb.kt:145`, `StockPlanningDb.kt:119`, `StockTraceDb.kt:124`); il satellite riscrive la quantità saltando prenotazioni/lotti (`TeamSync.kt:989-1003`) | Un controllo aggiunto in un solo punto porta a giacenze sbagliate | Una sola funzione in `StockDb` | Nessuno |
| 4 | Il satellite cancella gli articoli che il centrale non ha più (`TeamSync.kt:1011-1017`), con il loro storico | Se il centrale ripristina un backup vecchio, i satelliti perdono gli articoli creati dopo | Identificativo del centrale che cambia a ogni ripristino; il satellite chiede conferma prima di cancellare | Campo facoltativo |
| 5 | Protocollo di sync sempre "versione 1" (`TeamSync.kt:912,1211`) | Telefoni con versioni diverse ignorano in silenzio i dati nuovi | Versione dell'app nel file + avviso se i telefoni non sono allineati | Compatibile |
| 6 | Il database vive e muore con il ViewModel (`StockViewModel.kt:19,1113`) | Chiudere l'app durante un ripristino o una sync può far crashare (i dati restano integri) | Un unico `StockDb` per tutta l'app | Nessuno |
| 7 | `StockViewModel` 1.194 righe; mentre l'app è occupata i tocchi vengono ignorati senza avviso | L'operatore può credere di aver prelevato | Messaggio "Riprova tra un momento"; dividere col tempo | Nessuno |
| 8 | Doppio `endTransaction` in `importState` (`TeamSync.kt:934-938`) | Errore mostrato anche se i dati sono salvati | Una riga | Nessuno |

Ordine consigliato: 6+8, 2 (+ test di migrazione con database veri v6/v11/v12), 3, 4, 5, poi 1 (rompe la compatibilità del backup verso le versioni vecchie: va annunciato), infine 7.

Non controllati: schermate Compose, `TeamLink.kt`, query che leggono colonne per posizione.

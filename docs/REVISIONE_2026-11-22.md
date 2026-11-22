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

## Bug e sicurezza (revisore Kotlin/sicurezza): 11 problemi verificati leggendo il codice

| # | Gravità | Problema | Dove | Correzione minima |
|---|---|---|---|---|
| 1 | Critico | Ripristinando il backup di un satellite su un telefono nuovo, il numeratore delle modifiche riparte da 1: il centrale scarta i nuovi movimenti come «già importati» e poi il satellite li perde | `BackupArchive.kt:262-271`, `TeamSync.kt:765,1028` | Dopo il ripristino alzare il numeratore di `team_sync_outbox` in `sqlite_sequence` (es. all'ora in ms) |
| 2 | Alto | Scambiando i codici di due articoli sul centrale, il satellite incontra un codice doppio e ogni aggiornamento fallisce per sempre | `TeamSync.kt:989-1001`, `StockDb.kt:469-473` | Prima di scrivere il codice nuovo, dare un codice provvisorio all'articolo locale che lo occupa |
| 3 | Alto | Backup che si esporta senza errori ma poi non si ripristina (prenotazioni oltre la giacenza dopo una sync, articolo su un ripiano che non esiste più) | `TeamSync.kt:987-1003,1131,1147-1149`, `StockDb.kt:1189-1191,1236-1256` | `validateRestoredData()` prima dell'esportazione; togliere il ripiano agli articoli oltre i ripiani della struttura |
| 4 | Medio | Trasferimento tra sedi applicato a metà sul centrale se una delle due parti viene rifiutata | `TeamSync.kt:787-800`, `StockDb.kt:611-612` | Se l'uscita fallisce, saltare anche l'entrata collegata |
| 5 | Medio | «Salva articolo» può creare l'articolo due volte se la collocazione fallisce | `StockViewModel.kt:655-661` | Salvataggio e collocazione nella stessa transazione |
| 6 | Medio | Il centrale può inviare un aggiornamento incoerente se riceve un file mentre lo prepara | `TeamSync.kt:688-706` | `exportState`/`exportChanges` dentro una transazione |
| 7 | Medio | Uno sconosciuto vicino può esaurire la memoria del centrale (6 × 24 MB in parallelo) | `TeamLink.kt:131-137,171-174,338-346` | Leggere un messaggio alla volta o abbassare il limite |
| 8 | Basso | Collegandosi con la password, il primo «centrale» che risponde riceve una prova della password | `TeamLink.kt:236-242` | Confermare il nome del centrale o mettere il codice del magazzino nel QR |
| 9 | Basso | Un file con modifiche fuori ordine può far contare due volte i movimenti | `TeamSync.kt:818` | `processed = maxOf(processed, seq)` |
| 10 | Basso | Il centrale può eliminare un articolo prestato da un satellite: il prestito si perde | `StockDb.kt:339-347`, `TeamSync.kt:1011-1016` | `hasOpenWork` controlla anche `team_loans` |
| 11 | Basso | Il limite di reparto si aggira con una nota che inizia con «Annullato» o «Restituito» (serve un file costruito a mano) | `TeamSync.kt:791-792` | Riconoscere i resi dai movimenti originali |

Senza problemi: backup cifrato (BackupCipher), cifratura dell'archivio (DbCipher), componenti esposti nel manifest, FileProvider, CSV, biometria, codice di invito.

## Priorità
1. Bug 1, 2, 3: da correggere prima di consegnare la prossima versione.
2. Architettura 6 e 8, poi 2, 3 e 4 (insieme ai bug 4, 6, 9, 10).
3. Bug 5 e 7, architettura 5.
4. Backup a pezzi (architettura 1): da annunciare, perché i backup nuovi non si aprono sulle versioni vecchie.

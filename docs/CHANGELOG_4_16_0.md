# Ubicora 4.16.0 / Web 1.1.0 — foto di squadra, squadra sul PC, più codici per fotogramma

## Novità
| Funzione | Dettagli |
|---|---|
| **Foto nella sincronizzazione** | Le foto degli articoli seguono il centrale. Il satellite chiede quelle che gli mancano e le riceve con l'aggiornamento successivo (al massimo 60 foto e 8 MB per scambio, il resto agli scambi seguenti). La foto di un articolo creato su un satellite arriva al centrale insieme all'articolo |
| **Verifica scaffale: più codici insieme** | La fotocamera legge tutti i codici QR presenti nel fotogramma, non uno alla volta |
| **Ubicora Web 1.1.0: il magazzino della squadra sul PC** | «Dati e connessione» › «Apri aggiornamento della squadra»: il file esportato dal telefono centrale, aperto con la password della squadra, diventa una nuova sede con pianta, strutture, articoli e giacenze. È una copia da consultare: dal PC non torna nulla ai telefoni |

## Correzioni alla 4.15
| Problema | Correzione |
|---|---|
| Lo scorrimento su una riga scattava con un movimento breve del pollice | Serve trascinare la riga per circa un terzo della larghezza |
| Durante il prelievo guidato sotto la pianta restava la riga con «Rilievo AR» | In modalità giro resta solo la pianta |
| Dopo la sostituzione del centrale restavano in elenco i prestiti fatti dal vecchio centrale, senza modo di chiuderli | Escono dall'elenco alla sostituzione |

## Comportamento da conoscere
- Foto: servono due scambi perché una foto nuova arrivi su un satellite (al primo scopre che gli manca, al secondo la riceve). Una foto oltre 2 MB non viaggia. Una foto scattata su un satellite non viene mai cancellata da un aggiornamento: se il centrale non ha una foto di quell'articolo la riceve dal satellite, se ne ha già una vale quella del centrale. Una foto tolta sul centrale sparisce dai satelliti solo se l'avevano ricevuta da lui.
- Le foto viaggiano solo tra telefoni con la 4.16; con una versione precedente lo scambio funziona come prima, senza foto.
- Il PC legge lo stato della squadra ma non è un satellite: non invia carichi o prelievi ai telefoni, e non mostra foto, prestiti e storico.

## Per chi lavora sul codice
- `Screens.kt` (1.000 righe) è diviso in cinque file per area, senza modifiche al contenuto: `ProfileScreens.kt`, `InventoryScreen.kt`, `ItemScreens.kt`, `MovementsScreen.kt`, `SettingsScreen.kt`.
- `docs/SCHEMA.md` è l'indice unico delle tabelle e di dove sono definite, tenuto allineato da un controllo automatico.
- Non fatto: `StockDb.kt`, `StockViewModel.kt`, `TeamSync.kt` e `MapScreen.kt` restano file unici (sono classi o funzioni uniche: dividerle richiede di riscriverle, non di spostarle); i testi restano nel codice e non in `strings.xml`.

## Da provare
Verificato con i test automatici, non su dispositivi: scambio di foto tra telefoni reali (tempi e dimensioni via Bluetooth), verifica scaffale con più etichette nell'inquadratura, apertura sul PC di un file esportato da un telefono vero.

Test: `TeamSyncTest` (foto richieste e consegnate, foto di un articolo nuovo), `WebTeamFileTest` (file scritto dal browser aperto dal cifrario del telefono), `web/tests/core.test.mjs` (formato, password errata, manomissione, importazione della sede).

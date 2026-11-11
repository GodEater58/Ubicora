# Ubicora 4.16.1 — correzioni dopo la revisione del codice

## Importante
La 4.16.0 può cancellare le foto scattate su un satellite: al primo aggiornamento del centrale, un articolo di cui il centrale non ha la foto perdeva quella fatta sul satellite. **Installa la 4.16.1 al posto della 4.16.0 su tutti i telefoni**; se la 4.16.0 è già su un satellite, aggiornalo prima della sincronizzazione successiva.

## Correzioni
| Problema | Correzione |
|---|---|
| Un aggiornamento del centrale cancellava le foto scattate su un satellite (4.16.0) | Una foto scattata su un satellite non viene mai cancellata: se il centrale non ha una foto di quell'articolo la riceve dal satellite, se ne ha già una vale quella del centrale. Una foto tolta sul centrale sparisce dai satelliti solo se l'avevano ricevuta da lui |
| Lo scorrimento su una riga scattava con un movimento breve del pollice (4.15) | Serve trascinare la riga per circa un terzo della larghezza |
| Durante il prelievo guidato sotto la pianta restava la riga con «Rilievo AR» (4.15) | In modalità giro resta solo la pianta |
| Dopo la sostituzione del centrale restavano in elenco i prestiti fatti dal vecchio centrale, senza modo di chiuderli (4.15) | Escono dall'elenco alla sostituzione |

Test: `TeamSyncTest.photoTakenOnASatelliteIsKeptAndOfferedToTheCentral`.

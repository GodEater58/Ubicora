# Ubicora 4.10.2 — correzioni e velocità

| Tipo | Cosa | Effetto |
|---|---|---|
| 🔴 Bug | Prelievo a raffica: dopo il primo prelievo il ricaricamento dei dati spegneva la modalità, e lo scanner non si riapriva | Ora resta attiva fino a «Fine» o Indietro (test automatico dedicato) |
| 🟡 Bug | Sul satellite, spostando un articolo sulla mappa il contatore «modifiche da inviare» non si aggiornava subito | Aggiornato insieme alla mappa |
| ⚡ Velocità | Dopo carico, prelievo, ±1, annulla, prestito e restituzione l'app rileggeva tutto l'archivio (ordini, lotti, anagrafiche…) | Rilegge solo articoli, movimenti, prestiti, statistiche e sincronizzazione: meno attesa con magazzini grandi |
| ⚡ Velocità | Ricerche per struttura e per pareti senza indice | Due indici nuovi, creati da soli all'apertura (nessuna migrazione) |

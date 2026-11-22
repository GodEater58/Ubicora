# Ubicora 4.25.0: solidità

| Prima | Ora |
|---|---|
| Ogni schermata principale apriva il suo archivio e lo chiudeva uscendo, anche se un ripristino o uno scambio stava ancora scrivendo | **Un solo archivio** per tutta l'app, mai chiuso mentre si lavora |
| Il numero di versione del database era fermo dalla 4.8 e le aggiunte successive giravano a ogni avvio | **Versione 13**: le aggiunte della 4.10–4.24 sono una migrazione vera, l'apertura è tutto-o-niente |
| La regola che cambia la giacenza era copiata in cinque punti | **Una sola funzione** (prenotazioni e lotti controllati sempre allo stesso modo) |
| Se il centrale ripristinava un backup vecchio, i satelliti cancellavano subito gli articoli creati dopo | Il satellite **non cancella nulla** e mostra un conflitto; dopo «Ho controllato» si allinea |
| Un trasferimento tra sedi poteva arrivare a metà sul centrale (pezzi in più o spariti) | **Tutto o niente**: entrata rifiutata se l'uscita non passa, uscita annullata se l'entrata non passa |
| Backup con password di 8 caratteri | I nuovi backup chiedono **12 caratteri** (quelli vecchi si aprono ancora) |
| Nessun limite ai tentativi sbagliati verso il centrale | Dopo 5 file rifiutati in 5 minuti la rete del centrale si ferma **2 minuti** |
| Scaffale di fronte con caselle semplici | Foto dell'articolo, bordo giallo se sta finendo, quantità in grande, «Metti qui» sui posti liberi |
| Nel nuovo articolo il posto era solo un pulsante | Accanto c'è la **piantina della sede** con lo scaffale scelto acceso |

Attenzione: un database aperto dalla 4.25 non si apre con un'app più vecchia (versione 13). Prima di aggiornare fai un backup completo.

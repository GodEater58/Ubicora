# Ubicora 4.8.1 — sincronizzazione più robusta

Correzioni emerse dal controllo della 4.8.0 (nessuna modifica al database).

| Problema | Correzione |
|---|---|
| Un satellite rifiutato dal centrale (es. lettera già usata) non poteva scollegarsi finché aveva modifiche non inviate | «Scollega comunque», con avviso che le modifiche restano solo sul telefono |
| Dopo uno scollegamento il telefono perdeva la sua identità: ricollegandosi con la stessa lettera veniva rifiutato, e il centrale poteva bloccarsi con «Manca un invio precedente» | L'identità del telefono resta; i file sono cumulativi, quindi un salto di numerazione non blocca più |
| Se sul telefono c'era già una copia del magazzino del centrale (backup ripristinato), il collegamento falliva o aggiornava la copia sbagliata | Gli articoli si cercano solo nel magazzino sincronizzato; la copia vecchia riceve nuovi identificativi |
| Codici articolo di versioni vecchie (formato diverso da RIC-000001) bloccavano il collegamento | Accettato qualunque codice non vuoto |
| Sul satellite le modifiche a nome, categoria, scorta minima e dati d'acquisto venivano sovrascritte in silenzio al successivo aggiornamento | Bloccate con un messaggio chiaro: si cambiano dal centrale. Restano modificabili gli articoli creati sul satellite e non ancora inviati |

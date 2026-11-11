# Ubicora 4.19.2 / Web 1.1.1 — correzioni dalla revisione del codice

Comprende la 4.19.1.

| Problema | Correzione |
|---|---|
| Un telefono «Solo prelievo» vedeva il tasto «Nuovo articolo»: compilava il modulo e veniva rifiutato solo alla fine | Il tasto non compare. In Squadra il telefono legge «Permessi: solo prelievo e prestito» invece di un reparto con un nome strano |
| Da un satellite si poteva spostare un pezzo verso una sede non condivisa con la squadra: il centrale vedeva l'uscita ma non l'entrata | Da un satellite si sposta solo tra sedi della squadra |
| Dopo la sostituzione del centrale le etichette stampate dal vecchio centrale non venivano più riconosciute | Il nuovo centrale e i satelliti ricordano i numeri di sede delle etichette già stampate |
| Ubicora Web apriva solo la prima sede di un aggiornamento della squadra con più sedi | Aggiunge tutte le sedi condivise |

Test: `TeamSyncTest` (etichette dopo la sostituzione del centrale), `web/tests/core.test.mjs` (aggiornamento con due sedi). Non verificato su dispositivo.

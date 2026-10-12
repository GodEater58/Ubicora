# Ubicora 4.7.2 — comodità d'uso

## Lista articoli
- **Ordinamento**: chip «Ordina» con Nome A–Z, Critici prima (i più esauriti in cima), Quantità crescente/decrescente, Ultimi movimentati, Codice.
- **Ubicazione nella riga**: sotto il nome compare dove si trova l'articolo (es. «Scaffale A · R2 P3»); in arancione «Da assegnare» se non ha ubicazione.
- **Carico/prelievo rapido**: pulsanti −1 / +1 su ogni riga, senza aprire la scheda. Registrano un movimento tracciato («Carico rapido» / «Prelievo rapido»); il −1 è disattivato sotto 1 unità.

## Annulla movimento
- Dopo un carico o un prelievo (dalla scheda o dai pulsanti rapidi) compare per 6 secondi una barra con **Annulla**.
- Annulla non cancella la storia: registra il movimento opposto («Annullato: …»), quindi il registro resta completo.
- Se nel frattempo la merce è già stata usata (es. annullare un carico dopo averla prelevata), l'annullamento viene rifiutato e la giacenza non va mai sotto zero.
- Rettifiche e prestiti non mostrano Annulla: hanno già i loro passaggi dedicati.

## Tasto Indietro
- Indietro torna alla sezione precedente (fino a 12 passi) e poi alla Panoramica, invece di chiudere l'app.
- Sulla mappa, mentre disegni pareti, Indietro termina la catena di pareti.

## Verifiche
- Test unitari su ordinamenti, testo della barra Annulla, annullamento come movimento opposto e annullamento rifiutato senza giacenza.

## Limiti
- Non provato su telefono reale in questa sessione.
- L'ordine «Ultimi movimentati» usa i movimenti caricati nell'app (gli ultimi visibili in Movimenti).

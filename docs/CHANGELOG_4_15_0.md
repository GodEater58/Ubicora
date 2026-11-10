# Ubicora 4.15.0 — prelievo a raffica in una sola schermata

## Novità
| Funzione | Dettagli |
|---|---|
| **Prelievo a raffica in una schermata** | La fotocamera resta accesa nella metà alta; articolo, ubicazione, quantità e «Preleva» sono in basso, a portata di pollice. Non si apre più uno scanner separato a ogni articolo |
| **Suono e vibrazione** | Ogni lettura risponde con una vibrazione e un suono: uno per l'etichetta letta, uno diverso per un'etichetta non valida o per la giacenza esaurita. Il suono si spegne dall'icona in alto (la vibrazione resta) |
| **Stessa etichetta = un pezzo in più** | Allontana il telefono dall'etichetta e inquadrala di nuovo: la quantità sale di uno, fino a quanto c'è a scaffale. Tenere il telefono fermo sull'etichetta non aggiunge nulla |
| **Cornice che conferma** | La cornice di mira diventa verde con il codice dell'articolo letto, rossa se l'etichetta non vale |
| **Torcia** | Icona in alto a destra nella schermata di prelievo |
| **«Annulla» in basso** | La barra «Annulla» dopo un carico o un prelievo compare sopra la barra di navigazione, non più in alto. Anche i prelievi a raffica si possono annullare |

## Comportamento da conoscere
- Mentre un articolo aspetta la conferma, le etichette di **altri** articoli vengono ignorate: premi «Preleva» o «Salta» prima di passare al successivo. Evita che un'etichetta vicina sostituisca quella giusta.
- Lo scanner a schermo intero resta per le altre letture (scheda articolo, strutture, QR della squadra).

## Da provare sul telefono
Questa versione è verificata con i test automatici, non su un dispositivo: fotocamera, torcia, suono, vibrazione e la pausa di 2 secondi tra due letture della stessa etichetta vanno collaudati in officina.

Test: `BurstPickingTest` (stessa etichetta, etichetta di un altro articolo, annullamento), `BurstScanLogicTest` (quantità e filtro delle letture ripetute).

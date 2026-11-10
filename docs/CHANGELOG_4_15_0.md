# Ubicora 4.15.0 — nuova esperienza d'uso: prelievo, lista articoli, mappa

## Novità
| Funzione | Dettagli |
|---|---|
| **Prelievo a raffica in una schermata** | La fotocamera resta accesa nella metà alta; articolo, ubicazione, quantità e «Preleva» sono in basso, a portata di pollice. Non si apre più uno scanner separato a ogni articolo |
| **Suono e vibrazione** | Ogni lettura risponde con una vibrazione e un suono: uno per l'etichetta letta, uno diverso per un'etichetta non valida o per la giacenza esaurita. Il suono si spegne dall'icona in alto (la vibrazione resta) |
| **Stessa etichetta = un pezzo in più** | Allontana il telefono dall'etichetta e inquadrala di nuovo: la quantità sale di uno, fino a quanto c'è a scaffale. Tenere il telefono fermo sull'etichetta non aggiunge nulla |
| **Cornice che conferma** | La cornice di mira diventa verde con il codice dell'articolo letto, rossa se l'etichetta non vale |
| **Torcia** | Icona in alto a destra nella schermata di prelievo |
| **«Annulla» in basso** | La barra «Annulla» dopo un carico o un prelievo compare sopra la barra di navigazione, non più in alto. Anche i prelievi a raffica si possono annullare |
| **Scorri sulla riga** | Nella lista Articoli: a destra preleva 1, a sinistra carica 1 (con «Annulla» in basso) |
| **Tieni premuto** | Sulla riga: pannello dal basso con preleva/carica una quantità, mostra in pianta, sposta sulla mappa, lista di prelievo, etichetta QR, preferiti. Sui tasti − e +: «quanti?» |
| **Righe più leggibili** | Nome su due righe, ubicazione in evidenza, «Scorta bassa» solo quando serve. «Vista compatta» mostra più articoli togliendo foto e codice |
| **Scansiona nella barra in basso** | Tasto centrale della barra di navigazione, sempre nello stesso punto: non c'è più il pulsante flottante sopra le liste |
| **Prima schermata «Oggi»** | Al posto dei riquadri con i numeri: tre contatori compatti, l'elenco «Da fare adesso» (articoli sotto scorta, utensili fuori da più di 7 giorni, articoli senza ubicazione, conflitti della squadra), stato della squadra in una riga e ultimi movimenti. Le Impostazioni si aprono dall'ingranaggio in alto |
| **Barra in basso** | Oggi · Articoli · Scansiona · Mappa · Squadra. Movimenti si apre da «Tutti» in Oggi, le Impostazioni dall'ingranaggio |
| **Sezione Squadra** | La sincronizzazione ha una sua sezione nella barra (resta anche nelle Impostazioni) |
| **Prelievo guidato** | «Aggiungi alla lista di prelievo» dal pannello di un articolo. Nella Mappa un pannello mostra avanzamento, prossimo articolo, ubicazione e quantità; si conferma scansionando l'etichetta («Senza etichetta» se manca). In pianta: strutture già visitate in verde, prossima in arancio, tratto puntinato tra le due. Nelle altre sezioni resta una barra con «Vai» |
| **Prestito dal prelievo a raffica** | «Segna come prestito» accanto a «Salta»: chiede chi lo prende e prosegue |
| **Mappa: doppio tocco** | Due tocchi sul pavimento vuoto ingrandiscono su quel punto; a zoom già alto tornano alla pianta intera |
| **Mappa: etichette e contatori** | Le etichette si spostano o si nascondono invece di finire sotto i contatori degli articoli |

## Comportamento da conoscere
- Il percorso del prelievo guidato parte dalla struttura con il nome che viene prima (la pianta non ha un ingresso) e prosegue ogni volta con la più vicina; gli articoli senza ubicazione vengono dopo, quelli rimandati con «Dopo» per ultimi. La lista resta sul telefono dove è stata creata e si svuota chiudendo l'app.
- Mentre un articolo aspetta la conferma, le etichette di **altri** articoli vengono ignorate: premi «Preleva» o «Salta» prima di passare al successivo. Evita che un'etichetta vicina sostituisca quella giusta.
- Lo scanner a schermo intero resta per le altre letture (scheda articolo, strutture, QR della squadra).

## Da provare sul telefono
Questa versione è verificata con i test automatici, non su un dispositivo: fotocamera, torcia, suono, vibrazione, pausa di 2 secondi tra due letture della stessa etichetta, scorrimento sulle righe (anche con i guanti), pannello dal basso e doppio tocco sulla mappa vanno collaudati in officina.

Test: `BurstPickingTest` (stessa etichetta, etichetta di un altro articolo, annullamento), `BurstScanLogicTest` (quantità e filtro delle letture ripetute), `GuidedPickTest` (ordine del percorso, guida sulla mappa, prestiti scaduti, quantità digitate).

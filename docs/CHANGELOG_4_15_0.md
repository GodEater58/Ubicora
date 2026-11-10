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
| **Barra in basso** | Oggi · Articoli · Scansiona · Mappa · Squadra. L'ingranaggio delle Impostazioni è in alto a destra in ogni sezione |
| **Giacenze / Movimenti** | Articoli ha due linguette: la lista e lo storico. «Tutti» in Oggi porta allo storico |
| **Chi ha fatto il movimento** | Con la squadra attiva ogni movimento ricorda la lettera del telefono (A = centrale, B, C…) e la mostra in Oggi e nello storico. Sul centrale i movimenti arrivati da un satellite portano la sua lettera. Vale dai movimenti fatti con la 4.15 in poi |
| **Soglia dei prestiti** | Tieni premuto «Utensili fuori da più di 7 giorni» per scegliere 3, 7, 14 o 30 giorni |
| **Prestiti di tutta la squadra** | A ogni scambio i telefoni si dicono quali utensili hanno in prestito: «In prestito» e «Utensili fuori da più di N giorni» in Oggi contano anche quelli degli altri, e la sezione Squadra li elenca con la lettera del telefono. La restituzione si registra sul telefono che ha fatto il prestito |
| **Sostituire il centrale** | Sezione Squadra di un satellite › «Il centrale non c'è più: questo telefono lo sostituisce». Il satellite diventa centrale con le sue giacenze; gli altri satelliti proseguono con lui senza ricollegarsi, e le modifiche che avevano già consegnato non vengono applicate due volte |
| **Sezione Squadra** | La sincronizzazione ha una sua sezione nella barra (resta anche nelle Impostazioni) |
| **Prelievo guidato** | «Aggiungi alla lista di prelievo» dal pannello di un articolo. La Mappa entra in modalità giro: spariscono gli strumenti di modifica, il titolo diventa «Prelievo guidato · 2 di 5» con la barra di avanzamento, e il pannello in basso indica prossimo articolo, ubicazione e quantità. Si conferma scansionando l'etichetta; se la lettura fallisce o viene chiusa compare «Etichetta illeggibile? Conferma a mano». In pianta: strutture visitate con il segno di spunta, prossima in arancio, tratto puntinato ad angolo retto che evita le strutture quando può. Nelle altre sezioni resta una barra con «Vai» |
| **Prestito dal prelievo a raffica** | «Segna come prestito» accanto a «Salta»: chiede chi lo prende e prosegue |
| **Mappa: doppio tocco** | Due tocchi sul pavimento vuoto ingrandiscono su quel punto; a zoom già alto tornano alla pianta intera |
| **Mappa: etichette e contatori** | Le etichette si spostano o si nascondono invece di finire sotto i contatori degli articoli |

## Comportamento da conoscere
- Il percorso del prelievo guidato parte dalla struttura con il nome che viene prima (la pianta non ha un ingresso) e prosegue ogni volta con la più vicina; gli articoli senza ubicazione vengono dopo, quelli rimandati con «Dopo» per ultimi. La lista resta sul telefono dove è stata creata e si svuota chiudendo l'app.
- Mentre un articolo aspetta la conferma, le etichette di **altri** articoli vengono ignorate: premi «Preleva» o «Salta» prima di passare al successivo. Evita che un'etichetta vicina sostituisca quella giusta.
- Lo scanner a schermo intero resta per le altre letture (scheda articolo, strutture, QR della squadra).

- In modalità aziendale gli indicatori di gestione sono in fondo a Oggi, chiusi: si aprono con «Mostra indicatori».
- La lettera di chi ha fatto il movimento è salvata accanto ai movimenti, senza cambiare la loro tabella né il formato del backup: dopo un ripristino da backup le lettere dei movimenti passati non ci sono più.

- Sostituzione del centrale: conviene promuovere il satellite che ha sincronizzato per ultimo. Quello che solo il vecchio centrale sapeva dopo il suo ultimo aggiornamento a quel telefono va perso. Se il vecchio centrale torna in funzione va scollegato dalla squadra prima di usarlo: due centrali sullo stesso magazzino non si riconciliano.
- I prestiti degli altri telefoni arrivano solo quando entrambi hanno la 4.15; con un telefono su una versione precedente lo scambio funziona come prima, senza l'elenco dei prestiti.
- Le foto degli articoli non viaggiano ancora tra i telefoni.

## Da provare sul telefono
Questa versione è verificata con i test automatici, non su un dispositivo: fotocamera, torcia, suono, vibrazione, pausa di 2 secondi tra due letture della stessa etichetta, scorrimento sulle righe (anche con i guanti), pannello dal basso e doppio tocco sulla mappa vanno collaudati in officina.

Test: `BurstPickingTest` (stessa etichetta, etichetta di un altro articolo, annullamento), `BurstScanLogicTest` (quantità e filtro delle letture ripetute), `TeamSyncTest` (prestiti di squadra, lettera sui movimenti, sostituzione del centrale), `GuidedPickTest` (ordine del percorso, guida sulla mappa, tratto ad angolo, prestiti scaduti, quantità digitate).

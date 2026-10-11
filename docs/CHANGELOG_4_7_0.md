# Ubicora 4.7.0 / Web 1.0.4 — Mappa e ubicazioni

Data: 11/10/2026. Base: 4.6.3. Database Android **v11** (migrazione additiva, nessun dato toccato).

## Android: mappa utile ogni giorno

| Funzione | Come si usa |
|---|---|
| **Mostra in pianta** | Scheda articolo → icona mappa accanto all'ubicazione. La mappa si apre centrata sulla struttura, ingrandita, che lampeggia per 5 s. |
| **QR della struttura** | Mappa → tocca la struttura → *Etichetta QR*. Stampa 60×40 mm oppure foglio A4 con struttura + tutti i suoi articoli. Dall'intestazione della mappa: foglio A4 con le etichette di **tutte** le strutture. Scansionando l'etichetta si apre la mappa su quella struttura. Il QR non sposta mai merce. |
| **Scegli l'ubicazione** | Scheda articolo → *Ubicazione*: elenco con ricerca, oppure **Scegli sulla mappa** (tocchi la struttura). |
| **Assegnazione multipla** | Mappa → struttura → spunta più articoli (o *Tutti*) → *Assegna N articoli*. Una sola operazione, tutto o niente. |
| **Misura in tempo reale** | Disegnando una parete la lunghezza compare sulla linea. |
| **Duplica e crea in fila** | Strutture → seleziona → *Duplica* oppure *Crea in fila* (1–50 copie, distanza, verso destra/basso). Nomi progressivi: Scaffale A → B → C, S9 → S10. |
| **Dove stai guardando** | Le nuove strutture compaiono al centro della vista, non più sempre al centro della stanza. |
| **Vista ricordata** | Zoom e posizione restano cambiando scheda (per magazzino). |

## Android: ubicazioni precise

- Ogni struttura può avere **1–20 ripiani × 1–50 posizioni**. Un articolo può stare su `Scaffale A · R2 P3`.
- Ripiano e posizione sono facoltativi: «—» indica solo la struttura.
- Non si possono ridurre ripiani o posizioni se ci sono articoli oltre il nuovo limite: l'app indica quanti spostarne.
- Backup cifrato: i nuovi campi sono inclusi; il ripristino rifiuta articoli su ripiani inesistenti. I backup 4.6 si ripristinano normalmente.

## Android: editor completo

| Funzione | Dettagli |
|---|---|
| **Viste a colori** | Esplora → *Critici*, *Valore*, *Più usati* (movimenti 30 giorni), *Vuote*. Verde → arancio → rosso. |
| **Annulla / Ripeti** | Pulsanti ↶ ↷ sulla mappa, fino a 50 passi: creazione, eliminazione (articoli ricollegati), spostamento, dimensioni/rotazione, pareti. |
| **Pareti selezionabili** | Tocca una parete: lunghezza, *Lunghezza* esatta in metri, *Elimina*. |
| **Sovrapposizioni** | Strutture sovrapposte con bordo rosso e avviso. **Magnete**: allinea ai bordi delle strutture vicine e alle pareti (entro 15 cm). |
| **Porta, Colonna, Corridoio** | Elementi di riferimento: si vedono in pianta, non contengono articoli. |
| **Testi leggibili** | Quote del bordo in `sp`: seguono la dimensione dei caratteri del telefono. |
| **PDF della pianta** | Icona PDF: A4 orizzontale in scala + indice di tutte le strutture con articoli, ripiani e coordinate. |
| **Più veloce** | Le modifiche alla mappa ricaricano solo pianta e articoli, non ordini e statistiche; niente più tocchi ignorati durante il salvataggio quando si assegnano articoli. |

## Web 1.0.4: mappa alla pari

| Funzione | Come si usa |
|---|---|
| **Sposta e ridimensiona** | Trascina con il mouse (passo 25 cm); angolo arancione per ridimensionare; frecce da tastiera per spostare di 25 cm. |
| **Ruota, duplica, rinomina** | Clic sulla struttura → pannello sotto la pianta. |
| **Disegna pareti** | Clic dopo clic, con misura dal vivo; Esc per terminare; clic su una parete per eliminarla. |
| **Collegamento per ID** | Gli articoli sono collegati alla struttura, non al testo: rinominando la struttura il collegamento resta. Gli archivi 1.0.3 vengono aggiornati in automatico (collegati solo i nomi univoci). |
| **Mostra in pianta** | Inventario → «⌖ pianta» accanto all'ubicazione. |
| **Assegna articoli** | Pannello struttura → *Assegna articoli*: elenco con caselle degli articoli senza ubicazione. |

Il server accetta il nuovo formato (pareti, collegamento per ID) e continua ad accettare quello 1.0.3.

## Difetti trovati e corretti durante lo sviluppo

- Web: dopo un trascinamento la struttura veniva subito deselezionata (il clic finale cadeva sul pavimento).
- Web: ridimensionando un solo lato, l'altro veniva arrotondato (0,80 → 0,75 m).

## CI

La build Android ora riporta errori di compilazione, test falliti ed errori di lint come **annotazioni leggibili** nella PR, invece di un generico «exit code 1».

## Verifiche

Vedi la PR: build Android (lint, test unitari debug e release, APK), test Web (core + 4 test nel browser), test server su PostgreSQL.

## Limiti

- L'export CSV riporta l'ubicazione come nome struttura: ripiano e posizione non sono inclusi nel CSV (restano nel backup cifrato).
- Nessun test su telefono reale in questa sessione: gesti, stampa ed etichette vanno provati sul dispositivo.
- Le schede articolo restano finestre di dialogo; il passaggio a pagine intere è un intervento separato.

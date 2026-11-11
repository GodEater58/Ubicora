# Ubicora 4.17.0 — un'azienda, più sedi (primo passo)

Comprende i ritocchi della 4.16.2.

## Novità
| Funzione | Dettagli |
|---|---|
| **Azienda e sedi** | Il «profilo» ora si chiama azienda e i suoi magazzini sono le sedi. Cambiano solo le parole: i dati restano gli stessi |
| **Cambio sede dal nome** | In Oggi tocca il nome della sede in alto: elenco delle sedi dell'azienda, «Aggiungi o gestisci le sedi», «Cambia azienda» |
| **Si riparte dall'ultima sede** | Dopo il PIN l'app riapre la sede usata per ultima, senza passare dall'elenco |
| **Ricerca nelle altre sedi** | Cercando in Articoli, sotto i risultati compare «Nelle altre sedi» con sede e quantità. Un tocco porta in quella sede e apre l'articolo |
| **Le etichette portano alla sede giusta** | Inquadrando (o toccando con l'NFC) l'etichetta di un articolo o di una struttura di un'altra sede della stessa azienda, l'app passa a quella sede e apre ciò che hai letto |

## Limiti di questo primo passo
- **La squadra segue ancora una sola sede per telefono.** Sul telefono centrale, che ha entrambe le sedi, tutto questo funziona subito. Un satellite ha soltanto la sede a cui è collegato: non vede l'altra, né nel selettore né nella ricerca. L'estensione della squadra a tutta l'azienda è il passo successivo.
- Non c'è ancora il trasferimento di un pezzo da una sede all'altra fuori dalla Gestione aziendale.
- Durante un prelievo a raffica, guidato o una verifica scaffale le etichette di un'altra sede restano rifiutate: non si cambia sede a metà lavoro.

Test: `CompanySitesTest`. Non verificato su dispositivo.

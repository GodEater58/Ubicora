# Ubicora 4.20.3

Correzioni dalla revisione della 4.20.2.

| Prima | Ora |
|---|---|
| Se un satellite apriva per errore un aggiornamento del centrale più vecchio di uno già importato, cancellava gli articoli creati nel frattempo e i prelievi non ancora inviati non arrivavano più al centrale | L'aggiornamento più vecchio viene rifiutato: «Questo aggiornamento del centrale è più vecchio di quello già importato». Lo stesso file aperto due volte va ancora bene |
| Eliminando un articolo con un trasferimento in viaggio, un ordine, un conteggio o una manutenzione aperti, questi sparivano senza traccia (i pezzi spediti non rientravano più) | L'eliminazione è bloccata finché non si completano o annullano |
| Dopo il ripristino di un backup i movimenti perdevano la lettera del telefono che li aveva fatti | Il backup salva anche le lettere (i backup delle versioni precedenti si ripristinano come prima) |
| Il QR «Collega un nuovo telefono» cambiava codice quando un telefono si collegava, e chi l'aveva già inquadrato riceveva «QR scaduto» | Il codice resta lo stesso finché ha almeno 3 minuti di validità; il QR si prepara senza bloccare lo schermo |
| Se una sincronizzazione partiva mentre si trascinava una struttura, la struttura sembrava spostata ma non lo era | Torna al suo posto e si può trascinare di nuovo |
| Uno scambio con i telefoni vicini durante un'operazione lunga (ripristino, importazione CSV) sbloccava i pulsanti prima della fine | L'operazione lunga resta protetta fino alla fine |
| L'app chiedeva il permesso Internet senza usarlo | Permesso rimosso: l'app non usa la rete |

Il controllo usa l'orologio del telefono centrale: se viene portato molto indietro, i suoi aggiornamenti vengono rifiutati finché non superano l'ultimo importato.

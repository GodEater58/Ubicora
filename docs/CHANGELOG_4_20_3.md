# Ubicora 4.20.3

Correzioni dalla revisione della 4.20.2.

| Prima | Ora |
|---|---|
| Se un satellite apriva per errore un aggiornamento del centrale più vecchio di uno già importato, cancellava gli articoli creati nel frattempo e i prelievi non ancora inviati non arrivavano più al centrale | L'aggiornamento più vecchio viene rifiutato: «Questo aggiornamento del centrale è più vecchio di quello già importato». Lo stesso file aperto due volte va ancora bene |
| Eliminando un articolo con un trasferimento in viaggio, un ordine, un conteggio o una manutenzione aperti, questi sparivano senza traccia (i pezzi spediti non rientravano più) | L'eliminazione è bloccata finché non si completano o annullano |

Il controllo usa l'orologio del telefono centrale: se viene portato molto indietro, i suoi aggiornamenti vengono rifiutati finché non superano l'ultimo importato.

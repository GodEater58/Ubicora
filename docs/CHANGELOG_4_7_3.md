# Ubicora 4.7.3 / Web 1.0.6 — debug completo

Rilettura di tutto il progetto (Android, Web, server) e di tutti i test esistenti. Nessun difetto sulle giacenze o sui movimenti. Corretti 8 difetti, quasi tutti nelle funzioni nuove della mappa.

## Android

| Difetto | Effetto | Correzione |
|---|---|---|
| Annulla sulla mappa dopo aver ridotto la pianta | Strutture o pareti tornavano **fuori dai confini**; il backup cifrato poi veniva rifiutato al ripristino | Ridurre la pianta azzera la cronologia di Annulla; il ripristino di strutture e pareti verifica che entrino nella pianta |
| Annulla di più strutture insieme | Se una non poteva tornare, le altre tornavano a metà | Annulla di più elementi ora è tutto o niente |
| Doppio tocco veloce su Annulla/Ripeti | Una modifica poteva sparire da entrambe le liste | La modifica esce dalla lista solo quando l'annullamento parte davvero |
| Cronologia mappa dopo logout, ripristino backup, nuovo magazzino, demo | Annulla poteva agire su dati non più corrispondenti | La cronologia si azzera in tutti questi casi |
| Pareti disegnate in fretta | Un tocco mentre si salvava la parete precedente veniva perso: la catena restava aperta | Le pareti vengono messe in coda e salvate tutte, in ordine |
| Indietro mentre si sceglie l'ubicazione sulla mappa | La scelta restava in sospeso | Indietro annulla la scelta |
| Scelta ripiano/posizione | Si apriva sempre su «—» | Si apre sul ripiano e posizione attuali dell'articolo |
| QR di un altro magazzino | Il messaggio mostrava un numero interno | Messaggio chiaro: «appartiene a un altro magazzino» |

## Web 1.0.6

| Difetto | Effetto | Correzione |
|---|---|---|
| Rimuovere una struttura | Ripiano e posizione restavano sugli articoli: il CSV esportato dal Web veniva **rifiutato da Android** («ripiano senza ubicazione») | Rimuovendo la struttura si azzerano anche ripiano e posizione; l'export non scrive mai un ripiano senza ubicazione |
| Nuova struttura con un nome già usato | Gli articoli importati non si collegavano (nome ambiguo); la rinomina invece lo vietava già | Nomi delle strutture unici anche alla creazione |

## Server
Nessun difetto trovato: lo schema accetta ripiano/posizione e controlla gli stessi limiti del Web.

## Verifiche
- Contract test Android ✅ · Web core 32/32 + 4 test nel browser ✅ · server 38/38 su PostgreSQL 16 ✅
- Nuovi test: Annulla dopo riduzione della pianta (struttura e parete), Annulla di più strutture tutto-o-niente, rimozione struttura sul Web con CSV compatibile Android, nomi unici.
- La coda delle pareti e il doppio tocco su Annulla sono correzioni di logica nel ViewModel senza test automatico: vanno provate sul telefono.

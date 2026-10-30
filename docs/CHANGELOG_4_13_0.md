# Ubicora 4.13.0 — sincronizzazione automatica ed eliminazione articoli

## Novità
| Funzione | Dettagli |
|---|---|
| **Sincronizzazione automatica** | Con Ubicora aperta il centrale tiene la rete aperta in sottofondo; i satelliti lo cercano all'apertura e ogni 10 minuti. Nessuna schermata bloccante: solo un piccolo avviso in alto. Interruttore in Impostazioni › Sincronizzazione (attivo di default, serve il permesso «Dispositivi nelle vicinanze») |
| **Eliminare un articolo** | Scheda articolo › «Elimina articolo». Sul centrale l'articolo sparisce anche dai satelliti alla sincronizzazione successiva. Non consentito sui satelliti né con prestiti aperti |
| **Eliminare un magazzino dalla lista** | Nella schermata «Dove lavoriamo?» tieni premuto un magazzino |

## Correzioni
| Problema | Correzione |
|---|---|
| Il satellite cercava il centrale all'infinito | Dopo 90 secondi si ferma con un messaggio (45 s, in silenzio, per la ricerca automatica) |
| Il satellite poteva restare su «Ricevo l'archivio…» | Controllo continuo: 90 s senza dati in entrambe le direzioni chiudono lo scambio con un errore |
| «Annulla» durante l'unione dei dati sul satellite lasciava lo schermo con i dati vecchi | Lo schermo si aggiorna sempre quando l'archivio è stato salvato |
| Articoli eliminati (o demo rimossa) sul centrale restavano sui satelliti | I satelliti tolgono gli articoli che il centrale non ha più, tranne quelli creati da loro e non ancora inviati |
| Dopo la sincronizzazione il centrale tornava al radar e sembrava bloccato | Mostra «Sincronizzazione completata» |
| L'aggiornamento dopo uno scambio poteva sbloccare l'app durante un'altra operazione | Aspetta la fine dell'operazione in corso |

Compatibilità: le versioni precedenti continuano a scambiare dati con la 4.13; solo la rimozione degli articoli eliminati richiede la 4.13 sui satelliti.

Test: `TeamSyncTest` (articolo eliminato sul centrale, prestito aperto), `WarehouseDeleteTest` (eliminazione dalla lista, eliminazione articolo).

# Officina Stock 4.1.3: scanner e demo completa

## Scanner QR

Lo scanner usa un'Activity propria (`StockQrCaptureActivity`) con orientamento Android `user` e `ScanOptions.setOrientationLocked(false)`.
La vista segue la rotazione soltanto se l'autorotazione è consentita dal sistema. Con rotazione disabilitata, l'orientamento è quello preferito dal dispositivo, normalmente verticale.
Il QR di Officina Stock include il magazzino di appartenenza; la lettura apre la scheda corrispondente senza effettuare carichi o prelievi automatici.

## Scenario dimostrativo

Aprire **Impostazioni → Carica dati dimostrativi**. In un magazzino che contiene già demo, usare **Rigenera demo completa**; prima di rigenerare è consigliato un backup dei dati locali.

- 42 articoli tra tutte le nove categorie.
- Nove strutture con coordinate metriche, dimensioni e ubicazione; otto pareti per mostrare la planimetria.
- Scorte sufficienti, minime, esaurite, prestiti aperti e storico di restituzioni.
- Prezzi unitari, fornitori e tempi di consegna valorizzati, più un articolo senza costo per testare l'indicatore di incompletezza.
- Prelievi e carichi distribuiti nei 90 giorni precedenti, coerenti con la giacenza finale.
- Illustrazioni sintetiche con marcatura "IMMAGINE DEMO · NON REALE" sugli articoli selezionati; non sono foto reali dei ricambi.
- Etichette QR generabili e scansionabili per ogni articolo, con codici assegnati automaticamente.
- Dati interamente offline. Non sono creati profili con PIN preimpostati.

La demo non cancella i dati reali già inseriti. Ogni articolo e ogni elemento della mappa aggiunto dalla demo è taggato nella tabella `demo_assets`.

## Rimozione e rigenerazione

**Impostazioni → Rimuovi soltanto la demo** elimina gli articoli dimostrativi, le loro movimentazioni e i loro prestiti (vincoli SQLite), le immagini associate, i muri e le ubicazioni dimostrative. Gli articoli creati manualmente rimangono.

**Rigenera demo completa** elimina e ricrea gli elementi demo; le prove effettuate sugli articoli demo andranno perdute, ma non gli articoli personali. L'operazione è esplicitamente confermata dall'utente.

## Cosa provare

1. Dashboard → periodo 7/30/90 giorni: confrontare gli andamenti.
2. Articoli → filtro scorta critica: vedere gli articoli sotto minimo.
3. Articoli → aprire un articolo → visualizzare e stampare etichetta QR.
4. Inquadrare il codice QR con lo scanner della dashboard; confermare il caricamento della scheda.
5. Scheda → carico/prelievo/assegnazione; vedere il risultato in Movimenti e nella dashboard.
6. Movimenti → Prestiti: registrare una restituzione.
7. Mappa → esplorare scaffali e ubicazioni, sbloccare e spostare.
8. Impostazioni → backup completo cifrato e CSV; rimuovere la demo quando non serve più.

## Limiti da collaudare sul telefono

L'autorotazione effettiva dello scanner, la gestione della fotocamera, la stampa e le interazioni touch della mappa devono essere verificate su dispositivi reali. La compilazione GitHub Actions non sostituisce un test end-to-end fisico.

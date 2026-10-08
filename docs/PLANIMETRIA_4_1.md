# Officina Stock 4.1 · Planimetria Studio

## Principi
- L'area è disegnata in metri reali, non percentuali legate allo schermo. Imposta larghezza e profondità dal pulsante righello.
- Tre modalità separate: Esplora (pan e selezione), Pareti (tocco-tocco o trascinamento), Strutture (drag di oggetti sbloccati).
- Grid overlay di 50 cm con supporto a snap da 25 cm, ortogonale da 90° e aggancio agli estremi delle pareti già presenti.
- Arredi rettangolari in scala: dimensioni fisiche, rotazione, blocco/sblocco, articoli e allerta scorte.
- Pianta zoomabile e adattabile al telefono, con un singolo gestore touch e trasformazione di viewport bidirezionale.

## Compatibilità e migrazioni
- SQLite 4 → 5 aggiunge `map_width`, `map_height`, `width_m`, `depth_m`, `rotation_deg`; niente DROP TABLE.
- Mappa legacy al riferimento metrico iniziale 12×8 m: x*0.12, y*0.08, tanto per le pareti quanto per le strutture.
- Articoli, quantità, movimenti, foto, PIN e prestiti restano invariati.
- Lo scaling dei dati percentuali della precedente UI è una conversione convenzionale: verificare/correggere le quote reali dell'ambiente.

## Limiti noti
- L'editor è 2D; non produce planimetrie architettoniche certificate né CAD/DXF.
- La scansione AR resta una stima geometrica ricalibrata all'area della pianta, non una misura assoluta garantita.
- Gli oggetti con geometria reale possono sovrapporsi e uscire parzialmente dai bordi: è un editor manuale, non un solver di collisioni.
- Alla prima installazione una firma debug diversa dalla precedente può impedire l'aggiornamento; conservare una copia dei dati prima di rimuovere versioni.

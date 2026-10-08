# Gestione foto articolo · Officina Stock 4.0.2

- Scatta da fotocamera con permesso Android richiesto, o importa tramite selettore foto Android.
- Prima di salvare, anteprima foto; Rifai foto e Rimuovi foto. Se lo scatto è annullato, la foto precedente resta disponibile.
- Su un articolo già registrato, la nuova foto è provvisoria fino a **Usa questa foto**. Scarta nuovo scatto annulla la sostituzione.
- **Elimina foto** chiede conferma ed elimina soltanto l'immagine.
- File privati in `files/article_photos`, database SQLite versione 3 con `photo_file` (solo nome file).
- JPEG max 1600px lato lungo, qualità 85, orientamento EXIF normalizzato.
- Database migrato senza eliminare articoli o movimenti (v1->v3 e v2->v3).
- File originali precedenti cancellati solo dopo che SQLite conferma la sostituzione o la cancellazione.

## Verifiche previste su Android fisico

1. Fotografa, annulla dalla fotocamera: nessuna foto precedente persa.
2. Scatta una foto, quindi Rifai e conferma: soltanto la foto scelta diventa definitiva.
3. Importa dalla galleria, cancella la selezione e scegli di nuovo.
4. Sostituisci una foto esistente, poi prova Scarta e Usа questa foto.
5. Elimina foto dalla scheda articolo e verifica codice, quantità, storico invariati.
6. Chiudi e riavvia l'app: miniatura e immagine devono essere persistite.
7. Aggiorna l'APK 4.0.1 alla 4.0.2: schema SQLite v2→v3 senza perdita dati.

La scansione QR e ARCore non sono modificate da questo intervento. La mappa include la nuova modalità disegno pareti con trascinamento.

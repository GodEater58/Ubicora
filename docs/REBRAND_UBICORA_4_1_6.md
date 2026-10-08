# Ubicora 4.1.6 — Pacchetto Android e icona scelta

- Nuovo `applicationId`: `com.ubicora.app` (nome mostrato: Ubicora).
- Namespace interno Kotlin temporaneamente `com.officinastock.app`; non è l'ID dell'APK e non influenza il nome visualizzato.
- Icona selezionata: **prima proposta con U bianca scolpita nel magazzino tridimensionale**. Art originale già approvata; esclusivamente ritaglio, adattamento dimensioni e compressione per Android, non un nuovo design.
- Launcher adattivo: usa `@drawable/ubicora_selected_art`; la stessa immagine è usata dal wordmark delle schermate principali.
- FileProvider: autorità derivata da `${applicationId}` e `context.packageName`; funziona con il nuovo ID.
- Le etichette QR e il formato backup continuano a usare i marcatori storici per poter leggere i dati esportati in precedenza.

## Trasferimento dati (necessario!)

Cambiando `applicationId`, Android considera Ubicora 4.1.6 una seconda app. **Non sovrascrive** l'installazione con ID `com.officinastock.app` e **non può accedere automaticamente** al suo database o alle sue fotografie.

1. Nell'app precedente: Impostazioni → Esporta backup completo; scegliere una password e conservare il file esternamente all'app.
2. Installare Ubicora 4.1.6; completare gli step iniziali per accedere alle Impostazioni.
3. Impostazioni → Ripristina da backup; scegliere il backup e digitare la stessa password; confermare la sostituzione dei dati **della nuova app**.
4. Verificare articoli, profili, fotografie, prestiti, planimetrie e movimentazioni.
5. Eliminare la vecchia app **solo dopo** aver verificato il ripristino e conservato una copia del backup.

La build Debug non è una firma commerciale permanente: per il futuro Play Store è necessaria una firma release stabile.

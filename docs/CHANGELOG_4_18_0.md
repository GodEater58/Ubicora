# Ubicora 4.18.0 — la squadra su tutte le sedi dell'azienda

Comprende la 4.17.0 (azienda e sedi) e la 4.16.2.

## Novità
| Funzione | Dettagli |
|---|---|
| **Sedi della squadra** | Sul centrale: Squadra › «Sedi della squadra» › «Condividi» accanto a una sede della stessa azienda. Da quel momento fa parte della squadra |
| **Un collegamento, più sedi** | I satelliti ricevono le sedi condivise con il prossimo aggiornamento, senza ricollegarsi: pianta, strutture, articoli e giacenze. Un telefono nuovo le riceve tutte al collegamento |
| **Lavoro su ogni sede** | Dal satellite si preleva, si carica, si creano articoli e si spostano ubicazioni in qualsiasi sede condivisa. Il centrale applica ogni modifica nella sede giusta |
| **Stesse regole ovunque** | Reparti, limiti dei satelliti, prestiti di squadra, foto e lettera sui movimenti valgono su tutte le sedi condivise |
| **Etichette tra telefoni** | Il numero di sede stampato sulle etichette del centrale viene tradotto sui satelliti: un'etichetta dell'altra sede porta il manutentore in quella sede anche dal suo telefono |

## Compatibilità
- I file di scambio hanno campi in più (`sites`, `site`, `label_id`). Un satellite rimasto a una versione precedente continua a lavorare sulla prima sede e non vede le altre.
- **Aggiorna prima il centrale.** Un satellite con la 4.18 collegato a un centrale più vecchio funziona come prima, su una sede sola.
- Nessuna modifica alle tabelle. Le regole interne che registrano le modifiche da inviare vengono ricreate all'apertura dell'app.

## Limiti
- Una sede condivisa non si può più togliere dalla squadra né eliminare finché la squadra esiste.
- La sede da cui è nata la squadra resta la «prima»: se il centrale è più vecchio della 4.18 è l'unica che i satelliti vedono.
- Reparti e permessi sono gli stessi su tutte le sedi; non si possono ancora differenziare per sede.
- Non c'è ancora il trasferimento di un pezzo da una sede all'altra fuori dalla Gestione aziendale.
- Le etichette delle **strutture** contengono un numero interno del telefono che le ha stampate: stampale dal centrale. Se su un satellite non vengono riconosciute, è per questo (limite già presente nelle versioni precedenti).

## Da provare, in quest'ordine
1. Backup completo del centrale.
2. 4.18 sul centrale; in Squadra condividi la seconda sede.
3. 4.18 su **un solo** satellite; sincronizza; controlla che compaia la seconda sede con le giacenze giuste.
4. Dal satellite fai un prelievo nella seconda sede, sincronizza, controlla sul centrale.
5. Solo dopo, gli altri telefoni.

Test: `TeamSyncTest` (seconda sede su un satellite nuovo e su uno già collegato, modifiche applicate nella sede giusta, file ripetuto, reparti sulla seconda sede). Non verificato su dispositivo.

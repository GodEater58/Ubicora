# Ubicora Web 1.0.3 — PC e browser mobile

## Uso immediato, senza installazione

Aprire **Ubicora.html** con Edge o Chrome. Il file contiene l'intera applicazione, senza CDN, npm, account o collegamento Internet necessari per la modalità locale.

1. Creare il magazzino con nome, località e dimensioni della pianta.
2. Aggiungere articoli oppure, da **Inventario → Importa CSV Android**, importare il CSV di **Ubicora Android → Impostazioni → Esporta inventario CSV**.
3. Usare carichi/prelievi/rettifiche, richieste d'acquisto e arrivi parziali, prenotazioni e manutenzioni. La planimetria 2D usa metri e ubicazioni per nome.
4. In **Dati e connessione**, esportare periodicamente il backup web. Il JSON è in chiaro, non è il backup cifrato Android `.osbackup`.

L'archivio locale risiede in IndexedDB del browser. Stesso PC ma browser diverso = archivio diverso. Spostare il file HTML, usare navigazione privata o cancellare i dati del browser può rendere l'archivio non disponibile: conservare il backup. La demo è attivata esplicitamente e non sostituisce l'archivio locale; le sue modifiche rimangono in memoria.

### Ponte con Android: cosa trasferisce il CSV

Il CSV inventario Android 4.6, UTF-8 e separatore `;`, comprende codice, descrizione, categoria, quantità, minimo, unità, ubicazione, note, costo unitario, fornitore e giorni consegna. L'importazione aggiunge gli articoli e genera i movimenti iniziali; i codici duplicati annullano l'intera importazione. Importare preferibilmente in una nuova sede.

Non trasferisce foto, planimetria, prestiti, lotti, seriali o storico Android. Il nuovo CSV esportato dal web mantiene lo stesso formato; per importarlo nell'APK usare una sede nuova, perché l'import Android non aggiorna automaticamente articoli esistenti. Non è un meccanismo di sincronizzazione bidirezionale.

## Spazio condiviso PC-telefono

La stessa applicazione è servita dalla radice del backend FastAPI. Aprire **lo stesso indirizzo HTTPS nel browser del PC e del telefono** e usare **Dati e connessione → Accedi con account aziendale**. Le sessioni accedono allo stesso spazio centrale della propria organizzazione. Poll ogni 15 secondi senza interrompere i moduli aperti.

Lo spazio condiviso è distinto dal database SQLite dell'APK attuale: Android 4.6 non sincronizza automaticamente il suo archivio con il portale. La versione browser non implementa ancora foto, QR/scanner, AR, lotti/seriali, prestiti, trasferimenti tra sedi o ruoli granulari per le singole operazioni. Non presentarla come parità completa con l'app.

### Attivazione amministrativa

Seguire prima `server/README.md` per PostgreSQL 16+, provider OIDC, utente applicativo non proprietario/NOBYPASSRLS e provisioning delle appartenenze.

1. Applicare **una volta** `server/web_schema.sql` con l'utente di migrazione.
2. Concedere all'utente applicativo `SELECT,INSERT,UPDATE` su `web_workspaces`, `SELECT,INSERT` su `web_receipts`, `SELECT` su `tenants,tenant_members`. Non concedere modifica/cancellazione delle ricevute.
3. Creare un client OIDC **pubblico**, con Authorization Code + PKCE S256, access token RS256 verificabili con issuer/audience del backend. Nessun client secret nel browser. Configurare redirect URI esattamente uguale alla radice del portale, ad esempio `https://ubicora.example/`. Il token endpoint deve accettare CORS dall'origine del portale.
4. Aggiungere al server `UBICORA_WEB_CLIENT_ID`, `UBICORA_WEB_AUTHORIZE_URL` e `UBICORA_WEB_TOKEN_URL` (endpoint HTTPS amministrati, non inseriti dal visitatore).
5. Eseguire `python web/build.py` dalla radice del repository. Avviare il server o costruire il container aggiornato; esporlo tramite reverse proxy HTTPS. Per avvio locale di sviluppo: `cd server` e `python -m uvicorn app:app --host 127.0.0.1 --port 8080`.
6. Provisionare ogni persona in `tenant_members` con il vero subject OIDC. Solo **MANAGER** modifica lo spazio web; gli altri membri attivi sono in sola lettura. Il ruolo scritto nella rubrica Android non concede accesso.

I token restano soltanto in memoria; ricaricare la pagina richiede un nuovo accesso. In sessionStorage sono salvati solo verifier/state PKCE a vita breve, rimossi al callback. Non vengono memorizzati refresh token. Logout rimuove la sessione locale; il provider può mantenere il suo SSO.

### Conflitti e interruzioni

Ogni salvataggio usa `mutation_id` e `expected_revision`. Una modifica concorrente restituisce **409**; il browser conserva la copia non confermata ed offre l'esportazione prima di ricaricare. Non applica merge impliciti o last-writer-wins. Se si perde la conferma, riprovare con **Risolvi salvataggio**: il server riconosce la stessa ricevuta, senza duplicare carichi o prelievi. Non ripetere manualmente la stessa operazione.

Il server salva l'intero documento in una transazione PostgreSQL, con RLS per organizzazione, controllo di appartenenza e ricevute append-only. Limite HTTP 8 MiB; 20.000 articoli, 100 sedi e 100.000 movimenti sono limiti massimi di schema, non una garanzia che ogni archivio rientri negli 8 MiB. Nessun invio automatico degli archivi locali: i dati condivisi nascono nel portale e l'import CSV richiede conferma.

## Verifica

`node --test web/tests/core.test.mjs`: movimenti, stock riservato, arrivi parziali, manutenzione atomica, snapshot dei costi, CSV e import di 10.000 articoli.

`node web/tests/browser.cjs` con Playwright: browser reale, creazione, contenuto HTML non eseguibile, persistenza dopo reload, mappa, layout mobile e apertura del singolo HTML da file.

`cd server && python -m pytest tests -q`: test OIDC RS256 e, con il database della fixture CI, membership, RLS, revisioni concorrenti e retry idempotenti. Il provider OIDC reale e il deployment sono da verificare nell'ambiente aziendale.


### Correzioni 1.0.1
La demo è sempre identificata e avverte prima di abbandonare modifiche non salvate. Il ripristino rifiuta numeri testuali e planimetrie fuori dai confini. Il CSV generico è distinto dal CSV per Android: quest’ultimo verifica i codici e le categorie accettati dall’APK.

### Correzioni 1.0.3

- Ricezioni: il carico fisico non supera il residuo registrato nell'ordine.
- Prenotazioni decimali: accettato il saldo disponibile entro la tolleranza numerica.
- Riepiloghi: nomi macchina come `constructor` e `__proto__` mantengono totali corretti.
- Salvataggi: protetti cambi di archivio, disconnessione e risposte automatiche ritardate.
- CSV: rifiutati caratteri dopo un campo tra virgolette; preservati nomi Android fino a 180 caratteri e note fino a 3000.
- Archivio condiviso: allineati limiti, date, geometria, testi e completamento delle manutenzioni.

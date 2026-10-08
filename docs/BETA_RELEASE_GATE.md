# Ubicora 4.2.0: criterio di ingresso alla Beta privata

**Stato:** preparazione, NON approvazione alla distribuzione. La compilazione verde non equivale a collaudo Android.

## Bloccanti prima della prima installazione con dati reali

1. **Firma persistente.** Confrontare il certificato SHA-256 del pacchetto installato con quello del nuovo APK tramite `apksigner verify --print-certs`. La firma di debug generata dai runner GitHub Actions non è stabile. Usare una chiave posseduta e custodita fuori dal repository; conservare almeno due copie cifrate in luoghi distinti.
2. **Compatibilità.** Android aggiorna solo un pacchetto con identico applicationId, certificato compatibile e versionCode crescente. `com.ubicora.app` è diverso dal precedente `com.officinastock.app`: quest'ultimo richiede migrazione manuale dei dati, non un aggiornamento sul posto. Non disinstallare l'app con dati reali.
3. **Backup e restore.** Eseguire esportazione cifrata e ripristino integrale su un secondo telefono di prova, con almeno due profili, foto, prestiti e planimetrie. Verificare conteggi, codici QR e password errate prima di aggiornare.
4. **Scenari fisici.** Eseguire test UI, rotazione, notch/status bar, Android 15/16, picker foto, QR, scanner, accesso per profilo, background, AR con e senza servizi, tastiera, import CSV, mappa con zoom/drag e connessione offline.
5. **Carico reale.** Misurare avvio, RAM, ANR, scrolling e ricerca con 1.000, 10.000 e 20.000 articoli. Il benchmark JVM non misura la latenza del telefono.

## Firma release opzionale

Configurazione Gradle attraverso le variabili di ambiente seguenti, senza committare password o file di firma:

- `UBICORA_RELEASE_STORE_FILE`: percorso assoluto keystore esistente
- `UBICORA_RELEASE_STORE_PASSWORD`: password keystore
- `UBICORA_RELEASE_KEY_ALIAS`: alias
- `UBICORA_RELEASE_KEY_PASSWORD`: password chiave

Le quattro variabili sono obbligatorie insieme. Se assenti, il progetto continua a produrre una variante release **non firmata**, non installabile. Non è ammesso generare una nuova chiave per aggiornare installazioni già firmate con altra chiave. Validare manualmente il certificato prima della distribuzione.

## CSV e modifiche articoli

L'importazione CSV richiede esportazione Ubicora con intestazioni originali e formato UTF-8. È un'aggiunta di soli articoli nuovi: i codici già presenti, i codici duplicati e le ubicazioni sconosciute causano un rifiuto totale; nessuna riga deve essere committata se una è invalida. Richiede conferma dopo la verifica del file. Quantità importate generano movimenti di carico iniziale. Il CSV non recupera foto, prestiti o movimenti originali, per cui si deve usare il backup cifrato quando serve il ripristino.

La modifica anagrafica conserva SKU, QR, quantità e storico. Per cambiare giacenza si registra un movimento.

## Matrice di accettazione Beta

| Test | Criterio |
| --- | --- |
| CI | Tutti gli script e test JVM debug/release verdi, nessun errore lint, APK generato |
| Installazioni | Almeno 3 dispositivi Android, incluse versione 15 e 16, installazione pulita e upgrade compatibile |
| UI | Flussi essenziali end-to-end con screenshot e nessun blocco/crash |
| Integrità | Conteggi di magazzini, articoli, foto e movimenti invariati dopo upgrade |
| Sicurezza | PIN, multi-profilo, dati esterni e backup senza accessi incrociati |
| Affidabilità | Zero P0/P1 aperti e nessuna perdita dati in test di ripristino |
| Prestazioni | Ricerca su 10.000 articoli entro 2 secondi su un telefono di riferimento, verificata |
| Stabilizzazione | Prova privata di almeno 14 giorni con feedback e difetti tracciati |

## Funzioni differibili dalla Beta

Sincronizzazione cloud, analisi AI, scanner AR ad alta precisione e nuovi moduli non sono prerequisiti. L'AR può essere esplicitamente sperimentale, con mappa 2D manuale sempre disponibile. Rimangono da valutare conta fisica, restituzioni parziali, importazione fogli XLSX e trasferimenti inter-magazzino.

## Play Store

Per nuove app e aggiornamenti su smartphone, Google Play richiede target API 36 dal 31 agosto 2026 (salvo eccezioni/proroghe previste). Occorrono inoltre audit delle licenze, privacy policy definitiva, dichiarazioni dei dati e verifica della proprietà del marchio.

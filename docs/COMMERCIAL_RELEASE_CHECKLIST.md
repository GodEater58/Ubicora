# Verifica pre-rilascio commerciale (4.1.4)

## Adeguamenti tecnici inclusi

- [x] Elenco preliminare delle dipendenze principali con attribuzioni.
- [x] Testo integrale Apache License 2.0 disponibile nell'app e nel repository.
- [x] Schermata privacy e licenze in Impostazioni.
- [x] Avviso visibile prima del primo avvio ARCore con accesso a privacy/termini Google.
- [x] Mappatura AR indicata come opzionale e inventario accessibile senza AR.
- [x] Bozza di privacy policy e audit di provenienza.
- [x] Report automatico CI delle dipendenze runtime (non equivale a risoluzione delle licenze).

## Passaggi BLOCCANTI prima della distribuzione pubblica

- [ ] Confermare titolarità e diritti del codice del progetto e dell'ARScanActivity ereditata dalla 3.0.1.
- [ ] Eseguire audit delle dipendenze **transitive**: licenze esatte per ogni artefatto, NOTICE di upstream, obblighi speciali, pacchetti nativi.
- [ ] Individuare e dichiarare correttamente la titolarità del programma e il contatto privacy; completare e far valutare l'informativa in base alla normativa applicabile.
- [ ] Pubblicare la privacy policy in HTTPS a indirizzo pubblico stabile e inserire link nel Play Store.
- [ ] Verificare la dichiarazione Data safety con ARCore, gli SDK indiretti e i permessi finali dell'APK.
- [ ] Verificare in TMview/EUIPO/UIBM, domini e mercati di interesse l'eventuale marchio «Ubicora» prima di usarlo nel commercio.
- [ ] Confermare release signing persistente (non firmare ogni build con debug key diversa).
- [ ] Collaudare scanner, ARCore, backup/ripristino e perdita di dati su più dispositivi reali.
- [ ] Predisporre condizioni di vendita, garanzia, assistenza e documenti fiscali secondo il modello commerciale prescelto.

NON riportare il prodotto come “certificato copyright free” o “commercial ready” prima di questi passi.

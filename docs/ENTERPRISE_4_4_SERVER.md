# Ubicora 4.4 Enterprise Alpha | struttura manageriale e collegamento server

**Stato reale:** modello locale SQLite v8, interfaccia anagrafiche e ordini collegati a centri di costo/fornitori. **Nessun server, account cloud, sincronizzazione o controllo RBAC remoto è attivo.** Non pubblicare come SaaS già pronto.

## Cosa è implementato

1. Modalità Personale ancora completamente offline. In Aziendale, sezione **Centro operativo → Anagrafiche** per centri di costo, fornitori, macchine/impianti, referenti e ruoli *previsti*, non autorizzazioni.
2. Nuove tabelle con scope obbligatorio al profilo aziendale e UUID stabili per futura migrazione; controlli di isolamento quando si associano ordini e asset. Centri di costo con limite mensile e somma nota delle richieste approvate/ordinate/ricevute.
3. Le richieste d'acquisto possono associare **centro di costo** e **fornitore in anagrafica**. Un ordine conserva costo noto al momento della richiesta; importi non noti non vengono inventati.
4. **Ricevimento parziale:** si registra la quantità realmente arrivata, la giacenza aumenta solo di quella quantità; stato ORD​ERED finché l'ordine non è completo. L'azione è atomica in SQLite e auditata.
5. **Coda outbox** transazionale per la creazione di nuove anagrafiche. Ogni evento usa UUID e payload JSON, separato per profilo. Backup cifrato comprende tutte le nuove tabelle e gli eventi in attesa.
6. **Client HTTPS opt-in:** `EnterpriseSyncProtocol` e `EnterpriseServerClient` (senza URL/token salvati, redirect disabilitati, TLS validato dal sistema, limiti di dimensione, ack idempotenti). Non è ancora chiamato dalla UI.

## Compatibilità e migrazione

- SQLite **v7 → v8 additiva**: non cancella articoli, PIN, foto, movimenti, prestiti, scaffali, pareti o operazioni 4.3.
- Backup 4.4 = 20 tabelle; import legittimo di backup precedenti a **9 o 15 tabelle**, oltre ai nuovi a 20. Le dipendenze degli ordini sono ripristinate dopo le anagrafiche e vengono controllati i riferimenti incrociati.
- **Non recupera magicamente la firma APK installata.** Le versioni debug di GitHub non sono aggiornamenti dei vecchi certificati. Vedi `AGGIORNAMENTI_APK_SENZA_DISINSTALLARE.md`.
- Numerazione Android: `applicationId=com.ubicora.app`, `versionCode=440`.

## Architettura prevista per il server

```
Android SQLite v8 (offline)              API HTTPS aziendale                    PostgreSQL
  org_centers / org_suppliers              POST /v1/sync/events           tenants + tenant_members
  org_assets / org_staff_directory   -->  OAuth2/OIDC JWT validato  -->  accepted_events (dedup UUID)
  sync_outbox (solo create catalogs)        tenant permissions RBAC          entity_versions + revision
  ops_orders / movements (locali)          409 conflict; partial ack        audit append-only
```

Il client invia eventi **soltanto se una futura versione collega esplicitamente un server**. Un server deve:

- Verificare token JWT, issuer, audience, scadenza, membership del tenant e ruolo **lato backend**. I valori `proposed_role` locali sono una rubrica, non credenziali o permessi.
- Accettare transazioni idempotenti per `event_id`; inviare gli ID confermati **solo dopo commit**. In caso di timeout il client può ritentare senza doppioni.
- Versionare entità, dare risposte 409 per conflitti e fornire un endpoint incrementale per leggere le modifiche altrui; **pull e risoluzione conflitti NON implementati**.
- Proteggere i dati multi-tenant con isolamento applicativo e RLS; gestire backup server, cancellazione, audit, retention e GDPR.
- Salvare credenziali d'accesso del dispositivo soltanto con Android Keystore, mai in SQLite in chiaro. **Attualmente nessuna credenziale è memorizzata.**
- Associare ogni magazzino e profilo locale a UUID aziendali remoti prima di sincronizzare stock/ordini; i numeri SQLite non sono identità remota.

Riferimenti tecnici: `server/openapi.yaml` e `server/postgresql_schema.sql`. Quest'ultimo è uno **schema di riferimento, non un deployment production ready**.

## Prossime fasi NON completate

- Account personali e approvazioni realmente autorizzate con OIDC e RBAC su backend.
- Completa sincronizzazione bidirezionale di articoli, quantità, ordini, prestiti, foto e documenti, risoluzione conflitti e offline reconciliation.
- Gestione preventivi multipli, DDT, resi, ordini multi-riga, unità fiscali, IVA e fatture.
- Prenotazione materiali per attività, livelli di scorta di sicurezza, lotti, seriali e scadenze.
- Scadenziario manutenzioni, calendari e costo lavoro per asset.
- KPI consolidati tra dispositivi/magazzini con controllo del budget centralizzato.

**Gate di rilascio:** CI verde sul commit, backup 4.3 → 4.4 ripristinabile, test dispositivo Android reale e firma APK compatibile. Non dichiarare la predisposizione un server connesso.

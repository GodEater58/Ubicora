# Ubicora Enterprise Sync: server operativo (non distribuito)

## Portale PC e browser mobile

Il backend serve anche Ubicora Web alla radice. Eseguire `python web/build.py` dalla radice del repository prima dell'avvio/container. Configurazione OIDC del browser, migrazione additiva e diritti PostgreSQL sono descritti in [web/README.md](../web/README.md). Il portale ha un proprio spazio condiviso versionato; non sincronizza automaticamente il database SQLite Android.

Server FastAPI / PostgreSQL con validazione JWT RS256 da provider OIDC, ruoli lato server e `event_id` idempotenti. Nessun account di amministratore di default; nessuna password hardcoded. L'app Android attuale non ha ancora una procedura di login OIDC e non avvia automaticamente la sincronizzazione.

## Configurazione server

1. PostgreSQL 16+: creare un database e applicare `server/postgresql_schema.sql` con un utente dedicato alle MIGRAZIONI, non l'account applicativo.
2. Creare un utente PostgreSQL *non proprietario*, `NOBYPASSRLS`, con accesso CRUD soltanto a `entity_versions` e `accepted_events` e SELECT su `tenant_members`. L'accesso diretto al database non va mai aperto ai telefoni.
3. Con utenza amministrativa effettuare il provisioning esplicito di `tenants` e `tenant_members(tenant_id, subject, role, is_active)`. `subject` è il valore `sub` emesso dal provider OIDC, non il nome scritto nell'app.
4. Configurare le variabili **server-side**: `UBICORA_DATABASE_URL`, `UBICORA_OIDC_ISSUER`, `UBICORA_OIDC_AUDIENCE`, `UBICORA_OIDC_JWKS_URL` (HTTPS).
5. Avviare `uvicorn app:app --host 127.0.0.1 --port 8080` dalla cartella `server/`; esporre solo tramite reverse proxy TLS con HTTPS valido. Per container: `docker build -t ubicora-sync server`.
6. Verificare firewall, rate limiting, rotating secrets, log/audit, backup del DB, retention/GDPR, revoca OIDC e aggiornamenti sicurezza dipendenze prima di impieghi reali.

Il client invia batch fino a 100 eventi (512 KiB) a `POST /v1/sync/events`; il server risponde con ID confermati solo dopo COMMIT. Duplicati identici non riapplicano mutazioni; ID riutilizzati con payload diverso e revisioni concorrenti danno HTTP 409. `GET /v1/sync/changes` espone eventi incrementali filtrati per tenant e ruolo, con cursor. La scrittura serializzata per tenant evita di saltare transazioni tardive.

**Importante:** il backend implementa gli eventi di anagrafiche (centri di costo, fornitori, impianti e rubrica), non movimentazioni di inventario. Non collegarlo a magazzini con dati reali finché non sono pronti la sincronizzazione bidirezionale, la prima associazione degli UUID dei profili e i test end-to-end su più dispositivi. La presenza di una API non rende già multiutente l'app Android.

## Test

```sh
cd server
python -m pip install -r requirements-test.txt
python -m pytest tests -q
```

I test API inclusi verificano contratti, token mancanti e payload; l'integrazione PostgreSQL richiede un database test segregato. Non utilizzare credenziali di produzione.


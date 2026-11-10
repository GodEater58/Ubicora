# Archivio: tabelle e dove sono definite

Il database del telefono è SQLite cifrato (SQLCipher). Le tabelle sono create in più file, ciascuno per la sua area: questa pagina è l'indice unico. Un controllo automatico (`tests/sqlite_contract_test.py`) fallisce se una tabella del codice non è elencata qui.

## Archivio di base

| Tabella | Contenuto | Definita in |
|---|---|---|
| `profiles` | profili con PIN | `StockDb.kt` |
| `warehouses` | magazzini e dimensioni della pianta | `StockDb.kt` |
| `placements` | strutture della mappa (scaffali, armadi, aree) | `StockDb.kt` |
| `items` | articoli, giacenza, ubicazione, foto | `StockDb.kt` |
| `movements` | carichi e prelievi (storico) | `StockDb.kt` |
| `loans` | prestiti aperti e restituiti | `StockDb.kt` |
| `walls` | pareti della pianta | `StockDb.kt` |
| `sku_sequences` | contatori dei codici articolo | `StockDb.kt` |
| `demo_assets` | dati dimostrativi | `StockDb.kt` |

## Squadra

| Tabella | Contenuto | Definita in |
|---|---|---|
| `team_sync_settings` | ruolo e parametri della squadra su questo telefono | `TeamSync.kt` |
| `team_sync_outbox` | modifiche del satellite non ancora confermate | `TeamSync.kt` |
| `team_sync_peers` | satelliti noti al centrale | `TeamSync.kt` |
| `team_sync_conflicts` | modifiche rifiutate dal centrale | `TeamSync.kt` |
| `movement_actors` | lettera del telefono che ha fatto ogni movimento (4.15) | `TeamSync.kt` |
| `team_loans` | prestiti aperti sugli altri telefoni, copia dell'ultimo scambio (4.15) | `TeamSync.kt` |

## Gestione aziendale

| Tabella | Contenuto | Definita in |
|---|---|---|
| `ops_orders` | richieste d'acquisto | `OperationsDb.kt` |
| `ops_counts` | conteggi d'inventario | `OperationsDb.kt` |
| `ops_transfers` | trasferimenti tra magazzini | `OperationsDb.kt` |
| `ops_maintenance` | interventi di manutenzione | `OperationsDb.kt` |
| `ops_events` | registro delle operazioni di gestione | `OperationsDb.kt` |
| `ops_settings` | impostazioni della gestione | `OperationsDb.kt` |
| `org_centers` | centri di costo | `EnterpriseDb.kt` |
| `org_suppliers` | fornitori | `EnterpriseDb.kt` |
| `org_assets` | impianti | `EnterpriseDb.kt` |
| `org_staff_directory` | rubrica del personale | `EnterpriseDb.kt` |
| `sync_outbox` | coda per il server aziendale (non in uso) | `EnterpriseDb.kt` |
| `stock_reservations` | prenotazioni di materiale | `StockPlanningDb.kt` |
| `stock_lots` | lotti | `StockTraceDb.kt` |
| `stock_serials` | numeri di serie | `StockTraceDb.kt` |

## Regole

- Le tabelle di base hanno una versione dello schema e migrazioni in `StockDb.kt` (`onUpgrade`).
- Le tabelle accessorie della 4.15 (`movement_actors`, `team_loans`) sono create con `IF NOT EXISTS` all'apertura, senza cambiare la versione, e non fanno parte del backup: si ricostruiscono con l'uso.
- Il backup cifrato elenca le tabelle che salva in `BackupArchive.kt`.

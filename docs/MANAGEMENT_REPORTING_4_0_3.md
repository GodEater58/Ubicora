# Officina Stock 4.0.3 · Controllo manageriale

## KPI disponibili
- Giacenze a zero, referenze sotto minimo, ubicazioni mancanti, stock valorizzato solo per articoli con costo noto.
- Operazioni di carico e scarico per 7/30/90 giorni, raggruppate in grafici Compose nativi (non sommare quantità di unità diverse).
- Valore noto immobilizzato per categoria (quantità × costo unitario in EUR).
- Priorità di riordino ordinata per stockout e rischio copertura inferiori ai giorni di consegna.
- Valore dei prelievi per articolo (quantità prelevata × costo unitario) e analisi ABC 80/15/5.
- Articoli senza prelievi negli ultimi 90 giorni. Questa misura include gli articoli di nuovo inserimento e non costituisce di per sé un indice di obsolescenza.
- Storico movimenti e prestiti restano separati; movimenti “Assegnato a ...” non alimentano la metrica consumi.

## Nuovi dati e migrazioni
- unit_cost REAL nullable. NULL = costo sconosciuto, da distinguere esplicitamente dal valore 0.
- supplier TEXT facoltativo. lead_days INTEGER nullable, intervallo 0..3650.
- Migrazione incrementale SQLite 3 → 4 senza drop di tabelle o cancellazione fotografie/quantità.
- Dati introducibili durante il censimento e modificabili in seguito nella scheda articolo.

## Limiti dichiarati
- Valore parziale se mancano costi unitari; non è un bilancio contabile o una valorizzazione FIFO.
- ABC basata sui prelievi del periodo e sui costi unitari correnti, non su margini o vendite.
- Mancano ordini d'acquisto, entrate pianificate, costi storici, conteggi ciclici, margini e inventario medio. Pertanto non esponiamo rotazione contabile, servizio fornitore, stockout rate, GMROI, accuratezza fisica o giorni medi di giacenza come se fossero calcolabili.
- Budget di riordino a target personalizzato richiederebbe una policy di scorta massima e dati affidabili sui tempi di fornitura.

## Test
- tests/sqlite_contract_test.py
- tests/photo_storage_contract_test.py
- tests/manager_contract_test.py
- app/src/test/java/com/officinastock/app/ManagerMetricsTest.kt
- GitHub Actions assembla APK Debug dopo i test.

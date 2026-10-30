# Ubicora 4.14.0 — rinomina magazzino e cambio PIN

| Funzione | Dove |
|---|---|
| **Rinomina magazzino** (nome e località) | Impostazioni › Magazzini › «Rinomina questo magazzino», oppure tieni premuto un magazzino nella lista «Dove lavoriamo?». Sul centrale il nuovo nome arriva ai satelliti alla sincronizzazione; sui satelliti si cambia solo dal centrale |
| **Cambia PIN** del profilo in uso | Impostazioni › Sicurezza dell'archivio › «Cambia PIN». Serve il PIN attuale (stessi limiti di tentativi dell'accesso). Un backup fatto prima del cambio contiene ancora il PIN vecchio |

Test: `WarehouseDeleteTest` (rinomina, cambio PIN con PIN giusto/sbagliato).

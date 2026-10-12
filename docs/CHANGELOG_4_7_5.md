# Ubicora 4.7.5 — anagrafica da Excel

## Importazione CSV compatibile con Excel
- **Codice vuoto**: le righe senza codice ricevono il codice successivo della categoria (es. DPI-000001), come quando l'articolo si crea nell'app. I codici scritti nel file vengono riservati per primi, quindi non si scontrano mai con quelli automatici.
- **Righe vuote** del foglio (solo separatori) vengono ignorate.
- **Codifica**: si importano sia i file «CSV UTF-8» sia i «CSV» classici di Excel (Windows): «Quantità», «Unità» e i nomi accentati restano corretti.
- **Separatore**: punto e virgola (Excel italiano) oppure virgola (Excel con altre impostazioni regionali), riconosciuto dalla riga di intestazione.
- Quantità e minimo vuoti valgono 0, unità vuota vale «pz».
- Messaggi d'errore più chiari (categoria o codice non validi, con il numero di riga).

## Modello Excel
`docs/Ubicora_Modello_Anagrafica.xlsx` (generato da `scripts/build_import_template.py`):
- foglio **Articoli** con le 13 colonne esatte, celle gialle da compilare, colonna Codice grigia da lasciare vuota;
- menù a tendina per **Categoria**, **Unità** (prima quelle adatte alla categoria, come nell'app) e **Ubicazione** (dal foglio Strutture);
- controlli su quantità, costi, giorni di consegna, ripiano (1–20) e posizione (1–50);
- fogli **Istruzioni** ed **Esempio**.

Verifiche: test unitari su codice vuoto, righe vuote, separatore virgola, codifica Windows e codici senza collisioni; esportazione CSV del modello controllata con l'intestazione dell'importatore.

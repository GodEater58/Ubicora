# Ubicora 4.19.1 — file più piccoli (nessun cambiamento per chi usa l'app)

Comprende la 4.19.0. Per chi usa l'app non cambia nulla: è solo ordine nel codice.

| File di prima | Ora |
|---|---|
| `TeamSync.kt` (1.112 righe) | `TeamSync.kt` (968: scambio dei file) · `TeamSyncSchema.kt` (tabelle, regole e impostazioni) · `TeamSyncModels.kt` (stato della squadra) |
| `StockViewModel.kt` (1.136) | `StockViewModel.kt` (1.050: le azioni) · `StockUiState.kt` (ciò che le schermate leggono) |
| `MapScreen.kt` (928) | `MapScreen.kt` (734: la pianta) · `MapDialogs.kt` (finestre e piccoli comandi) |

Il contenuto è stato spostato senza modifiche; nelle finestre della mappa le funzioni `private` sono diventate `internal` per restare raggiungibili dall'altro file.

## Cosa resta grande, e perché
`StockDb` (1.238 righe), `StockViewModel` e `TeamSync` sono ciascuno una sola classe, e `NativeMapScreen` una sola funzione: in Kotlin una classe non si divide su più file. Per ridurli vanno riprogettati (per esempio separando da `StockDb` gli articoli, la mappa e i prestiti in classi diverse), e ogni passo va provato sull'app. È un lavoro da fare un pezzo alla volta dopo il collaudo, non insieme a nuove funzioni.

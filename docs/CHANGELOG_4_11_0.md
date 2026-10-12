# Ubicora 4.11.0 — sicurezza della sincronizzazione

| Punto debole | Prima | Ora |
|---|---|---|
| QR di collegamento | Conteneva la password della squadra: chi lo fotografava la otteneva per sempre | Contiene un **codice usa e getta valido 10 minuti**. La password arriva al nuovo telefono solo cifrata con quel codice, dentro il collegamento |
| Password della squadra sul telefono | Salvata in chiaro nel database | **Sigillata con il portachiavi sicuro di Android** (Keystore): una copia del database o un backup ripristinato su un altro telefono non la rivelano. Dopo un ripristino su un telefono nuovo va reinserita una volta (avviso in Impostazioni › Sincronizzazione) |
| Rete del centrale | Accettava messaggi fino a 80 MB da chiunque vicino, senza limiti di collegamenti | Massimo **24 MB**, **6 telefoni** alla volta, collegamenti muti chiusi dopo **60 secondi** |

- I QR stampati con la 4.10 si leggono ancora.
- Lo scambio con i file richiede sempre la password della squadra (il codice del QR vale solo per la rete vicina).
- Le password scritte prima della 4.11 vengono sigillate da sole al primo uso.

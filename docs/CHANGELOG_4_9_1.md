# Ubicora 4.9.1 — correzioni sincronizzazione

| Problema | Correzione |
|---|---|
| Due telefoni nuovi potevano collegarsi con la stessa lettera dalla rete vicina; il secondo veniva bloccato solo dopo aver già creato articoli | La lettera viene prenotata sul centrale al primo collegamento: il secondo telefono è fermato subito con «lettera già usata» |
| Con la password sbagliata il centrale inviava comunque l'archivio (illeggibile) | Il centrale controlla la password prima di inviare e risponde «Password della squadra errata» |
| Un articolo non valido o con codice già presente bloccava per sempre tutti gli invii di quel satellite | L'articolo diventa un conflitto da controllare; il resto delle modifiche passa |
| Su un satellite un CSV con codici del centrale (es. RIC-000050) poteva creare codici in conflitto | Importazione rifiutata con istruzione: lasciare vuoto il Codice |
| Il centrale poteva segnare «Collegamento interrotto» un satellite già sincronizzato (chiusura veloce del collegamento) | Se i dati erano già uniti il satellite risulta sincronizzato |
| Chiudendo la rete durante un'unione, le schermate del centrale non si aggiornavano | Aggiornamento sempre eseguito |
| Messaggio permessi poco chiaro con «posizione approssimativa» | Indica che serve la posizione precisa |

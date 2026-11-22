# Ubicora 4.26.1: revisione di sicurezza

Correzioni della revisione completa fatta con il plugin ecc ([GodEater58/Ubicora#65](https://github.com/GodEater58/Ubicora/pull/65)), portate sulla 4.26.

## App Android

| Prima | Ora |
|---|---|
| Cambiando la data del telefono il blocco dopo 5 PIN sbagliati si annullava subito | Il blocco segue l'orologio interno, che la data non sposta; anche riavviando l'attesa ricomincia |
| Un telefono vicino poteva provare la password della squadra senza limiti | Dopo 5 tentativi sbagliati il centrale attende 1, 2, 4… fino a 60 minuti |
| Un errore momentaneo spegneva l'impronta | L'impronta si spegne solo se non può più funzionare (nuova impronta registrata, chiave persa) |
| Cambiando il PIN l'impronta continuava ad aprire il profilo | Dopo il cambio PIN l'impronta va riattivata (su un telefono condiviso possono esserci dita di altri) |
| Dopo il ripristino di un backup impronte e bozze dell'archivio precedente restavano attive | Il ripristino le azzera |
| Ruotando il telefono durante un ripristino o un'esportazione l'operazione si interrompeva a metà | Prosegue fino alla fine |
| La foto della bozza si perdeva dopo 24 ore, o subito se presa dalla galleria e l'app veniva chiusa | La foto viene copiata nell'app e resta finché c'è la bozza |
| Il PIN digitato si perdeva senza avviso se l'app era occupata | «Accedi» resta disattivato finché l'operazione in corso finisce |
| Backup: 220.000 iterazioni per ricavare la chiave dalla password | Nuovi backup: 600.000 iterazioni (OWASP), con la password di 12 caratteri della 4.25. I backup già fatti si aprono come prima |

## Server e web

| Prima | Ora |
|---|---|
| Un token con chiave inventata riceveva 503 e faceva riscaricare le chiavi al provider a ogni richiesta | Riceve 401; la chiave sconosciuta viene ricordata per 5 minuti |
| Un testo con carattere nullo bloccava per sempre un intero invio («storage non disponibile») | Rifiutato subito con 400 |
| Un centro di costo poteva essere eliminato mentre un cespite lo usava, se il riferimento era scritto in maiuscolo | Il riferimento deve essere nella forma standard |
| Un VIEWER vedeva costi, prezzi e budget dello spazio web | Li riceve vuoti |
| Documenti grandi bloccavano il server per tutti durante la lettura, anche da utenti senza permesso di scrittura | Prima si controlla il ruolo, poi si legge il documento fuori dal ciclo principale |
| Oltre circa 8 MB lo spazio condiviso non salvava più nulla | Vicino al limite i movimenti più vecchi vengono scaricati in un file e tolti dallo storico condiviso, con conferma |
| Un errore del proxy (pagina HTML) faceva ritentare il salvataggio all'infinito; una connessione appesa bloccava la pagina | Lo stato dell'errore resta; dopo 30 secondi la richiesta termina |
| Sessione scaduta: modifica non confermata persa all'uscita | Viene scaricata come file prima di uscire o cambiare spazio |
| La pagina servita poteva connettersi a qualunque sito HTTPS | Solo al server e al provider di accesso configurato |
| Contratto OpenAPI incompleto | Descrive tutti gli endpoint, `expected_version` e le risposte 503 |

Sincronizzazione della squadra: i file tra telefoni restano nel formato precedente, così telefoni con versioni diverse continuano a capirsi.

Già presenti prima e quindi non ripetute: la bozza del nuovo articolo che resta aperta (4.21.1, 4.24.1), «Annulla» che non la fa ricomparire (4.21.2), la password di 12 caratteri (4.25). Per l'articolo il cui posto sparisce durante il salvataggio resta la scelta della 4.24.1: l'articolo è salvato con la foto e compare «assegnalo dalla mappa».

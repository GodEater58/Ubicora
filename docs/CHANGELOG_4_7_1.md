# Ubicora 4.7.1 / Web 1.0.5

## Novità

### Pareti che chiudono la forma (Android e Web)
- Disegnando una parete, se la fine arriva vicino a un **angolo esistente** (o sopra) si aggancia a quel punto e la forma si **chiude da sola**: il tocco successivo inizia una forma nuova. Così si disegnano in pochi tocchi quadrati, rettangoli, triangoli, colonne e altri ingombri.
- Se la fine arriva vicino a una **parete esistente** si aggancia sul punto della parete e chiude anche in quel caso (utile per ingombri addossati al muro).
- L'angolo ha la precedenza su griglia e modalità ortogonale: con l'ortogonale attivo si può comunque chiudere un triangolo in diagonale.
- Anteprima: quando la parete sta per chiudere, il punto finale diventa **verde** (Android) o la linea diventa verde con la scritta «chiude la forma» (Web). Alla chiusura compare «Forma chiusa».
- Il raggio di aggancio è circa un polpastrello sullo schermo, al massimo 35 cm e mai più del 45% della parete che si sta disegnando, quindi anche le colonne piccole si disegnano senza chiusure involontarie (per forme molto piccole conviene ingrandire).
- La parete appena disegnata non può agganciarsi al proprio punto di partenza (niente forme a superficie zero).

### CSV con ripiano e posizione
- Il CSV esportato ha due colonne in più in fondo: **Ripiano** (1–20) e **Posizione** (1–50).
- L'importazione accetta sia il nuovo formato (13 colonne) sia quello delle versioni precedenti (11 colonne).
- Android: se la struttura di destinazione ha meno ripiani o posizioni di quelli indicati, l'importazione viene rifiutata per intero, senza scrivere nulla, con un messaggio che dice cosa impostare.
- Web: ripiano e posizione vengono conservati e mostrati accanto all'ubicazione (es. «Scaffale A · R2 P3»); spostando l'articolo su un'altra struttura vengono azzerati. Il server accetta i due campi.

### Schede articolo a pagina intera (Android)
- Scheda articolo, Nuovo articolo e Modifica articolo si aprono a schermo intero, con titolo in alto e il pulsante principale (Conferma / Salva) fisso in basso: più spazio per i campi e per la tastiera.

## Verifiche
- Android: test unitari su aggancio pareti (quadrato, triangolo con ortogonale, parete appena disegnata, aggancio su parete, griglia) e su CSV (lettura colonne, file a 11 colonne, valori fuori limite, ripiano senza ubicazione, esportazione → importazione con struttura troppo piccola rifiutata).
- Web: test core (aggancio e CSV) e test nel browser che disegna un triangolo chiudendolo vicino al primo angolo.
- Server: test dello schema con ripiano/posizione.

## Limiti
- Nessuna prova su telefono reale in questa sessione: aggancio e pagine intere vanno provati sul dispositivo.
- Sul Web l'aggancio su una parete obliqua viene poi arrotondato alla griglia di 25 cm, quindi il punto può discostarsi leggermente dalla parete.
- Il Web non gestisce ancora ripiani e posizioni delle strutture: li conserva soltanto.

# Ubicora 4.6: indicatori verificabili

In modalità Aziendale aprire il Centro operativo → Decisioni.

- Acquisti: distribuzione per stato di tutte le richieste del magazzino. Toccare una barra apre Acquisti.
- Scorte critiche: quantità fisica meno tutte le prenotazioni attive, confrontata con il minimo. Prenotazioni mostra disponibilità e impegni.
- Lotti: solo stock residuo; scaduti e scadenze nei prossimi 30 giorni. Toccare apre Lotti e seriali.
- Budget: impegni delle richieste approvate, ordinate e ricevute create nel mese, su TUTTI i magazzini del profilo. Bozze e annullamenti esclusi. Non misura pagamenti o fatturato. Prezzi sconosciuti rendono il residuo parziale.
- Manutenzione: completamenti nel periodo selezionato, costo ricambi acquisito al completamento più ore × tariffa. Costi incompleti esclusi e conteggiati esplicitamente. Zero ore implica zero manodopera, anche senza tariffa. Nessuna quantità kg/metri/pezzi sommata insieme.
- Inventari: quota dei conteggi confermati e creati nel periodo che non avevano scostamento. È un campione, non una certificazione di accuratezza del magazzino.

I consuntivi sono selezionabili a 7/30/90 giorni. Scorte, stati aperti e scadenze sono indicatori attuali, il budget è mensile: le etichette distinguono gli ambiti.

I totali usano aggregazioni complete SQLite, fuori dal thread UI. Le liste operative restano limitate alle 250 righe recenti: un totale completo non implica che ogni vecchia operazione sia già visualizzata nella lista. Il campione visuale per macchina mostra le prime sei voci per costo noto.

Le firme degli APK debug possono variare; non disinstallare il telefono con dati reali per provarli. Login multiutente verificato e sync inventario non sono ancora attivi. I test OIDC esercitano firme RS256 reali tramite la dipendenza HTTP di autenticazione; solo il trasporto JWKS è simulato. Il provider reale e i test su più telefoni restano da collaudare.

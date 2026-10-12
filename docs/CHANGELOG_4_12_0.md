# Ubicora 4.12.0 — archivio cifrato sul telefono

- Tutto l'archivio (articoli, movimenti, profili, mappe, sincronizzazione…) è cifrato con **SQLCipher, AES-256**.
- La chiave è casuale (256 bit) e custodita dal **portachiavi sicuro di Android** (Keystore): copiando i file dal telefono i dati non si leggono.
- **Migrazione automatica** al primo avvio: copia cifrata → verifica tabella per tabella e controllo d'integrità → solo allora il file in chiaro viene eliminato. Se qualcosa va storto l'archivio resta com'era e l'app continua a funzionare (avviso in Impostazioni › Sicurezza dell'archivio); un'interruzione a metà (telefono spento) viene completata all'avvio successivo.
- Impostazioni › **Sicurezza dell'archivio** mostra se l'archivio è cifrato.
- Il **backup completo** resta il modo per passare a un altro telefono: la chiave non lascia mai il telefono.
- Verificato su emulatore Android (test strumentato dedicato: migrazione con 250 articoli, archivio nuovo, migrazione interrotta).

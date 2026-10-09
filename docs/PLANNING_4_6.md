# Ubicora 4.6 | Pianificazione e tracciabilità offline

Questa guida descrive funzionalità **concrete sul telefono**, non una sincronizzazione cloud già distribuita.

## Scorta fisica, prenotata e disponibile (4.5)

- **Fisica:** ciò che è realmente nel magazzino.
- **Prenotata:** quantità impegnata per una commessa/intervento e non ancora prelevata.
- **Disponibile:** fisica − prenotata.
- Prenotare non varia l'inventario fisico; **Consuma** scarica e registra il movimento, **Libera** annulla l'impegno senza scarichi.
- I normali prelievi, prestiti, trasferimenti, inventari e manutenzioni non possono intaccare la quota prenotata.

## Lotti e seriali (4.6)

- Dalla scheda Aziendale → **Lotti e seriali**, registrare un lotto con quantità e data di scadenza facoltativa, oppure un numero di serie per un articolo con unità `pz`.
- La registrazione **non crea giacenze**: può identificare soltanto quantità fisicamente presenti e non già etichettate.
- Un articolo può usare lotti oppure seriali, non entrambi contemporaneamente.
- Un movimento non tracciato viene bloccato se ridurrebbe le quantità fisiche sotto quelle ancora associate a lotti/seriali disponibili.
- Il consumo di un lotto scarica contestualmente lotto e inventario; l'assegnazione di un seriale scarica una singola unità con riferimento in cronologia.
- Un lotto scaduto **non può essere consumato**: si registra lo **scarto** in modo esplicito e auditabile.
- Il conteggio delle scorte e il backup sono coerenti con la tracciabilità. Numeri di serie e lotti restano accessibili nella cronologia locale.

**Attenzione:** manca ancora la gestione multi-sede dei lotti e il trasferimento di seriali tra magazzini; le normali spedizioni non possono bypassare il tracciamento. Non usare questa versione come sistema normativo o farmaceutico certificato.

## Manutenzione economica (4.5)

- Gli ordini di lavoro possono essere collegati a un impianto dell'anagrafica aziendale.
- Ore lavorate e tariffa oraria sono facoltative; il costo del ricambio è acquisito al momento del completamento e non cambia retroattivamente se il prezzo cambia.
- Se non è noto il prezzo, il consuntivo completo resta **non noto**: non viene inventato un costo nullo.

## Database, backup e update APK

- 4.5: SQLite v9, backup 21 tabelle compatibile con 9/15/20.
- 4.6: SQLite v10, backup 23 tabelle compatibile con 9/15/20/21.
- Tutte le migrazioni sono additive: non cancellano volontariamente l'inventario.
- Prima di aggiornare un telefono con dati reali, verificare firma APK compatibile e backup cifrato testato. **Una build debug GitHub non è un aggiornamento firmato della versione precedente.**
- Il server PostgreSQL e l'API OIDC sono codice separato, ma la sincronizzazione Android bidirezionale non è ancora attivata. I profili locali non sono account verificati dal server.

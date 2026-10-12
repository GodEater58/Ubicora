# Ubicora 4.8.0 — sincronizzazione tra telefoni senza server (tappa 1)

Più persone usano l'app sullo stesso magazzino. Un telefono è il **centrale**, gli altri sono **satelliti** (lettere B…Z). Lavorano offline e si scambiano **file cifrati** (WhatsApp, email, USB) con la password della squadra.

## Come si usa
1. **Centrale** — Impostazioni → Sincronizzazione → *Rendi questo il telefono centrale* (scegli la password della squadra) → *Invia aggiornamento a tutti*.
2. **Satellite** — Impostazioni → *Collega a un telefono centrale* → password + lettera → scegli il file ricevuto. Si crea il magazzino con mappa, articoli e giacenze del centrale.
3. Quando capita: il satellite fa *Invia al centrale*; il centrale *Importa dai satelliti* e poi *Invia aggiornamento a tutti*; i satelliti *Importa aggiornamento dal centrale*.

## Cosa si sincronizza (tappa 1)
| Dal satellite al centrale | Dal centrale a tutti |
|---|---|
| Articoli nuovi (codici con la lettera, es. RIC-B000001) | Mappa: strutture, ripiani, pareti, dimensioni |
| Carichi, prelievi, rettifiche, e ogni altro movimento di giacenza | Articoli con dati e giacenze |
| Spostamenti di ubicazione | Elenco di cosa è già stato ricevuto da ogni satellite |

## Regole che garantiscono giacenze corrette
- Le giacenze si uniscono **sommando i movimenti**, mai copiando le quantità: 10 − 3 (B) − 2 (centrale) = 5.
- Ogni modifica del satellite ha un numero progressivo: **importare due volte lo stesso file non cambia nulla**; mancando un file intermedio, l'import viene rifiutato con un messaggio.
- Il file del satellite contiene **tutto quello che il centrale non ha ancora confermato**: non importa ogni quanto vi scambiate i file.
- Quando un satellite riceve l'aggiornamento, le sue **modifiche non ancora inviate restano** sopra le giacenze del centrale.
- Un prelievo impossibile (giacenza insufficiente, materiale prenotato) **non rende la giacenza negativa**: diventa un **conflitto** nell'elenco del centrale; al primo aggiornamento il satellite si riallinea.
- Sui satelliti la **mappa è in sola lettura** e la demo è disattivata.
- Una lettera è di un solo telefono: un secondo telefono con la stessa lettera viene rifiutato.

## Dati e compatibilità
- Database v12, migrazione additiva: identificativo globale per articoli e strutture (assegnato anche ai dati esistenti), coda delle modifiche alimentata da trigger SQLite (copre ogni operazione che muove la giacenza).
- Il backup cifrato include il ruolo nella sincronizzazione; i backup precedenti si ripristinano normalmente. **Non ripristinare lo stesso backup su due telefoni che restano in uso.**

## Limiti della tappa 1
- Non si sincronizzano: foto, prestiti come elenco (si sincronizza il movimento di giacenza), lotti/seriali, ordini, conteggi, manutenzioni, modifiche ai dati anagrafici fatte sui satelliti (vince il centrale).
- Gli articoli non si eliminano tra telefoni.

## Verifiche
Test con tre database (centrale + satelliti B e C): partenza dal centrale con mappa e ubicazioni, somma dei movimenti da più telefoni, doppia importazione senza effetti, modifiche in sospeso conservate all'aggiornamento, convergenza di due satelliti con codici distinti, conflitto senza giacenze negative, controlli di sicurezza (password, lettera doppia, ruoli, uscita con modifiche non inviate).

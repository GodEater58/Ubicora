# Ubicora 4.3 Manager — guida funzioni e limiti

**Stato**: Alpha di sviluppo, branch `feature/ubicora-manager-operations`. Non usare come sostituzione dell'app con dati reali prima di CI verde, prova upgrade/restore su Android e firma release compatibile.

## Modalità personale e aziendale
Da **Impostazioni → Modalità d'uso** scegli Personale o Aziendale. La preferenza è separata per ciascun profilo e persistente in SQLite; puoi tornare alla modalità personale senza perdere dati manageriali. In modalità Aziendale appare nella Home il **Centro operativo**. L'archivio rimane locale e offline.

## Centro operativo
- **Decisioni**: acquisiti aperti, conteggi da approvare, trasferimenti in corso, interventi aperti, articoli critici e quadro budget.
- **Acquisti**: crea richiesta indicando articolo, quantità, fornitore e responsabile. Procedura `Bozza → Approvato → Ordinato → Ricevuto` (o annullato). **La giacenza sale solo quando confermi il ricevimento**. Le richieste registrano costo e fornitore disponibili all'atto della creazione; i costi non valorizzati non vengono inventati.
- **Inventari ciclici**: registri il conteggio fisico e il motivo; rimane `PENDING` finché non premi *Conferma rettifica*. Se la quantità a sistema è cambiata dall'avvio, viene rifiutato il vecchio conteggio: occorre rifarlo. Ogni variazione positiva/negativa crea un movimento verificabile.
- **Trasferimenti**: richiedi da un magazzino a un altro **dello stesso profilo**. La spedizione scarica l'origine e mette `IN_TRANSIT`; la ricezione, dal magazzino destinatario, carica la destinazione. Puoi annullare l'invio e reintegrare l'origine finché non risulta ricevuto. Se manca l'articolo in destinazione lo crea con lo stesso SKU; non copia automaticamente la foto privata del magazzino origine.
- **Manutenzioni**: specifica macchina, descrizione, e se necessario ricambio e quantità. Il materiale viene scalato soltanto quando completi l'intervento; la mancanza di quantità sufficiente impedisce la chiusura.
- **Registro**: gli eventi manageriali sono registrati in SQLite con data, azione, profilo e riferimento. Limite UI delle ultime 80 operazioni; backup conserva tutto.
- **Report**: icona *Esporta* in Centro operativo crea un CSV apribile in Excel: ordini, conteggi, trasferimenti, interventi e scorte critiche (fino alle ultime 250 voci per tipologia mostrata, non report contabile esaustivo).

## Budget
In **Impostazioni** puoi inserire un budget mensile per profilo. La schermata Decisioni confronta il limite con il valore noto delle richieste approvate/ordinate/ricevute nel mese. È **un indicatore di impegno**, non un blocco agli acquisti e non un sostituto di contabilità, IVA o consuntivi. Se mancano prezzi la stima è parziale.

## Dati, backup, sicurezza
- Database SQLite aggiornato additivamente dallo schema 6 al 7: niente reset di inventario, profili e planimetrie.
- Il backup cifrato include `ops_orders`, `ops_counts`, `ops_transfers`, `ops_maintenance`, `ops_events`, `ops_settings` e mantiene la lettura dei backup schema v1 legacy senza queste sei tabelle.
- La modalità Aziendale non sincronizza i telefoni e **non introduce account professionali autenticati, ruoli differenziati o firme di approvazione legalmente verificabili**. Il profilo è un ambito di dati locale, non una prova dell'identità della persona che ha approvato un ordine.
- Confronta sempre il certificato dell'APK con quello dell'app Android già installata; non disinstallare senza un backup testato.
- Non sono ancora implementati gestione dettagliata delle consegne parziali, storico prezzi dei fornitori, preventivi multipli, dashboard inter-magazzino condivisa, notifiche push o previsioni AI.

# Ubicora 4.12.1 — sincronizzazione vicina e magazzini

| Problema | Correzione |
|---|---|
| Dopo la sincronizzazione automatica tra telefoni vicini restava a schermo «Operazione in corso…» per sempre (su centrale e satellite) e tutti i pulsanti erano bloccati, anche se i dati erano già stati uniti | Al termine del ricaricamento dopo lo scambio l'app chiude sempre l'indicatore di attesa, anche in caso di errore |
| Non c'era modo di eliminare un magazzino creato | Impostazioni › Magazzini › «Elimina questo magazzino»: conferma scrivendo il nome; cancella articoli, foto, movimenti, prestiti, planimetria e dati gestionali. Bloccato se il magazzino è sincronizzato con il team (prima «Scollega questo telefono») |

Test: `WarehouseDeleteTest` (nome errato, eliminazione completa, magazzino sincronizzato protetto).

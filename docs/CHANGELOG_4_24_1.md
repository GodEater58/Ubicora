# Ubicora 4.24.1: backup e squadra più sicuri

| Prima | Ora |
|---|---|
| Ripristinando il backup di un satellite su un telefono nuovo, i prelievi fatti dopo venivano scartati dal centrale e poi persi | Dopo il ripristino il telefono continua a inviare: i nuovi prelievi arrivano al centrale |
| Dopo certe sincronizzazioni (prenotazioni sopra la giacenza, ripiano tolto su un altro telefono) il backup si esportava ma non si ripristinava | Il ripristino accetta questi stati; un ripiano che non esiste più viene tolto, l'articolo resta sulla sua struttura |
| Un backup poteva contenere al massimo 300.000 righe per tabella (32 MB) e veniva caricato tutto in memoria | Nessun limite per tabella (solo quello totale di 4 GB); il ripristino legge una riga alla volta. I backup vecchi si ripristinano come prima |
| Le modifiche alle schede non ancora validate non erano nel backup; i prestiti visti dagli altri telefoni restavano dopo un ripristino | Le modifiche viaggiano nel backup; i prestiti degli altri telefoni tornano con lo scambio successivo |
| Il backup leggeva le tabelle una alla volta: un file della squadra arrivato a metà poteva renderlo incoerente | Tutte le tabelle sono lette insieme, in un'unica fotografia |
| Codici scambiati tra due articoli sul centrale bloccavano per sempre gli aggiornamenti dei satelliti | Lo scambio arriva ai satelliti |
| Toccando un pulsante mentre l'app era occupata (sincronizzazione), il prelievo non veniva registrato senza alcun avviso | Compare «Operazione in corso: non registrato, riprova» |
| Se il posto scelto per un nuovo articolo spariva nel frattempo, l'articolo restava senza foto e risalvandolo si creava un doppione | L'articolo resta salvato con la foto e compare «assegnalo dalla mappa» |
| Una sincronizzazione arrivata mentre si compilava un nuovo articolo chiudeva il modulo | Il modulo resta aperto |
| Un articolo prestato da un altro telefono si poteva eliminare | Non si elimina finché il prestito è aperto |
| Un telefono vicino poteva mandare al centrale molti file insieme | Un file alla volta per collegamento |
| Un ripristino interrotto lasciava copie leggibili nella cache | Ripulite all'avvio |

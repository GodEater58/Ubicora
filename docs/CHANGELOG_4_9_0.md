# Ubicora 4.9.0 — sincronizzazione automatica tra telefoni vicini e reparti

Il telefono centrale apre una **sua rete** (Google Nearby: Bluetooth + Wi-Fi diretto, niente router né internet).
I satelliti vicini con Ubicora aperta lo trovano da soli e fanno lo scambio, con animazione.

| Dove | Cosa fare |
|---|---|
| Centrale | Impostazioni › Sincronizzazione › **Apri la rete per i satelliti vicini** |
| Satellite | Impostazioni › Sincronizzazione › **Sincronizza con il centrale vicino** |
| Nuovo telefono | «Collega a un telefono centrale» › password + lettera › **Cerca il centrale vicino** |

- Stesso contenuto e stessa cifratura dei file `.ubisync`: i due modi si possono alternare.
- Uno scambio interrotto si ripete senza doppioni.
- Il centrale gestisce più satelliti uno dopo l'altro e mostra lo stato di ognuno.
- Lo schermo resta acceso finché la rete è aperta; la rete si chiude se l'app va in background.
- Permessi richiesti al primo uso: «Dispositivi nelle vicinanze» e «Posizione» (Android li impone per Bluetooth/Wi-Fi diretto).
- Richiede i servizi Google Play; senza, resta lo scambio con i file.

## Reparti degli operatori

Il centrale assegna a ogni satellite (lettera B–F) le sue categorie, es. B = Meccanica + Pneumatica, C = Elettrico.

| | Nel suo reparto | Fuori reparto |
|---|---|---|
| Vedere gli articoli | ✅ | ✅ |
| Prelevare | ✅ | ✅ |
| Annullare un proprio prelievo / restituire un prestito | ✅ | ✅ |
| Creare articoli, caricare, rettificare, spostare | ✅ | ❌ |

- Il reparto arriva al satellite al primo scambio successivo (rete o file).
- Doppio controllo: il satellite blocca subito (anche a livello di database), il centrale scarta comunque come conflitto
  ciò che arriva fuori reparto, anche da telefoni con versioni vecchie.
- Nel nuovo articolo il satellite vede solo le categorie del suo reparto.

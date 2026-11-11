# Ubicora 4.19.0 — trasferimenti tra sedi e permessi dei satelliti

Comprende la 4.18.0, la 4.17.0 e la 4.16.2.

## Novità
| Funzione | Dettagli |
|---|---|
| **Sposta in un'altra sede** | Tieni premuto un articolo › «Sposta in un'altra sede…»: scegli la sede e la quantità. I pezzi escono da una sede ed entrano nell'altra nello stesso momento, con due movimenti («Trasferito a…», «Trasferito da…») |
| **Stesso articolo nelle due sedi** | Nell'altra sede l'articolo è quello con lo stesso codice o, se manca, l'unico con stesso nome, categoria e unità. Se non c'è, il centrale lo crea con lo stesso codice |
| **Permessi dei satelliti** | Sul centrale: Squadra › «Modifica» accanto a un telefono. Tre livelli: **Solo prelievo** (preleva e presta), **Operatore di reparto** (tutto nelle categorie scelte, fuori solo prelievo), **Magazziniere** (tutto in ogni categoria). Arrivano al prossimo scambio |

## Comportamento da conoscere
- Da un satellite si trasferisce solo verso un articolo che nell'altra sede esiste già: se manca, va creato dal centrale (basta un primo trasferimento fatto da lì). Evita che due telefoni lo creino insieme senza saperlo.
- Un satellite trasferisce solo dove può caricare: «Solo prelievo» non trasferisce, un operatore di reparto solo nelle sue categorie.
- «Solo prelievo» viaggia come un reparto senza categorie: lo rispettano anche i satelliti con una versione precedente, e il centrale lo ricontrolla quando importa.
- Un trasferimento non ha «Annulla»: per tornare indietro si fa il trasferimento opposto.

## Non incluso
Permessi che concedono più di oggi (modificare le schede degli articoli o la pianta da un satellite) e permessi diversi per sede: richiedono che quelle modifiche viaggino dal satellite al centrale.

Test: `TeamSyncTest` (trasferimento sul centrale e da un satellite, satellite di solo prelievo). Non verificato su dispositivo.

# Ubicora 4.7.4 — unità di misura da menù

- **Nuovo articolo**: l'unità di misura non si scrive più a mano. Un menù a tendina propone prima le unità adatte alla categoria, poi tutte le altre:

| Categoria | Unità proposte per prime (la prima è quella predefinita) |
|---|---|
| Ricambi | pz, set, paia, conf |
| Utensili | pz, set |
| Elettrico | pz, m, rotoli, conf |
| Pneumatica | pz, m, set, conf |
| Idraulica | pz, m, l, set |
| Consumabili | conf, pz, kg, l, rotoli, g, ml, m |
| DPI | paia, pz, conf |
| Meccanica | pz, kg, m, set |
| Altro | pz |

- Cambiando categoria, l'unità resta se è adatta alla nuova categoria, altrimenti passa a quella predefinita.
- Unità disponibili: pz, paia, set, conf, scatole, rotoli, m, cm, mm, m², kg, g, l, ml.
- **Modifica articolo**: stesso menù. Un'unità scritta a mano nelle versioni precedenti resta visibile in cima e selezionabile. Restano le regole di sicurezza: l'unità si cambia solo con giacenza zero e senza operazioni aperte.
- Il CSV e il Web continuano ad accettare qualsiasi unità esistente: niente dati rifiutati.

Verifiche: test unitari sull'elenco per categoria, sul cambio di categoria e sulle unità scritte a mano.

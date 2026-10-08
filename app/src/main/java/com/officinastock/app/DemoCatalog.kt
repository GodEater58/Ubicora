package com.officinastock.app

/** Scenario deterministico: dati fittizi credibili, non contiene informazioni personali. */
data class DemoItem(
    val name:String, val category:String, val stock:Double, val minimum:Double, val unit:String,
    val cost:Double?, val supplier:String, val leadDays:Int?, val placement:Int?,
    val used90:Double, val received30:Double, val onLoan:Double, val illustration:Boolean, val note:String
)
object DemoCatalog {
    val items:List<DemoItem> = listOf(
        DemoItem("Cuscinetto 6204 2RS", "Ricambi", 14.0, 8.0, "pz", 8.9, "SKF Distribuzione", 5, 0, 28.0, 4.0, 0.0, true, "Ricambio motore linea A."),
        DemoItem("Cuscinetto 6205 ZZ", "Ricambi", 3.0, 8.0, "pz", 12.5, "SKF Distribuzione", 7, 0, 19.0, 6.0, 0.0, true, "Cuscinetto supporto albero; sottoscorta."),
        DemoItem("Cinghia dentata T10 3220 mm", "Ricambi", 2.0, 4.0, "pz", 148.0, "Trasmissioni Veneto", 18, 0, 8.0, 0.0, 0.0, true, "Ricambio critico azionamento."),
        DemoItem("Cinghia trapezoidale SPZ 1000", "Ricambi", 0.0, 5.0, "pz", 16.8, "Trasmissioni Veneto", 8, 0, 24.0, 5.0, 0.0, false, "Articolo esaurito: test riordino urgente."),
        DemoItem("Filtro aria compressore", "Ricambi", 6.0, 4.0, "pz", 45.0, "Compressori Service", 12, 3, 7.0, 0.0, 0.0, true, "Manutenzione compressori 45 kW."),
        DemoItem("Filtro olio compressore", "Ricambi", 5.0, 6.0, "pz", 32.4, "Compressori Service", 10, 3, 11.0, 2.0, 0.0, false, "Consumo periodico."),
        DemoItem("Giunto elastico 28 mm", "Ricambi", 10.0, 3.0, "pz", 35.0, "Meccatronica Ricambi", 12, 0, 0.0, 0.0, 0.0, false, "Ricambio fermo da oltre 90 giorni."),
        DemoItem("Pompa dosatrice 24 V", "Ricambi", 1.0, 2.0, "pz", 520.0, "Automazione Service", 24, 2, 3.0, 0.0, 0.0, true, "Ricambio ad alto valore."),
        DemoItem("Sensore induttivo M18 PNP", "Elettrico", 3.0, 6.0, "pz", 36.0, "Automazione Service", 12, 2, 17.0, 5.0, 0.0, true, "Sensore prossimità."),
        DemoItem("Sensore fotoelettrico PNP", "Elettrico", 7.0, 4.0, "pz", 89.0, "Automazione Service", 14, 2, 12.0, 0.0, 0.0, true, "Sensore linea imballaggio."),
        DemoItem("Relè 24 VDC 2 scambi", "Elettrico", 20.0, 8.0, "pz", 11.2, "Elettroforniture", 3, 2, 29.0, 10.0, 0.0, false, "Quadri elettrici e comando."),
        DemoItem("Fusibile rapido 10 A", "Elettrico", 44.0, 15.0, "pz", 1.4, "Elettroforniture", 2, 2, 75.0, 30.0, 0.0, false, "Alta rotazione."),
        DemoItem("Contattore 18 A", "Elettrico", 2.0, 3.0, "pz", 68.0, "Elettroforniture", 9, 2, 7.0, 0.0, 0.0, false, "Scorta critica."),
        DemoItem("Inverter 0,75 kW", "Elettrico", 1.0, 1.0, "pz", 385.0, "Automazione Service", 30, 2, 0.0, 0.0, 0.0, true, "Ricambio di alto valore, no movimentazioni."),
        DemoItem("Interruttore finecorsa", "Elettrico", 8.0, 4.0, "pz", 18.5, "Elettroforniture", 5, 2, 13.0, 0.0, 0.0, false, "Finecorsa meccanico."),
        DemoItem("Chiave combinata 13 mm", "Utensili", 5.0, 2.0, "pz", 13.0, "Beta Tools", 5, 1, 0.0, 0.0, 1.0, true, "Una chiave in prestito demo."),
        DemoItem("Avvitatore 18 V", "Utensili", 2.0, 1.0, "pz", 220.0, "Utensili Veneto", 15, 1, 0.0, 0.0, 1.0, true, "Un elettroutensile assegnato a manutentore."),
        DemoItem("Trapano a colonna", "Utensili", 1.0, 1.0, "pz", 490.0, "Utensili Veneto", 15, 7, 0.0, 0.0, 0.0, true, "Bene durevole."),
        DemoItem("Punta HSS Ø6", "Utensili", 9.0, 4.0, "pz", 3.3, "Beta Tools", 4, 1, 12.0, 4.0, 0.0, false, "Consumabile usato in attrezzeria."),
        DemoItem("Calibro digitale 150 mm", "Utensili", 3.0, 2.0, "pz", 42.0, "Beta Tools", 5, 1, 0.0, 0.0, 0.0, true, "Strumento dimostrativo per restituzione prestiti."),
        DemoItem("Kit chiavi a brugola", "Utensili", 4.0, 2.0, "pz", 26.0, "Beta Tools", 5, 1, 0.0, 0.0, 0.0, false, "Prestito restituito presente nello storico."),
        DemoItem("Raccordo pneumatico 1/4", "Pneumatica", 5.0, 10.0, "pz", 3.8, "Pneumatica Rovigo", 4, 3, 50.0, 18.0, 0.0, true, "Sottoscorta, test acquisti."),
        DemoItem("Elettrovalvola 5/2 24 V", "Pneumatica", 2.0, 3.0, "pz", 74.0, "Pneumatica Rovigo", 10, 3, 8.0, 0.0, 0.0, true, "Azionamenti pneumatici."),
        DemoItem("Tubo poliuretano Ø8", "Pneumatica", 82.0, 30.0, "m", 1.9, "Pneumatica Rovigo", 3, 3, 105.0, 40.0, 0.0, false, "Quantità misurata in metri."),
        DemoItem("Manometro 0-10 bar", "Pneumatica", 4.0, 3.0, "pz", 28.0, "Pneumatica Rovigo", 7, 3, 3.0, 0.0, 0.0, false, "Verifica pressione aria."),
        DemoItem("Guarnizione NBR 30 mm", "Meccanica", 18.0, 8.0, "pz", 2.4, "Guarnizioni Italia", 6, 0, 21.0, 0.0, 0.0, false, "Ricambi montaggi."),
        DemoItem("Vite TCEI M8 x 35", "Meccanica", 240.0, 40.0, "pz", 0.18, "Ferramenta Industriale", 2, 0, 90.0, 100.0, 0.0, true, "Alta disponibilità e utilizzo."),
        DemoItem("Dado autobloccante M8", "Meccanica", 0.0, 60.0, "pz", 0.08, "Ferramenta Industriale", 4, 0, 125.0, 0.0, 0.0, false, "Articolo esaurito."),
        DemoItem("Rondella M8 zincata", "Meccanica", 540.0, 100.0, "pz", 0.04, "Ferramenta Industriale", 2, 0, 180.0, 150.0, 0.0, false, "Quantità alta per test giacenze."),
        DemoItem("Olio idraulico ISO VG46", "Idraulica", 32.0, 20.0, "l", 7.9, "Oleodinamica Servizi", 7, 4, 45.0, 18.0, 0.0, true, "Litri: non sommare ai pezzi nei grafici."),
        DemoItem("Raccordo oleodinamico 3/8", "Idraulica", 6.0, 5.0, "pz", 14.5, "Oleodinamica Servizi", 9, 4, 10.0, 4.0, 0.0, false, "Raccordi idraulici."),
        DemoItem("Filtro idraulico ritorno", "Idraulica", 3.0, 4.0, "pz", 85.0, "Oleodinamica Servizi", 21, 4, 7.0, 0.0, 0.0, true, "Scorta minima superata."),
        DemoItem("Grasso multiuso NLGI 2", "Consumabili", 14.0, 7.0, "kg", 9.2, "Lubrificanti Veneto", 5, 5, 16.0, 8.0, 0.0, false, "Gestione in chilogrammi."),
        DemoItem("Panno industriale rotolo", "Consumabili", 25.0, 10.0, "pz", 8.3, "Pulizia Industriale", 4, 5, 60.0, 24.0, 0.0, false, "Alta frequenza prelievi."),
        DemoItem("Detergente parti meccaniche", "Consumabili", 18.0, 8.0, "l", 4.9, "Pulizia Industriale", 5, 5, 34.0, 9.0, 0.0, false, "Gestione in litri."),
        DemoItem("Guanti nitrile taglia L", "DPI", 5.0, 20.0, "conf", 11.0, "Sicurezza Lavoro", 4, 6, 55.0, 10.0, 0.0, true, "Scorta critica sicurezza."),
        DemoItem("Occhiali protettivi", "DPI", 19.0, 10.0, "pz", 7.2, "Sicurezza Lavoro", 7, 6, 14.0, 8.0, 0.0, true, "DPI anticaduta particelle."),
        DemoItem("Mascherina FFP2", "DPI", 120.0, 40.0, "pz", 0.48, "Sicurezza Lavoro", 3, 6, 66.0, 34.0, 0.0, false, "Dispositivo protezione respiratoria."),
        DemoItem("Batteria tampone CR2032", "Altro", 14.0, 5.0, "pz", 1.2, "Distribuzione Elettronica", 5, 8, 7.0, 0.0, 0.0, false, "Materiale accessorio generico."),
        DemoItem("Scatola minuteria 12 scomparti", "Altro", 8.0, 3.0, "pz", 19.0, "Organizzazione Officina", 10, 8, 0.0, 0.0, 0.0, true, "Per organizzare parti."),
        DemoItem("Etichette adesive 60x40", "Altro", 8.0, 4.0, "rotoli", 15.0, "Organizzazione Officina", 3, 8, 23.0, 5.0, 0.0, true, "Rotoli per etichette QR."),
        DemoItem("Materiale non prezzato", "Altro", 4.0, 2.0, "pz", null, "Fornitore da definire", null, null, 1.0, 0.0, 0.0, false, "Test indicatore costi mancanti e assenza ubicazione."),
    )
    val categories: Set<String> = items.map { it.category }.toSet()
    init {
        require(items.size == 42 && categories == ArticleCodes.categoryPrefixes.keys)
        require(items.all { it.stock>=0 && it.minimum>=0 && it.used90>=0 && it.received30>=0 })
    }
}

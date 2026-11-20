package com.officinastock.app

/** Scenario deterministico: dati fittizi credibili, non contiene informazioni personali. */
data class DemoItem(
    val name:String, val category:String, val stock:Double, val minimum:Double, val unit:String,
    val cost:Double?, val supplier:String, val leadDays:Int?, val placement:Int?,
    val used90:Double, val received30:Double, val onLoan:Double, val illustration:Boolean, val note:String
)
object DemoCatalog {
    val items:List<DemoItem> = listOf(
        DemoItem("Cuscinetto 6204 2RS", "Ricambi", 14.0, 8.0, "pz", 8.9, "SKF Distribuzione", 5, 0, 28.0, 4.0, 0.0, true, "Per il motore della linea A."),
        DemoItem("Cuscinetto 6205 ZZ", "Ricambi", 3.0, 8.0, "pz", 12.5, "SKF Distribuzione", 7, 0, 19.0, 6.0, 0.0, true, "Per il supporto dell'albero."),
        DemoItem("Cinghia dentata T10 3220 mm", "Ricambi", 2.0, 4.0, "pz", 148.0, "Trasmissioni Veneto", 18, 0, 8.0, 0.0, 0.0, true, "Ricambio critico dell'azionamento."),
        DemoItem("Cinghia trapezoidale SPZ 1000", "Ricambi", 0.0, 5.0, "pz", 16.8, "Trasmissioni Veneto", 8, 0, 24.0, 5.0, 0.0, false, "Esaurita: da riordinare subito."),
        DemoItem("Filtro aria compressore", "Ricambi", 6.0, 4.0, "pz", 45.0, "Compressori Service", 12, 3, 7.0, 0.0, 0.0, true, "Per i compressori da 45 kW."),
        DemoItem("Filtro olio compressore", "Ricambi", 5.0, 6.0, "pz", 32.4, "Compressori Service", 10, 3, 11.0, 2.0, 0.0, false, "Si cambia a ogni manutenzione."),
        DemoItem("Giunto elastico 28 mm", "Ricambi", 10.0, 3.0, "pz", 35.0, "Meccatronica Ricambi", 12, 0, 0.0, 0.0, 0.0, false, "Non usato da oltre 90 giorni."),
        DemoItem("Pompa dosatrice 24 V", "Ricambi", 1.0, 2.0, "pz", 520.0, "Automazione Service", 24, 2, 3.0, 0.0, 0.0, true, "Ricambio di valore alto."),
        DemoItem("Sensore induttivo M18 PNP", "Elettrico", 3.0, 6.0, "pz", 36.0, "Automazione Service", 12, 2, 17.0, 5.0, 0.0, true, "Sensore di prossimità."),
        DemoItem("Sensore fotoelettrico PNP", "Elettrico", 7.0, 4.0, "pz", 89.0, "Automazione Service", 14, 2, 12.0, 0.0, 0.0, true, "Per la linea di imballaggio."),
        DemoItem("Relè 24 VDC 2 scambi", "Elettrico", 20.0, 8.0, "pz", 11.2, "Elettroforniture", 3, 2, 29.0, 10.0, 0.0, false, "Per quadri elettrici e di comando."),
        DemoItem("Fusibile rapido 10 A", "Elettrico", 44.0, 15.0, "pz", 1.4, "Elettroforniture", 2, 2, 75.0, 30.0, 0.0, false, "Si usa spesso."),
        DemoItem("Contattore 18 A", "Elettrico", 2.0, 3.0, "pz", 68.0, "Elettroforniture", 9, 2, 7.0, 0.0, 0.0, false, "Scorta da tenere sotto controllo."),
        DemoItem("Inverter 0,75 kW", "Elettrico", 1.0, 1.0, "pz", 385.0, "Automazione Service", 30, 2, 0.0, 0.0, 0.0, true, "Ricambio di valore alto, mai usato finora."),
        DemoItem("Interruttore finecorsa", "Elettrico", 8.0, 4.0, "pz", 18.5, "Elettroforniture", 5, 2, 13.0, 0.0, 0.0, false, "Finecorsa meccanico a leva."),
        DemoItem("Chiave combinata 13 mm", "Utensili", 5.0, 2.0, "pz", 13.0, "Beta Tools", 5, 1, 0.0, 0.0, 1.0, true, "Una è in prestito."),
        DemoItem("Avvitatore 18 V", "Utensili", 2.0, 1.0, "pz", 220.0, "Utensili Veneto", 15, 1, 0.0, 0.0, 1.0, true, "Elettroutensile a batteria, uno è in prestito a un manutentore."),
        DemoItem("Trapano a colonna", "Utensili", 1.0, 1.0, "pz", 490.0, "Utensili Veneto", 15, 7, 0.0, 0.0, 0.0, true, "Attrezzatura fissa dell'officina."),
        DemoItem("Punta HSS Ø6", "Utensili", 9.0, 4.0, "pz", 3.3, "Beta Tools", 4, 1, 12.0, 4.0, 0.0, false, "Si consuma in attrezzeria."),
        DemoItem("Calibro digitale 150 mm", "Utensili", 3.0, 2.0, "pz", 42.0, "Beta Tools", 5, 1, 0.0, 0.0, 0.0, true, "Strumento di misura, si presta e si restituisce."),
        DemoItem("Kit chiavi a brugola", "Utensili", 4.0, 2.0, "pz", 26.0, "Beta Tools", 5, 1, 0.0, 0.0, 0.0, false, "Prestato e già restituito: lo trovi nella cronologia."),
        DemoItem("Raccordo pneumatico 1/4", "Pneumatica", 5.0, 10.0, "pz", 3.8, "Pneumatica Rovigo", 4, 3, 50.0, 18.0, 0.0, true, "Sotto la scorta minima: da ordinare."),
        DemoItem("Elettrovalvola 5/2 24 V", "Pneumatica", 2.0, 3.0, "pz", 74.0, "Pneumatica Rovigo", 10, 3, 8.0, 0.0, 0.0, true, "Per gli azionamenti pneumatici."),
        DemoItem("Tubo poliuretano Ø8", "Pneumatica", 82.0, 30.0, "m", 1.9, "Pneumatica Rovigo", 3, 3, 105.0, 40.0, 0.0, false, "Si misura in metri."),
        DemoItem("Manometro 0-10 bar", "Pneumatica", 4.0, 3.0, "pz", 28.0, "Pneumatica Rovigo", 7, 3, 3.0, 0.0, 0.0, false, "Per controllare la pressione dell'aria."),
        DemoItem("Guarnizione NBR 30 mm", "Meccanica", 18.0, 8.0, "pz", 2.4, "Guarnizioni Italia", 6, 0, 21.0, 0.0, 0.0, false, "Per i montaggi."),
        DemoItem("Vite TCEI M8 x 35", "Meccanica", 240.0, 40.0, "pz", 0.18, "Ferramenta Industriale", 2, 0, 90.0, 100.0, 0.0, true, "Molto usata, scorta ampia."),
        DemoItem("Dado autobloccante M8", "Meccanica", 0.0, 60.0, "pz", 0.08, "Ferramenta Industriale", 4, 0, 125.0, 0.0, 0.0, false, "Esaurito."),
        DemoItem("Rondella M8 zincata", "Meccanica", 540.0, 100.0, "pz", 0.04, "Ferramenta Industriale", 2, 0, 180.0, 150.0, 0.0, false, "Scorta ampia."),
        DemoItem("Olio idraulico ISO VG46", "Idraulica", 32.0, 20.0, "l", 7.9, "Oleodinamica Servizi", 7, 4, 45.0, 18.0, 0.0, true, "Si misura in litri."),
        DemoItem("Raccordo oleodinamico 3/8", "Idraulica", 6.0, 5.0, "pz", 14.5, "Oleodinamica Servizi", 9, 4, 10.0, 4.0, 0.0, false, "Raccordo per i circuiti idraulici."),
        DemoItem("Filtro idraulico ritorno", "Idraulica", 3.0, 4.0, "pz", 85.0, "Oleodinamica Servizi", 21, 4, 7.0, 0.0, 0.0, true, "Sotto la scorta minima."),
        DemoItem("Grasso multiuso NLGI 2", "Consumabili", 14.0, 7.0, "kg", 9.2, "Lubrificanti Veneto", 5, 5, 16.0, 8.0, 0.0, false, "Si misura in chilogrammi."),
        DemoItem("Panno industriale rotolo", "Consumabili", 25.0, 10.0, "pz", 8.3, "Pulizia Industriale", 4, 5, 60.0, 24.0, 0.0, false, "Si preleva spesso."),
        DemoItem("Detergente parti meccaniche", "Consumabili", 18.0, 8.0, "l", 4.9, "Pulizia Industriale", 5, 5, 34.0, 9.0, 0.0, false, "Si misura in litri."),
        DemoItem("Guanti nitrile taglia L", "DPI", 5.0, 20.0, "conf", 11.0, "Sicurezza Lavoro", 4, 6, 55.0, 10.0, 0.0, true, "Indispensabili per la sicurezza."),
        DemoItem("Occhiali protettivi", "DPI", 19.0, 10.0, "pz", 7.2, "Sicurezza Lavoro", 7, 6, 14.0, 8.0, 0.0, true, "Proteggono gli occhi da schegge e polvere."),
        DemoItem("Mascherina FFP2", "DPI", 120.0, 40.0, "pz", 0.48, "Sicurezza Lavoro", 3, 6, 66.0, 34.0, 0.0, false, "Protezione delle vie respiratorie."),
        DemoItem("Batteria tampone CR2032", "Altro", 14.0, 5.0, "pz", 1.2, "Distribuzione Elettronica", 5, 8, 7.0, 0.0, 0.0, false, "Per strumenti e telecomandi."),
        DemoItem("Scatola minuteria 12 scomparti", "Altro", 8.0, 3.0, "pz", 19.0, "Organizzazione Officina", 10, 8, 0.0, 0.0, 0.0, true, "Per tenere in ordine la minuteria."),
        DemoItem("Etichette adesive 60x40", "Altro", 8.0, 4.0, "rotoli", 15.0, "Organizzazione Officina", 3, 8, 23.0, 5.0, 0.0, true, "Per stampare le etichette QR."),
        DemoItem("Materiale non prezzato", "Altro", 4.0, 2.0, "pz", null, "Fornitore da definire", null, null, 1.0, 0.0, 0.0, false, "Senza costo e senza posto: da completare."),
    )
    val categories: Set<String> = items.map { it.category }.toSet()
    init {
        require(items.size == 42 && categories == ArticleCodes.categoryPrefixes.keys)
        require(items.all { it.stock>=0 && it.minimum>=0 && it.used90>=0 && it.received30>=0 })
    }
}

"""Ubicora — modello Excel per l'anagrafica iniziale (import CSV 4.7.5)."""
import sys
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.workbook.defined_name import DefinedName
from openpyxl.comments import Comment

HEADER = ['Codice', 'Descrizione', 'Categoria', 'Quantità', 'Minimo', 'Unità', 'Ubicazione', 'Note',
          'Costo unitario EUR', 'Fornitore', 'Consegna giorni', 'Ripiano', 'Posizione']
UNITS = ['pz', 'paia', 'set', 'conf', 'scatole', 'rotoli', 'm', 'cm', 'mm', 'm²', 'kg', 'g', 'l', 'ml']
LABELS = {'pz': 'pezzi', 'paia': 'paia', 'set': 'set / kit', 'conf': 'confezioni', 'scatole': 'scatole',
          'rotoli': 'rotoli', 'm': 'metri', 'cm': 'centimetri', 'mm': 'millimetri', 'm²': 'metri quadri',
          'kg': 'chilogrammi', 'g': 'grammi', 'l': 'litri', 'ml': 'millilitri'}
# Same lists as MeasureUnits in the app: suggested first, then all the others.
SUGGESTED = {
    'Ricambi': ['pz', 'set', 'paia', 'conf'], 'Utensili': ['pz', 'set'],
    'Elettrico': ['pz', 'm', 'rotoli', 'conf'], 'Pneumatica': ['pz', 'm', 'set', 'conf'],
    'Idraulica': ['pz', 'm', 'l', 'set'], 'Consumabili': ['conf', 'pz', 'kg', 'l', 'rotoli', 'g', 'ml', 'm'],
    'DPI': ['paia', 'pz', 'conf'], 'Meccanica': ['pz', 'kg', 'm', 'set'], 'Altro': ['pz'],
}
CATEGORIES = list(SUGGESTED)
ROWS = 500

NAVY = '10333D'; TEAL = '087E83'; SOFT = 'EEF5F2'; INPUT = 'FFFDE7'; GREY = 'E9ECEB'
F = 'Arial'
thin = Side(style='thin', color='D6E3DE')
box = Border(left=thin, right=thin, top=thin, bottom=thin)

wb = Workbook()

# --- Sheet 1: Articoli (the one saved as CSV) -------------------------------------------------
ws = wb.active
ws.title = 'Articoli'
widths = [14, 34, 15, 11, 10, 10, 22, 30, 14, 22, 12, 9, 10]
for c, (name, w) in enumerate(zip(HEADER, widths), 1):
    cell = ws.cell(1, c, name)
    cell.font = Font(name=F, bold=True, color='FFFFFF')
    cell.fill = PatternFill('solid', fgColor=NAVY)
    cell.alignment = Alignment(horizontal='center', vertical='center', wrap_text=True)
    cell.border = box
    ws.column_dimensions[cell.column_letter].width = w
ws.row_dimensions[1].height = 30
ws.freeze_panes = 'B2'
for r in range(2, ROWS + 2):
    for c in range(1, 14):
        cell = ws.cell(r, c)
        cell.font = Font(name=F, size=10)
        cell.border = box
        cell.fill = PatternFill('solid', fgColor=GREY if c == 1 else INPUT)
        if c in (4, 5, 9):
            cell.number_format = '0.##'
        if c in (11, 12, 13):
            cell.number_format = '0'

notes = {
    'A1': 'Lascia VUOTO: il codice (es. RIC-000001) lo assegna l\'app all\'importazione.',
    'B1': 'Obbligatoria. Nome dell\'articolo, massimo 180 caratteri.',
    'C1': 'Obbligatoria. Scegli dal menù.',
    'D1': 'Quantità presente oggi. Vuota = 0. Usa la virgola per i decimali.',
    'E1': 'Scorta minima: sotto questo valore l\'articolo diventa critico. Vuota = 0.',
    'F1': 'Scegli dal menù: in alto le unità adatte alla categoria. Vuota = pz.',
    'G1': 'Nome ESATTO di una struttura già creata nella Mappa dell\'app (vedi foglio Strutture). Vuota = da assegnare.',
    'H1': 'Facoltative.',
    'I1': 'Facoltativo. Solo il numero, senza simbolo €.',
    'J1': 'Facoltativo.',
    'K1': 'Facoltativo. Giorni di consegna dal fornitore, da 0 a 3650.',
    'L1': 'Facoltativo, solo con Ubicazione. Da 1 a 20, non oltre i ripiani della struttura.',
    'M1': 'Facoltativo, solo con Ubicazione. Da 1 a 50, non oltre le posizioni della struttura.',
}
for ref, text in notes.items():
    ws[ref].comment = Comment(text, 'Ubicora', width=260, height=90)

# --- Hidden lists -----------------------------------------------------------------------------
ls = wb.create_sheet('Liste')
ls['A1'] = 'Categoria'
for i, cat in enumerate(CATEGORIES, 2):
    ls.cell(i, 1, cat)
wb.defined_names['Categorie'] = DefinedName('Categorie', attr_text=f"Liste!$A$2:$A${len(CATEGORIES) + 1}")
for col, cat in enumerate(CATEGORIES, 3):
    ordered = SUGGESTED[cat] + [u for u in UNITS if u not in SUGGESTED[cat]]
    ls.cell(1, col, cat)
    for row, unit in enumerate(ordered, 2):
        ls.cell(row, col, unit)
    letter = ls.cell(1, col).column_letter
    wb.defined_names[f'Unita_{cat}'] = DefinedName(f'Unita_{cat}', attr_text=f"Liste!${letter}$2:${letter}${len(ordered) + 1}")
ls.cell(1, 13, 'Tutte le unità')
for row, unit in enumerate(UNITS, 2):
    ls.cell(row, 13, unit)
wb.defined_names['Unita_tutte'] = DefinedName('Unita_tutte', attr_text=f"Liste!$M$2:$M${len(UNITS) + 1}")
ls.sheet_state = 'hidden'

# --- Strutture: names from the app map, used as a suggestion list -----------------------------
st = wb.create_sheet('Strutture')
st['A1'] = 'Nome struttura (come nella Mappa)'
st['B1'] = 'Ripiani'
st['C1'] = 'Posizioni'
for c in range(1, 4):
    cell = st.cell(1, c)
    cell.font = Font(name=F, bold=True, color='FFFFFF')
    cell.fill = PatternFill('solid', fgColor=TEAL)
    cell.alignment = Alignment(wrap_text=True, vertical='center')
st.column_dimensions['A'].width = 36
st.column_dimensions['B'].width = 10
st.column_dimensions['C'].width = 10
st.row_dimensions[1].height = 30
for r in range(2, 202):
    for c in range(1, 4):
        st.cell(r, c).fill = PatternFill('solid', fgColor=INPUT)
        st.cell(r, c).font = Font(name=F, size=10)
        st.cell(r, c).border = box
st['E1'] = 'Scrivi qui i nomi delle strutture create nella Mappa dell\'app, uguali lettera per lettera.'
st['E2'] = 'Nel foglio Articoli la colonna Ubicazione li proporrà nel menù.'
st['E3'] = 'Ripiani e Posizioni sono solo un promemoria: impostali nell\'app (Mappa → struttura → Ripiani e posizioni).'
for ref in ('E1', 'E2', 'E3'):
    st[ref].font = Font(name=F, size=10, color='587076')
wb.defined_names['Strutture'] = DefinedName('Strutture', attr_text="Strutture!$A$2:$A$201")

# --- Data validation on Articoli --------------------------------------------------------------
last = ROWS + 1
dv_cat = DataValidation(type='list', formula1='=Categorie', allow_blank=True, showErrorMessage=True,
                        errorTitle='Categoria non valida', error='Scegli una categoria dal menù.')
dv_unit = DataValidation(type='list', formula1='=INDIRECT("Unita_"&IF($C2="","tutte",$C2))', allow_blank=True,
                         showErrorMessage=True, errorTitle='Unità non valida',
                         error='Scegli un\'unità dal menù: le prime sono quelle adatte alla categoria.')
dv_loc = DataValidation(type='list', formula1='=Strutture', allow_blank=True, showErrorMessage=False)
dv_qty = DataValidation(type='decimal', operator='between', formula1='0', formula2='100000000', allow_blank=True,
                        showErrorMessage=True, errorTitle='Numero non valido', error='Inserisci un numero da 0 in su.')
dv_cost = DataValidation(type='decimal', operator='between', formula1='0', formula2='100000000', allow_blank=True,
                         showErrorMessage=True, errorTitle='Costo non valido', error='Solo il numero, senza €.')
dv_days = DataValidation(type='whole', operator='between', formula1='0', formula2='3650', allow_blank=True,
                         showErrorMessage=True, errorTitle='Giorni non validi', error='Un numero intero da 0 a 3650.')
dv_level = DataValidation(type='whole', operator='between', formula1='1', formula2='20', allow_blank=True,
                          showErrorMessage=True, errorTitle='Ripiano non valido', error='Un numero intero da 1 a 20.')
dv_slot = DataValidation(type='whole', operator='between', formula1='1', formula2='50', allow_blank=True,
                         showErrorMessage=True, errorTitle='Posizione non valida', error='Un numero intero da 1 a 50.')
dv_code = DataValidation(type='textLength', operator='equal', formula1='0', allow_blank=True, showErrorMessage=True,
                         errorTitle='Lascia vuoto', error='Il codice lo assegna l\'app: lascia la colonna vuota.')
for dv, col in ((dv_cat, 'C'), (dv_unit, 'F'), (dv_loc, 'G'), (dv_qty, 'D'), (dv_qty, 'E'), (dv_cost, 'I'),
                (dv_days, 'K'), (dv_level, 'L'), (dv_slot, 'M'), (dv_code, 'A')):
    dv.add(f'{col}2:{col}{last}')
for dv in (dv_cat, dv_unit, dv_loc, dv_qty, dv_cost, dv_days, dv_level, dv_slot, dv_code):
    ws.add_data_validation(dv)

# --- Istruzioni (first tab the user sees) -----------------------------------------------------
ins = wb.create_sheet('Istruzioni', 0)
ins.column_dimensions['A'].width = 8
ins.column_dimensions['B'].width = 110
title = ins['B1']
title.value = 'Ubicora · Anagrafica iniziale degli articoli'
title.font = Font(name=F, size=16, bold=True, color=NAVY)
ins['B2'] = 'Per app Ubicora 4.7.5 o successiva. Compili gli articoli qui, li salvi come CSV e li importi nell\'app in un colpo solo.'
ins['B2'].font = Font(name=F, size=10, color='587076')
steps = [
    ('PRIMA', 'Nell\'app crea il magazzino e, nella Mappa, le strutture (scaffali, armadi…) con ripiani e posizioni.'),
    ('1', 'Foglio Strutture: scrivi i nomi delle strutture esattamente come nella Mappa.'),
    ('2', 'Foglio Articoli: una riga per articolo. Compila le celle gialle; lascia vuota la colonna grigia Codice.'),
    ('3', 'Categoria e Unità si scelgono dal menù (freccina nella cella). Le prime unità sono quelle adatte alla categoria.'),
    ('4', 'Ubicazione: scegli dal menù una struttura del foglio Strutture, oppure lascia vuoto. Ripiano e Posizione solo se c\'è l\'Ubicazione.'),
    ('5', 'Apri il foglio Articoli e salvalo: File → Salva con nome → «CSV UTF-8» (va bene anche «CSV»). Salva solo quel foglio.'),
    ('6', 'Copia il file .csv sul telefono, poi nell\'app: Impostazioni → Importa articoli da CSV → controlla l\'anteprima → Importa.'),
    ('NOTA', 'Se una riga ha un errore l\'app non importa nulla e ti dice quale riga correggere: correggi e riprova.'),
    ('NOTA', 'L\'importazione aggiunge solo articoli nuovi: non modifica quelli già presenti nell\'app.'),
]
r = 4
for tag, text in steps:
    ins.cell(r, 1, tag).font = Font(name=F, bold=True, color=TEAL, size=10)
    c = ins.cell(r, 2, text)
    c.font = Font(name=F, size=11)
    c.alignment = Alignment(wrap_text=True, vertical='top')
    ins.row_dimensions[r].height = 30
    r += 1
r += 1
ins.cell(r, 2, 'Esempio di riga compilata (solo da guardare: non copiarla nel foglio Articoli)').font = Font(name=F, bold=True, color=NAVY)
r += 1
example = ['', 'Guanti in nitrile taglia L', 'DPI', '40', '10', 'paia', 'Scaffale A', 'Taglia L',
           '0,35', 'Rossi Forniture', '5', '2', '3']
ex = wb.create_sheet('Esempio')  # kept separate so it is never saved into the CSV by mistake
ex.sheet_state = 'visible'
for c, (h, v) in enumerate(zip(HEADER, example), 1):
    hc = ex.cell(1, c, h)
    hc.font = Font(name=F, bold=True, color='FFFFFF')
    hc.fill = PatternFill('solid', fgColor=NAVY)
    vc = ex.cell(2, c, v)
    vc.font = Font(name=F, size=10, color='0000FF')
    ex.column_dimensions[hc.column_letter].width = widths[c - 1]
ex['A4'] = 'Codice vuoto: l\'app assegnerà DPI-000001 (o il numero successivo libero).'
ex['A5'] = 'Ubicazione «Scaffale A», ripiano 2, posizione 3: nell\'app comparirà «Scaffale A · R2 P3».'
for ref in ('A4', 'A5'):
    ex[ref].font = Font(name=F, size=10, color='587076')
ins.cell(r, 2, 'Vedi il foglio «Esempio».').font = Font(name=F, size=11)
r += 2
ins.cell(r, 2, 'Legenda colori').font = Font(name=F, bold=True, color=NAVY)
r += 1
for fill, text in ((INPUT, 'Giallo: celle da compilare'), (GREY, 'Grigio: lascia vuoto (lo compila l\'app)')):
    ins.cell(r, 1).fill = PatternFill('solid', fgColor=fill)
    ins.cell(r, 2, text).font = Font(name=F, size=11)
    r += 1
wb.active = wb.sheetnames.index('Istruzioni')
wb.save(sys.argv[1])
print('saved', sys.argv[1])

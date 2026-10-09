// Shared browser domain logic. No network, DOM or implicit side effects.
export const uid=()=>crypto.randomUUID();
export const empty=()=>({format:'ubicora-web-1',warehouses:[],items:[],movements:[],placements:[],orders:[],reservations:[],works:[],budget:null});
export const number=(v,min=0)=>{const n=Number(String(v).replace(',','.'));if(String(v).trim()===''||!Number.isFinite(n)||n<min||n>1e12)throw Error('Inserisci un numero valido');return n;};
export const optional=v=>String(v??'').trim()===''?null:number(v);
export const reserved=(s,id)=>s.reservations.filter(r=>r.itemId===id&&r.status==='ACTIVE').reduce((a,r)=>a+r.quantity,0);
export const available=(s,i)=>i.quantity-reserved(s,i.id);
export const cost=w=>w.partsCost===null||(w.hours>0&&w.rate===null)?null:w.partsCost+w.hours*(w.rate??0);
const positive=v=>{const n=number(v);if(n<=0)throw Error('La quantità deve essere maggiore di zero');return n;};
const name=v=>{v=String(v??'').trim();if(!v||v.length>160)throw Error('Inserisci un nome (massimo 160 caratteri)');return v;};
const find=(rows,id)=>{const r=rows.find(x=>x.id===id);if(!r)throw Error('Riferimento non trovato');return r;};
const storedNumber=(v,min=0)=>{if(typeof v!=='number')throw Error('Backup non valido: il valore numerico deve essere un numero');return number(v,min);};
const storedText=(v,max=2000)=>{if(typeof v!=='string'||v.length>max)throw Error('Testo non valido o troppo lungo');return v;};
const deliveryDays=v=>{if(v!==null&&(!Number.isInteger(v)||v<0||v>3650))throw Error('Giorni di consegna: usa un intero da 0 a 3650');};
const stamp=v=>{if(!Number.isSafeInteger(v)||v<0||v>8640000000000000)throw Error('Data non valida');};
export function validate(s){
 if(!s||s.format!=='ubicora-web-1')throw Error('Formato non compatibile: usa un backup Ubicora Web');
 for(const key of ['warehouses','items','movements','placements','orders','reservations','works']){
  if(!Array.isArray(s[key])||s[key].length>(key==='movements'?100000:key==='warehouses'?100:20000))throw Error('Archivio troppo grande o non valido');
  for(const x of s[key])if(!x||typeof x!=='object'||typeof x.id!=='string'||!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(x.id))throw Error('Identificativo non valido');
  if(new Set(s[key].map(x=>x.id.toLowerCase())).size!==s[key].length)throw Error('Identificativi duplicati');
 }
 const warehouses=new Map(s.warehouses.map(w=>[w.id,w])),itemIds=new Map(s.items.map(i=>[i.id,i])),reservedTotals=new Map();
 for(const r of s.reservations){storedNumber(r.quantity);positive(r.quantity);stamp(r.at);storedText(r.reference,300);if(!['ACTIVE','CONSUMED','RELEASED'].includes(r.status)||r.reference.trim().length<3)throw Error('Prenotazione non valida');if(r.status==='ACTIVE')reservedTotals.set(r.itemId,(reservedTotals.get(r.itemId)??0)+r.quantity);}
 const skus=new Set();for(const i of s.items){
  if(!warehouses.has(i.warehouseId))throw Error('Magazzino non valido');if(!storedText(i.name,180).trim())throw Error('Nome articolo mancante');name(storedText(i.sku,100));name(storedText(i.unit,24));storedText(i.category,100);storedText(i.location,160);storedText(i.note,3000);storedText(i.supplier,160);
  storedNumber(i.quantity);storedNumber(i.minimum);if(i.cost!==null)storedNumber(i.cost);deliveryDays(i.leadDays);
  const k=i.warehouseId+'|'+i.sku.trim().toLowerCase();if(skus.has(k))throw Error('Codice articolo già presente');skus.add(k);if(i.quantity-(reservedTotals.get(i.id)??0)<-1e-7)throw Error('Le prenotazioni superano la giacenza');
 }
 for(const r of [...s.movements,...s.orders,...s.reservations])if(!itemIds.has(r.itemId))throw Error('Articolo non valido');
 for(const r of [...s.works,...s.placements]){if(!warehouses.has(r.warehouseId))throw Error('Magazzino non valido');if(r.itemId&&itemIds.get(r.itemId)?.warehouseId!==r.warehouseId)throw Error('Articolo di un’altra sede');}
 for(const o of s.orders){storedNumber(o.quantity);positive(o.quantity);storedNumber(o.received);if(o.price!==null)storedNumber(o.price);stamp(o.at);storedText(o.supplier,160);if(o.received>o.quantity||!['DRAFT','APPROVED','ORDERED','RECEIVED','CANCELLED'].includes(o.status)||(o.status==='RECEIVED'&&o.received!==o.quantity))throw Error('Ordine non valido');}
 for(const w of s.warehouses){name(storedText(w.name,160));storedText(w.location,300);if(storedNumber(w.width)<2||w.width>150||storedNumber(w.depth)<2||w.depth>150)throw Error('Dimensioni: da 2 a 150 metri');}
 for(const m of s.movements){storedNumber(m.delta,-1e12);stamp(m.at);storedText(m.note,2000);if(!m.note.trim())throw Error('Motivo movimento mancante');}
 for(const p of s.placements){name(storedText(p.name,160));storedNumber(p.x);storedNumber(p.y);storedNumber(p.width);storedNumber(p.depth);positive(p.width);positive(p.depth);const w=warehouses.get(p.warehouseId);if(p.x+p.width>w.width||p.y+p.depth>w.depth)throw Error('La struttura esce dalla pianta');}
 for(const w of s.works){name(storedText(w.machine,160));if(!storedText(w.description,2000).trim())throw Error('Descrivi il lavoro');storedNumber(w.quantity);if(storedNumber(w.hours)>100000)throw Error('Ore lavorate non valide');if(w.rate!==null&&storedNumber(w.rate)>1e9)throw Error('Tariffa non valida');if(w.partsCost!==null)storedNumber(w.partsCost);stamp(w.at);if(w.completedAt!==null)stamp(w.completedAt);if(!['OPEN','DONE','CANCELLED'].includes(w.status)||(w.status==='DONE'&&w.completedAt===null))throw Error('Intervento non valido');}
 if(s.budget!==null)storedNumber(s.budget);return s;
}

function move(s,i,delta,note,at,ignoreReservation=false){
 if(i.quantity+delta<-1e-7||(!ignoreReservation&&available(s,i)+delta<-1e-7))throw Error('Quantità disponibile insufficiente: controlla le prenotazioni');
 if(String(note).trim().length<3)throw Error('Indica un motivo di almeno 3 caratteri');
 i.quantity=Math.max(0,i.quantity+delta);s.movements.unshift({id:uid(),itemId:i.id,delta,note:String(note).trim(),at});
}
export function apply(state,action,at=Date.now()){
 const s=structuredClone(state),a=action;
 switch(a.type){
 case 'warehouse':s.warehouses.push({id:uid(),name:name(a.name),location:String(a.location??'').trim(),width:number(a.width??12),depth:number(a.depth??8)});break;
 case 'item':{
  find(s.warehouses,a.warehouseId);const item={id:uid(),warehouseId:a.warehouseId,sku:name(a.sku),name:name(a.name),category:String(a.category||'Ricambi').trim(),unit:name(a.unit||'pz'),quantity:number(a.quantity),minimum:number(a.minimum||0),cost:optional(a.cost),supplier:String(a.supplier??'').trim(),leadDays:optional(a.leadDays),location:String(a.location??'').trim(),note:String(a.note??'').trim()};
  if(item.leadDays!==null&&(!Number.isInteger(item.leadDays)||item.leadDays>3650))throw Error('Giorni di consegna non validi');
  s.items.push(item);if(item.quantity)s.movements.unshift({id:uid(),itemId:item.id,delta:item.quantity,note:'Giacenza iniziale',at});break;
 }
 case 'edit':{const i=find(s.items,a.id);Object.assign(i,{name:name(a.name),category:String(a.category||'Ricambi').trim(),minimum:number(a.minimum),cost:optional(a.cost),supplier:String(a.supplier??'').trim(),leadDays:optional(a.leadDays),location:String(a.location??'').trim(),note:String(a.note??'').trim()});break;}
 case 'move':{if(!['in','out','count'].includes(a.kind))throw Error('Tipo movimento non valido');const i=find(s.items,a.id);const q=a.kind==='count'?number(a.quantity):positive(a.quantity);move(s,i,a.kind==='in'?q:a.kind==='out'?-q:q-i.quantity,a.note,at);break;}
 case 'reserve':{const i=find(s.items,a.id),q=positive(a.quantity);if(q>available(s,i)+1e-7)throw Error('Quantità disponibile insufficiente');if(String(a.reference).trim().length<3)throw Error('Indica la commessa o attività');s.reservations.push({id:uid(),itemId:i.id,quantity:q,reference:String(a.reference).trim(),status:'ACTIVE',at});break;}
 case 'reservation':{const r=find(s.reservations,a.id);if(r.status!=='ACTIVE')throw Error('Prenotazione già chiusa');r.status=a.consume?'CONSUMED':'RELEASED';if(a.consume)move(s,find(s.items,r.itemId),-r.quantity,'Consumo prenotazione: '+r.reference,at);break;}
 case 'order':{const i=find(s.items,a.id);s.orders.unshift({id:uid(),itemId:i.id,quantity:positive(a.quantity),received:0,price:optional(a.price),supplier:String(a.supplier??i.supplier).trim(),status:'DRAFT',at});break;}
 case 'advance':{const o=find(s.orders,a.id);const next={DRAFT:'APPROVED',APPROVED:'ORDERED'}[o.status];if(!next)throw Error('Passaggio non disponibile');o.status=next;break;}
 case 'cancelOrder':{const o=find(s.orders,a.id);if(['RECEIVED','CANCELLED'].includes(o.status))throw Error('Ordine già chiuso');o.status='CANCELLED';break;}
 case 'receive':{const o=find(s.orders,a.id);if(o.status!=='ORDERED')throw Error('Ordine non inviato o già chiuso');const q=positive(a.quantity);if(q>o.quantity-o.received+1e-7)throw Error('Arrivo superiore al residuo');const residual=o.quantity-o.received,applied=Math.min(q,residual);if(applied<=0)throw Error('Ordine già ricevuto');move(s,find(s.items,o.itemId),applied,'Ricezione ordine '+o.id.slice(0,8),at);o.received=applied===residual?o.quantity:Math.min(o.quantity,o.received+applied);if(o.received===o.quantity)o.status='RECEIVED';break;}
 case 'work':{find(s.warehouses,a.warehouseId);const item=a.id?find(s.items,a.id):null;s.works.unshift({id:uid(),warehouseId:a.warehouseId,machine:name(a.machine),description:String(a.description).trim(),itemId:item?.id??null,quantity:item?positive(a.quantity):0,hours:number(a.hours||0),rate:optional(a.rate),partsCost:null,status:'OPEN',at,completedAt:null});if(!s.works[0].description)throw Error('Descrivi il lavoro');break;}
 case 'finish':{const w=find(s.works,a.id);if(w.status!=='OPEN')throw Error('Intervento già chiuso');w.status=a.cancel?'CANCELLED':'DONE';if(!a.cancel){const i=w.itemId?find(s.items,w.itemId):null;if(i)move(s,i,-w.quantity,'Manutenzione: '+w.machine,at);w.partsCost=i?(i.cost===null?null:i.cost*w.quantity):0;w.completedAt=at;}break;}
 case 'placement':{const w=find(s.warehouses,a.warehouseId);const p={id:uid(),warehouseId:w.id,name:name(a.name),x:number(a.x),y:number(a.y),width:positive(a.width),depth:positive(a.depth)};if(p.x+p.width>w.width||p.y+p.depth>w.depth)throw Error('La struttura esce dalla pianta');s.placements.push(p);break;}
 case 'removePlacement':{const p=find(s.placements,a.id);s.placements=s.placements.filter(x=>x.id!==p.id);break;}
 case 'budget':s.budget=optional(a.value);break;
 default:throw Error('Operazione sconosciuta');
 }return validate(s);
}
export function analytics(s,wid,days=30,now=Date.now()){
 const items=s.items.filter(i=>i.warehouseId===wid),ids=new Set(items.map(i=>i.id));
 const totals=new Map();for(const r of s.reservations)if(r.status==='ACTIVE')totals.set(r.itemId,(totals.get(r.itemId)??0)+r.quantity);
 const orders=s.orders.filter(o=>ids.has(o.itemId)),works=s.works.filter(w=>w.warehouseId===wid&&w.status==='DONE'&&w.completedAt>=now-days*86400000);
 const date=new Date(now),start=new Date(date.getFullYear(),date.getMonth(),1).getTime();
 const committed=s.orders.filter(o=>['APPROVED','ORDERED','RECEIVED'].includes(o.status)&&o.at>=start&&o.at<=now);
 return {items,critical:items.filter(i=>i.quantity-(totals.get(i.id)??0)<=i.minimum),value:items.reduce((a,i)=>a+(i.cost??0)*i.quantity,0),unknown:items.filter(i=>i.cost===null).length,
  stages:Object.fromEntries(['DRAFT','APPROVED','ORDERED','RECEIVED','CANCELLED'].map(k=>[k,orders.filter(o=>o.status===k).length])),
  commitments:committed.reduce((a,o)=>a+o.quantity*(o.price??0),0),unpriced:committed.filter(o=>o.price===null).length,
  workCost:works.reduce((a,w)=>a+(cost(w)??0),0),workUnknown:works.filter(w=>cost(w)===null).length,
  machines:Object.entries(works.reduce((a,w)=>{a[w.machine]=(a[w.machine]??0)+(cost(w)??0);return a;},Object.create(null))).sort((a,b)=>b[1]-a[1]).slice(0,6),
  moves:s.movements.filter(m=>ids.has(m.itemId)&&m.at>=now-days*86400000),orders,works};
}
export const CSV_HEADER=['Codice','Descrizione','Categoria','Quantità','Minimo','Unità','Ubicazione','Note','Costo unitario EUR','Fornitore','Consegna giorni'];
export function parseCsv(text){
 if(new TextEncoder().encode(text).length>8*1024*1024)throw Error('CSV superiore a 8 MiB');
 text=text.replace(/^\uFEFF/,'');const rows=[];let row=[],field='',quote=false,closed=false;
 for(let i=0;i<text.length;i++){const ch=text[i];if(closed&&![';','\n','\r'].includes(ch))throw Error('Caratteri dopo la chiusura del campo CSV');if(ch==='"'){if(quote&&text[i+1]==='"'){field+='"';i++;}else if(quote||!field){if(quote)closed=true;quote=!quote;}else throw Error('Virgolette CSV non valide');}
 else if(ch===';'&&!quote){row.push(field);field='';closed=false;}else if((ch==='\n'||ch==='\r')&&!quote){if(ch==='\r'&&text[i+1]==='\n')i++;row.push(field);if(row.some(Boolean))rows.push(row);row=[];field='';closed=false;}else field+=ch;}
 if(quote)throw Error('CSV incompleto');if(field||row.length){row.push(field);rows.push(row);}if(rows.length>20001)throw Error('Massimo 20.000 articoli');
 if(rows[0]?.length!==11||!rows[0].every((x,i)=>x===CSV_HEADER[i]))throw Error('Usa il CSV inventario esportato da Ubicora Android (11 colonne, separatore ;)');
 return rows.slice(1).map(r=>{if(r.length!==11)throw Error('Numero colonne non valido');return {sku:r[0],name:r[1],category:r[2],quantity:r[3],minimum:r[4],unit:r[5],location:r[6],note:r[7],cost:r[8],supplier:r[9],leadDays:r[10]};});
}
export function importCsv(state,text,wid){
 find(state.warehouses,wid);const s=structuredClone(state),at=Date.now();
 for(const a of parseCsv(text)){
  const i={id:uid(),warehouseId:wid,sku:name(a.sku),name:String(a.name).trim(),category:a.category||'Ricambi',unit:name(a.unit),quantity:number(a.quantity),minimum:number(a.minimum),cost:optional(a.cost),supplier:a.supplier,leadDays:optional(a.leadDays),location:a.location,note:a.note};
  if(i.leadDays!==null&&(!Number.isInteger(i.leadDays)||i.leadDays>3650))throw Error('Giorni di consegna non validi');
  s.items.push(i);if(i.quantity)s.movements.push({id:uid(),itemId:i.id,delta:i.quantity,note:'Importazione inventario CSV Android',at});
 }return validate(s);
}
const csvField=x=>'"'+String(x??'').replace(/"/g,'""')+'"';
export function exportCsv(s,wid){return '\uFEFF'+[CSV_HEADER,...s.items.filter(i=>i.warehouseId===wid).map(i=>[i.sku,i.name,i.category,i.quantity,i.minimum,i.unit,i.location,i.note,i.cost,i.supplier,i.leadDays])].map(r=>r.map(x=>csvField(typeof x==='string'&&/^[\s]*[=+@-]/.test(x)?"'"+x:x)).join(';')).join('\r\n');}
const ANDROID_CATEGORIES=new Set(['Ricambi','Utensili','Elettrico','Pneumatica','Idraulica','Consumabili','DPI','Meccanica','Altro']);
export function exportAndroidCsv(s,wid){
 find(s.warehouses,wid);
 for(const i of s.items.filter(i=>i.warehouseId===wid)){
  if(!/^(RIC|UTE|ELE|PNE|IDR|CON|DPI|MEC|ALT)-[0-9]{6,9}$/.test(i.sku)||Number(i.sku.split('-')[1])<1||!ANDROID_CATEGORIES.has(i.category)||i.quantity>1e8||i.minimum>1e8||(i.cost!==null&&i.cost>1e8))throw Error('CSV Android non compatibile per '+i.sku+': usa un codice riconosciuto (es. RIC-000001), una categoria Android e quantità/costi entro 100 milioni. Puoi esportare il CSV generico.');
 }
 return exportCsv(s,wid);
}

export function demo(){let s=empty();s=apply(s,{type:'warehouse',name:'Officina - esempio',location:'Archivio dimostrativo',width:12,depth:8});const wid=s.warehouses[0].id;
 for(const [sku,name,q,min,c,loc]of[['CUS-000001','Cuscinetto 6204',26,5,8.5,'Scaffale A'],['SNS-000001','Sensore induttivo M12',3,5,24,'Cassettiera B'],['UTN-000001','Chiave dinamometrica',2,1,75,'Banco 1'],['RIC-000002','Cinghia T10',0,2,null,'Scaffale A']])s=apply(s,{type:'item',warehouseId:wid,sku,name,quantity:q,minimum:min,cost:c,location:loc,supplier:'Fornitore esempio'});
 s=apply(s,{type:'reserve',id:s.items[0].id,quantity:6,reference:'Manutenzione compressore'});
 for(const [name,x,y,width,depth]of[['Scaffale A',1,1,3,.8],['Cassettiera B',6,1,2,.8],['Banco 1',1,5,2,1]])s=apply(s,{type:'placement',warehouseId:wid,name,x,y,width,depth});
 return s;
}


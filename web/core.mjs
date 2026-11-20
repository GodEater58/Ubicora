// Shared browser domain logic. No network, DOM or implicit side effects.
export const uid=()=>crypto.randomUUID();
export const empty=()=>({format:'ubicora-web-1',warehouses:[],items:[],movements:[],placements:[],orders:[],reservations:[],works:[],walls:[],budget:null});
const GRID=.25;
export const snap=v=>Math.round(v/GRID)*GRID;
const clamp=(v,min,max)=>Math.min(Math.max(v,min),Math.max(min,max));
/**
 * Where a wall point lands while drawing. An existing corner within `tol` metres wins over the grid;
 * next a point on an existing wall; otherwise the grid point. Landing on existing geometry
 * (`closes`) ends the chain, so squares, triangles and other footprints close by themselves.
 */
export function wallPoint(raw,from,walls,tol){
 const near=(p,w)=>{const vx=w.x2-w.x1,vy=w.y2-w.y1,l=vx*vx+vy*vy,t=l?Math.min(Math.max(((p.x-w.x1)*vx+(p.y-w.y1)*vy)/l,0),1):0;return {x:w.x1+t*vx,y:w.y1+t*vy,d:Math.hypot(p.x-w.x1-t*vx,p.y-w.y1-t*vy)};};
 const leaving=from?walls.filter(w=>near(from,w).d<1e-3):[],others=walls.filter(w=>!leaving.includes(w));
 let best=null;
 for(const w of others)for(const v of [{x:w.x1,y:w.y1},{x:w.x2,y:w.y2}]){if(from&&Math.hypot(v.x-from.x,v.y-from.y)<1e-3)continue;const d=Math.hypot(v.x-raw.x,v.y-raw.y);if(d<=tol&&(!best||d<best.d))best={...v,d};}
 if(best)return {x:best.x,y:best.y,closes:true,kind:'vertex'};
 const g={x:snap(raw.x),y:snap(raw.y)};
 for(const w of others){const vx=w.x2-w.x1,vy=w.y2-w.y1,dx=g.x-(from?.x??g.x),dy=g.y-(from?.y??g.y),dl=Math.hypot(dx,dy),wl=Math.hypot(vx,vy);
  if(!wl||(from&&dl>1e-3&&Math.abs(dx*vy-dy*vx)/(dl*wl)<.17))continue; // parallel to the stroke
  const p=near(g,w);if(p.d<=tol&&(!best||p.d<best.d))best=p;}
 return best?{x:best.x,y:best.y,closes:true,kind:'wall'}:{...g,closes:false,kind:'grid'};
}
/**
 * 1.0.4: items link to structures by ID (renaming a structure no longer breaks the link) and the
 * plan stores walls. Older archives are upgraded in place: walls start empty, and items are linked
 * to the structure whose name matches their location text (only when the match is unique).
 */
export function upgrade(s){
 if(!s||s.format!=='ubicora-web-1')return s;
 if(s.walls===undefined)s.walls=[];
 if(Array.isArray(s.items)&&Array.isArray(s.placements))for(const i of s.items){
  if(i&&typeof i==='object'&&i.placementId===undefined){
   const matches=s.placements.filter(p=>p&&p.warehouseId===i.warehouseId&&p.name===i.location);
   i.placementId=matches.length===1?matches[0].id:null;
  }
 }
 return s;
}
export function nextLabel(label){const t=String(label).trim();let m=t.match(/^(.*?)([0-9]+)$/);if(m)return m[1]+String(BigInt(m[2])+1n).padStart(m[2].length,'0');
 m=t.match(/^(.*?[^A-Za-z])?([A-Ya-y])$/);if(m)return (m[1]??'')+String.fromCharCode(m[2].charCodeAt(0)+1);return t+' 2';}
const placementByName=(s,wid,location)=>{const m=s.placements.filter(p=>p.warehouseId===wid&&p.name===String(location??'').trim());return m.length===1?m[0].id:null;};
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
 upgrade(s);
 for(const key of ['warehouses','items','movements','placements','orders','reservations','works','walls']){
  if(!Array.isArray(s[key])||s[key].length>(key==='movements'?100000:key==='warehouses'?100:key==='walls'?5000:20000))throw Error('Archivio troppo grande o non valido');
  for(const x of s[key])if(!x||typeof x!=='object'||typeof x.id!=='string'||!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(x.id))throw Error('Identificativo non valido');
  if(new Set(s[key].map(x=>x.id.toLowerCase())).size!==s[key].length)throw Error('Identificativi duplicati');
 }
 const warehouses=new Map(s.warehouses.map(w=>[w.id,w])),itemIds=new Map(s.items.map(i=>[i.id,i])),reservedTotals=new Map();
 for(const r of s.reservations){storedNumber(r.quantity);positive(r.quantity);stamp(r.at);storedText(r.reference,300);if(!['ACTIVE','CONSUMED','RELEASED'].includes(r.status)||r.reference.trim().length<3)throw Error('Prenotazione non valida');if(r.status==='ACTIVE')reservedTotals.set(r.itemId,(reservedTotals.get(r.itemId)??0)+r.quantity);}
 const skus=new Set();for(const i of s.items){
  if(!warehouses.has(i.warehouseId))throw Error('Magazzino non valido');if(!storedText(i.name,180).trim())throw Error('Nome articolo mancante');name(storedText(i.sku,100));name(storedText(i.unit,24));storedText(i.category,100);storedText(i.location,160);storedText(i.note,3000);storedText(i.supplier,160);
  storedNumber(i.quantity);storedNumber(i.minimum);if(i.cost!==null)storedNumber(i.cost);deliveryDays(i.leadDays);
  for(const [k,max] of [['shelfLevel',20],['shelfSlot',50]])if(i[k]!==undefined&&i[k]!==null&&(!Number.isInteger(i[k])||i[k]<1||i[k]>max))throw Error('Ripiano o posizione non validi');
  const k=i.warehouseId+'|'+i.sku.trim().toLowerCase();if(skus.has(k))throw Error('Codice articolo già presente');skus.add(k);if(i.quantity-(reservedTotals.get(i.id)??0)<-1e-7)throw Error('Le prenotazioni superano la giacenza');
 }
 for(const r of [...s.movements,...s.orders,...s.reservations])if(!itemIds.has(r.itemId))throw Error('Articolo non valido');
 for(const r of [...s.works,...s.placements]){if(!warehouses.has(r.warehouseId))throw Error('Magazzino non valido');if(r.itemId&&itemIds.get(r.itemId)?.warehouseId!==r.warehouseId)throw Error('Articolo di un’altra sede');}
 for(const o of s.orders){storedNumber(o.quantity);positive(o.quantity);storedNumber(o.received);if(o.price!==null)storedNumber(o.price);stamp(o.at);storedText(o.supplier,160);if(o.received>o.quantity||!['DRAFT','APPROVED','ORDERED','RECEIVED','CANCELLED'].includes(o.status)||(o.status==='RECEIVED'&&o.received!==o.quantity))throw Error('Ordine non valido');}
 for(const w of s.warehouses){name(storedText(w.name,160));storedText(w.location,300);if(storedNumber(w.width)<2||w.width>150||storedNumber(w.depth)<2||w.depth>150)throw Error('Dimensioni: da 2 a 150 metri');}
 for(const m of s.movements){storedNumber(m.delta,-1e12);stamp(m.at);storedText(m.note,2000);if(!m.note.trim())throw Error('Motivo movimento mancante');}
 for(const p of s.placements){name(storedText(p.name,160));storedNumber(p.x);storedNumber(p.y);storedNumber(p.width);storedNumber(p.depth);positive(p.width);positive(p.depth);const w=warehouses.get(p.warehouseId);if(p.x+p.width>w.width+1e-6||p.y+p.depth>w.depth+1e-6)throw Error('La struttura esce dalla pianta');}
 const placements=new Map(s.placements.map(p=>[p.id,p]));
 for(const i of s.items)if(i.placementId!==null&&(typeof i.placementId!=='string'||placements.get(i.placementId)?.warehouseId!==i.warehouseId))throw Error('Ubicazione dell’articolo non valida');
 for(const w of s.walls){const room=warehouses.get(w.warehouseId);if(!room)throw Error('Magazzino non valido');for(const k of ['x1','y1','x2','y2'])storedNumber(w[k]);
  if(w.x1>room.width+1e-6||w.x2>room.width+1e-6||w.y1>room.depth+1e-6||w.y2>room.depth+1e-6||Math.hypot(w.x2-w.x1,w.y2-w.y1)<.2)throw Error('Parete non valida');}
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
  find(s.warehouses,a.warehouseId);const item={id:uid(),warehouseId:a.warehouseId,sku:name(a.sku),name:name(a.name),category:String(a.category||'Ricambi').trim(),unit:name(a.unit||'pz'),quantity:number(a.quantity),minimum:number(a.minimum||0),cost:optional(a.cost),supplier:String(a.supplier??'').trim(),leadDays:optional(a.leadDays),location:String(a.location??'').trim(),note:String(a.note??'').trim(),placementId:null};
  item.placementId=placementByName(s,item.warehouseId,item.location);
  if(item.leadDays!==null&&(!Number.isInteger(item.leadDays)||item.leadDays>3650))throw Error('Giorni di consegna non validi');
  s.items.push(item);if(item.quantity)s.movements.unshift({id:uid(),itemId:item.id,delta:item.quantity,note:'Giacenza iniziale',at});break;
 }
 case 'edit':{const i=find(s.items,a.id);Object.assign(i,{name:name(a.name),category:String(a.category||'Ricambi').trim(),minimum:number(a.minimum),cost:optional(a.cost),supplier:String(a.supplier??'').trim(),leadDays:optional(a.leadDays),location:String(a.location??'').trim(),note:String(a.note??'').trim()});const was=i.placementId;i.placementId=placementByName(s,i.warehouseId,i.location);if(i.placementId!==was||!i.placementId){i.shelfLevel=null;i.shelfSlot=null;}break;}
 case 'move':{if(!['in','out','count'].includes(a.kind))throw Error('Tipo movimento non valido');const i=find(s.items,a.id);const q=a.kind==='count'?number(a.quantity):positive(a.quantity);move(s,i,a.kind==='in'?q:a.kind==='out'?-q:q-i.quantity,a.note,at);break;}
 case 'reserve':{const i=find(s.items,a.id),q=positive(a.quantity);if(q>available(s,i)+1e-7)throw Error('Quantità disponibile insufficiente');if(String(a.reference).trim().length<3)throw Error('Indica la commessa o attività');s.reservations.push({id:uid(),itemId:i.id,quantity:q,reference:String(a.reference).trim(),status:'ACTIVE',at});break;}
 case 'reservation':{const r=find(s.reservations,a.id);if(r.status!=='ACTIVE')throw Error('Prenotazione già chiusa');r.status=a.consume?'CONSUMED':'RELEASED';if(a.consume)move(s,find(s.items,r.itemId),-r.quantity,'Consumo prenotazione: '+r.reference,at);break;}
 case 'order':{const i=find(s.items,a.id);s.orders.unshift({id:uid(),itemId:i.id,quantity:positive(a.quantity),received:0,price:optional(a.price),supplier:String(a.supplier??i.supplier).trim(),status:'DRAFT',at});break;}
 case 'advance':{const o=find(s.orders,a.id);const next={DRAFT:'APPROVED',APPROVED:'ORDERED'}[o.status];if(!next)throw Error('Passaggio non disponibile');o.status=next;break;}
 case 'cancelOrder':{const o=find(s.orders,a.id);if(['RECEIVED','CANCELLED'].includes(o.status))throw Error('Ordine già chiuso');o.status='CANCELLED';break;}
 case 'receive':{const o=find(s.orders,a.id);if(o.status!=='ORDERED')throw Error('Ordine non inviato o già chiuso');const q=positive(a.quantity);if(q>o.quantity-o.received+1e-7)throw Error('Arrivo superiore al residuo');const residual=o.quantity-o.received,applied=Math.min(q,residual);if(applied<=0)throw Error('Ordine già ricevuto');move(s,find(s.items,o.itemId),applied,'Ricezione ordine '+o.id.slice(0,8),at);o.received=applied===residual?o.quantity:Math.min(o.quantity,o.received+applied);if(o.received===o.quantity)o.status='RECEIVED';break;}
 case 'work':{find(s.warehouses,a.warehouseId);const item=a.id?find(s.items,a.id):null;s.works.unshift({id:uid(),warehouseId:a.warehouseId,machine:name(a.machine),description:String(a.description).trim(),itemId:item?.id??null,quantity:item?positive(a.quantity):0,hours:number(a.hours||0),rate:optional(a.rate),partsCost:null,status:'OPEN',at,completedAt:null});if(!s.works[0].description)throw Error('Descrivi il lavoro');break;}
 case 'finish':{const w=find(s.works,a.id);if(w.status!=='OPEN')throw Error('Intervento già chiuso');w.status=a.cancel?'CANCELLED':'DONE';if(!a.cancel){const i=w.itemId?find(s.items,w.itemId):null;if(i)move(s,i,-w.quantity,'Manutenzione: '+w.machine,at);w.partsCost=i?(i.cost===null?null:i.cost*w.quantity):0;w.completedAt=at;}break;}
 case 'placement':{const w=find(s.warehouses,a.warehouseId);if(s.placements.some(x=>x.warehouseId===w.id&&x.name===String(a.name??'').trim()))throw Error('Esiste già una struttura con questo nome');const p={id:uid(),warehouseId:w.id,name:name(a.name),x:number(a.x),y:number(a.y),width:positive(a.width),depth:positive(a.depth)};if(p.x+p.width>w.width+1e-6||p.y+p.depth>w.depth+1e-6)throw Error('La struttura esce dalla pianta');s.placements.push(p);break;}
 case 'movePlacement':{const p=find(s.placements,a.id),w=find(s.warehouses,p.warehouseId);p.x=clamp(snap(number(a.x,-1e12)),0,w.width-p.width);p.y=clamp(snap(number(a.y,-1e12)),0,w.depth-p.depth);break;}
 case 'resizePlacement':{const p=find(s.placements,a.id),w=find(s.warehouses,p.warehouseId);const width=positive(a.width),depth=positive(a.depth);if(width<.2||depth<.2||width>w.width||depth>w.depth)throw Error('Dimensioni: da 0,2 m fino alla pianta');p.width=width;p.depth=depth;p.x=clamp(p.x,0,w.width-width);p.y=clamp(p.y,0,w.depth-depth);break;}
 case 'rotatePlacement':{const p=find(s.placements,a.id),w=find(s.warehouses,p.warehouseId);if(p.depth>w.width||p.width>w.depth)throw Error('Ruotata non entrerebbe nella pianta');const cx=p.x+p.width/2,cy=p.y+p.depth/2;[p.width,p.depth]=[p.depth,p.width];p.x=clamp(cx-p.width/2,0,w.width-p.width);p.y=clamp(cy-p.depth/2,0,w.depth-p.depth);break;}
 case 'renamePlacement':{const p=find(s.placements,a.id),next=name(a.name);if(s.placements.some(x=>x.id!==p.id&&x.warehouseId===p.warehouseId&&x.name===next))throw Error('Esiste già una struttura con questo nome');for(const i of s.items)if(i.placementId===p.id)i.location=next;p.name=next;break;}
 case 'duplicatePlacement':{const p=find(s.placements,a.id),w=find(s.warehouses,p.warehouseId);let label=nextLabel(p.name);while(s.placements.some(x=>x.warehouseId===w.id&&x.name===label))label=nextLabel(label);
  const x=p.x+p.width+.25<=w.width-p.width?p.x+p.width+.25:p.x,y=x===p.x?Math.min(p.y+p.depth+.25,w.depth-p.depth):p.y;s.placements.push({...p,id:uid(),name:label,x:snap(x),y:snap(y)});break;}
 case 'assignPlacement':{const target=a.placementId?find(s.placements,a.placementId):null;const ids=[...new Set(a.ids??[])];if(!ids.length)throw Error('Seleziona almeno un articolo');
  for(const id of ids){const i=find(s.items,id);if(target&&target.warehouseId!==i.warehouseId)throw Error('Struttura di un’altra sede');if(i.placementId!==(target?.id??null)){i.shelfLevel=null;i.shelfSlot=null;}i.placementId=target?.id??null;i.location=target?.name??'';}break;}
 case 'wall':{const w=find(s.warehouses,a.warehouseId);const pts=['x1','y1','x2','y2'].map(k=>snap(number(a[k],-1e12)));const [x1,y1,x2,y2]=[clamp(pts[0],0,w.width),clamp(pts[1],0,w.depth),clamp(pts[2],0,w.width),clamp(pts[3],0,w.depth)];
  if(Math.hypot(x2-x1,y2-y1)<.2)throw Error('Parete troppo corta (minimo 20 cm)');s.walls.push({id:uid(),warehouseId:w.id,x1,y1,x2,y2});break;}
 case 'removeWall':{const wall=find(s.walls,a.id);s.walls=s.walls.filter(x=>x.id!==wall.id);break;}
 case 'removePlacement':{const p=find(s.placements,a.id);for(const i of s.items)if(i.placementId===p.id){i.placementId=null;i.location='';i.shelfLevel=null;i.shelfSlot=null;}s.placements=s.placements.filter(x=>x.id!==p.id);break;}
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
// 4.7.1 adds Ripiano/Posizione at the end; the 11-column layout of earlier versions is still read.
export const CSV_HEADER=['Codice','Descrizione','Categoria','Quantità','Minimo','Unità','Ubicazione','Note','Costo unitario EUR','Fornitore','Consegna giorni','Ripiano','Posizione'];
const LEGACY_COLUMNS=11;
/** Shelf level (1–20) or position (1–50) of an item, or null. */
const shelfNumber=(v,max,label)=>{if(v===null||v===undefined||String(v).trim()==='')return null;const n=Number(String(v).trim());if(!Number.isInteger(n)||n<1||n>max)throw Error(label+' non valido (1–'+max+')');return n;};
export const shelfCode=i=>[i.shelfLevel?'R'+i.shelfLevel:'',i.shelfSlot?'P'+i.shelfSlot:''].filter(Boolean).join(' ');
export function parseCsv(text){
 if(new TextEncoder().encode(text).length>8*1024*1024)throw Error('CSV superiore a 8 MiB');
 text=text.replace(/^\uFEFF/,'');const rows=[];let row=[],field='',quote=false,closed=false;
 for(let i=0;i<text.length;i++){const ch=text[i];if(closed&&![';','\n','\r'].includes(ch))throw Error('Caratteri dopo la chiusura del campo CSV');if(ch==='"'){if(quote&&text[i+1]==='"'){field+='"';i++;}else if(quote||!field){if(quote)closed=true;quote=!quote;}else throw Error('Virgolette CSV non valide');}
 else if(ch===';'&&!quote){row.push(field);field='';closed=false;}else if((ch==='\n'||ch==='\r')&&!quote){if(ch==='\r'&&text[i+1]==='\n')i++;row.push(field);if(row.some(Boolean))rows.push(row);row=[];field='';closed=false;}else field+=ch;}
 if(quote)throw Error('CSV incompleto');if(field||row.length){row.push(field);rows.push(row);}if(rows.length>20001)throw Error('Massimo 20.000 articoli');
 const width=rows[0]?.length;if((width!==CSV_HEADER.length&&width!==LEGACY_COLUMNS)||!rows[0].every((x,i)=>x===CSV_HEADER[i]))throw Error('Usa il CSV inventario esportato da Ubicora (13 o 11 colonne, separatore ;)');
 return rows.slice(1).map(r=>{if(r.length!==width)throw Error('Numero colonne non valido');return {sku:r[0],name:r[1],category:r[2],quantity:r[3],minimum:r[4],unit:r[5],location:r[6],note:r[7],cost:r[8],supplier:r[9],leadDays:r[10],level:r[11]??'',slot:r[12]??''};});
}
// Undo the apostrophe added by exportCsv / Android csv() to neutralise spreadsheet formulas.
const unformula=v=>{v=String(v??'');return /^'[\s]*[=+@-]/.test(v)||/^'[\t\r]/.test(v)?v.slice(1):v;};
export function importCsv(state,text,wid){
 find(state.warehouses,wid);const s=structuredClone(state),at=Date.now();
 for(const a of parseCsv(text)){
  const i={id:uid(),warehouseId:wid,sku:name(unformula(a.sku)),name:unformula(a.name).trim(),category:a.category||'Ricambi',unit:name(a.unit),quantity:number(a.quantity),minimum:number(a.minimum),cost:optional(a.cost),supplier:unformula(a.supplier),leadDays:optional(a.leadDays),location:unformula(a.location),note:unformula(a.note),placementId:null,shelfLevel:shelfNumber(a.level,20,'Ripiano'),shelfSlot:shelfNumber(a.slot,50,'Posizione')};i.placementId=placementByName(s,wid,i.location);
  if(i.leadDays!==null&&(!Number.isInteger(i.leadDays)||i.leadDays>3650))throw Error('Giorni di consegna non validi');
  s.items.push(i);if(i.quantity)s.movements.push({id:uid(),itemId:i.id,delta:i.quantity,note:'Importazione inventario CSV Android',at});
 }return validate(s);
}
const csvField=x=>'"'+String(x??'').replace(/"/g,'""')+'"';
export function exportCsv(s,wid){return '\uFEFF'+[CSV_HEADER,...s.items.filter(i=>i.warehouseId===wid).map(i=>[i.sku,i.name,i.category,i.quantity,i.minimum,i.unit,i.location,i.note,i.cost,i.supplier,i.leadDays,String(i.location??'').trim()?i.shelfLevel??'':'',String(i.location??'').trim()?i.shelfSlot??'':''])].map(r=>r.map(x=>csvField(typeof x==='string'&&/^[\s]*[=+@-]/.test(x)?"'"+x:x)).join(';')).join('\r\n');}
const ANDROID_CATEGORIES=new Set(['Ricambi','Utensili','Elettrico','Pneumatica','Idraulica','Consumabili','DPI','Meccanica','Altro']);
export function exportAndroidCsv(s,wid){
 find(s.warehouses,wid);
 for(const i of s.items.filter(i=>i.warehouseId===wid)){
  if(!/^(RIC|UTE|ELE|PNE|IDR|CON|DPI|MEC|ALT)-[0-9]{6,9}$/.test(i.sku)||Number(i.sku.split('-')[1])<1||!ANDROID_CATEGORIES.has(i.category)||i.quantity>1e8||i.minimum>1e8||(i.cost!==null&&i.cost>1e8))throw Error('CSV Android non compatibile per '+i.sku+': usa un codice riconosciuto (es. RIC-000001), una categoria Android e quantità/costi entro 100 milioni. Puoi esportare il CSV generico.');
 }
 return exportCsv(s,wid);
}

// 1.1.3: every operation adds a movement and the shared space is saved whole (server limit 8 MiB).
// Above the soft limit the oldest movements leave the document (to a file first), so saving never stops.
export const SHARED_SOFT_LIMIT=7*1024*1024;
export function archiveOldMovements(s,maxBytes=SHARED_SOFT_LIMIT){
 const size=x=>new TextEncoder().encode(JSON.stringify(x)).length;
 let total=size(s);if(total<=maxBytes)return null;
 // Oldest by date (imports append at the end, so the array order is not the age order).
 const byAge=s.movements.map((m,i)=>i).sort((a,b)=>s.movements[a].at-s.movements[b].at);
 const target=maxBytes*0.75,out=new Set();
 for(const i of byAge){if(total<=target)break;out.add(i);total-=size(s.movements[i])+1;}
 if(!out.size)return null;
 return {state:{...s,movements:s.movements.filter((m,i)=>!out.has(i))},archived:s.movements.filter((m,i)=>out.has(i))};
}
export function demo(){let s=empty();s=apply(s,{type:'warehouse',name:'Officina - esempio',location:'Archivio dimostrativo',width:12,depth:8});const wid=s.warehouses[0].id;
 for(const [name,x,y,width,depth]of[['Scaffale A',1,1,3,.8],['Cassettiera B',6,1,2,.8],['Banco 1',1,5,2,1]])s=apply(s,{type:'placement',warehouseId:wid,name,x,y,width,depth});
 for(const [sku,name,q,min,c,loc]of[['CUS-000001','Cuscinetto 6204',26,5,8.5,'Scaffale A'],['SNS-000001','Sensore induttivo M12',3,5,24,'Cassettiera B'],['UTN-000001','Chiave dinamometrica',2,1,75,'Banco 1'],['RIC-000002','Cinghia T10',0,2,null,'Scaffale A']])s=apply(s,{type:'item',warehouseId:wid,sku,name,quantity:q,minimum:min,cost:c,location:loc,supplier:'Fornitore esempio'});
 s=apply(s,{type:'reserve',id:s.items[0].id,quantity:6,reference:'Manutenzione compressore'});
 return s;
}


// ---- 1.1.0: team files of Ubicora Android (encrypted update of the central phone) ----
// Same format as BackupCipher.kt: "OSTOCKB2", 16-byte salt, 8-byte nonce prefix, then frames of
// [4-byte length][AES-256-GCM ciphertext + 16-byte tag]; nonce = prefix + frame number; AAD =
// signature + frame number + length; an empty frame ends the file. Inside: gzip of the JSON.
const TEAM_SIGNATURE=new TextEncoder().encode('OSTOCKB2'),TEAM_ROUNDS=220000,TEAM_CHUNK=65536;
const be32=n=>new Uint8Array([n>>>24&255,n>>>16&255,n>>>8&255,n&255]);
const joinBytes=parts=>{const out=new Uint8Array(parts.reduce((a,p)=>a+p.length,0));let o=0;for(const p of parts){out.set(p,o);o+=p.length;}return out;};
async function teamKey(password,salt){
 if(typeof password!=='string'||password.length<8)throw Error('La password della squadra ha almeno 8 caratteri');
 const base=await crypto.subtle.importKey('raw',new TextEncoder().encode(password),'PBKDF2',false,['deriveKey']);
 return crypto.subtle.deriveKey({name:'PBKDF2',salt,iterations:TEAM_ROUNDS,hash:'SHA-256'},base,{name:'AES-GCM',length:256},false,['encrypt','decrypt']);
}
const teamFrame=(prefix,seq,length)=>({name:'AES-GCM',iv:joinBytes([prefix,be32(seq)]),additionalData:joinBytes([TEAM_SIGNATURE,be32(seq),be32(length)]),tagLength:128});
async function gzip(bytes,inflate){
 const stream=new Blob([bytes]).stream().pipeThrough(inflate?new DecompressionStream('gzip'):new CompressionStream('gzip'));
 return new Uint8Array(await new Response(stream).arrayBuffer());
}
/** Decrypts a team file and returns its JSON. Wrong password and damaged files give the same message, as on the phone. */
export async function teamDecrypt(bytes,password){
 bytes=bytes instanceof Uint8Array?bytes:new Uint8Array(bytes);
 if(bytes.length<32||TEAM_SIGNATURE.some((b,i)=>bytes[i]!==b))throw Error('Questo non è un file di sincronizzazione di Ubicora');
 const key=await teamKey(password,bytes.slice(8,24)),prefix=bytes.slice(24,32),plain=[];let o=32,seq=0,total=0;
 try{
  for(;;){
   if(o+4>bytes.length)throw Error('incompleto');
   const length=(bytes[o]<<24|bytes[o+1]<<16|bytes[o+2]<<8|bytes[o+3])>>>0;o+=4;
   if(length>TEAM_CHUNK||o+length+16>bytes.length)throw Error('blocco');
   const block=new Uint8Array(await crypto.subtle.decrypt(teamFrame(prefix,seq++,length),key,bytes.slice(o,o+length+16)));o+=length+16;
   if(length===0){if(o!==bytes.length)throw Error('coda');break;}
   total+=block.length;if(total>64*1024*1024)throw Error('grande');plain.push(block);
  }
  return JSON.parse(new TextDecoder().decode(await gzip(joinBytes(plain),true)));
 }catch(e){throw Error('File non valido o password della squadra errata');}
}
/** Writes a team file (used by the tests and by the phone-compatibility fixture). */
export async function teamEncrypt(json,password,random=n=>crypto.getRandomValues(new Uint8Array(n))){
 const salt=random(16),prefix=random(8),key=await teamKey(password,salt),data=await gzip(new TextEncoder().encode(JSON.stringify(json)),false);
 const parts=[TEAM_SIGNATURE,salt,prefix];let seq=0;
 for(let o=0;o<data.length;o+=TEAM_CHUNK){const chunk=data.slice(o,o+TEAM_CHUNK);parts.push(be32(chunk.length),new Uint8Array(await crypto.subtle.encrypt(teamFrame(prefix,seq++,chunk.length),key,chunk)));}
 parts.push(be32(0),new Uint8Array(await crypto.subtle.encrypt(teamFrame(prefix,seq,0),key,new Uint8Array(0))));
 return joinBytes(parts);
}
/**
 * Adds the warehouse of a central phone's update as a new site: plan, structures, walls, articles
 * with quantities and locations. It is a picture of that moment to consult on the PC: nothing goes
 * back to the phones, and photos, loans and history are not part of it.
 */
export function importTeam(state,snapshot,label='squadra',at=Date.now()){
 if(!snapshot||snapshot.format!=='UBICORA_SYNC_STATE'||snapshot.version!==1)throw Error('Serve il file di aggiornamento del telefono centrale');
 const s=structuredClone(state),first=addTeamSite(s,snapshot,label,at);let items=first.items;
 // 1.1.1: the other sites of the company shared with the team (Ubicora 4.18 and later), each one a site here too.
 const more=Array.isArray(snapshot.sites)?snapshot.sites.slice(0,10):[];
 for(const site of more)items+=addTeamSite(s,site,label,at).items;
 return {state:validate(s),warehouseId:first.wid,items,sites:1+more.length};
}
function addTeamSite(s,snapshot,label,at){
 const room=snapshot.warehouse??{},wid=uid();
 const width=number(room.width),depth=number(room.height);
 s.warehouses.push({id:wid,name:name((String(room.name??'Magazzino').trim().slice(0,140)+' · '+label).slice(0,160)),location:String(room.location??'').trim().slice(0,300),width,depth});
 const byUid=new Map(),taken=new Set();
 for(const p of snapshot.placements??[]){
  // The phone stores centre and rotation; the browser stores the top-left corner of the footprint.
  const quarter=Math.round((Number(p.rotation)||0)/90)%2!==0;
  const w=Math.min(positive(quarter?p.depth:p.width),width),d=Math.min(positive(quarter?p.width:p.depth),depth);
  let label=String(p.label||p.kind||'Struttura').trim().slice(0,150)||'Struttura';while(taken.has(label))label=nextLabel(label);taken.add(label);
  const id=uid();byUid.set(p.uid,{id,name:label});
  s.placements.push({id,warehouseId:wid,name:label,x:clamp(number(p.x,-1e12)-w/2,0,width-w),y:clamp(number(p.y,-1e12)-d/2,0,depth-d),width:w,depth:d});
 }
 for(const w of snapshot.walls??[]){
  const [x1,y1,x2,y2]=[clamp(number(w.x1,-1e12),0,width),clamp(number(w.y1,-1e12),0,depth),clamp(number(w.x2,-1e12),0,width),clamp(number(w.y2,-1e12),0,depth)];
  if(Math.hypot(x2-x1,y2-y1)>=.2)s.walls.push({id:uid(),warehouseId:wid,x1,y1,x2,y2});
 }
 const whole=(v,max)=>Number.isInteger(v)&&v>=1&&v<=max?v:null;
 for(const a of snapshot.items??[]){
  const place=a.placement==null?null:byUid.get(a.placement)??null;
  const i={id:uid(),warehouseId:wid,sku:name(a.sku),name:String(a.name??'').trim().slice(0,180),category:String(a.category||'Ricambi').slice(0,100),unit:name(a.unit||'pz').slice(0,24),
   quantity:number(a.quantity),minimum:number(a.minimum??0),cost:a.cost==null?null:number(a.cost),supplier:String(a.supplier??'').slice(0,160),
   leadDays:Number.isInteger(a.lead)&&a.lead>=0&&a.lead<=3650?a.lead:null,location:place?.name??'',note:String(a.note??'').slice(0,3000),
   placementId:place?.id??null,shelfLevel:place?whole(a.level,20):null,shelfSlot:place?whole(a.slot,50):null};
  s.items.push(i);if(i.quantity)s.movements.push({id:uid(),itemId:i.id,delta:i.quantity,note:'Aggiornamento della squadra',at});
 }
 return {wid,items:(snapshot.items??[]).length};
}

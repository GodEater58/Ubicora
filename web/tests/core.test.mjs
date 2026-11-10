import test from 'node:test';
import assert from 'node:assert/strict';
import {apply,empty,demo,available,reserved,analytics,cost,exportCsv,importCsv,parseCsv,validate,upgrade,nextLabel,teamEncrypt,teamDecrypt,importTeam} from '../core.mjs';
const stock=()=>demo();
test('Reservations release without stock change and consumption is exactly once',()=>{
 let s=stock(),i=s.items[0],r=s.reservations[0];assert.equal(available(s,i),20);
 assert.throws(()=>apply(s,{type:'move',id:i.id,kind:'out',quantity:21,note:'Prelievo'}));
 const consumed=apply(s,{type:'reservation',id:r.id,consume:true});assert.equal(consumed.items[0].quantity,20);assert.equal(reserved(consumed,i.id),0);
 assert.throws(()=>apply(consumed,{type:'reservation',id:r.id,consume:true}));
 const released=apply(s,{type:'reservation',id:r.id,consume:false});assert.equal(released.items[0].quantity,26);
});
test('Partial receipts change quantity only when ordered and received, duplicate receipt rejected',()=>{
 let s=stock(),id=s.items[1].id;s=apply(s,{type:'order',id,quantity:10,price:24});let o=s.orders[0];
 assert.throws(()=>apply(s,{type:'receive',id:o.id,quantity:4}));
 s=apply(s,{type:'advance',id:o.id});s=apply(s,{type:'advance',id:o.id});s=apply(s,{type:'receive',id:o.id,quantity:4});assert.equal(s.items[1].quantity,7);assert.equal(s.orders[0].status,'ORDERED');
 assert.throws(()=>apply(s,{type:'receive',id:o.id,quantity:7}));s=apply(s,{type:'receive',id:o.id,quantity:6});assert.equal(s.orders[0].status,'RECEIVED');assert.equal(s.items[1].quantity,13);
 assert.throws(()=>apply(s,{type:'receive',id:o.id,quantity:1}));
});
test('Failed maintenance is atomic; costs are captured at completion and zero labor is known',()=>{
 let s=stock();const id=s.items[0].id,wid=s.warehouses[0].id;
 s=apply(s,{type:'work',warehouseId:wid,id,machine:'Compressore',description:'Ricambio',quantity:21,hours:0,rate:''});const old=structuredClone(s);
 assert.throws(()=>apply(s,{type:'finish',id:s.works[0].id}));assert.deepEqual(s,old);
 s.works[0].quantity=2;s=apply(s,{type:'finish',id:s.works[0].id});assert.equal(cost(s.works[0]),17);s.items[0].cost=100;assert.equal(cost(s.works[0]),17);
 assert.throws(()=>apply(s,{type:'finish',id:s.works[0].id}));
});
test('CSV Android roundtrip includes multiline quotes; duplicate import rolls back',()=>{
 let s=stock();s.items[0].note='Riga uno\n"due"; tre';const csv=exportCsv(s,s.warehouses[0].id),rows=parseCsv(csv);assert.equal(rows[0].note,s.items[0].note);
 let dest=apply(empty(),{type:'warehouse',name:'Importazione'});dest=importCsv(dest,csv,dest.warehouses[0].id);assert.equal(dest.items.length,4);
 const before=structuredClone(dest);assert.throws(()=>importCsv(dest,csv,dest.warehouses[0].id));assert.deepEqual(dest,before);
 assert.throws(()=>parseCsv('bad;header'));assert.throws(()=>validate({...dest,items:[{...dest.items[0],quantity:-1}]}));
});
test('Complete analytics use all orders and profile warehouses, not the first 250',()=>{
 let s=stock();const w=s.warehouses[0].id,id=s.items[0].id,now=Date.now();
 s.orders=Array.from({length:301},(_,i)=>({id:crypto.randomUUID(),itemId:id,quantity:1,received:0,price:2,supplier:'',status:'APPROVED',at:now}));
 const a=analytics(s,w,30,now);assert.equal(a.stages.APPROVED,301);assert.equal(a.commitments,602);
});
test('10,000 CSV items import in one validated transaction',()=>{
 let s=apply(empty(),{type:'warehouse',name:'Grande magazzino'});const header='Codice;Descrizione;Categoria;Quantità;Minimo;Unità;Ubicazione;Note;Costo unitario EUR;Fornitore;Consegna giorni\n';
 const csv=header+Array.from({length:10000},(_,i)=>`R-${i};Articolo ${i};Ricambi;1;0;pz;A;;2;Fornitore;3`).join('\n');
 const start=performance.now();s=importCsv(s,csv,s.warehouses[0].id);assert.equal(s.items.length,10000);assert.equal(s.movements.length,10000);assert.ok(performance.now()-start<5000,'Import must avoid repeated full clones');
});
test('Map rejects structures outside the physical warehouse',()=>{
 const s=stock();assert.throws(()=>apply(s,{type:'placement',warehouseId:s.warehouses[0].id,name:'Scaffale',x:11,y:1,width:2,depth:1}));
});


test('Restore rejects numeric strings before inventory can concatenate them',()=>{
 const s=demo();s.items[0].quantity='26';assert.throws(()=>validate(s),/numero/);
 const r=demo();r.reservations[0].quantity='6';assert.throws(()=>validate(r),/numero/);
});
test('Restore checks order prices and placement geometry',()=>{
 let s=demo();s=apply(s,{type:'order',id:s.items[0].id,quantity:2,price:4});
 s.orders[0].price=-3;assert.throws(()=>validate(s));
 const p=demo();p.placements[0].x=150;assert.throws(()=>validate(p),/pianta/);
});
test('Delivery days must stay integral and bounded through editing and restore',()=>{
 const s=demo(),i=s.items[0];
 for(const leadDays of [1.5,3651,-1])assert.throws(()=>apply(s,{type:'edit',id:i.id,name:i.name,minimum:i.minimum,leadDays}));
 const bad=demo();bad.items[0].leadDays=1.5;assert.throws(()=>validate(bad));
 const good=apply(s,{type:'edit',id:i.id,name:i.name,minimum:i.minimum,leadDays:3650});assert.equal(good.items[0].leadDays,3650);
});
test('Unknown movement kind is rejected without changing stock',()=>{
 const s=demo();assert.throws(()=>apply(s,{type:'move',id:s.items[0].id,kind:'typo',quantity:1,note:'Prova'}));assert.equal(s.items[0].quantity,26);
});

import {exportAndroidCsv} from '../core.mjs';
test('Android export rejects unsupported codes/categories while generic export remains available',()=>{
 const s=demo();s.items[0].sku='BOLT-1';s.items[0].category='Bulloneria';const wid=s.warehouses[0].id;
 assert.throws(()=>exportAndroidCsv(s,wid),/Android/);assert.match(exportCsv(s,wid),/BOLT-1/);
 const compatible=demo();compatible.items=compatible.items.slice(0,1);compatible.items[0].sku='RIC-000001';compatible.items[0].category='Ricambi';
 assert.match(exportAndroidCsv(compatible,compatible.warehouses[0].id),/RIC-000001/);
 compatible.items[0].quantity=1e8+1;assert.throws(()=>exportAndroidCsv(compatible,compatible.warehouses[0].id));
});

import fs from 'node:fs';
import vm from 'node:vm';
import {webcrypto} from 'node:crypto';
function appContext(){
 const nodes=new Map();const get=s=>{if(!nodes.has(s))nodes.set(s,{textContent:'',innerHTML:'',value:'',classList:{toggle(){}},addEventListener(){}});return nodes.get(s);};
 const context=vm.createContext({crypto:webcrypto,structuredClone,TextEncoder,URL,Blob,console,setTimeout:()=>0,clearTimeout(){},confirm:()=>true,document:{querySelector:get,querySelectorAll:()=>[],addEventListener(){}},window:{addEventListener(){},scrollTo(){}},location:{protocol:'file:'}});
 const core=fs.readFileSync(new URL('../core.mjs',import.meta.url),'utf8').replaceAll('export ','');
 const app=fs.readFileSync(new URL('../app.mjs',import.meta.url),'utf8').split('\n').slice(1).join('\n').split('await start();')[0];
 vm.runInContext(core+'\n'+app+'\nrender=()=>renderHeader();',context);return context;
}
test('Demo remains visibly transient after edits and never writes the local archive',async()=>{
 const c=appContext();const result=await vm.runInContext(`(async()=>{let writes=0;storagePut=async()=>{writes++};await showDemo();await commit({type:'budget',value:50});render();return {writes,badge:$('#modeBadge').textContent,notice:$('#connectionNotice').textContent,dirty:demoDirty,budget:state.budget};})()`,c);
 assert.equal(result.writes,0);assert.equal(result.budget,50);assert.equal(result.dirty,true);assert.match(result.badge,/DEMO/);assert.match(result.notice,/solo in memoria/);
 assert.equal(vm.runInContext('confirm=()=>false;leaveDemo()',c),false);
});
test('A failed server reload retains the unconfirmed conflict copy',async()=>{
 const c=appContext();const result=await vm.runInContext(`(async()=>{shared=true;tenant={id:'tenant'};pending={conflict:true,data:empty()};download=()=>{};api=async()=>{throw Error('offline')};await refresh();return pending!==null;})()`,c);
 assert.equal(result,true);
});

test('Receipt tolerance never invents stock beyond the order remainder',()=>{
 let s=demo();s=apply(s,{type:'order',id:s.items[0].id,quantity:1,price:2});const id=s.orders[0].id;
 s=apply(s,{type:'advance',id});s=apply(s,{type:'advance',id});s=apply(s,{type:'receive',id,quantity:1.00000005});
 assert.equal(s.items[0].quantity,27);assert.equal(s.orders[0].received,1);assert.equal(s.movements[0].delta,1);
 assert.throws(()=>apply(s,{type:'receive',id,quantity:0.00000001}));
});
test('Fractional stock can be fully reserved after an earlier decimal reservation',()=>{
 let s=demo();s.reservations=[];s.items[0].quantity=0.3;
 s=apply(s,{type:'reserve',id:s.items[0].id,quantity:0.1,reference:'Prima attività'});
 s=apply(s,{type:'reserve',id:s.items[0].id,quantity:0.2,reference:'Seconda attività'});
 for(const r of s.reservations)s=apply(s,{type:'reservation',id:r.id,consume:true});
 assert.equal(s.items[0].quantity,0);
});
test('Machine names matching object properties retain numeric independent totals',()=>{
 let s=demo();const wid=s.warehouses[0].id;
 for(const machine of ['constructor','__proto__','toString']){
  s=apply(s,{type:'work',warehouseId:wid,machine,description:'Prova',hours:1,rate:10});s=apply(s,{type:'finish',id:s.works[0].id});
 }
 assert.deepEqual(new Map(analytics(s,wid).machines),new Map([['constructor',10],['__proto__',10],['toString',10]]));
});
test('Android CSV supports 180-character descriptions and 3000-character notes',()=>{
 const s=demo();s.items=s.items.slice(0,1);s.items[0].name='A'.repeat(180);s.items[0].note='N'.repeat(3000);
 let dest=apply(empty(),{type:'warehouse',name:'Importazione'});dest=importCsv(dest,exportCsv(s,s.warehouses[0].id),dest.warehouses[0].id);
 assert.equal(dest.items[0].name.length,180);assert.equal(dest.items[0].note.length,3000);
});
test('CSV rejects junk after a quoted field without partially importing rows',()=>{
 const s=demo(),csv=exportCsv(s,s.warehouses[0].id).replace('"Cuscinetto 6204"','"Cuscinetto 6204"spazzatura');
 assert.throws(()=>parseCsv(csv),/chiusura/);assert.throws(()=>importCsv(s,csv,s.warehouses[0].id));assert.equal(s.items.length,4);
});
test('Maintenance descriptions accepted by the server can be reopened in the browser',()=>{
 let s=demo();s=apply(s,{type:'work',warehouseId:s.warehouses[0].id,machine:'Compressore',description:'A'.repeat(2000),hours:1,rate:10});
 assert.equal(validate(JSON.parse(JSON.stringify(s))).works[0].description.length,2000);
 assert.throws(()=>apply(s,{type:'work',warehouseId:s.warehouses[0].id,machine:'X',description:'Test',hours:100001,rate:1}));
});
test('Switching archives during a delayed save cannot replace its state or flags',async()=>{
 const c=appContext();const result=await vm.runInContext(`(async()=>{
 state=demo();let release;storagePut=()=>new Promise(r=>release=r);
 const saving=persist({...state,budget:17});let blocked=false;try{await showDemo()}catch{blocked=true}
 release();await saving;return {blocked,demo:demoMode,budget:state.budget};})()`,c);
 assert.equal(result.blocked,true);assert.equal(result.demo,false);assert.equal(result.budget,17);
});
test('A failed logout keeps the shared session and pending copy recoverable',async()=>{
 const c=appContext();const result=await vm.runInContext(`(async()=>{shared=true;token='session';tenant={id:'tenant'};pending={data:demo()};storageGet=async()=>{throw Error('disk error')};try{await logout()}catch{}return {shared,token,pending:pending!==null,busy};})()`,c);
 assert.equal(result.shared,true);assert.equal(result.token,'session');assert.equal(result.pending,true);assert.equal(result.busy,false);
});
test('Seeded mixed operations preserve stock balance and never mutate rejected inputs',()=>{
 let s=demo();s.reservations=[];s.items[0].quantity=100;s.movements=s.movements.filter(m=>m.itemId!==s.items[0].id);
 let seed=719,expected=100;const rand=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed;};
 for(let n=0;n<1000;n++){
  const q=rand()%7+1,kind=rand()%3===0?'out':'in',before=structuredClone(s);
  if(kind==='out'&&q>expected){assert.throws(()=>apply(s,{type:'move',id:s.items[0].id,kind,quantity:q,note:'Sequenza'}));assert.deepEqual(s,before);}
  else{s=apply(s,{type:'move',id:s.items[0].id,kind,quantity:q,note:'Sequenza'});expected+=kind==='in'?q:-q;}
  assert.equal(s.items[0].quantity,expected);assert.equal(100+s.movements.filter(m=>m.itemId===s.items[0].id).reduce((a,m)=>a+m.delta,0),expected);
 }
});

test('A delayed background response cannot roll back a newer confirmed save',async()=>{
 const c=appContext();const result=await vm.runInContext(`(async()=>{
 shared=true;tenant={id:'tenant'};revision=1;state=demo();let release;
 api=async(path,opts)=>opts?.method==='PUT'?{revision:2,mutation_id:JSON.parse(opts.body).mutation_id}:new Promise(r=>release=r);
 const polling=pollShared();await persist({...state,budget:29});release({revision:1,can_write:true,data:empty()});await polling;
 return {budget:state.budget,revision};})()`,c);
 assert.equal(result.budget,29);assert.equal(result.revision,2);
});
test('CSV round trip does not accumulate the anti-formula apostrophe (1.0.3)',()=>{
 let s=stock();const wid=s.warehouses[0].id;s.items[0].note='-10% sconto';s.items[0].supplier='=Fornitore';s.items[0].location='@Scaffale';
 const csv=exportCsv(s,wid);assert.match(csv,/"'-10% sconto"/,'export must still neutralise formulas');
 let dest=apply(empty(),{type:'warehouse',name:'Importazione'});dest=importCsv(dest,csv,dest.warehouses[0].id);
 const i=dest.items.find(x=>x.sku===s.items[0].sku);assert.equal(i.note,'-10% sconto');assert.equal(i.supplier,'=Fornitore');assert.equal(i.location,'@Scaffale');
 assert.equal(exportCsv(dest,dest.warehouses[0].id),csv.replace(wid,dest.warehouses[0].id));
 // A genuine leading apostrophe not followed by a formula character is preserved.
 s.items[0].note="'normale";let other=apply(empty(),{type:'warehouse',name:'X'});other=importCsv(other,exportCsv(s,wid),other.warehouses[0].id);
 assert.equal(other.items.find(x=>x.sku===s.items[0].sku).note,"'normale");
});

test('1.0.4 map: items link to structures by ID and survive renames',()=>{
 let s=demo();const wid=s.warehouses[0].id,shelf=s.placements.find(p=>p.name==='Scaffale A');
 const linked=s.items.filter(i=>i.placementId===shelf.id);assert.equal(linked.length,2);
 s=apply(s,{type:'renamePlacement',id:shelf.id,name:'Scaffale Z'});
 assert.ok(s.items.filter(i=>i.placementId===shelf.id).every(i=>i.location==='Scaffale Z'));
 assert.throws(()=>apply(s,{type:'renamePlacement',id:shelf.id,name:'Banco 1'}),/già/);
 s=apply(s,{type:'removePlacement',id:shelf.id});
 assert.ok(s.items.every(i=>i.placementId!==shelf.id));assert.equal(s.items.filter(i=>i.location==='Scaffale Z').length,0);
 const free=s.items.filter(i=>!i.placementId).map(i=>i.id),bench=s.placements.find(p=>p.name==='Banco 1');
 s=apply(s,{type:'assignPlacement',ids:free,placementId:bench.id});
 assert.ok(free.every(id=>s.items.find(i=>i.id===id).placementId===bench.id));
 const other=apply(s,{type:'warehouse',name:'Altra'});
 assert.throws(()=>apply(other,{type:'assignPlacement',ids:[s.items[0].id],placementId:apply(other,{type:'placement',warehouseId:other.warehouses[1].id,name:'X',x:0,y:0,width:1,depth:1}).placements.at(-1).id}));
});
test('1.0.4 map: drag snaps to 25 cm and stays inside, rotate and resize keep the footprint in the room',()=>{
 let s=demo();const p=s.placements[0],w=s.warehouses[0];
 s=apply(s,{type:'movePlacement',id:p.id,x:4.13,y:2.9});let q=s.placements[0];assert.equal(q.x,4.25);assert.equal(q.y,3);
 s=apply(s,{type:'movePlacement',id:p.id,x:99,y:-5});q=s.placements[0];assert.equal(q.x,w.width-q.width);assert.equal(q.y,0);
 s=apply(s,{type:'rotatePlacement',id:p.id});q=s.placements[0];assert.equal(q.width,.8);assert.equal(q.depth,3);
 assert.ok(q.x>=0&&q.y>=0&&q.x+q.width<=w.width&&q.y+q.depth<=w.depth);
 assert.throws(()=>apply(s,{type:'resizePlacement',id:p.id,width:40,depth:1}));
 s=apply(s,{type:'resizePlacement',id:p.id,width:2,depth:1});assert.equal(s.placements[0].width,2);
 s=apply(s,{type:'duplicatePlacement',id:p.id});assert.equal(s.placements.at(-1).name,'Scaffale B');
 s=apply(s,{type:'duplicatePlacement',id:p.id});assert.equal(s.placements.at(-1).name,'Scaffale C');
 assert.equal(nextLabel('S09'),'S10');assert.equal(nextLabel('Banco'),'Banco 2');
});
test('1.0.4 map: walls are validated, snapped and removable',()=>{
 let s=demo();const wid=s.warehouses[0].id;
 s=apply(s,{type:'wall',warehouseId:wid,x1:0,y1:0,x2:5.06,y2:0});assert.equal(s.walls[0].x2,5);
 assert.throws(()=>apply(s,{type:'wall',warehouseId:wid,x1:1,y1:1,x2:1.05,y2:1}),/corta/);
 s=apply(s,{type:'wall',warehouseId:wid,x1:0,y1:0,x2:50,y2:0});assert.equal(s.walls[1].x2,12);
 s=apply(s,{type:'removeWall',id:s.walls[0].id});assert.equal(s.walls.length,1);
 const bad=structuredClone(s);bad.walls[0].x2=99;assert.throws(()=>validate(bad),/Parete/);
});
test('1.0.4 map: 1.0.3 archives upgrade in place, linking only unique names',()=>{
 const s=demo();const old=structuredClone(s);delete old.walls;for(const i of old.items)delete i.placementId;
 old.placements.push({...old.placements[0],id:crypto.randomUUID(),x:6,y:5});// duplicate name: ambiguous
 const up=validate(upgrade(old));assert.deepEqual(up.walls,[]);
 assert.equal(up.items.find(i=>i.location==='Scaffale A').placementId,null);
 assert.equal(up.items.find(i=>i.location==='Banco 1').placementId,up.placements.find(p=>p.name==='Banco 1').id);
});

import {wallPoint} from '../core.mjs';
test('Wall drawing closes shapes on nearby corners and walls',()=>{
 const sq=[{x1:1,y1:1,x2:2,y2:1},{x1:2,y1:1,x2:2,y2:2},{x1:2,y1:2,x2:1,y2:2}];
 assert.deepEqual(wallPoint({x:1.08,y:1.1},{x:1,y:2},sq,.3),{x:1,y:1,closes:true,kind:'vertex'});
 // A diagonal closes a triangle even off the grid.
 const tri=[{x1:0,y1:0,x2:3,y2:0},{x1:3,y1:0,x2:3,y2:2}];
 assert.equal(wallPoint({x:.1,y:.12},{x:3,y:2},tri,.3).closes,true);
 // The wall just drawn never captures its own start.
 assert.equal(wallPoint({x:.15,y:.05},{x:1,y:0},[{x1:0,y1:0,x2:1,y2:0}],.3).closes,false);
 const t=wallPoint({x:.12,y:2.6},{x:2,y:2.5},[{x1:0,y1:0,x2:0,y2:6}],.3);
 assert.equal(t.kind,'wall');assert.equal(t.x,0);assert.equal(t.y,2.5);
 assert.deepEqual(wallPoint({x:3.1,y:3.4},{x:1,y:0},[{x1:0,y1:0,x2:1,y2:0}],.3),{x:3,y:3.5,closes:false,kind:'grid'});
});

test('CSV carries shelf level and position; 11-column files from older versions still import',()=>{
 let s=apply(empty(),{type:'warehouse',name:'Mag',width:12,depth:8});const wid=s.warehouses[0].id;
 s=apply(s,{type:'placement',warehouseId:wid,name:'Scaffale A',x:1,y:1,width:2,depth:.8});
 const head='Codice;Descrizione;Categoria;Quantità;Minimo;Unità;Ubicazione;Note;Costo unitario EUR;Fornitore;Consegna giorni';
 const now=importCsv(s,head+';Ripiano;Posizione\nRIC-000001;Vite;Ricambi;5;1;pz;Scaffale A;;;;;2;3',wid);
 assert.equal(now.items[0].shelfLevel,2);assert.equal(now.items[0].shelfSlot,3);
 const out=exportCsv(now,wid);assert.match(out,/"Ripiano";"Posizione"/);assert.match(out,/"2";"3"$/);
 assert.equal(parseCsv(out)[0].slot,'3');
 const old=importCsv(s,head+'\nRIC-000001;Vite;Ricambi;5;1;pz;Scaffale A;;;;',wid);assert.equal(old.items[0].shelfLevel,null);
 assert.throws(()=>importCsv(s,head+';Ripiano;Posizione\nRIC-000001;Vite;Ricambi;5;1;pz;Scaffale A;;;;;21;1',wid),/Ripiano/);
 // Moving the item to another structure drops a shelf that no longer applies.
 const b=apply(now,{type:'placement',warehouseId:wid,name:'Scaffale B',x:5,y:1,width:2,depth:.8});
 const moved=apply(b,{type:'assignPlacement',placementId:b.placements[1].id,ids:[b.items[0].id]});assert.equal(moved.items[0].shelfLevel,null);
});

test('Removing a structure clears shelves so the CSV stays importable on Android; names stay unique',()=>{
 let s=apply(empty(),{type:'warehouse',name:'Mag',width:12,depth:8});const wid=s.warehouses[0].id;
 s=apply(s,{type:'placement',warehouseId:wid,name:'Scaffale A',x:1,y:1,width:2,depth:.8});
 const head='Codice;Descrizione;Categoria;Quantità;Minimo;Unità;Ubicazione;Note;Costo unitario EUR;Fornitore;Consegna giorni;Ripiano;Posizione';
 s=importCsv(s,head+'\nRIC-000001;Vite;Ricambi;5;1;pz;Scaffale A;;;;;2;3',wid);
 s=apply(s,{type:'removePlacement',id:s.placements[0].id});
 assert.equal(s.items[0].shelfLevel,null);assert.equal(s.items[0].shelfSlot,null);
 assert.match(exportCsv(s,wid),/"Vite";"Ricambi";"5";"1";"pz";"";"";"";"";"";"";""$/);
 // An old archive that still has a shelf without location never exports it.
 const old=structuredClone(s);old.items[0].shelfLevel=4;assert.match(exportCsv(old,wid),/;"";""$/);
 assert.throws(()=>apply(apply(s,{type:'placement',warehouseId:wid,name:'B',x:5,y:1,width:1,depth:1}),{type:'placement',warehouseId:wid,name:'B',x:8,y:1,width:1,depth:1}),/già una struttura/);
});

const teamSnapshot=()=>({format:'UBICORA_SYNC_STATE',version:1,warehouse_uid:'w',warehouse:{name:'Officina',location:'Rovigo',width:12,height:8},
 placements:[{uid:'p1',kind:'Scaffale',label:'Scaffale A',x:3,y:3,width:1.6,depth:.75,rotation:90,levels:3,slots:4},{uid:'p2',kind:'Scaffale',label:'Scaffale A',x:9,y:6,width:1.6,depth:.75,rotation:0,levels:1,slots:1}],
 walls:[{x1:0,y1:0,x2:12,y2:0,source:'manual'},{x1:1,y1:1,x2:1.05,y2:1,source:'manual'}],
 items:[{uid:'i1',sku:'RIC-000001',name:'Vite M8',category:'Ricambi',quantity:10,minimum:2,unit:'pz',placement:'p1',level:2,slot:1,note:'',cost:null,supplier:'',lead:null,photo:null},
  {uid:'i2',sku:'DPI-B000001',name:'Guanti',category:'DPI',quantity:0,minimum:1,unit:'paia',placement:null,level:null,slot:null,note:'',cost:3.5,supplier:'ACME',lead:7}]});
test('Team file: phone format round trip, wrong password and tampering refused',async()=>{
 const padded={...teamSnapshot(),pad:Array.from({length:40000},(_,i)=>i*7919%1000).join('-')}; // more than one 64 KiB frame after gzip? forces several reads either way
 const file=await teamEncrypt(padded,'squadra-2026');
 assert.equal(new TextDecoder().decode(file.slice(0,8)),'OSTOCKB2');
 assert.deepEqual(await teamDecrypt(file,'squadra-2026'),padded);
 await assert.rejects(()=>teamDecrypt(file,'sbagliata-2026'),/password della squadra errata/);
 const bad=file.slice();bad[bad.length-20]^=1;await assert.rejects(()=>teamDecrypt(bad,'squadra-2026'));
 await assert.rejects(()=>teamDecrypt(file.slice(0,file.length-5),'squadra-2026'));
 await assert.rejects(()=>teamDecrypt(new Uint8Array(100),'squadra-2026'),/non è un file di sincronizzazione/);
 await assert.rejects(()=>teamEncrypt(padded,'corta'));
});
test('Team update becomes a new site with plan, structures, stock and locations',()=>{
 const before=demo(),r=importTeam(before,teamSnapshot(),'squadra 10/11',1000),s=r.state,w=s.warehouses.at(-1);
 assert.equal(w.name,'Officina · squadra 10/11');assert.equal(w.width,12);assert.equal(w.depth,8);assert.equal(r.items,2);
 const mine=x=>x.warehouseId===w.id,places=s.placements.filter(mine);
 // Rotated a quarter turn: the footprint is swapped; the centre (3,3) becomes the top-left corner.
 assert.deepEqual([places[0].name,places[0].x,places[0].y,places[0].width,places[0].depth],['Scaffale A',2.625,2.2,.75,1.6]);
 assert.notEqual(places[1].name,'Scaffale A'); // same label twice on the phone: kept apart here
 assert.equal(s.walls.filter(mine).length,1); // the 5 cm wall is dropped
 const vite=s.items.find(i=>mine(i)&&i.sku==='RIC-000001'),guanti=s.items.find(i=>mine(i)&&i.sku==='DPI-B000001');
 assert.deepEqual([vite.quantity,vite.location,vite.placementId,vite.shelfLevel,vite.shelfSlot],[10,'Scaffale A',places[0].id,2,1]);
 assert.deepEqual([guanti.quantity,guanti.placementId,guanti.cost,guanti.leadDays],[0,null,3.5,7]);
 assert.equal(s.movements.filter(m=>m.itemId===vite.id).length,1);assert.equal(s.movements.some(m=>m.itemId===guanti.id),false);
 assert.equal(s.items.length,before.items.length+2); // the other sites are untouched
 assert.throws(()=>importTeam(before,{format:'UBICORA_SYNC_CHANGES',version:1}),/telefono centrale/);
});

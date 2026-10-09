import test from 'node:test';
import assert from 'node:assert/strict';
import {apply,empty,demo,available,reserved,analytics,cost,exportCsv,importCsv,parseCsv,validate} from '../core.mjs';
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

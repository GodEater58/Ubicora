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

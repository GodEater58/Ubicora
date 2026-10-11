// Web 1.0.4 map editor in a real browser: drag, resize, rotate, walls, assignment, "show on map", persistence.
const {chromium}=require('playwright');const http=require('node:http');const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
(async()=>{
 const html=fs.readFileSync(path.join(__dirname,'../Ubicora.html'));
 const server=http.createServer((req,res)=>{if(req.url==='/v1/web/config'){res.setHeader('Content-Type','application/json');res.end('{"configured":false}');}else{res.setHeader('Content-Type','text/html');res.end(html);}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];
 page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
 const archive=()=>page.evaluate(()=>new Promise((ok,ko)=>{const r=indexedDB.open('ubicora-web',1);r.onsuccess=()=>{const g=r.result.transaction('archives').objectStore('archives').get('local');g.onsuccess=()=>ok(g.result);g.onerror=ko;};r.onerror=ko;}));
 try{
  await page.goto(origin);
  await page.getByRole('button',{name:'Crea magazzino',exact:true}).click();await page.getByLabel('Nome magazzino').fill('Officina mappa');await page.getByRole('button',{name:'Salva',exact:true}).click();
  await page.getByRole('heading',{name:'Tutto parte da Officina mappa.'}).waitFor();
  await page.getByRole('button',{name:'Planimetria',exact:false}).click();
  await page.getByRole('button',{name:'+ Struttura',exact:true}).first().click();await page.getByLabel('Nome / ubicazione').fill('Scaffale A');await page.getByLabel('Posizione x (metri)').fill('1');await page.getByLabel('Posizione y (metri)').fill('1');await page.getByRole('button',{name:'Salva',exact:true}).click();
  const shelf=page.locator('[data-placement]').first();await shelf.waitFor();
  // Drag: 12 m room; move right by about 3 m and down 2 m, snapped to 25 cm.
  await page.getByRole('button',{name:'Sposta e ridimensiona'}).click();
  const floor=await page.locator('#floor').boundingBox();const box=await page.locator('[data-placement]').first().boundingBox();
  const mx=floor.width/12,my=floor.height/8;
  await page.mouse.move(box.x+box.width/2,box.y+box.height/2);await page.mouse.down();
  await page.mouse.move(box.x+box.width/2+mx*1.5,box.y+box.height/2+my,{steps:6});await page.mouse.move(box.x+box.width/2+mx*3.04,box.y+box.height/2+my*2.02,{steps:6});
  await page.locator('#measure').waitFor();await page.mouse.up();
  await page.locator('#toast',{hasText:'Operazione salvata'}).waitFor();await page.waitForTimeout(200);
  let s=await archive();assert.equal(s.placements[0].x,4);assert.equal(s.placements[0].y,3);
  // Resize with the corner handle: +1 m wide.
  const handle=await page.locator('[data-placement] .handle').first().boundingBox();
  await page.mouse.move(handle.x+handle.width/2,handle.y+handle.height/2);await page.mouse.down();await page.mouse.move(handle.x+handle.width/2+mx,handle.y+handle.height/2,{steps:5});await page.mouse.up();
  await page.locator('.map-panel h2',{hasText:'Scaffale A'}).waitFor();await page.waitForTimeout(300);
  s=await archive();assert.equal(s.placements[0].width,3);
  // Arrow key nudges the selected structure by 25 cm.
  await page.keyboard.press('ArrowRight');await page.waitForTimeout(300);s=await archive();assert.equal(s.placements[0].x,4.25);
  // Rotate and duplicate from the panel.
  await page.locator('.map-panel').getByRole('button',{name:'Ruota 90°'}).click();await page.waitForTimeout(300);
  s=await archive();assert.equal(s.placements[0].width,.8);assert.equal(s.placements[0].depth,3);
  await page.locator('.map-panel').getByRole('button',{name:'Duplica'}).click();await page.getByRole('button',{name:'Scaffale B, 0 articoli'}).waitFor();
  // Walls: three clicks = two walls, live measurement shown.
  await page.getByRole('button',{name:'Disegna pareti'}).click();
  const at=(x,y)=>[floor.x+x*mx,floor.y+y*my];
  await page.mouse.click(...at(0.5,7.5));await page.mouse.move(...at(6.5,7.5),{steps:4});await page.locator('#measure',{hasText:'6 m'}).waitFor();
  await page.mouse.click(...at(6.5,7.5));await page.waitForTimeout(250);await page.mouse.click(...at(6.5,5.5));await page.waitForTimeout(250);await page.keyboard.press('Escape');
  s=await archive();assert.equal(s.walls.length,2);assert.equal(Math.hypot(s.walls[0].x2-s.walls[0].x1,s.walls[0].y2-s.walls[0].y1),6);
  // Item linked by ID: create, assign from the structure panel, rename structure, link survives.
  await page.getByRole('button',{name:'Inventario',exact:false}).first().click();
  await page.getByRole('button',{name:'+ Articolo',exact:true}).click();await page.getByLabel('Codice articolo').fill('VIT-001');await page.getByLabel('Descrizione',{exact:true}).fill('Vite M8');await page.getByRole('button',{name:'Salva',exact:true}).click();await page.locator('#editor').waitFor({state:'hidden'});
  await page.getByRole('button',{name:'Planimetria',exact:false}).click();await page.getByRole('button',{name:'Esplora'}).click();
  await page.getByRole('button',{name:'Scaffale A, 0 articoli'}).click();
  await page.locator('.map-panel').getByRole('button',{name:/Assegna articoli/}).click();await page.getByLabel(/Vite M8/).check();await page.getByRole('button',{name:'Assegna',exact:true}).click();
  await page.getByRole('button',{name:'Scaffale A, 1 articoli'}).waitFor();
  await page.locator('.map-panel').getByRole('button',{name:'Rinomina'}).click();await page.getByLabel('Nome',{exact:true}).fill('Scaffale Nord');await page.getByRole('button',{name:'Salva',exact:true}).click();
  await page.getByRole('button',{name:'Scaffale Nord, 1 articoli'}).waitFor();
  // "Show on map" from the inventory highlights the structure.
  await page.getByRole('button',{name:'Inventario',exact:false}).first().click();await page.getByRole('cell',{name:/Scaffale Nord/}).waitFor();
  await page.getByTitle('Mostra in pianta').click();await page.locator('.placement.pulse.selected').waitFor();
  assert.equal(await page.locator('.map-panel h2').textContent(),'Scaffale Nord');
  // Everything survives a reload.
  await page.reload();await page.getByRole('button',{name:'Planimetria',exact:false}).click();
  await page.getByRole('button',{name:'Scaffale Nord, 1 articoli'}).waitFor();assert.equal(await page.locator('svg.walls line:not(.draft)').count(),2);
  await page.screenshot({path:process.env.UBICORA_MAP_SCREENSHOT||'/tmp/ubicora-map.png',fullPage:true});
  assert.deepEqual(errors,[]);console.log('Map editor: drag, resize, keyboard, rotate, duplicate, walls, ID links, show on map and reload passed');
 }finally{await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1;});

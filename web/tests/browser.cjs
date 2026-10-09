const {chromium}=require('playwright');
const http=require('node:http');const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
(async()=>{
 const html=fs.readFileSync(path.join(__dirname,'../Ubicora.html'));
 const server=http.createServer((req,res)=>{if(req.url==='/v1/web/config'){res.setHeader('Content-Type','application/json');res.end('{"configured":false}');}else{res.setHeader('Content-Type','text/html');res.end(html);}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
 try{
  await page.goto(origin);await page.getByRole('heading',{name:'Partiamo dal tuo ambiente'}).waitFor();
  await page.getByRole('button',{name:'Crea magazzino',exact:true}).click();
  await page.getByLabel('Nome magazzino').fill('Officina PC');await page.getByRole('button',{name:'Salva',exact:true}).click();
  await page.getByRole('heading',{name:'Tutto parte da Officina PC.'}).waitFor();
  await page.getByRole('button',{name:'+ Articolo',exact:true}).click();await page.getByLabel('Codice articolo').fill('CUS-001');await page.getByLabel('Descrizione',{exact:true}).fill('Cuscinetto <script>alert(1)</script>');await page.getByLabel('Quantità iniziale').fill('10');await page.getByLabel('Scorta minima').fill('2');await page.getByRole('button',{name:'Salva',exact:true}).click();
  await page.getByRole('button',{name:'Inventario',exact:false}).first().click();await page.getByRole('cell',{name:'Cuscinetto <script>alert(1)</script>',exact:false}).waitFor();
  await page.getByRole('button',{name:'Movimento',exact:true}).click();await page.getByLabel('Tipo',{exact:true}).selectOption('out');await page.getByLabel('Quantità',{exact:true}).fill('3');await page.getByLabel('Motivo / riferimento').fill('Prova prelievo');await page.getByRole('button',{name:'Conferma',exact:true}).click();
  await page.getByRole('cell',{name:'7 pz',exact:true}).waitFor();await page.reload();await page.getByRole('button',{name:'Inventario',exact:false}).first().click();await page.getByRole('cell',{name:'7 pz',exact:true}).waitFor();
  await page.getByRole('button',{name:'Planimetria',exact:false}).click();await page.getByRole('button',{name:'+ Struttura',exact:true}).first().click();await page.getByLabel('Nome / ubicazione').fill('Scaffale A');await page.getByRole('button',{name:'Salva',exact:true}).click();await page.locator('.placement').waitFor();
  await page.getByRole('button',{name:'Panoramica',exact:false}).click();await page.screenshot({path:process.env.UBICORA_SCREENSHOT||'/tmp/ubicora-desktop.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:process.env.UBICORA_MOBILE_SCREENSHOT||'/tmp/ubicora-mobile.png',fullPage:true});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Mobile layout overflows');
  // Two tabs share IndexedDB: a stale whole-document edit must never overwrite the other tab.
  const second=await page.context().newPage();await second.goto(origin);await second.getByRole('heading',{name:'Tutto parte da Officina PC.'}).waitFor();
  await page.setViewportSize({width:1440,height:1000});
  await page.getByRole('button',{name:'+ Articolo',exact:true}).click();await page.getByLabel('Codice articolo').fill('RIC-000002');await page.getByLabel('Descrizione',{exact:true}).fill('Secondo articolo');await page.getByRole('button',{name:'Salva',exact:true}).click();await page.locator('#editor').waitFor({state:'hidden'});
  await second.getByRole('button',{name:'+ Articolo',exact:true}).click();await second.getByLabel('Codice articolo').fill('RIC-000003');await second.getByLabel('Descrizione',{exact:true}).fill('Modifica obsoleta');await second.getByRole('button',{name:'Salva',exact:true}).click();
  await second.locator('#formError').getByText('Archivio modificato in un’altra scheda.',{exact:false}).waitFor();await second.getByRole('button',{name:'Annulla',exact:true}).click();await second.getByRole('button',{name:'Aggiorna',exact:true}).click();await second.getByRole('button',{name:'Inventario',exact:false}).first().click();await second.getByRole('cell',{name:'Secondo articolo',exact:false}).waitFor();assert.equal(await second.getByRole('cell',{name:'Modifica obsoleta',exact:false}).count(),0);await second.close();
  assert.deepEqual(errors,[]);console.log('Browser: create, escaped content, stock movement, IndexedDB reload, map and mobile layout passed');
  // Delivered single-file build is also usable by double click, without a web server.
  const offline=await browser.newPage();await offline.goto('file://'+path.join(__dirname,'../Ubicora.html'));await offline.getByRole('heading',{name:'Partiamo dal tuo ambiente'}).waitFor();await offline.getByRole('button',{name:'Esplora demo',exact:true}).click();await offline.getByRole('heading',{name:'Tutto parte da Officina - esempio.'}).waitFor();await offline.close();console.log('Offline file:// launch passed');
 }finally{await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1;});


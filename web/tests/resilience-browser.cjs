// Regression tests added in Web 1.0.3: failures outside the archive must never lock the user out.
const {chromium}=require('playwright');const http=require('node:http');const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
(async()=>{
 const html=fs.readFileSync(path.join(__dirname,'../Ubicora.html'));let origin,revision=0,forbid=false,state={format:'ubicora-web-1',warehouses:[],items:[],movements:[],placements:[],orders:[],reservations:[],works:[],budget:null};
 const server=http.createServer(async(req,res)=>{
  const url=new URL(req.url,origin);const json=(s,d)=>{res.writeHead(s,{'Content-Type':'application/json'});res.end(JSON.stringify(d));};
  if(url.pathname==='/v1/web/config')return json(200,{configured:true,client_id:'c',authorization_endpoint:origin+'/authorize',token_endpoint:origin+'/token',scope:'openid profile'});
  if(url.pathname==='/authorize'){res.writeHead(302,{Location:url.searchParams.get('redirect_uri')+'?code=x&state='+url.searchParams.get('state')});return res.end();}
  if(url.pathname==='/token')return json(200,{access_token:'tok',token_type:'Bearer'});
  if(url.pathname==='/v1/web/tenants')return json(200,{tenants:[{id:'11111111-1111-4111-8111-111111111111',display_name:'Spazio',role:'MANAGER'}]});
  if(url.pathname.startsWith('/v1/web/workspace/')){
   if(req.method==='GET')return json(200,{data:state,revision,can_write:!forbid});
   let raw='';for await(const c of req)raw+=c;if(forbid)return json(403,{detail:'Forbidden'});
   const cmd=JSON.parse(raw);state=cmd.data;revision++;return json(200,{revision,mutation_id:cmd.mutation_id});
  }
  res.writeHead(200,{'Content-Type':'text/html'});res.end(html);
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true});
 try{
  // 1. A cancelled / failed login redirect must leave the local archive usable.
  const p=await browser.newPage();const errors=[];p.on('pageerror',e=>errors.push(e.message));p.on('dialog',d=>d.accept());
  await p.goto(origin);await p.getByRole('button',{name:'Crea magazzino',exact:true}).click();await p.getByLabel('Nome magazzino').fill('Locale');await p.getByRole('button',{name:'Salva',exact:true}).click();
  await p.getByRole('heading',{name:'Tutto parte da Locale.'}).waitFor();
  await p.goto(origin+'/?error=access_denied&state=x');
  await p.getByRole('heading',{name:'Tutto parte da Locale.'}).waitFor({timeout:5000});
  assert.equal(await p.locator('#primary').isDisabled(),false,'Primary action disabled after failed login');
  await p.getByText('Accesso annullato',{exact:false}).waitFor();
  console.log('Failed OIDC callback keeps local archive usable');
  // 2. A permanent rejection (403: role revoked) must not leave an unresolvable pending save.
  await p.getByRole('button',{name:'Dati e connessione',exact:false}).click();await p.getByRole('button',{name:'Accedi con account aziendale'}).click();
  await p.getByText('Condiviso · Spazio',{exact:true}).waitFor();
  forbid=true;
  await p.getByRole('button',{name:'+ Nuovo magazzino'}).click();await p.getByLabel('Nome magazzino').fill('Rifiutato');await p.getByRole('button',{name:'Salva',exact:true}).click();
  await p.locator('#formError').getByText('non può eseguire',{exact:false}).waitFor();await p.getByRole('button',{name:'Annulla',exact:true}).click();
  const download=p.waitForEvent('download');await p.getByRole('button',{name:'Risolvi salvataggio',exact:true}).click();await download;
  await p.getByRole('button',{name:'Aggiorna',exact:true}).waitFor({timeout:5000});
  assert.match(await p.locator('#connectionNotice').textContent(),/sola lettura/);
  assert.deepEqual(errors,[]);console.log('Rejected shared save resolves to read-only reload with exported copy');
 }finally{await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1;});

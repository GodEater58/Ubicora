// Browser integration with an explicit OIDC/API fixture. Actual JWT/RLS and CAS
// authorization are tested separately against PostgreSQL by server/tests.
const {chromium}=require('playwright');const http=require('node:http');const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');const crypto=require('node:crypto');
(async()=>{
 let revision=0,state={format:'ubicora-web-1',warehouses:[],items:[],movements:[],placements:[],orders:[],reservations:[],works:[],budget:null},dropNext=false;
 const receipts=new Map(),codes=new Map();const html=fs.readFileSync(path.join(__dirname,'../Ubicora.html'));let origin;
 const server=http.createServer(async(req,res)=>{
  const url=new URL(req.url,origin);const json=(status,data)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(data));};
  if(url.pathname==='/v1/web/config')return json(200,{configured:true,client_id:'test-public-client',authorization_endpoint:origin+'/authorize',token_endpoint:origin+'/token',scope:'openid profile'});
  if(url.pathname==='/authorize'){assert.equal(url.searchParams.get('code_challenge_method'),'S256');const code=crypto.randomUUID();codes.set(code,url.searchParams.get('code_challenge'));res.writeHead(302,{Location:url.searchParams.get('redirect_uri')+'?code='+code+'&state='+url.searchParams.get('state')});return res.end();}
  if(url.pathname==='/token'){let body='';for await(const chunk of req)body+=chunk;const p=new URLSearchParams(body),code=p.get('code'),challenge=crypto.createHash('sha256').update(p.get('code_verifier')).digest('base64url');if(codes.get(code)!==challenge)return json(400,{error:'invalid_grant'});codes.delete(code);return json(200,{access_token:'browser-fixture-manager-token',token_type:'Bearer'});}
  if(url.pathname.startsWith('/v1/web/')&&req.headers.authorization!=='Bearer browser-fixture-manager-token')return json(401,{detail:'Unauthorized'});
  if(url.pathname==='/v1/web/tenants')return json(200,{tenants:[{id:'11111111-1111-4111-8111-111111111111',display_name:'Test shared workspace',role:'MANAGER'}]});
  if(url.pathname.startsWith('/v1/web/workspace/')){
   if(req.method==='GET')return json(200,{data:state,revision,can_write:true});
   let raw='';for await(const chunk of req)raw+=chunk;const command=JSON.parse(raw),digest=crypto.createHash('sha256').update(raw).digest('hex'),prior=receipts.get(command.mutation_id);
   if(prior)return prior.digest===digest?json(200,{revision:prior.revision,mutation_id:command.mutation_id}):json(409,{detail:'Reused mutation'});
   if(command.expected_revision!==revision)return json(409,{detail:'Stale revision'});state=command.data;revision++;receipts.set(command.mutation_id,{digest,revision});
   if(dropNext){dropNext=false;res.writeHead(200,{'Content-Type':'application/json'});return res.end('{"revision":');}return json(200,{revision,mutation_id:command.mutation_id});
  }
  res.writeHead(200,{'Content-Type':'text/html'});res.end(html);
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));origin='http://127.0.0.1:'+server.address().port;
 let browser;
 try{
  browser=await chromium.launch({headless:true});const contexts=[await browser.newContext(),await browser.newContext()],pages=await Promise.all(contexts.map(c=>c.newPage()));const errors=[];
  for(const p of pages){p.on('pageerror',e=>errors.push(e.message));p.on('dialog',d=>d.accept());await p.goto(origin);await p.getByRole('button',{name:'Dati e connessione',exact:false}).click();await p.getByRole('button',{name:'Accedi con account aziendale'}).click();await p.getByText('Condiviso · Test shared workspace',{exact:true}).waitFor();assert.equal(await p.evaluate(()=>sessionStorage.getItem('ubicora-pkce')),null);}
  const [a,b]=pages;await a.getByRole('button',{name:'+ Nuovo magazzino'}).click();await a.getByLabel('Nome magazzino').fill('Condiviso PC');await a.getByRole('button',{name:'Salva',exact:true}).click();await a.getByRole('button',{name:'Inventario',exact:false}).first().click();await a.getByRole('button',{name:'+ Articolo',exact:true}).click();await a.getByLabel('Codice articolo').fill('SH-001');await a.getByLabel('Descrizione',{exact:true}).fill('Articolo condiviso');await a.getByLabel('Quantità iniziale').fill('10');await a.getByRole('button',{name:'Salva',exact:true}).click();await a.getByRole('cell',{name:'10 pz',exact:true}).waitFor();
  await b.getByRole('button',{name:'Aggiorna',exact:true}).click();await b.getByRole('button',{name:'Inventario',exact:false}).first().click();await b.getByRole('cell',{name:'10 pz',exact:true}).waitFor();
  // Both devices start from the same version. The later stale edit cannot win.
  await a.getByRole('button',{name:'Movimento',exact:true}).click();await a.getByLabel('Tipo',{exact:true}).selectOption('out');await a.getByLabel('Quantità',{exact:true}).fill('1');await a.getByLabel('Motivo / riferimento').fill('Stale client');
  await b.getByRole('button',{name:'Movimento',exact:true}).click();await b.getByLabel('Tipo',{exact:true}).selectOption('out');await b.getByLabel('Quantità',{exact:true}).fill('2');await b.getByLabel('Motivo / riferimento').fill('Second device');await b.getByRole('button',{name:'Conferma',exact:true}).click();await b.getByRole('cell',{name:'8 pz',exact:true}).waitFor();
  await a.getByRole('button',{name:'Conferma',exact:true}).click();await a.locator('#formError').filter({hasText:'altro dispositivo'}).waitFor();assert.equal(state.items[0].quantity,8);await a.getByRole('button',{name:'Chiudi',exact:true}).click();await a.getByRole('button',{name:'Risolvi salvataggio'}).click();await a.getByRole('cell',{name:'8 pz',exact:true}).waitFor();
  // The server commits but acknowledgement is lost. Explicit retry uses same ID.
  dropNext=true;await a.getByRole('button',{name:'Movimento',exact:true}).click();await a.getByLabel('Tipo',{exact:true}).selectOption('in');await a.getByLabel('Quantità',{exact:true}).fill('3');await a.getByLabel('Motivo / riferimento').fill('Lost ack');await a.getByRole('button',{name:'Conferma',exact:true}).click();await a.locator('#formError').filter({hasText:'Risposta server non valida'}).waitFor();await a.getByRole('button',{name:'Chiudi',exact:true}).click();const committedRevision=revision;await a.getByRole('button',{name:'Risolvi salvataggio'}).click();await a.getByRole('cell',{name:'11 pz',exact:true}).waitFor();assert.equal(revision,committedRevision);assert.equal(state.movements.filter(m=>m.note==='Lost ack').length,1);assert.deepEqual(errors,[]);
  console.log('Shared browser: PKCE, two isolated sessions, same data, conflict preservation and lost-ack idempotent retry passed');
 }finally{if(browser)await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1;});

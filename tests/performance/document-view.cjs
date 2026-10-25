const { chromium, request } = require('../E2E/node_modules/@playwright/test');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
(async () => {
  if (process.env.PERF_ALLOW_TEST_DB !== '1') throw Error('Set PERF_ALLOW_TEST_DB=1 for an isolated local test database');
  const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:18080';
  if (!['localhost','127.0.0.1'].includes(new URL(baseURL).hostname)) throw Error('Local test server required');
  const database = process.env.PERF_DATABASE || 'knowledge_base_test';
  if (!database.endsWith('_test')) throw Error('Database name must end in _test');
  const dbArgs = ['-h','127.0.0.1','-p',process.env.PERF_DB_PORT || '55433','-U',process.env.PERF_DB_USER || 'kb_user','-d',database,'-v','ON_ERROR_STOP=1'];
  const psql = process.env.PSQL || 'psql';
  const api = await request.newContext({baseURL});
  await api.get('/login');
  const token=(await api.storageState()).cookies.find(c=>c.name==='XSRF-TOKEN')?.value || '';
  const headers={'X-XSRF-TOKEN':decodeURIComponent(token)};
  const login=await api.post('/api/auth/login',{headers,data:{login:process.env.PERF_LOGIN || 'admin',password:process.env.PERF_PASSWORD || 'admin123'}});
  if(!login.ok()) throw Error('login '+login.status());
  const me=await (await api.get('/api/auth/me')).json();
  const space=await (await api.post('/api/admin/spaces',{headers,data:{name:'Perf measured '+Date.now(),description:'Local benchmark'}})).json();
  const doc=await (await api.post('/api/documents',{headers,data:{spaceId:space.id,title:'Performance sample',content:'# Контрольный документ\n\n'+('Текст для измерения открытия документа. '.repeat(300))}})).json();
  await api.post(`/api/documents/${doc.id}/publish`,{headers});
  if(!Number.isInteger(me.id)||!Number.isInteger(doc.id)) throw Error('Invalid fixture');
  const sql=`WITH inserted AS (INSERT INTO spaces(name,description,owner_id,created_at,updated_at) SELECT 'Perf space ' || g || ' ${Date.now()}', 'Local benchmark', ${me.id}, now(), now() FROM generate_series(1,50) g RETURNING id) INSERT INTO documents(title,git_file_path,status,author_id,space_id,sort_order,created_at,updated_at) SELECT 'Perf doc ' || g, 'perf/' || s.id || '/' || g || '.md', 'Published', ${me.id}, s.id, g, now(), now() FROM inserted s CROSS JOIN generate_series(1,100) g;`;
  execFileSync(psql,[...dbArgs,'-c',sql],{stdio:'pipe'});
  const browser=await chromium.launch();
  const context=await browser.newContext({storageState:await api.storageState(),viewport:{width:1440,height:900}});
  const page=await context.newPage();
  const measurements=[];
  for(let i=0;i<5;i++){
    const start=performance.now();
    const response=await page.goto(`${baseURL}/documents/${doc.id}`);
    await page.locator('#documentContent').getByText('Контрольный документ',{exact:true}).waitFor();
    measurements.push({readyMs:Math.round(performance.now()-start),htmlBytes:(await response.body()).length,sidebarDocumentNodes:await page.locator('#spacesSidebar .document-item').count()});
  }
  const result={addedSpaces:50,addedDocuments:5000,iterations:5,measurements};
  if (process.argv[2]) fs.writeFileSync(process.argv[2],JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify(result));
  await browser.close();await api.dispose();
})().catch(e=>{console.error(e);process.exit(1)});

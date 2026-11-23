'use strict';
const $ = id => document.getElementById(id);
let mode='tools', sessionId='demo-'+crypto.randomUUID(), ready=false, busy=false, archiveToken='', activeController=null, inspect='trace';
const samples={tools:['搜索 Java','商品 2 详情','我的订单','查看他人 1002 的订单','商品 999 详情','商品 9223372036854775808 详情'],knowledge:['已经发货的订单能直接取消吗？应该先做什么？','周末人工客服的上班时间是几点？']};
const proxied=location.pathname.startsWith('/demo/');
if(proxied){$('return-link').href='/showcase.html';$('evidence-link').href='/showcase.html';}
async function api(path, options={}) {
  const response=await fetch(path,{...options,headers:{...(archiveToken?{'X-Archive-Token':archiveToken}:{}),...options.headers}});
  return response;
}
async function jsonApi(path, body) {
  const response=await api(path,body===undefined?{}:{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  const data=await response.json();
  if(!response.ok) throw new Error(data.message||'请求未成功，请重试。');
  return data;
}
function textNode(tag,text,className){const node=document.createElement(tag);node.textContent=String(text);if(className)node.className=className;return node;}
function appendMessage(role,text,citations=[]){
  const article=document.createElement('article');article.className='message '+role;
  article.append(textNode('div',role==='user'?'YOU / REQUEST':'SHOPGUARD / RESPONSE','message-label'),textNode('p',text));
  for(const cite of citations)article.append(textNode('div','资料出处：'+cite.source+' / '+cite.section+' / 版本 '+cite.version+'\n片段：'+cite.chunkId,'citations'));
  $('messages').append(article);$('messages').scrollTop=$('messages').scrollHeight;return article;
}
function renderSamples(){
  $('examples').replaceChildren();
  for(const text of samples[mode]||[]){const b=textNode('button',text);b.type='button';b.disabled=busy;b.onclick=()=>{$('question').value=text;$('question').focus();};$('examples').append(b);}
}
function setBusy(value){
  busy=value;$('send').disabled=value||!ready;$('send').textContent=value?'处理中…':'发送 ↗';$('new-session').disabled=value;
  document.querySelectorAll('[data-mode]').forEach(b=>b.disabled=value);$('cancel').hidden=!value;renderSamples();
}
function renderTrace(trace){$('trace-list').replaceChildren();for(const e of trace||[]){const li=document.createElement('li');li.append(textNode('strong',String(e.step).padStart(2,'0')+' / '+e.stage),textNode('small',e.code),textNode('div',e.detail));$('trace-list').append(li);}}
async function refreshSession(){
  try{const messages=await jsonApi('api/demo/workbench/session?sessionId='+encodeURIComponent(sessionId));$('session-list').replaceChildren();
  if(!messages.length)$('session-list').append(textNode('p','当前会话尚无保存记录。','context-note'));
  for(const m of messages){const a=document.createElement('article');a.append(textNode('small',m.sequence+' / '+m.role),textNode('p',m.content));$('session-list').append(a);}
  }catch(e){$('session-list').replaceChildren(textNode('p',e.message,'context-note'));}
}
function setInspect(value){inspect=value;$('trace-panel').hidden=value!=='trace';$('session-panel').hidden=value!=='session';document.querySelectorAll('[data-inspect]').forEach(b=>b.classList.toggle('active',b.dataset.inspect===value));if(value==='session')refreshSession();}
document.querySelectorAll('[data-inspect]').forEach(b=>b.onclick=()=>setInspect(b.dataset.inspect));
function table(target,headers,rows){
  const t=document.createElement('table'),head=document.createElement('thead'),tr=document.createElement('tr');
  headers.forEach(h=>tr.append(textNode('th',h)));head.append(tr);t.append(head);const body=document.createElement('tbody');
  for(const row of rows){const r=document.createElement('tr');for(const item of row){const cell=textNode('td',typeof item==='object'?item.text:item);if(typeof item==='object'&&item.className)cell.className=item.className;r.append(cell);}body.append(r);}t.append(body);$(target).replaceChildren(t);
}
async function refreshKnowledge(){
  const k=await jsonApi('api/demo/workbench/knowledge');$('knowledge-stats').replaceChildren();
  for(const [value,label] of [[k.report.documentCount,'来源文档'],[k.storedChunks,'已存 Chunk'],[k.dimensions,'Fake 向量维度'],[k.chunkSize+'/'+k.overlap,'切片 / 重叠']]){const strong=textNode('strong',value);strong.append(textNode('small',label));$('knowledge-stats').append(strong);}
  table('chunk-list',['来源','章节','长度','版本'],k.chunks.map(c=>[c.source,c.section,c.characters,c.version]));
}
async function refreshMetrics(){
  const m=await jsonApi('api/demo/workbench/observability');
  if(!m.timers.length){$('metrics-results').replaceChildren(textNode('p','尚无 Timer 样本；先执行一次交易或规则问答。','result-line'));return;}
  table('metrics-results',['阶段','结果码','次数','总耗时 ms','最大 ms'],m.timers.map(t=>[t.stage,t.resultCode,t.count,t.totalMs.toFixed(2),t.maxMs.toFixed(2)]));
}
async function refreshEvidence(){await Promise.all([refreshKnowledge(),refreshMetrics()]);}
function guardedButton(id,handler){$(id).onclick=async()=>{const b=$(id);b.disabled=true;try{await handler();}catch(e){$('fault-result').textContent=e.message;}finally{b.disabled=false;}};}
guardedButton('refresh-evidence',refreshEvidence);
guardedButton('refresh-metrics',refreshMetrics);
guardedButton('reimport',async()=>{const r=await jsonApi('api/demo/workbench/ingestion',{});$('ingestion-result').textContent='实际导入结果 / created='+r.created+' / updated='+r.updated+' / unchanged='+r.unchanged+' / failed='+r.failed;await refreshKnowledge();});
guardedButton('evaluate',async()=>{
  const report=await jsonApi('api/demo/workbench/evaluation',{}),m=report.metrics;
  $('evaluation-summary').textContent='规则替身实测：'+m.correct+' / '+m.total+'，准确率 '+(m.accuracy*100).toFixed(1)+'%。此数字仅属于本次固定矩阵，不代表外部模型。';
  table('evaluation-results',['样本','预期','实际','结果'],report.caseResults.map(c=>[c.testCase.userMessage,c.result.expected,c.result.actual,{text:c.result.correct?'通过':c.result.errorType,className:c.result.correct?'pass':'fail'}]));
});
guardedButton('inject-fault',async()=>{const r=await jsonApi('api/demo/workbench/fault',{kind:$('fault-kind').value});$('fault-result').textContent='注入位置：'+r.kind+'\n实际结果码：'+r.code+'\n安全消息：'+r.message+'\n引用数：'+r.citations.length+'\n失败会话新增消息：'+r.savedMessages+'\n实验隔离：'+r.isolated;await refreshMetrics();});
document.querySelectorAll('[data-mode]').forEach(b=>b.onclick=()=>{
  mode=b.dataset.mode;document.querySelectorAll('[data-mode]').forEach(item=>item.classList.toggle('active',item===b));
  $('workspace').hidden=mode==='evidence';$('evidence-view').hidden=mode!=='evidence';
  const titles={tools:['TRANSACTION','SAFE.<br><span>EVERY TURN.</span>','查询商品与本人订单。跟踪工具调用，查看每一步的执行边界。'],knowledge:['KNOWLEDGE','EVIDENCE.<br><span>BEFORE ANSWER.</span>','让回答来自本次实际检索到的资料。出处可查，证据不足时拒答。'],evidence:['VERIFICATION','TRACE.<br><span>VERIFY. REPLAY.</span>','用真实组件复现导入、评测与故障。把每项能力变成可检查的运行证据。']};
  const selected=titles[mode];$('breadcrumb').textContent=selected[0];$('hero-title').innerHTML=selected[1];$('hero-description').textContent=selected[2];
  $('mode-title').textContent=mode==='knowledge'?'平台规则问答':'交易助手';$('question').placeholder=mode==='knowledge'?'例如：已经发货的订单能直接取消吗？':'例如：搜索 Java';
  $('mode-hint').textContent=mode==='knowledge'?'仅依据当前知识库回答':'只读工具 · 服务器身份';$('stream-choice').hidden=mode!=='tools';
  $('transport-notice').textContent=mode==='knowledge'?'回答是基于真实检索的原文摘录，引用元数据由服务器解析。':'模型为规则替身。SSE 将已经完成的回答分段发送，不是供应商 Token 流。';
  $('question').value='';renderSamples();if(mode==='evidence')refreshEvidence().catch(e=>$('fault-result').textContent=e.message);
});
$('new-session').onclick=()=>{sessionId='demo-'+crypto.randomUUID();$('messages').replaceChildren();appendMessage('assistant','新会话已开始，请输入问题。').id='welcome';renderTrace([]);$('request-details').textContent='尚未发起请求。';refreshSession();};
$('cancel').onclick=()=>activeController?.abort();
async function receiveSse(response){
  if(!response.body)throw new Error('浏览器无法读取 SSE 响应。');
  const reader=response.body.getReader(),decoder=new TextDecoder();let buffer='',answer='',done=false,result=null;
  const article=appendMessage('assistant','正在接收已完成回答…'),paragraph=article.querySelector('p');
  try {
    for(;;){const part=await reader.read();buffer+=decoder.decode(part.value||new Uint8Array(),{stream:!part.done});buffer=buffer.replace(/\r\n/g,'\n');
      let separator;while((separator=buffer.indexOf('\n\n'))>=0){
        const frame=buffer.slice(0,separator);buffer=buffer.slice(separator+2);
        let name='message';const lines=[];for(const line of frame.split('\n')){if(line.startsWith('event:'))name=line.slice(6).trim();if(line.startsWith('data:'))lines.push(line.slice(5).trimStart());}
        if(!lines.length)continue;const data=JSON.parse(lines.join('\n'));
        if(name==='delta'){answer+=data.text;paragraph.textContent=answer;$('messages').scrollTop=$('messages').scrollHeight;}
        if(name==='done'){done=true;result=data;paragraph.textContent=data.answer;renderTrace(data.trace);}
        if(name==='error')throw new Error(data.message||'SSE 安全终止。');
      }
      if(part.done)break;
    }
  }finally{reader.releaseLock();}
  if(!done)throw new Error('SSE 未收到完成事件；部分文本不算成功。');
  return result;
}
$('chat-form').addEventListener('submit',async event=>{
  event.preventDefault();const question=$('question').value.trim();if(!question||busy||!ready)return;
  const submittedMode=mode,stream=submittedMode==='tools'&&$('use-stream').checked,requestId=crypto.randomUUID(),start=performance.now();
  $('welcome')?.remove();appendMessage('user',question);$('question').value='';setBusy(true);
  activeController=new AbortController();const timeout=setTimeout(()=>activeController?.abort(),35000);
  try{
    const path=submittedMode==='knowledge'?'api/demo/knowledge':'api/demo/workbench/chat'+(stream?'/stream':'');
    const response=await api(path,{method:'POST',headers:{'Content-Type':'application/json','X-Request-Id':requestId},body:JSON.stringify(submittedMode==='knowledge'?{question}:{sessionId,question}),signal:activeController.signal});
    if(!response.ok){const e=await response.json();throw new Error(e.message||'请求未成功，请重试。');}
    const data=stream?await receiveSse(response):await response.json();
    $('request-details').textContent='请求编号：'+(data.requestId||requestId)+'\nHTTP：'+response.status+'\n耗时：'+Math.round(performance.now()-start)+' ms\n结果：'+(data.code||'SUCCESS')+'\n传输：'+(stream?'SSE / COMPLETION_REPLAY':'JSON');
    if(!stream)appendMessage(data.success===false?'error':'assistant',data.answer,data.citations||[]);
    if(submittedMode==='tools'){renderTrace(data.trace);await refreshSession();}
    else renderTrace([{step:1,stage:'RAG',code:data.code,detail:'服务器权限 / 版本过滤 → 检索 → 重排 → 引用白名单；引用 '+(data.citations||[]).length+' 条'}]);
  }catch(error){appendMessage('error',error.name==='AbortError'?'已停止接收。已完成的业务轮次可能已经保存，查看会话可核实。':error.message);$('request-details').textContent='请求编号：'+requestId+'\n传输未完成；不宣称回答成功。';await refreshSession();}
  finally{clearTimeout(timeout);activeController=null;setBusy(false);}
});
async function initialize(){
  setBusy(false);
  try{
    if(proxied){const response=await fetch('/api/bootstrap');if(!response.ok)throw new Error('学习中枢未连接');const boot=await response.json();archiveToken=boot.token;}
    const info=await jsonApi('api/demo/info');if(info.mode!=='DETERMINISTIC_DEMO')throw new Error('运行模式不匹配');
    ready=true;$('status').textContent='● 本地演示已连接';setBusy(false);renderSamples();await refreshSession();
  }catch(e){$('status').textContent='演示未连接';appendMessage('error','请从项目展示页启动本机项目，或运行 scripts/run-demo.ps1 -Port 18083。');}
}
initialize();

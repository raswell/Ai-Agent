(function(){
  const $=id=>document.getElementById(id); let config={};
  function toast(message){const t=$('toast');t.textContent=message;t.classList.add('show');setTimeout(()=>t.classList.remove('show'),2200)}
  function applyTheme(theme){if(!theme)return;const root=document.documentElement;['bg','panel','panel2','text','muted','accent','accent2','radius'].forEach(k=>{if(theme[k])root.style.setProperty('--'+k,theme[k])});}
  function renderModules(items){const host=$('modules');host.innerHTML='';(items||[]).forEach((m,i)=>{const card=document.createElement('div');card.className='module-card focusable';card.tabIndex=0;card.dataset.module=m.id||String(i);const symbol=document.createElement('div');symbol.className='symbol';symbol.textContent=m.icon||'◇';const title=document.createElement('h3');title.textContent=m.title||'Module';const desc=document.createElement('p');desc.textContent=m.description||'';card.append(symbol,title,desc);card.addEventListener('click',()=>openModule(m));card.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();openModule(m)}});host.appendChild(card)});}
  function openModule(m){if(m&&m.route&&/^modules\/[a-zA-Z0-9_./-]+\.html$/.test(m.route)&&!m.route.includes('..')){location.href='/runtime/'+m.route+'?id='+encodeURIComponent(m.id||'');}else toast((m.title||'Module')+' · route not configured')}
  function loadConfig(){fetch('/runtime/config.json',{cache:'no-store'}).then(r=>r.json()).then(c=>{config=c;applyTheme(c.theme);$('brandName').textContent=c.brand||'HOMEOS';renderModules(c.modules);}).catch(()=>renderModules([]));}
  function renderApps(){try{const apps=JSON.parse(HomeOSNative.getAppsJson()||'[]');const host=$('apps');host.innerHTML='';if(!apps.length){host.innerHTML='<div class="empty">No launchable apps found.</div>';return}apps.forEach(a=>{const card=document.createElement('div');card.className='app-card focusable';card.tabIndex=0;const icon=document.createElement('div');icon.className='app-icon';icon.textContent=(a.label||'A').trim().charAt(0).toUpperCase();const label=document.createElement('span');label.textContent=a.label||a.packageName;card.append(icon,label);const launch=()=>HomeOSNative.launchApp(a.packageName);card.addEventListener('click',launch);card.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();launch()}});host.appendChild(card)});}catch(e){$('apps').innerHTML='<div class="empty">Application list unavailable.</div>'}}
  function tick(){const d=new Date();$('clock').textContent=d.toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'});}
  function checkHealth(){const base=HomeOSNative.getServerBase();$('serverLabel').textContent=base;fetch('/api/health',{cache:'no-store'}).then(r=>r.json()).then(data=>{const ok=data.status==='ok';$('connection').textContent=ok?'● Connected':'● Unavailable';$('connection').className='status '+(ok?'online':'offline')}).catch(()=>{$('connection').textContent='● Offline';$('connection').className='status offline'})}
  $('refresh').addEventListener('click',()=>{toast('Checking for HomeOS updates…');HomeOSNative.refreshRuntime()});
  $('runtimeVersion').textContent='Runtime '+HomeOSNative.getRuntimeVersion()+' · Shell '+HomeOSNative.getShellVersion();
  loadConfig();renderApps();tick();checkHealth();setInterval(tick,1000);setInterval(checkHealth,30000);
  document.addEventListener('keydown',function(e){
    const keys=['ArrowUp','ArrowDown','ArrowLeft','ArrowRight']; if(!keys.includes(e.key))return;
    const nodes=Array.from(document.querySelectorAll('.focusable')).filter(n=>n.offsetParent!==null);
    const active=document.activeElement; if(!nodes.length)return;
    const current=nodes.includes(active)?active:nodes[0]; const a=current.getBoundingClientRect(); const ax=a.left+a.width/2, ay=a.top+a.height/2;
    let best=null, bestScore=Infinity;
    nodes.forEach(n=>{if(n===current)return;const r=n.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2,dx=x-ax,dy=y-ay;
      const forward=e.key==='ArrowRight'?dx>5:e.key==='ArrowLeft'?dx< -5:e.key==='ArrowDown'?dy>5:dy< -5;
      if(!forward)return; const primary=(e.key==='ArrowLeft'||e.key==='ArrowRight')?Math.abs(dx):Math.abs(dy); const secondary=(e.key==='ArrowLeft'||e.key==='ArrowRight')?Math.abs(dy):Math.abs(dx); const score=primary+secondary*2.2;
      if(score<bestScore){bestScore=score;best=n;}
    });
    if(best){e.preventDefault();best.focus();}
  },true);
  window.HomeOS={handleBack:function(){location.href='/runtime/index.html';return true},handleKey:function(){},reloadConfig:loadConfig};
})();

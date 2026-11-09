(function(){
 const $=id=>document.getElementById(id); const id=new URLSearchParams(location.search).get('id')||'';
 $('back').addEventListener('click',()=>location.href='/runtime/index.html');
 $('back').addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){location.href='/runtime/index.html'}});
 fetch('/runtime/config.json',{cache:'no-store'}).then(r=>r.json()).then(config=>{
   const m=(config.modules||[]).find(x=>x.id===id); if(!m){$('moduleTitle').textContent='Module not found';return;}
   $('moduleId').textContent=(m.id||'MODULE').toUpperCase();$('moduleTitle').textContent=m.title||'Module';$('moduleDescription').textContent=m.description||'';
   const host=$('sections');
   (m.sections||[{title:'Module ready',text:'Hermes can extend this module by publishing a new runtime bundle.'}]).forEach(section=>{
     const card=document.createElement('section');card.className='module-section';const h=document.createElement('h2');h.textContent=section.title||'Section';card.appendChild(h);
     if(section.text){const p=document.createElement('p');p.textContent=section.text;card.appendChild(p)}
     (section.items||[]).forEach(item=>{const row=document.createElement('div');row.className='module-item';const label=document.createElement('span');label.textContent=item.label||'';const value=document.createElement('strong');value.textContent=item.value||'';row.append(label,value);card.appendChild(row)});
     host.appendChild(card);
   });
 }).catch(()=>{$('moduleTitle').textContent='Unable to load module configuration'});
 document.addEventListener('keydown',e=>{if(!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.key))return;const nodes=Array.from(document.querySelectorAll('.focusable'));if(nodes.length>1){const i=nodes.indexOf(document.activeElement);nodes[(i+1+nodes.length)%nodes.length].focus();e.preventDefault();}},true);
})();

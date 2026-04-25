import Foundation

public final class AdminRouter: HTTPRouting, @unchecked Sendable {
    private let config: ServerConfig
    private let store: AdminConfigStore

    public init(config: ServerConfig, store: AdminConfigStore) {
        self.config = config
        self.store = store
    }

    public func route(_ request: HTTPRequest) async -> HTTPResponse {
        switch (request.method, request.path) {
        case ("GET", "/"), ("GET", "/admin"):
            return HTTPResponse.html(Self.page)
        case ("GET", "/admin/api/config"):
            guard authorized(request) else { return unauthorized() }
            return HTTPResponse.json(store.snapshot())
        case ("POST", "/admin/api/config"):
            guard authorized(request) else { return unauthorized() }
            do {
                let next = try JSONCoding.decoder.decode(ClientConfig.self, from: request.body)
                try store.update(next)
                return HTTPResponse.json(store.snapshot())
            } catch {
                return HTTPResponse.json(ErrorResponse(ok: false, error: error.localizedDescription), status: 400, reason: "Bad Request")
            }
        default:
            return HTTPResponse.text("Not found", status: 404, reason: "Not Found")
        }
    }

    private func authorized(_ request: HTTPRequest) -> Bool {
        Auth.isAuthorized(request: request, config: config)
    }

    private func unauthorized() -> HTTPResponse {
        HTTPResponse.json(ErrorResponse(ok: false, error: "Unauthorized"), status: 401, reason: "Unauthorized")
    }

    private static let page = """
    <!doctype html>
    <html lang="en">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>VibeLink Admin</title>
      <script src="https://cdn.tailwindcss.com"></script>
      <style>
        :root{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#111827;background:#f8fafc}
        *{box-sizing:border-box}
        body{margin:0}
        .row{display:flex;gap:10px;align-items:center}
        input,textarea{width:100%;font:inherit;border:1px solid #cbd5e1;border-radius:10px;padding:10px 12px;background:white;outline:none}
        input:focus,textarea:focus{border-color:#3b82f6;box-shadow:0 0 0 3px #dbeafe}
        textarea{min-height:78px;resize:vertical;line-height:1.45}
        button{border:1px solid #bfdbfe;background:#eff6ff;color:#1d4ed8;border-radius:10px;padding:9px 13px;font-weight:700;cursor:pointer}
        button.primary{background:#2563eb;color:white;border-color:#2563eb}
        button.danger{background:#fee2e2;color:#b91c1c;border-color:#fecaca}
        button.ghost{background:white;color:#1d4ed8}
        .panel{background:white;border:1px solid #e2e8f0;border-radius:12px;padding:14px;margin:10px 0;box-shadow:0 8px 24px rgba(15,23,42,.04)}
        .hidden{display:none}
        .list{display:grid;gap:10px}
        .button-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
        .quick-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
        .command-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
        .shortcut-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
        .item{border:1px solid #dbeafe;border-radius:12px;background:#f8fbff;padding:10px}
        .control-item{min-height:74px;display:grid;grid-template-columns:24px minmax(0,1fr);gap:8px;align-items:center}
        .handle{color:#64748b;cursor:grab;text-align:center;font-weight:800}
        .control-label{font-size:15px;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
        .control-type{margin-top:2px;color:#64748b;font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
        .quick{display:grid;grid-template-columns:1fr;gap:8px;align-items:start}
        .quick-top{display:flex;align-items:center;justify-content:space-between;gap:8px}
        .quick-top .handle{width:24px}
        .quick-body{display:grid;gap:8px}
        .quick-head{display:grid;gap:8px;align-items:center}
        .quick input{padding:8px 10px;font-size:13px;font-weight:700}
        .quick textarea{padding:8px 10px;font-size:13px;min-height:72px}
        .quick .danger{padding:6px 9px;border-radius:8px;font-size:12px}
        .command{display:grid;gap:10px}
        .command-top{display:flex;align-items:center;justify-content:space-between;gap:8px}
        .command-top-left{display:flex;align-items:center;gap:8px;min-width:0}
        .command-title{font-size:14px;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
        .command-body{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:8px}
        .command-body .wide{grid-column:1 / -1}
        .command input,.command textarea{padding:8px 10px;font-size:13px}
        .command textarea{min-height:70px}
        .check-row{display:flex;gap:8px;align-items:center;color:#475569;font-size:12px;font-weight:700}
        .check-row input{width:auto}
        .shortcut{display:grid;gap:8px}
        .shortcut-top{display:flex;align-items:center;justify-content:space-between;gap:8px}
        .shortcut-title{font-size:13px;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
        .shortcut-body{display:grid;grid-template-columns:1fr 1fr;gap:8px}
        .shortcut-body .wide{grid-column:1 / -1}
        .shortcut input{padding:8px 9px;font-size:12px}
        .field span{display:block;margin:0 0 5px;color:#64748b;font-size:12px;font-weight:700;text-transform:uppercase}
        .muted{color:#64748b;font-size:13px}
        .tab-button{border:0;background:transparent;color:#475569;border-radius:12px;padding:10px 14px;font-weight:800;white-space:nowrap}
        .tab-button.active{background:#2563eb;color:white;box-shadow:0 8px 20px rgba(37,99,235,.22)}
        #status{color:#64748b}
        @media (max-width:640px){
          .button-grid{grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}
          .control-item{min-height:68px;grid-template-columns:1fr;gap:4px;text-align:center;padding:8px 6px}
          .control-item .handle{font-size:15px}
          .control-label{font-size:13px}
          .control-type{display:none}
          .quick-grid{grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}
          .quick{padding:8px 6px}
          .quick .field span{font-size:10px}
          .quick input,.quick textarea{font-size:12px;padding:7px}
          .quick textarea{min-height:64px}
          .quick .danger{width:100%;padding:6px 4px;font-size:11px}
          .command-grid{grid-template-columns:1fr}
          .command-body{grid-template-columns:1fr}
          .shortcut-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
          .shortcut-body{grid-template-columns:1fr}
        }
      </style>
    </head>
    <body class="min-h-screen bg-slate-50 text-slate-950">
      <main class="mx-auto max-w-6xl px-5 py-6">
        <header class="mb-6 flex items-start justify-between gap-4">
          <div class="min-w-0">
            <h1 class="text-2xl font-black tracking-tight sm:text-3xl" data-i18n="adminTitle">VibeLink Admin</h1>
            <p class="mt-1 hidden text-sm text-slate-500 sm:block" data-i18n="subtitle">Manage mobile controls, replies, commands, and shortcut click points.</p>
          </div>
          <div class="flex shrink-0 items-center gap-3">
            <label class="flex items-center gap-2 text-sm font-bold text-slate-500">
              <span data-i18n="language">Language</span>
              <select id="languageSelect" class="w-auto rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm font-bold text-slate-700 outline-none" onchange="setLanguage(this.value)">
                <option value="en">English</option>
                <option value="zh">中文</option>
              </select>
            </label>
            <div id="saveBar" class="hidden shrink-0 items-center gap-3">
              <span id="status" class="text-sm text-slate-500"></span>
              <button class="primary px-5 py-2.5" onclick="save()" data-i18n="save">Save</button>
            </div>
          </div>
        </header>

        <div id="login" class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <div class="flex gap-3">
            <input id="token" type="password" placeholder="Token" data-i18n-placeholder="tokenPlaceholder" class="min-w-0 flex-1">
            <button class="primary" onclick="login()" data-i18n="enter">Enter</button>
          </div>
        </div>

        <div id="app" class="hidden overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <nav class="flex gap-2 overflow-x-auto border-b border-slate-200 bg-slate-100/70 p-2">
            <button id="tabButton-control" class="tab-button" onclick="showTab('control')" data-i18n="tabControl">Control Buttons</button>
            <button id="tabButton-quick" class="tab-button" onclick="showTab('quick')" data-i18n="tabQuick">Quick Replies</button>
            <button id="tabButton-commands" class="tab-button" onclick="showTab('commands')" data-i18n="tabCommands">Commands</button>
            <button id="tabButton-shortcuts" class="tab-button" onclick="showTab('shortcuts')" data-i18n="tabShortcuts">Shortcut Buttons</button>
          </nav>

          <section id="tab-control" class="tab-panel p-5">
            <div class="mb-4">
              <h2 class="text-xl font-black" data-i18n="controlTitle">Control Buttons</h2>
              <div class="muted mt-1" data-i18n="controlDescription">Drag to reorder the 12 mobile control buttons.</div>
            </div>
            <div id="buttons" class="list button-grid"></div>
          </section>

          <section id="tab-quick" class="tab-panel hidden p-5">
            <div class="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h2 class="text-xl font-black" data-i18n="quickTitle">Quick Replies</h2>
                <div class="muted mt-1" data-i18n="quickDescription">Drag to reorder; the mobile app refreshes by polling. Tapping a mobile button sends its content directly to the computer.</div>
              </div>
              <button class="ghost" onclick="addQuickText()" data-i18n="addReply">Add Reply</button>
            </div>
            <div id="quickTexts" class="list quick-grid"></div>
          </section>

          <section id="tab-commands" class="tab-panel hidden p-5">
            <div class="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h2 class="text-xl font-black" data-i18n="commandsTitle">Commands</h2>
                <div class="muted mt-1" data-i18n="commandsDescription">Predefine commands shown in the mobile Commands area. Tapping one runs it on the computer server.</div>
              </div>
              <button class="ghost" onclick="addCommand()" data-i18n="addCommand">Add Command</button>
            </div>
            <div id="commands" class="list command-grid"></div>
          </section>

          <section id="tab-shortcuts" class="tab-panel hidden p-5">
            <div class="mb-4">
              <h2 class="text-xl font-black" data-i18n="shortcutsTitle">Shortcut Buttons</h2>
              <div class="muted mt-1" data-i18n="shortcutsDescription">Manage mobile shortcut click points. The mobile app can also add them by dragging a virtual pointer on the screen.</div>
            </div>
            <div id="shortcutButtons" class="list shortcut-grid"></div>
          </section>
        </div>
        <footer class="mt-8 pb-2 text-center text-xs text-slate-400" data-i18n="copyright">Copyright © Hangzhou Duomo Technology Co., Ltd. All rights reserved.</footer>
      </main>
      <script>
        const translations = {
          en: {
            adminTitle:'VibeLink Admin',
            subtitle:'Manage mobile controls, replies, commands, and shortcut click points.',
            language:'Language',
            save:'Save',
            enter:'Enter',
            tokenPlaceholder:'Token',
            tabControl:'Control Buttons',
            tabQuick:'Quick Replies',
            tabCommands:'Commands',
            tabShortcuts:'Shortcut Buttons',
            controlTitle:'Control Buttons',
            controlDescription:'Drag to reorder the 12 mobile control buttons.',
            quickTitle:'Quick Replies',
            quickDescription:'Drag to reorder; the mobile app refreshes by polling. Tapping a mobile button sends its content directly to the computer.',
            commandsTitle:'Commands',
            commandsDescription:'Predefine commands shown in the mobile Commands area. Tapping one runs it on the computer server.',
            shortcutsTitle:'Shortcut Buttons',
            shortcutsDescription:'Manage mobile shortcut click points. The mobile app can also add them by dragging a virtual pointer on the screen.',
            addReply:'Add Reply',
            addCommand:'Add Command',
            delete:'Delete',
            label:'Label',
            content:'Content',
            name:'Name',
            workingDir:'Working Dir',
            command:'Command',
            requireConfirmation:'Require confirmation',
            shortcut:'Shortcut',
            screenW:'Screen W',
            screenH:'Screen H',
            saved:'Saved',
            saveFailed:'Save failed',
            newReply:'New Reply',
            newContent:'New content',
            newCommand:'New Command',
            copyright:'Copyright © Hangzhou Duomo Technology Co., Ltd. All rights reserved.'
          },
          zh: {
            adminTitle:'VibeLink 管理端',
            subtitle:'管理移动端控制按钮、快捷回复、命令和快捷点击点。',
            language:'语言',
            save:'保存',
            enter:'进入',
            tokenPlaceholder:'令牌',
            tabControl:'控制按钮',
            tabQuick:'快捷回复',
            tabCommands:'命令',
            tabShortcuts:'快捷按钮',
            controlTitle:'控制按钮',
            controlDescription:'拖动调整 12 个移动端控制按钮的位置。',
            quickTitle:'快捷回复',
            quickDescription:'拖动调整顺序；移动端会轮询刷新。点击移动端按钮会直接发送内容到电脑。',
            commandsTitle:'命令',
            commandsDescription:'预先定义移动端命令区域展示的命令，点击后会在电脑服务端执行。',
            shortcutsTitle:'快捷按钮',
            shortcutsDescription:'管理移动端快捷点击点。移动端也可以通过添加快捷按钮在屏幕上拖动虚拟指针录入。',
            addReply:'添加回复',
            addCommand:'添加命令',
            delete:'删除',
            label:'标签',
            content:'内容',
            name:'名称',
            workingDir:'工作目录',
            command:'命令',
            requireConfirmation:'需要确认',
            shortcut:'快捷按钮',
            screenW:'屏幕宽',
            screenH:'屏幕高',
            saved:'已保存',
            saveFailed:'保存失败',
            newReply:'新回复',
            newContent:'新内容',
            newCommand:'新命令',
            copyright:'版权所有 © 杭州多模科技有限公司'
          }
        };
        let language = localStorage.getItem('vibelinkAdminLanguage') || 'en';
        let token = localStorage.getItem('vibelinkAdminToken') || '';
        let config = null;
        let activeTab = 'control';
        const tabNames = ['control','quick','commands','shortcuts'];
        document.getElementById('token').value = token;
        document.getElementById('languageSelect').value = language;
        applyLanguage();
        if (token) load();
        function t(key){return (translations[language]&&translations[language][key])||translations.en[key]||key;}
        function setLanguage(next){language=next==='zh'?'zh':'en';localStorage.setItem('vibelinkAdminLanguage',language);applyLanguage();if(config) render();}
        function applyLanguage(){
          document.documentElement.lang=language==='zh'?'zh-CN':'en';
          document.querySelectorAll('[data-i18n]').forEach(el=>{el.textContent=t(el.dataset.i18n);});
          document.querySelectorAll('[data-i18n-placeholder]').forEach(el=>{el.placeholder=t(el.dataset.i18nPlaceholder);});
          document.title=t('adminTitle');
        }
        function headers(){return {'Authorization':'Bearer '+token,'Content-Type':'application/json'};}
        async function login(){token=document.getElementById('token').value.trim();localStorage.setItem('vibelinkAdminToken',token);await load();}
        async function load(){
          const r=await fetch('/admin/api/config',{headers:headers()});
          if(!r.ok){document.getElementById('login').classList.remove('hidden');document.getElementById('app').classList.add('hidden');document.getElementById('saveBar').classList.add('hidden');return;}
          config=await r.json();
          document.getElementById('login').classList.add('hidden');
          document.getElementById('app').classList.remove('hidden');
          document.getElementById('saveBar').classList.remove('hidden');
          document.getElementById('saveBar').classList.add('flex');
          render();
          showTab(activeTab);
        }
        function showTab(name){
          activeTab=name;
          tabNames.forEach(tab=>{
            const panel=document.getElementById('tab-'+tab);
            const button=document.getElementById('tabButton-'+tab);
            if(panel) panel.classList.toggle('hidden',tab!==name);
            if(button) button.classList.toggle('active',tab===name);
          });
        }
        function render(){renderButtons();renderQuickTexts();renderCommands();renderShortcutButtons();}
        function draggable(el,list,key,index){
          el.draggable=true;
          el.ondragstart=e=>e.dataTransfer.setData('text/plain',index);
          el.ondragover=e=>e.preventDefault();
          el.ondrop=e=>{e.preventDefault();const from=Number(e.dataTransfer.getData('text/plain'));const row=config[key].splice(from,1)[0];config[key].splice(index,0,row);render();};
        }
        function renderButtons(){
          const root=document.getElementById('buttons');root.innerHTML='';
          config.controlButtons.forEach((b,i)=>{
            const el=document.createElement('div');el.className='item control-item';
            el.innerHTML='<div class="handle">☰</div><div><div class="control-label">'+escapeHtml(localizedControlLabel(b))+'</div><div class="control-type">'+escapeHtml(b.type)+'</div></div>';
            draggable(el,root,'controlButtons',i);root.appendChild(el);
          });
        }
        function renderQuickTexts(){
          const root=document.getElementById('quickTexts');root.innerHTML='';
          config.quickTexts.forEach((q,i)=>{
            const el=document.createElement('div');el.className='item quick';
            el.innerHTML='<div class="quick-top"><div class="handle">☰</div><button class="danger" onclick="removeQuickText('+i+')">'+escapeHtml(t('delete'))+'</button></div><div class="quick-body"><label class="field"><span>'+escapeHtml(t('label'))+'</span><input value="'+attr(q.name)+'" oninput="config.quickTexts['+i+'].name=this.value"></label><label class="field"><span>'+escapeHtml(t('content'))+'</span><textarea oninput="config.quickTexts['+i+'].content=this.value">'+escapeHtml(q.content)+'</textarea></label></div>';
            draggable(el,root,'quickTexts',i);root.appendChild(el);
          });
        }
        function addQuickText(){config.quickTexts.push({id:'quick_'+Date.now(),name:t('newReply'),group:'',content:t('newContent')});renderQuickTexts();}
        function removeQuickText(i){config.quickTexts.splice(i,1);renderQuickTexts();}
        function renderCommands(){
          const root=document.getElementById('commands');root.innerHTML='';
          config.commands=(config.commands||[]);
          config.commands.forEach((c,i)=>{
            const el=document.createElement('div');el.className='item command';
            const checked=c.requiresConfirmation?'checked':'';
            el.innerHTML='<div class="command-top"><div class="command-top-left"><div class="handle">☰</div><div class="command-title">'+escapeHtml(c.name||t('command'))+'</div></div><button class="danger" onclick="removeCommand('+i+')">'+escapeHtml(t('delete'))+'</button></div><div class="command-body"><label class="field"><span>'+escapeHtml(t('name'))+'</span><input value="'+attr(c.name)+'" oninput="config.commands['+i+'].name=this.value"></label><label class="field"><span>'+escapeHtml(t('workingDir'))+'</span><input value="'+attr(c.workingDirectory)+'" oninput="config.commands['+i+'].workingDirectory=this.value"></label><label class="field wide"><span>'+escapeHtml(t('command'))+'</span><textarea oninput="config.commands['+i+'].command=this.value">'+escapeHtml(c.command)+'</textarea></label><label class="check-row wide"><input type="checkbox" '+checked+' onchange="config.commands['+i+'].requiresConfirmation=this.checked">'+escapeHtml(t('requireConfirmation'))+'</label></div>';
            draggable(el,root,'commands',i);root.appendChild(el);
          });
        }
        function addCommand(){config.commands=(config.commands||[]);config.commands.push({id:'command_'+Date.now(),name:t('newCommand'),command:'pwd',workingDirectory:'',requiresConfirmation:false});renderCommands();}
        function removeCommand(i){config.commands.splice(i,1);renderCommands();}
        function renderShortcutButtons(){
          const root=document.getElementById('shortcutButtons');root.innerHTML='';
          config.shortcutButtons=(config.shortcutButtons||[]);
          config.shortcutButtons.forEach((s,i)=>{
            const el=document.createElement('div');el.className='item shortcut';
            const checked=s.requiresConfirmation?'checked':'';
            el.innerHTML='<div class="shortcut-top"><div class="handle">☰</div><div class="shortcut-title">'+escapeHtml(s.name||t('shortcut'))+'</div><button class="danger" onclick="removeShortcutButton('+i+')">'+escapeHtml(t('delete'))+'</button></div><div class="shortcut-body"><label class="field wide"><span>'+escapeHtml(t('name'))+'</span><input value="'+attr(s.name)+'" oninput="config.shortcutButtons['+i+'].name=this.value"></label><label class="field"><span>X</span><input type="number" value="'+attr(s.x)+'" oninput="config.shortcutButtons['+i+'].x=Number(this.value)"></label><label class="field"><span>Y</span><input type="number" value="'+attr(s.y)+'" oninput="config.shortcutButtons['+i+'].y=Number(this.value)"></label><label class="field"><span>'+escapeHtml(t('screenW'))+'</span><input type="number" value="'+attr(s.screenWidth)+'" oninput="config.shortcutButtons['+i+'].screenWidth=Number(this.value)"></label><label class="field"><span>'+escapeHtml(t('screenH'))+'</span><input type="number" value="'+attr(s.screenHeight)+'" oninput="config.shortcutButtons['+i+'].screenHeight=Number(this.value)"></label><label class="check-row wide"><input type="checkbox" '+checked+' onchange="config.shortcutButtons['+i+'].requiresConfirmation=this.checked">'+escapeHtml(t('requireConfirmation'))+'</label></div>';
            draggable(el,root,'shortcutButtons',i);root.appendChild(el);
          });
        }
        function removeShortcutButton(i){config.shortcutButtons.splice(i,1);renderShortcutButtons();}
        async function save(){
          const r=await fetch('/admin/api/config',{method:'POST',headers:headers(),body:JSON.stringify(config)});
          document.getElementById('status').textContent=r.ok?t('saved'):t('saveFailed');
          if(r.ok){config=await r.json();render();}
        }
        function localizedControlLabel(button){
          if(language!=='zh') return button.label;
          const labels={sendText:'发送文本',backspace:'退格',keyboard:'键盘',voice:'键盘',selectAll:'全选',enter:'回车',cmdEnter:'命令回车',copy:'复制',paste:'粘贴',escape:'退出',interrupt:'中断',undo:'撤销',close:'关闭'};
          return labels[button.type]||button.label;
        }
        function escapeHtml(s){return String(s||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
        function attr(s){return escapeHtml(s).replace(/"/g,'&quot;');}
      </script>
    </body>
    </html>
    """
}

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
    <html lang="zh-CN">
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
            <h1 class="text-2xl font-black tracking-tight sm:text-3xl">VibeLink Admin</h1>
            <p class="mt-1 hidden text-sm text-slate-500 sm:block">Manage mobile controls, replies, commands, and shortcut click points.</p>
          </div>
          <div id="saveBar" class="hidden shrink-0 items-center gap-3">
            <span id="status" class="text-sm text-slate-500"></span>
            <button class="primary px-5 py-2.5" onclick="save()">Save</button>
          </div>
        </header>

        <div id="login" class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <div class="flex gap-3">
            <input id="token" type="password" placeholder="Token" class="min-w-0 flex-1">
            <button class="primary" onclick="login()">Enter</button>
          </div>
        </div>

        <div id="app" class="hidden overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <nav class="flex gap-2 overflow-x-auto border-b border-slate-200 bg-slate-100/70 p-2">
            <button id="tabButton-control" class="tab-button" onclick="showTab('control')">Control Buttons</button>
            <button id="tabButton-quick" class="tab-button" onclick="showTab('quick')">Quick Replies</button>
            <button id="tabButton-commands" class="tab-button" onclick="showTab('commands')">Commands</button>
            <button id="tabButton-shortcuts" class="tab-button" onclick="showTab('shortcuts')">Shortcut Buttons</button>
          </nav>

          <section id="tab-control" class="tab-panel p-5">
            <div class="mb-4">
              <h2 class="text-xl font-black">Control Buttons</h2>
              <div class="muted mt-1">拖动调整 12 个移动端按钮的位置。</div>
            </div>
            <div id="buttons" class="list button-grid"></div>
          </section>

          <section id="tab-quick" class="tab-panel hidden p-5">
            <div class="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h2 class="text-xl font-black">Quick Replies</h2>
                <div class="muted mt-1">拖动调整顺序；移动端会轮询刷新。点击移动端按钮会直接发送内容到电脑。</div>
              </div>
              <button class="ghost" onclick="addQuickText()">Add Reply</button>
            </div>
            <div id="quickTexts" class="list quick-grid"></div>
          </section>

          <section id="tab-commands" class="tab-panel hidden p-5">
            <div class="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h2 class="text-xl font-black">Commands</h2>
                <div class="muted mt-1">预先定义移动端 Commands 区域展示的命令，点击后会在电脑服务端执行。</div>
              </div>
              <button class="ghost" onclick="addCommand()">Add Command</button>
            </div>
            <div id="commands" class="list command-grid"></div>
          </section>

          <section id="tab-shortcuts" class="tab-panel hidden p-5">
            <div class="mb-4">
              <h2 class="text-xl font-black">Shortcut Buttons</h2>
              <div class="muted mt-1">管理移动端快捷点击点。移动端也可以通过 Add Shortcut 在屏幕上拖动虚拟指针录入。</div>
            </div>
            <div id="shortcutButtons" class="list shortcut-grid"></div>
          </section>
        </div>
      </main>
      <script>
        let token = localStorage.getItem('vibelinkAdminToken') || '';
        let config = null;
        let activeTab = 'control';
        const tabNames = ['control','quick','commands','shortcuts'];
        document.getElementById('token').value = token;
        if (token) load();
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
            el.innerHTML='<div class="handle">☰</div><div><div class="control-label">'+escapeHtml(b.label)+'</div><div class="control-type">'+escapeHtml(b.type)+'</div></div>';
            draggable(el,root,'controlButtons',i);root.appendChild(el);
          });
        }
        function renderQuickTexts(){
          const root=document.getElementById('quickTexts');root.innerHTML='';
          config.quickTexts.forEach((q,i)=>{
            const el=document.createElement('div');el.className='item quick';
            el.innerHTML='<div class="quick-top"><div class="handle">☰</div><button class="danger" onclick="removeQuickText('+i+')">Delete</button></div><div class="quick-body"><label class="field"><span>Label</span><input value="'+attr(q.name)+'" oninput="config.quickTexts['+i+'].name=this.value"></label><label class="field"><span>Content</span><textarea oninput="config.quickTexts['+i+'].content=this.value">'+escapeHtml(q.content)+'</textarea></label></div>';
            draggable(el,root,'quickTexts',i);root.appendChild(el);
          });
        }
        function addQuickText(){config.quickTexts.push({id:'quick_'+Date.now(),name:'New Reply',group:'',content:'New content'});renderQuickTexts();}
        function removeQuickText(i){config.quickTexts.splice(i,1);renderQuickTexts();}
        function renderCommands(){
          const root=document.getElementById('commands');root.innerHTML='';
          config.commands=(config.commands||[]);
          config.commands.forEach((c,i)=>{
            const el=document.createElement('div');el.className='item command';
            const checked=c.requiresConfirmation?'checked':'';
            el.innerHTML='<div class="command-top"><div class="command-top-left"><div class="handle">☰</div><div class="command-title">'+escapeHtml(c.name||'Command')+'</div></div><button class="danger" onclick="removeCommand('+i+')">Delete</button></div><div class="command-body"><label class="field"><span>Name</span><input value="'+attr(c.name)+'" oninput="config.commands['+i+'].name=this.value"></label><label class="field"><span>Working Dir</span><input value="'+attr(c.workingDirectory)+'" oninput="config.commands['+i+'].workingDirectory=this.value"></label><label class="field wide"><span>Command</span><textarea oninput="config.commands['+i+'].command=this.value">'+escapeHtml(c.command)+'</textarea></label><label class="check-row wide"><input type="checkbox" '+checked+' onchange="config.commands['+i+'].requiresConfirmation=this.checked">Require confirmation</label></div>';
            draggable(el,root,'commands',i);root.appendChild(el);
          });
        }
        function addCommand(){config.commands=(config.commands||[]);config.commands.push({id:'command_'+Date.now(),name:'New Command',command:'pwd',workingDirectory:'',requiresConfirmation:false});renderCommands();}
        function removeCommand(i){config.commands.splice(i,1);renderCommands();}
        function renderShortcutButtons(){
          const root=document.getElementById('shortcutButtons');root.innerHTML='';
          config.shortcutButtons=(config.shortcutButtons||[]);
          config.shortcutButtons.forEach((s,i)=>{
            const el=document.createElement('div');el.className='item shortcut';
            const checked=s.requiresConfirmation?'checked':'';
            el.innerHTML='<div class="shortcut-top"><div class="handle">☰</div><div class="shortcut-title">'+escapeHtml(s.name||'Shortcut')+'</div><button class="danger" onclick="removeShortcutButton('+i+')">Delete</button></div><div class="shortcut-body"><label class="field wide"><span>Name</span><input value="'+attr(s.name)+'" oninput="config.shortcutButtons['+i+'].name=this.value"></label><label class="field"><span>X</span><input type="number" value="'+attr(s.x)+'" oninput="config.shortcutButtons['+i+'].x=Number(this.value)"></label><label class="field"><span>Y</span><input type="number" value="'+attr(s.y)+'" oninput="config.shortcutButtons['+i+'].y=Number(this.value)"></label><label class="field"><span>Screen W</span><input type="number" value="'+attr(s.screenWidth)+'" oninput="config.shortcutButtons['+i+'].screenWidth=Number(this.value)"></label><label class="field"><span>Screen H</span><input type="number" value="'+attr(s.screenHeight)+'" oninput="config.shortcutButtons['+i+'].screenHeight=Number(this.value)"></label><label class="check-row wide"><input type="checkbox" '+checked+' onchange="config.shortcutButtons['+i+'].requiresConfirmation=this.checked">Require confirmation</label></div>';
            draggable(el,root,'shortcutButtons',i);root.appendChild(el);
          });
        }
        function removeShortcutButton(i){config.shortcutButtons.splice(i,1);renderShortcutButtons();}
        async function save(){
          const r=await fetch('/admin/api/config',{method:'POST',headers:headers(),body:JSON.stringify(config)});
          document.getElementById('status').textContent=r.ok?'Saved':'Save failed';
          if(r.ok){config=await r.json();render();}
        }
        function escapeHtml(s){return String(s||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
        function attr(s){return escapeHtml(s).replace(/"/g,'&quot;');}
      </script>
    </body>
    </html>
    """
}

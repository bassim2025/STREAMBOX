// IPTV playlist portal. No dependencies. Run: node server.js
const http = require('http'), fs = require('fs'), crypto = require('crypto');
const PORT = process.env.PORT || 3000;
const BASE = process.env.BASE_URL || ''; // e.g. https://iptv.example.com
const DB = 'data.json';
let db = {};
try { db = JSON.parse(fs.readFileSync(DB, 'utf8')); } catch {}
const save = () => fs.writeFileSync(DB, JSON.stringify(db));
const norm = m => String(m || '').toUpperCase().replace(/[^0-9A-F]/g, '');
const eq = (a, b) => { a = Buffer.from(String(a)); b = Buffer.from(String(b)); return a.length === b.length && crypto.timingSafeEqual(a, b); };

const fails = {};
const blocked = ip => (fails[ip] || 0) >= 10;
const fail = ip => { fails[ip] = (fails[ip] || 0) + 1; setTimeout(() => fails[ip]--, 15 * 60 * 1000); };

const readBody = req => new Promise((res, rej) => {
  let d = ''; req.on('data', c => { d += c; if (d.length > 5e6) { rej(); req.destroy(); } });
  req.on('end', () => { try { res(d ? JSON.parse(d) : {}); } catch { res({}); } });
});
const send = (r, code, obj, type = 'application/json') =>
  { r.writeHead(code, { 'Content-Type': type, 'Access-Control-Allow-Origin': '*' }); r.end(type === 'application/json' ? JSON.stringify(obj) : obj); };

function auth(ip, mac, key) {
  const d = db[norm(mac)];
  if (!d || !eq(d.key, key)) { fail(ip); return null; }
  return d;
}
const linkFor = (mac, d, p) => p.url || `${BASE}/m3u/${norm(mac)}/${d.key}/${p.id}`;

const PAGE = `<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><title>Playlists</title>
<style>body{font-family:system-ui;margin:0;padding:16px;max-width:520px;margin:auto;background:#111;color:#eee}
input,textarea,button{width:100%;box-sizing:border-box;padding:12px;margin:6px 0;border-radius:8px;border:1px solid #444;background:#222;color:#eee;font-size:16px}
button{background:#2563eb;border:0}.del{background:#b91c1c;width:auto;padding:6px 12px}.row{display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #333;padding:8px 0}
.err{color:#f87171}</style>
<h2>Playlist Manager</h2>
<div id=login><input id=mac placeholder="MAC address"><input id=key placeholder="Device key" type=password><button onclick=go()>Log in</button></div>
<div id=main hidden><h3>Add playlist</h3><input id=name placeholder="Name"><input id=url placeholder="M3U URL (or paste below)">
<textarea id=content rows=5 placeholder="Or paste M3U content"></textarea><button onclick=add()>Add</button><h3>Your playlists</h3><div id=list></div></div>
<p id=msg class=err></p>
<script>
let A={};const $=i=>document.getElementById(i);
async function api(p,b){const r=await fetch('/api/'+p,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...A,...b})});return r.json()}
async function go(){A={mac:$('mac').value,key:$('key').value};await load()}
async function load(){const r=await api('list');if(r.error){$('msg').textContent=r.error;return}
$('msg').textContent='';$('login').hidden=true;$('main').hidden=false;
$('list').innerHTML=r.playlists.map(p=>'<div class=row><span>'+esc(p.name)+'</span><button class=del onclick="del(\\''+p.id+'\\')">Delete</button></div>').join('')||'<p>None yet</p>'}
async function add(){const r=await api('add',{name:$('name').value,url:$('url').value,content:$('content').value});
if(r.error){$('msg').textContent=r.error;return}$('name').value=$('url').value=$('content').value='';load()}
async function del(id){await api('delete',{id});load()}
const esc=s=>s.replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
</script>`;

http.createServer(async (req, res) => {
  const ip = req.socket.remoteAddress, u = new URL(req.url, 'http://x');
  if (req.method === 'OPTIONS') return send(res, 204, '', 'text/plain');
  if (u.pathname === '/') return send(res, 200, PAGE, 'text/html');
  if (blocked(ip)) return send(res, 429, { error: 'Too many failed attempts, try later' });

  // M3U served to the player
  let m = u.pathname.match(/^\/m3u\/([0-9A-F]+)\/([^/]+)\/([^/]+)$/);
  if (m) {
    const d = auth(ip, m[1], m[2]); const p = d && d.playlists.find(x => x.id === m[3]);
    return p ? send(res, 200, p.content || '', 'audio/x-mpegurl') : send(res, 404, 'Not found', 'text/plain');
  }
  // App fetches its playlists
  if (u.pathname === '/api/playlists' && req.method === 'GET') {
    const mac = u.searchParams.get('mac'), d = auth(ip, mac, u.searchParams.get('key'));
    if (!d) return send(res, 403, { error: 'Invalid device' });
    return send(res, 200, d.playlists.map(p => ({ id: p.id, name: p.name, url: linkFor(mac, d, p) })));
  }
  if (req.method !== 'POST') return send(res, 404, { error: 'Not found' });
  const b = await readBody(req).catch(() => ({}));
  const mac = norm(b.mac);

  if (u.pathname === '/api/register') { // called once by the app on first launch
    if (mac.length !== 12 || !b.key || String(b.key).length < 6) return send(res, 400, { error: 'Bad MAC or key' });
    if (!db[mac]) { db[mac] = { key: String(b.key), playlists: [] }; save(); return send(res, 200, { ok: true }); }
    return eq(db[mac].key, b.key) ? send(res, 200, { ok: true }) : (fail(ip), send(res, 403, { error: 'Invalid device' }));
  }
  const d = auth(ip, mac, b.key);
  if (!d) return send(res, 403, { error: 'Invalid MAC or device key' });
  if (u.pathname === '/api/list') return send(res, 200, { playlists: d.playlists.map(p => ({ id: p.id, name: p.name })) });
  if (u.pathname === '/api/add') {
    if (!b.url && !b.content) return send(res, 400, { error: 'Provide a URL or M3U content' });
    d.playlists.push({ id: crypto.randomBytes(6).toString('hex'), name: String(b.name || 'Playlist').slice(0, 60), url: b.url ? String(b.url) : '', content: b.url ? '' : String(b.content) });
    save(); return send(res, 200, { ok: true });
  }
  if (u.pathname === '/api/delete') { d.playlists = d.playlists.filter(p => p.id !== b.id); save(); return send(res, 200, { ok: true }); }
  send(res, 404, { error: 'Not found' });
}).listen(PORT, () => console.log('Portal running on port ' + PORT));

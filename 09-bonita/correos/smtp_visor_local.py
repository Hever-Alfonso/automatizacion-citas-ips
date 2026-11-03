"""
Servidor SMTP local (puerto 1025) + Bandeja Web de Correos (http://localhost:8025)
para visualizar en tiempo real las notificaciones enviadas por el Conector de Correo
de Bonita Studio (Confirmación de cita y Recordatorio 24h antes).
"""
import asyncio
import email
from email.header import decode_header
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
import threading
from datetime import datetime

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "bandeja")
os.makedirs(OUT_DIR, exist_ok=True)
MESSAGES = []


def _decode(val):
    if not val:
        return ""
    parts = decode_header(val)
    out = []
    for text, enc in parts:
        if isinstance(text, bytes):
            out.append(text.decode(enc or "utf-8", errors="replace"))
        else:
            out.append(str(text))
    return "".join(out)


def _parse_and_store(raw_bytes):
    msg = email.message_from_bytes(raw_bytes)
    subject = _decode(msg.get("Subject", "(Sin asunto)"))
    sender = _decode(msg.get("From", ""))
    recipient = _decode(msg.get("To", ""))
    body_html = ""
    if msg.is_multipart():
        for part in msg.walk():
            ctype = part.get_content_type()
            if ctype in ("text/html", "text/plain"):
                payload = part.get_payload(decode=True)
                if payload:
                    body_html = payload.decode(part.get_content_charset() or "utf-8", errors="replace")
                    if ctype == "text/html":
                        break
    else:
        payload = msg.get_payload(decode=True)
        if payload:
            body_html = payload.decode(msg.get_content_charset() or "utf-8", errors="replace")

    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    fname = f"correo_{len(MESSAGES) + 1:03d}_{datetime.now().strftime('%H%M%S')}.html"
    fpath = os.path.join(OUT_DIR, fname)
    with open(fpath, "w", encoding="utf-8") as f:
        f.write(body_html)

    entry = {
        "id": len(MESSAGES) + 1,
        "fecha": now_str,
        "de": sender,
        "para": recipient,
        "asunto": subject,
        "html": body_html,
        "archivo": fpath,
    }
    MESSAGES.insert(0, entry)
    print(f"[{now_str}] Correo recibido -> Para: {recipient} | Asunto: {subject}")


class _SMTPProtocol(asyncio.Protocol):
    def connection_made(self, transport):
        self.transport = transport
        self.buffer = b""
        self.in_data = False
        self.data_buf = bytearray()
        self.transport.write(b"220 localhost ESMTP IPS-Salud Mail Server\r\n")

    def data_received(self, data):
        self.buffer += data
        while b"\r\n" in self.buffer:
            line, self.buffer = self.buffer.split(b"\r\n", 1)
            if self.in_data:
                if line == b".":
                    self.in_data = False
                    try:
                        _parse_and_store(bytes(self.data_buf))
                    except Exception as e:
                        print("Error parseando correo:", e)
                    self.data_buf.clear()
                    self.transport.write(b"250 2.0.0 OK Mensaje recibido\r\n")
                else:
                    if line.startswith(b".."):
                        line = line[1:]
                    self.data_buf.extend(line + b"\r\n")
            else:
                cmd = line.decode("latin-1", errors="ignore").upper()
                if cmd.startswith(("EHLO", "HELO")):
                    self.transport.write(b"250-localhost\r\n250 AUTH PLAIN LOGIN\r\n")
                elif cmd.startswith("AUTH"):
                    self.transport.write(b"235 2.7.0 Authentication successful\r\n")
                elif cmd.startswith(("MAIL FROM", "RCPT TO", "RSET", "NOOP")):
                    self.transport.write(b"250 2.1.0 OK\r\n")
                elif cmd == "DATA":
                    self.in_data = True
                    self.data_buf.clear()
                    self.transport.write(b"354 End data with <CR><LF>.<CR><LF>\r\n")
                elif cmd.startswith("QUIT"):
                    self.transport.write(b"221 2.0.0 Bye\r\n")
                    self.transport.close()
                else:
                    self.transport.write(b"250 OK\r\n")


HTML_PAGE = """<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8"/>
<title>Bandeja de Correos IPS Salud (Bonita BPM)</title>
<style>
  body { font-family: 'Segoe UI', Arial, sans-serif; margin: 0; background: #f1f5f9; color: #0f172a; }
  header { background: #0f766e; color: white; padding: 14px 24px; display: flex; justify-content: space-between; align-items: center; }
  header h1 { margin: 0; font-size: 18px; }
  .badge { background: #14b8a6; padding: 4px 10px; border-radius: 999px; font-size: 12px; }
  .container { display: grid; grid-template-columns: 360px 1fr; height: calc(100vh - 54px); }
  .list { background: white; border-right: 1px solid #cbd5e1; overflow-y: auto; }
  .item { padding: 14px 16px; border-bottom: 1px solid #e2e8f0; cursor: pointer; }
  .item:hover, .item.active { background: #f0fdfa; }
  .item .subj { font-weight: 600; font-size: 14px; color: #0f766e; margin-bottom: 4px; }
  .item .meta { font-size: 12px; color: #64748b; }
  .viewer { padding: 24px; overflow-y: auto; }
  .card { background: white; border-radius: 8px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
  .empty { color: #64748b; text-align: center; margin-top: 80px; }
</style>
</head>
<body>
<header>
  <h1>Bandeja de Correos - IPS Salud (SMTP localhost:1025)</h1>
  <span class="badge" id="count">0 correos</span>
</header>
<div class="container">
  <div class="list" id="list"></div>
  <div class="viewer" id="viewer"><div class="empty">Selecciona un correo o envía una solicitud en Bonita para ver la notificación aquí en vivo.</div></div>
</div>
<script>
let selectedId = null;
async function load() {
  const res = await fetch('/api/messages');
  const msgs = await res.json();
  document.getElementById('count').textContent = msgs.length + ' correo(s)';
  const list = document.getElementById('list');
  if (!msgs.length) {
    list.innerHTML = '<div class="empty" style="margin:30px 16px">Sin correos recibidos aún.</div>';
    return;
  }
  if (!selectedId) selectedId = msgs[0].id;
  list.innerHTML = msgs.map(m => `
    <div class="item ${m.id === selectedId ? 'active' : ''}" onclick="selectMsg(${m.id})">
      <div class="subj">${m.asunto}</div>
      <div class="meta">Para: <b>${m.para}</b><br/>${m.fecha}</div>
    </div>
  `).join('');
  const cur = msgs.find(m => m.id === selectedId) || msgs[0];
  if (cur) {
    document.getElementById('viewer').innerHTML = `
      <div class="card">
        <div style="border-bottom:1px solid #e2e8f0;padding-bottom:12px;margin-bottom:16px">
          <div style="font-size:18px;font-weight:700;color:#0f766e">${cur.asunto}</div>
          <div style="font-size:13px;color:#475569;margin-top:6px">
            <b>De:</b> ${cur.de} &nbsp;|&nbsp; <b>Para:</b> ${cur.para} &nbsp;|&nbsp; <b>Fecha:</b> ${cur.fecha}
          </div>
        </div>
        <div>${cur.html}</div>
      </div>`;
  }
}
function selectMsg(id) { selectedId = id; load(); }
setInterval(load, 2000);
load();
</script>
</body>
</html>
"""


class _WebHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/api/messages":
            data = json.dumps(MESSAGES, ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            self.wfile.write(data)
        else:
            data = HTML_PAGE.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(data)

    def log_message(self, format, *args):
        return


def _run_http():
    httpd = HTTPServer(("127.0.0.1", 8025), _WebHandler)
    httpd.serve_forever()


async def main():
    threading.Thread(target=_run_http, daemon=True).start()
    loop = asyncio.get_running_loop()
    server = await loop.create_server(_SMTPProtocol, "127.0.0.1", 1025)
    print("Servidor SMTP escuchando en localhost:1025 | Visor Web en http://localhost:8025")
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    asyncio.run(main())

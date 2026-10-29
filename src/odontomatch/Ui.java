package odontomatch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

/** Layout, componentes, formatação e script de tempo real. */
public final class Ui {
    private Ui() {}

    static final Locale BR = Locale.forLanguageTag("pt-BR");

    public static String h(Object o) {
        if (o == null) return "";
        String s = o.toString();
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    public static String money(BigDecimal v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(BR);
        return f.format(v == null ? BigDecimal.ZERO : v).replace('\u00a0', ' ');
    }

    public static String num(BigDecimal v) { return v == null ? "0" : v.stripTrailingZeros().toPlainString().replace('.', ','); }

    public static String inputNum(BigDecimal v) { return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString(); }

    public static ZoneId zone() {
        try { return ZoneId.of(Svc.cfg("fuso", "America/Sao_Paulo")); } catch (Exception e) { return ZoneId.of("America/Sao_Paulo"); }
    }

    public static String today() { return LocalDate.now(zone()).toString(); }
    public static String nowHm() { return LocalTime.now(zone()).format(DateTimeFormatter.ofPattern("HH:mm")); }
    public static String month() { return today().substring(0, 7); }
    public static String plusDays(String d, int n) { return LocalDate.parse(d).plusDays(n).toString(); }

    public static String d(String iso) {
        if (iso == null || iso.length() < 10) return iso == null ? "" : iso;
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
    }

    public static String dw(String iso) {
        try { return LocalDate.parse(iso).getDayOfWeek().getDisplayName(TextStyle.SHORT, BR).replace(".", "") + ", " + d(iso); } catch (Exception e) { return d(iso); }
    }

    public static String dLong(String iso) {
        try { return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", BR)); } catch (Exception e) { return d(iso); }
    }

    public static String ts(long ms) {
        if (ms <= 0) return "—";
        return Instant.ofEpochMilli(ms).atZone(zone()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public static String monthName(String ym) {
        try {
            YearMonth y = YearMonth.parse(ym);
            String n = y.getMonth().getDisplayName(TextStyle.SHORT, BR).replace(".", "");
            return n.substring(0, 1).toUpperCase() + n.substring(1) + "/" + String.valueOf(y.getYear()).substring(2);
        } catch (Exception e) { return ym; }
    }

    public static int age(String birth) {
        try { return Period.between(LocalDate.parse(birth), LocalDate.now(zone())).getYears(); } catch (Exception e) { return -1; }
    }

    // ---------------- componentes ----------------
    public static String input(String label, String name, Object value, String type, String attrs) {
        return "<label>" + h(label) + "<input type=\"" + type + "\" name=\"" + name + "\" value=\"" + h(value) + "\" " + (attrs == null ? "" : attrs) + "></label>";
    }

    public static String input(String label, String name, Object value) { return input(label, name, value, "text", ""); }

    public static String area(String label, String name, Object value, int rows) {
        return "<label>" + h(label) + "<textarea name=\"" + name + "\" rows=\"" + rows + "\">" + h(value) + "</textarea></label>";
    }

    public static String select(String label, String name, String[][] opts, String sel, String attrs) {
        StringBuilder b = new StringBuilder("<label>" + h(label) + "<select name=\"" + name + "\" " + (attrs == null ? "" : attrs) + ">");
        for (String[] o : opts) b.append("<option value=\"").append(h(o[0])).append("\"").append(o[0].equals(sel) ? " selected" : "").append(">").append(h(o[1])).append("</option>");
        return b.append("</select></label>").toString();
    }

    public static String[][] opts(String... values) {
        String[][] r = new String[values.length][];
        for (int i = 0; i < values.length; i++) r[i] = new String[]{values[i], values[i]};
        return r;
    }

    /** Lista de opções com uma primeira opção "todas". */
    public static String[][] withAll(String allLabel, String[][] base) {
        String[][] r = new String[base.length + 1][];
        r[0] = new String[]{"", allLabel};
        System.arraycopy(base, 0, r, 1, base.length);
        return r;
    }

    public static String check(String label, String name, boolean on) {
        return "<label class=\"chk\"><input type=\"checkbox\" name=\"" + name + "\"" + (on ? " checked" : "") + "> " + h(label) + "</label>";
    }

    public static String badge(String text, String cls) { return "<span class=\"badge " + cls + "\">" + h(text) + "</span>"; }

    public static String kpi(String label, String value, String icon) {
        return "<div class=\"card kpi\"><div class=\"ic\">" + icon + "</div><div><div class=\"v\">" + value + "</div><div class=\"l\">" + h(label) + "</div></div></div>";
    }

    public static String post(Req r, String action, String label, String cls, String confirm, String... hidden) {
        StringBuilder b = new StringBuilder("<form method=\"post\" action=\"" + h(action) + "\" class=\"inl\"");
        if (confirm != null) b.append(" onsubmit=\"return confirm('").append(h(confirm.replace("'", "\\'"))).append("')\"");
        b.append(">").append(r.csrf());
        for (int i = 0; i + 1 < hidden.length; i += 2) b.append("<input type=\"hidden\" name=\"").append(h(hidden[i])).append("\" value=\"").append(h(hidden[i + 1])).append("\">");
        return b.append("<button class=\"btn ").append(cls).append("\">").append(label).append("</button></form>").toString();
    }

    public static String table(String[] heads, List<String[]> rows, String empty) {
        if (rows.isEmpty()) return "<div class=\"empty\">" + h(empty) + "</div>";
        StringBuilder b = new StringBuilder("<div class=\"tw\"><table class=\"resp\"><thead><tr>");
        for (String hd : heads) b.append("<th>").append(h(hd)).append("</th>");
        b.append("</tr></thead><tbody>");
        for (String[] r : rows) {
            b.append("<tr>");
            for (int i = 0; i < r.length; i++) b.append("<td data-l=\"").append(h(i < heads.length ? heads[i] : "")).append("\">").append(r[i] == null ? "" : r[i]).append("</td>");
            b.append("</tr>");
        }
        return b.append("</tbody></table></div>").toString();
    }

    public static String empty(String t) { return "<div class=\"empty\">" + h(t) + "</div>"; }

    /** Grade de disponibilidade semanal (dia 1..7 × turno M/T/N). */
    public static String dispGrid(String disp) {
        Set<String> on = new HashSet<>(Arrays.asList(disp.split(",")));
        String[] dias = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
        String[][] tur = {{"M", "Manhã"}, {"T", "Tarde"}, {"N", "Noite"}};
        StringBuilder b = new StringBuilder("<div class=\"tw\"><table class=\"disp\"><tr><th></th>");
        for (String d : dias) b.append("<th>").append(d).append("</th>");
        b.append("</tr>");
        for (String[] t : tur) {
            b.append("<tr><th>").append(t[1]).append("</th>");
            for (int i = 1; i <= 7; i++) {
                String k = i + t[0];
                b.append("<td><input type=\"checkbox\" name=\"disp_").append(k).append("\"").append(on.contains(k) ? " checked" : "").append(" aria-label=\"").append(dias[i - 1]).append(" ").append(t[1]).append("\"></td>");
            }
            b.append("</tr>");
        }
        return b.append("</table></div>").toString();
    }

    /** Campos de localização com botão do GPS do navegador. */
    public static String geoFields(Row u) {
        return "<div class=\"row\">" + input("Latitude", "lat", u.s("lat"), "text", "placeholder=\"-15.7998\" inputmode=\"decimal\"")
            + input("Longitude", "lng", u.s("lng"), "text", "placeholder=\"-47.8919\" inputmode=\"decimal\"") + "</div>"
            + "<button type=\"button\" class=\"btn sm sec mt\" data-geo>📍 Usar minha localização atual</button>"
            + "<p class=\"mut\">Usada para calcular distâncias e o match. Dica: no Google Maps, clique com o botão direito no endereço para copiar as coordenadas.</p>";
    }

    // ---------------- layouts ----------------
    static String head(String title) {
        return "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<title>" + h(title) + " · " + h(Svc.appName()) + "</title><link rel=\"stylesheet\" href=\"/app.css\"></head>";
    }

    static String flash(Req r) {
        if (r.ses == null || r.ses.flash == null) return "";
        String f = "<div class=\"flash " + r.ses.flashType + "\">" + h(r.ses.flash) + "</div>";
        r.ses.flash = null;
        return f;
    }

    public static String pub(Req r, String title, String body) {
        return head(title) + "<body class=\"pub\"><div class=\"pubwrap\"><a href=\"/\" class=\"brand big\" style=\"display:block;text-decoration:none\">🦷 " + h(Svc.appName()) + "</a>" + flash(r) + body
            + "<p class=\"mut center\">Senhas protegidas com hash PBKDF2-SHA256 (600 mil iterações) — ninguém consegue visualizá-las. · <a href=\"/contato\">Contato e suporte</a></p></div>"
            + "<script>" + JS_FORMS + "</script></body></html>";
    }

    static final String[][] NAV_ADMIN = {{"/admin", "📊", "Painel"}, {"/admin/usuarios", "👥", "Usuários"}, {"/admin/assinaturas", "💳", "Assinaturas"},
        {"/admin/faturas", "🧾", "Faturas"}, {"/admin/financeiro", "💰", "Financeiro"}, {"/admin/marketplace", "🤝", "Marketplace"},
        {"/admin/ocorrencias", "⚠️", "Cancelamentos e strikes"}, {"/admin/suporte", "💬", "Suporte"}, {"/admin/emails", "📧", "E-mails"},
        {"/admin/config", "⚙️", "Configurações"}, {"/admin/auditoria", "🛡️", "Auditoria"}};
    static final String[][] NAV_CLINICA = {{"/clinica", "🏠", "Início"}, {"/clinica/agenda", "📅", "Agenda"}, {"/clinica/pacientes", "👥", "Pacientes"},
        {"/clinica/oportunidades", "📣", "Turnos / oportunidades"}, {"/negociacoes", "🤝", "Negociações"}, {"/clinica/profissionais", "🎯", "Encontrar profissionais"},
        {"/clinica/salas", "🚪", "Aluguel de salas"}, {"/clinica/repasses", "💰", "Repasses"}, {"/conta/plano", "💳", "Meu plano"}, {"/conta/perfil", "🏥", "Perfil da clínica"}};
    static final String[][] NAV_DENTISTA = {{"/dentista", "🏠", "Início"}, {"/dentista/oportunidades", "🔎", "Turnos para mim"}, {"/negociacoes", "🤝", "Negociações"},
        {"/dentista/agenda", "📅", "Minha agenda"}, {"/dentista/salas", "🚪", "Alugar sala"}, {"/dentista/ganhos", "💰", "Ganhos"}, {"/conta/plano", "💳", "Meu plano"},
        {"/conta/perfil", "👤", "Perfil e disponibilidade"}};

    public static String page(Req r, String title, String body) {
        String role = r.role();
        String[][] nav = switch (role) { case "ADMIN" -> NAV_ADMIN; case "CLINICA" -> NAV_CLINICA; default -> NAV_DENTISTA; };
        long unread = Db.count("SELECT COUNT(*) FROM notificacoes WHERE usuario_id=? AND lida=FALSE", r.uid());
        long turn = role.equals("ADMIN") ? 0 : Svc.myTurnCount(r);
        Object last = Db.val("SELECT MAX(criado) FROM notificacoes WHERE usuario_id=?", r.uid());
        StringBuilder b = new StringBuilder(head(title));
        b.append("<body data-uid=\"").append(h(r.uid())).append("\" data-n=\"").append(unread).append("\" data-l=\"").append(last == null ? "0" : last).append("\">")
            .append("<input type=\"checkbox\" id=\"nt\" class=\"nt\"><aside class=\"side\"><div class=\"brand\">🦷 ").append(h(Svc.appName()))
            .append("<small>").append(switch (role) { case "ADMIN" -> "Administração"; case "CLINICA" -> "Painel da clínica"; default -> "Painel do profissional"; }).append("</small></div>")
            .append("<div class=\"who\"><b>").append(h(r.user.s("nome"))).append("</b><span>").append(h(r.user.s("email"))).append("</span>")
            .append(role.equals("DENTISTA") ? "<span class=\"whocat\">" + h(Svc.label(Svc.CATEGORIAS, Svc.cat(r.user))) + "</span>" : "").append("</div><nav>");
        for (String[] n : nav) {
            boolean on = r.path.equals(n[0]) || (!n[0].equals("/admin") && !n[0].equals("/clinica") && !n[0].equals("/dentista") && r.path.startsWith(n[0]));
            String cnt = n[0].equals("/negociacoes") ? "<span class=\"cnt\" id=\"cntneg\"" + (turn > 0 ? "" : " style=\"display:none\"") + ">" + turn + "</span>" : "";
            b.append("<a href=\"").append(n[0]).append("\" class=\"").append(on ? "on" : "").append("\"><span>").append(n[1]).append("</span>").append(h(n[2])).append(cnt).append("</a>");
        }
        b.append("</nav><div class=\"sidef\">").append(role.equals("ADMIN") ? "" : "<a href=\"/contato\">💬 Suporte</a>").append("<a href=\"/conta/senha\">🔑 Alterar senha</a>")
            .append(post(r, "/sair", "⎋ Sair", "side-btn", null)).append("</div></aside>")
            .append("<label for=\"nt\" class=\"ovl\"></label><div class=\"main\"><header class=\"top\"><label for=\"nt\" class=\"burger\">☰</label><h1>").append(h(title)).append("</h1>")
            .append("<a class=\"bell\" id=\"bell\" href=\"/notificacoes\" title=\"Notificações\">🔔<span class=\"dot\"").append(unread > 0 ? "" : " style=\"display:none\"").append(">").append(unread).append("</span></a></header>")
            .append("<main class=\"content\">").append(Svc.banner(r)).append(flash(r)).append(body).append("</main></div><div id=\"toasts\"></div>")
            .append("<script>").append(JS_FORMS).append(JS_LIVE).append("</script></body></html>");
        return b.toString();
    }

    public static String error(String title, String msg) {
        return "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>" + h(title)
            + "</title><link rel=\"stylesheet\" href=\"/app.css\"></head><body class=\"pub\"><div class=\"pubwrap\"><div class=\"card\"><h2>" + h(title) + "</h2><p>" + h(msg)
            + "</p><a class=\"btn\" href=\"/\">Voltar ao início</a></div></div></body></html>";
    }

    /** Comportamentos de formulário: campos de valores, GPS e trava de duplo clique. */
    static final String JS_FORMS = """
        (function(){
          document.querySelectorAll('select[name=modelo]').forEach(function(s){function f(){var fm=s.form;if(!fm)return;var m=s.value;
            var d=fm.querySelector('[name=diaria]'),pp=fm.querySelector('[name=pct_part]'),pc=fm.querySelector('[name=pct_conv]');
            if(d)d.closest('label').style.display=m==='PERCENTUAL'?'none':'';[pp,pc].forEach(function(x){if(x)x.closest('label').style.display=m==='DIARIA'?'none':'';});}
            s.addEventListener('change',f);f();});
          document.querySelectorAll('[data-geo]').forEach(function(b){b.addEventListener('click',function(){var f=b.form||b.closest('form');
            if(!navigator.geolocation){alert('Seu navegador não permite localização. Preencha manualmente.');return;}
            b.textContent='Obtendo localização...';
            navigator.geolocation.getCurrentPosition(function(p){f.querySelector('[name=lat]').value=p.coords.latitude.toFixed(6);f.querySelector('[name=lng]').value=p.coords.longitude.toFixed(6);b.textContent='📍 Localização capturada — salve o formulário';},
              function(){alert('Não foi possível obter a localização. Em acesso pelo IP da rede o navegador exige HTTPS; use localhost ou preencha manualmente.');b.textContent='📍 Usar minha localização atual';},{timeout:15000});});});
          document.querySelectorAll('form').forEach(function(f){f.addEventListener('submit',function(){var b=f.querySelector('button:not([type=button])');if(b){setTimeout(function(){b.disabled=true;b.style.opacity=.6;},0);}});});
        })();
        """;

    /** Atualização em tempo real (a cada 8 s): sino, contador, avisos e detecção de troca de conta. */
    static final String JS_LIVE = """
        (function(){
          var B=document.body,U=B.getAttribute('data-uid'),N=+B.getAttribute('data-n'),L=B.getAttribute('data-l'),P=location.pathname,stop=false;
          var AUTO=['/negociac','/clinica/oportunidades','/clinica/agenda','/clinica/salas','/dentista/oportunidades','/dentista/agenda','/dentista/salas','/notificacoes','/admin/suporte'];
          function isAuto(){if(P==='/clinica'||P==='/dentista'||P==='/admin')return true;for(var i=0;i<AUTO.length;i++){if(P.indexOf(AUTO[i])===0)return true;}return false;}
          function el(t,c,h){var e=document.createElement(t);if(c)e.className=c;if(h!=null)e.innerHTML=h;return e;}
          function esc(s){return String(s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
          function toast(t,link){if(!t)return;var d=el('div','toast','<div>🔔 '+esc(t)+'</div>'+(link?'<a href="'+esc(link)+'">Abrir</a>':'')+'<button type="button" aria-label="Fechar">×</button>');
            d.querySelector('button').onclick=function(){d.remove();};document.getElementById('toasts').appendChild(d);setTimeout(function(){d.remove();},15000);}
          var pend=sessionStorage.getItem('om_toast');if(pend){sessionStorage.removeItem('om_toast');try{var o=JSON.parse(pend);toast(o.t,o.l);}catch(e){}}
          function typing(){var a=document.activeElement;if(a&&(a.tagName==='TEXTAREA'||a.tagName==='SELECT'))return true;
            if(a&&a.tagName==='INPUT'&&['hidden','submit','button','checkbox'].indexOf(a.type)<0)return true;
            var f=document.querySelectorAll('input[type=text],input[type=number],textarea');for(var i=0;i<f.length;i++){if(f[i].value!==f[i].defaultValue)return true;}return false;}
          function upd(sel,v){var e=document.querySelector(sel);if(e){e.textContent=v;e.style.display=v>0?'':'none';}}
          function swap(d){stop=true;var o=el('div','swap','<div class="card"><h2>⚠️ Outra conta entrou neste navegador</h2><p>Esta aba estava conectada como outro usuário, mas '
            +(d.uid?('agora quem está conectado é <b>'+esc(d.nome)+'</b>'):'a sessão foi encerrada')+'.</p><p>Cada navegador mantém apenas <b>um usuário por vez</b>. '
            +'Para usar clínica e profissional ao mesmo tempo, abra um deles em uma <b>janela anônima (Ctrl+Shift+N)</b> ou em outro navegador.</p>'
            +'<a class="btn" href="/">Continuar como '+(d.uid?esc(d.nome):'visitante')+'</a></div>');B.appendChild(o);}
          function tick(){if(stop)return;fetch('/api/estado',{credentials:'same-origin',cache:'no-store'}).then(function(r){return r.json();}).then(function(d){
            if(d.uid!==U){swap(d);return;}
            upd('#bell .dot',d.n);upd('#cntneg',d.vez);
            if(d.l!==L){var auto=isAuto();
              if(d.n>N&&d.texto){if(auto&&!typing()){sessionStorage.setItem('om_toast',JSON.stringify({t:d.texto,l:d.link}));location.reload();return;}toast(d.texto,d.link);}
              else if(auto&&!typing()){location.reload();return;}}
            N=d.n;L=d.l;}).catch(function(){});}
          setInterval(tick,8000);document.addEventListener('visibilitychange',function(){if(!document.hidden)tick();});
        })();
        """;

    public static final String CSS = """
        :root{--p:#0d9488;--pd:#0f766e;--a:#6366f1;--bg:#f4f7fb;--card:#fff;--t:#0f172a;--m:#64748b;--bd:#e2e8f0;--ok:#16a34a;--w:#f59e0b;--e:#dc2626;--r:14px}
        *{box-sizing:border-box}html,body{margin:0}body{font-family:Inter,system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;background:var(--bg);color:var(--t);font-size:15px}
        a{color:var(--pd)}h1{font-size:20px;margin:0}h2{font-size:18px;margin:0 0 12px}h3{font-size:16px;margin:0 0 10px}h4{margin:16px 0 6px;font-size:13px;color:var(--pd);text-transform:uppercase;letter-spacing:.05em}
        .mut{color:var(--m);font-size:13px}.center{text-align:center}.right{text-align:right}
        .nt{display:none}
        .side{position:fixed;inset:0 auto 0 0;width:250px;background:linear-gradient(180deg,#0b3b3a,#0f4f4d);color:#e6fffb;padding:18px 12px;display:flex;flex-direction:column;z-index:60;overflow-y:auto;transition:transform .25s}
        .brand{font-weight:800;font-size:19px;margin:0 8px 16px;color:#fff}.brand small{display:block;font-size:11px;font-weight:500;opacity:.7}.brand.big{font-size:26px;color:var(--pd);text-align:center;margin:10px 0 18px}
        .who{background:rgba(255,255,255,.08);border-radius:12px;padding:10px 12px;margin-bottom:12px;font-size:13px;word-break:break-all}.who b{display:block;font-size:14px;word-break:normal}.who span{opacity:.75;display:block}.who .whocat{opacity:1;color:#5eead4;font-weight:600;word-break:normal}
        .side nav a{display:flex;gap:10px;align-items:center;padding:9px 12px;border-radius:10px;color:#cdeeea;text-decoration:none;font-weight:500;margin-bottom:2px}
        .side nav a.on,.side nav a:hover{background:rgba(255,255,255,.14);color:#fff}
        .cnt{margin-left:auto;background:#f59e0b;color:#111;border-radius:99px;font-size:12px;padding:1px 8px;font-weight:700}
        .sidef{margin-top:auto;padding-top:12px;display:flex;flex-direction:column;gap:6px}.sidef a{color:#cdeeea;text-decoration:none;padding:8px 12px;font-size:14px}
        .side-btn{width:100%;background:rgba(255,255,255,.08)!important;color:#e6fffb!important;text-align:left}
        .main{margin-left:250px;min-width:0}
        .top{position:sticky;top:0;background:rgba(244,247,251,.94);backdrop-filter:blur(6px);display:flex;align-items:center;gap:12px;padding:12px 24px;border-bottom:1px solid var(--bd);z-index:30}
        .burger{display:none;cursor:pointer;font-size:22px;background:#fff;border:1px solid var(--bd);border-radius:10px;padding:4px 10px}
        .bell{margin-left:auto;position:relative;background:#fff;border:1px solid var(--bd);border-radius:12px;padding:8px 11px;text-decoration:none;font-size:17px}
        .bell .dot{position:absolute;top:-7px;right:-7px;background:var(--e);color:#fff;border-radius:99px;font-size:11px;padding:2px 6px;font-weight:700}
        .ovl{display:none}
        .content{padding:22px 24px;max-width:1280px}
        .card{background:var(--card);border:1px solid var(--bd);border-radius:var(--r);padding:18px;box-shadow:0 1px 2px rgba(15,23,42,.04);margin-bottom:16px}
        .grid{display:grid;gap:16px}.g2{grid-template-columns:1fr 1fr}.g3{grid-template-columns:repeat(3,1fr)}.g4{grid-template-columns:repeat(4,1fr)}
        .grid>.card{margin-bottom:0}.mt{margin-top:16px}
        .kpi{display:flex;gap:14px;align-items:center}.kpi .ic{width:46px;height:46px;border-radius:12px;background:#e6fffb;display:flex;align-items:center;justify-content:center;font-size:22px;flex:none}
        .kpi .v{font-size:21px;font-weight:800}.kpi .l{color:var(--m);font-size:13px}
        .btn{border:0;border-radius:10px;padding:9px 14px;font-weight:600;cursor:pointer;background:var(--p);color:#fff;font-size:14px;display:inline-flex;align-items:center;gap:6px;text-decoration:none;font-family:inherit;white-space:nowrap}
        .btn:hover{filter:brightness(.94)}.btn.sec{background:#d9f6f3;color:var(--pd)}.btn.gh{background:#fff;border:1px solid var(--bd);color:var(--t)}
        .btn.red{background:#fee2e2;color:#b91c1c}.btn.acc{background:var(--a)}.btn.ok{background:var(--ok)}.btn.sm{padding:6px 10px;font-size:13px}.btn.block{width:100%;justify-content:center}.btn.lg{padding:13px 22px;font-size:16px}
        form.inl{display:inline}
        .badge{display:inline-block;padding:3px 9px;border-radius:99px;font-size:12px;font-weight:700;background:#eef2ff;color:#4338ca;white-space:nowrap}
        .b-ok{background:#dcfce7;color:#166534}.b-w{background:#fef3c7;color:#92400e}.b-e{background:#fee2e2;color:#991b1b}.b-m{background:#f1f5f9;color:#475569}.b-p{background:#ccfbf1;color:#115e59}.b-hot{background:#ffedd5;color:#c2410c}
        .b-ver{background:#dbeafe;color:#1d4ed8}
        label{display:block;font-size:13px;font-weight:600;color:#334155;margin:10px 0 0}
        input,select,textarea{display:block;width:100%;margin-top:4px;padding:10px 12px;border:1px solid var(--bd);border-radius:10px;font:inherit;background:#fff;color:var(--t)}
        input:focus,select:focus,textarea:focus{outline:2px solid #99f6e4;border-color:var(--p)}
        label.chk{display:inline-flex;align-items:center;gap:6px;border:1px solid var(--bd);border-radius:99px;padding:6px 12px;font-weight:500;margin:6px 6px 0 0;background:#fff}
        label.chk input{width:auto;margin:0;display:inline}
        .row{display:flex;gap:12px;flex-wrap:wrap}.row>*{flex:1;min-width:150px}
        .bar{display:flex;gap:8px;flex-wrap:wrap;align-items:center;justify-content:space-between;margin-bottom:14px}.tools{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
        .tools form{display:flex;gap:8px;flex-wrap:wrap;align-items:flex-end}.tools label{margin:0}
        .tw{overflow-x:auto}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:10px 8px;border-bottom:1px solid var(--bd);font-size:14px;vertical-align:middle}
        th{color:var(--m);font-weight:600;font-size:12px;text-transform:uppercase}
        table.disp{width:auto}table.disp td,table.disp th{text-align:center;padding:6px 8px}table.disp input{width:20px;height:20px;margin:0 auto}
        .empty{text-align:center;color:var(--m);padding:26px 10px}
        .flash{padding:12px 14px;border-radius:12px;margin-bottom:14px;font-weight:500}.flash.ok{background:#dcfce7;color:#166534}.flash.err{background:#fee2e2;color:#991b1b}
        .banner{padding:12px 14px;border-radius:12px;margin-bottom:14px;background:#fef3c7;color:#92400e;border:1px solid #fde68a}
        .alert{padding:10px 12px;border-radius:10px;background:#fef2f2;border-left:4px solid var(--e);margin-bottom:10px;font-size:14px}
        .item{display:flex;gap:12px;align-items:center;padding:12px;border:1px solid var(--bd);border-radius:12px;margin-bottom:8px;background:#fff;flex-wrap:wrap;border-left:5px solid #cbd5e1}
        .item .info{flex:1 1 220px;min-width:0}.item .tm{font-weight:800;font-size:16px;min-width:56px;text-align:center}.item .tm small{display:block;font-size:11px;color:var(--m);font-weight:500}
        .st-AGENDADO{border-left-color:#94a3b8}.st-CONFIRMADO{border-left-color:#3b82f6}.st-NA_CLINICA{border-left-color:#f59e0b}.st-ATENDIDO{border-left-color:#16a34a}.st-FALTOU{border-left-color:#dc2626}.st-CANCELADO{opacity:.55}
        .terms{font-size:18px;font-weight:800;color:var(--pd)}
        .gain{background:linear-gradient(135deg,#ecfeff,#eef2ff);border-radius:12px;padding:10px 12px;display:flex;justify-content:space-between;align-items:center;gap:8px;flex-wrap:wrap;margin:10px 0}.gain b{font-size:19px;color:var(--a)}
        .hot{border:2px solid #fdba74}
        .match{display:inline-flex;align-items:center;gap:6px;font-weight:800;color:#065f46;background:#d1fae5;border-radius:99px;padding:3px 10px;font-size:13px}
        .meter{height:8px;background:#e2e8f0;border-radius:99px;overflow:hidden;margin:6px 0}.meter span{display:block;height:100%;background:linear-gradient(90deg,#14b8a6,#6366f1)}
        .why{display:flex;flex-wrap:wrap;gap:4px;margin-top:6px}.why span{font-size:11px;background:#f1f5f9;border-radius:6px;padding:2px 6px;color:#334155}
        .tl{display:flex;flex-direction:column;gap:8px;margin:12px 0}
        .msg{max-width:85%;padding:10px 12px;border-radius:14px;font-size:14px;background:#f1f5f9}.msg.me{margin-left:auto;background:#ccfbf1}
        .msg.of{border:2px dashed var(--a);background:#eef2ff}.msg.of.me{background:#e0f2fe}.msg.sys{max-width:100%;background:transparent;text-align:center;color:var(--m);font-size:12px}
        .msg .t{font-size:11px;color:var(--m);margin-top:4px}.msg .ot{font-weight:800;font-size:16px;color:#3730a3}
        .week{display:grid;grid-template-columns:repeat(7,1fr);gap:6px;margin:12px 0}
        .wd{background:#fff;border:1px solid var(--bd);border-radius:12px;padding:8px 2px;text-align:center;text-decoration:none;color:var(--t)}.wd small{display:block;color:var(--m)}.wd b{font-size:17px;display:block}.wd span{font-size:11px;color:var(--pd);font-weight:700;display:block;min-height:14px}
        .wd.on{background:var(--p);color:#fff;border-color:var(--p)}.wd.on small,.wd.on span{color:#e6fffb}
        .odo{display:grid;grid-template-columns:repeat(16,1fr);gap:4px;margin:6px 0}.odo label{margin:0;font-size:11px;text-align:center}.odo select{padding:4px 2px;font-size:11px}
        .bars{display:flex;align-items:flex-end;gap:10px;height:170px;padding-top:10px}.bars .col{flex:1;display:flex;flex-direction:column;align-items:center;gap:4px;height:100%;justify-content:flex-end}
        .bars .pair{display:flex;gap:3px;align-items:flex-end;height:100%;width:100%;justify-content:center}.bars .bi{width:42%;background:var(--ok);border-radius:6px 6px 0 0;min-height:2px}.bars .bo{width:42%;background:#f87171;border-radius:6px 6px 0 0;min-height:2px}
        .bars small{font-size:11px;color:var(--m)}
        .stars{color:#f59e0b;font-weight:700}
        .pub{background:radial-gradient(circle at 20% 10%,#ccfbf1,#f4f7fb 50%)}.pubwrap{max-width:760px;margin:0 auto;padding:30px 16px}.pubwrap.narrow{max-width:460px}.pubwrap.wide{max-width:1100px}
        .steps{display:flex;gap:8px;margin-bottom:6px;flex-wrap:wrap}.steps span{background:#ccfbf1;color:#115e59;border-radius:99px;padding:4px 10px;font-size:12px;font-weight:700}
        .secret{font-family:Consolas,monospace;font-size:20px;background:#0f172a;color:#5eead4;padding:12px 16px;border-radius:10px;display:inline-block;letter-spacing:1px}
        .hero{text-align:center;padding:30px 10px 10px}.hero h1{font-size:38px;line-height:1.15;margin:10px 0}.hero p{font-size:18px;color:#334155;max-width:720px;margin:0 auto 20px}
        .step{text-align:center}.step .n{width:44px;height:44px;border-radius:50%;background:var(--p);color:#fff;font-weight:800;display:flex;align-items:center;justify-content:center;margin:0 auto 10px;font-size:18px}
        .feat{font-size:28px}
        ul.ck{list-style:none;padding:0;margin:8px 0}ul.ck li{margin:6px 0}ul.ck li::before{content:"✔ ";color:var(--ok);font-weight:800}
        #toasts{position:fixed;right:16px;bottom:16px;z-index:150;display:flex;flex-direction:column;gap:8px;max-width:min(380px,92vw)}
        .toast{background:#0f172a;color:#fff;border-radius:14px;padding:12px 14px;box-shadow:0 10px 30px rgba(0,0,0,.3);display:flex;gap:10px;align-items:center;font-size:14px;animation:tin .25s}
        .toast div{flex:1}.toast a{color:#5eead4;font-weight:700;white-space:nowrap}.toast button{background:none;border:0;color:#94a3b8;font-size:20px;cursor:pointer}
        @keyframes tin{from{transform:translateY(20px);opacity:0}to{transform:none;opacity:1}}
        .swap{position:fixed;inset:0;background:rgba(15,23,42,.75);z-index:200;display:flex;align-items:center;justify-content:center;padding:16px}.swap .card{max-width:520px}
        pre.mail{white-space:pre-wrap;background:#f8fafc;border:1px solid var(--bd);border-radius:10px;padding:12px;font-family:inherit;font-size:14px}
        @media(max-width:1100px){.g4{grid-template-columns:1fr 1fr}.g3{grid-template-columns:1fr 1fr}}
        @media(max-width:900px){
         .side{transform:translateX(-105%)}.nt:checked~.side{transform:none}.nt:checked~.ovl{display:block;position:fixed;inset:0;background:rgba(0,0,0,.4);z-index:50}
         .main{margin-left:0}.burger{display:inline-block}.content{padding:14px}.top{padding:10px 14px}.g2,.g3{grid-template-columns:1fr}
         table.resp thead{display:none}table.resp tr{display:block;border:1px solid var(--bd);border-radius:12px;margin-bottom:10px;padding:6px;background:#fff}
         table.resp td{display:flex;justify-content:space-between;gap:10px;border:0;padding:6px 8px;text-align:right}
         table.resp td::before{content:attr(data-l);font-weight:600;color:var(--m);font-size:12px;text-align:left}
         .odo{grid-template-columns:repeat(8,1fr)}.week{gap:3px}.wd b{font-size:14px}.hero h1{font-size:28px}
        }
        @media(max-width:520px){.g4{grid-template-columns:1fr 1fr}.kpi .ic{display:none}.kpi .v{font-size:17px}.msg{max-width:100%}}
        """;
}

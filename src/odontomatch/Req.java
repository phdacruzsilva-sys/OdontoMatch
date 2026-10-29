package odontomatch;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Requisição/resposta HTTP simplificada. */
public final class Req {
    final HttpExchange ex;
    final String method, path;
    final Map<String, List<String>> query = new HashMap<>(), form = new HashMap<>();
    Sec.Session ses;
    Row user;
    boolean sent;

    Req(HttpExchange ex) {
        this.ex = ex;
        this.method = ex.getRequestMethod().toUpperCase();
        URI u = ex.getRequestURI();
        String p = u.getPath();
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        this.path = p;
        parse(u.getRawQuery(), query);
    }

    void readForm() throws IOException {
        byte[] body = ex.getRequestBody().readNBytes(2_000_000);
        parse(new String(body, StandardCharsets.UTF_8), form);
    }

    private static void parse(String raw, Map<String, List<String>> into) {
        if (raw == null || raw.isEmpty()) return;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int i = pair.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            into.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
        }
    }

    public String p(String k) {
        List<String> l = form.get(k);
        if (l == null || l.isEmpty()) l = query.get(k);
        return l == null || l.isEmpty() ? "" : l.get(0).trim();
    }

    public List<String> all(String k) { return form.getOrDefault(k, List.of()); }

    public boolean chk(String k) { String v = p(k); return v.equals("on") || v.equals("true") || v.equals("1"); }

    public BigDecimal num(String k) {
        String v = p(k).replace("R$", "").replace(" ", "");
        if (v.isEmpty()) return BigDecimal.ZERO;
        if (v.contains(",")) v = v.replace(".", "").replace(",", ".");
        try { return new BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP); } catch (Exception e) { throw new Bad("Valor numérico inválido: " + p(k)); }
    }

    public int intp(String k, int def) { try { return Integer.parseInt(p(k)); } catch (Exception e) { return def; } }

    public String req(String k, String label) {
        String v = p(k);
        if (v.isEmpty()) throw new Bad("Preencha o campo: " + label);
        return v;
    }

    public String ip() { return ex.getRemoteAddress() == null ? "" : ex.getRemoteAddress().getAddress().getHostAddress(); }

    public String uid() { return user == null ? null : user.s("id"); }
    public String role() { return user == null ? "" : user.s("perfil"); }

    /** Token CSRF + identificação do usuário dono da página (detecta troca de conta no mesmo navegador). */
    public String csrf() {
        return "<input type=\"hidden\" name=\"_csrf\" value=\"" + ses.csrf + "\"><input type=\"hidden\" name=\"_u\" value=\"" + (ses.userId == null ? "" : ses.userId) + "\">";
    }

    public void ok(String msg) { ses.flash = msg; ses.flashType = "ok"; }
    public void err(String msg) { ses.flash = msg; ses.flashType = "err"; }

    public String back(String def) {
        String ref = ex.getRequestHeaders().getFirst("Referer");
        if (ref == null) return def;
        try {
            URI u = URI.create(ref);
            String host = ex.getRequestHeaders().getFirst("Host");
            if (u.getHost() != null && host != null && !host.startsWith(u.getHost())) return def;
            String r = u.getRawPath() + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
            return r.startsWith("/") && !r.startsWith("//") ? r : def;
        } catch (Exception e) { return def; }
    }

    void send(int code, String type, byte[] body) throws IOException {
        if (sent) return;
        sent = true;
        var h = ex.getResponseHeaders();
        h.set("Content-Type", type);
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "same-origin");
        h.set("Cache-Control", "no-store");
        h.set("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'");
        ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        if (body.length > 0) try (OutputStream o = ex.getResponseBody()) { o.write(body); }
        else ex.close();
    }

    public void html(String page) throws IOException { send(200, "text/html; charset=utf-8", page.getBytes(StandardCharsets.UTF_8)); }

    public void html(int code, String page) throws IOException { send(code, "text/html; charset=utf-8", page.getBytes(StandardCharsets.UTF_8)); }

    public void redirect(String loc) throws IOException {
        if (sent) return;
        ex.getResponseHeaders().set("Location", loc);
        send(303, "text/plain; charset=utf-8", new byte[0]);
    }

    public void download(String name, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + name + "\"");
        send(200, type, data);
    }
}

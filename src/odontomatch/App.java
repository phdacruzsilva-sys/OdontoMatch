package odontomatch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/** Ponto de entrada: servidor web embarcado + roteamento + regras de acesso + API de tempo real. */
public final class App {
    public interface H { void handle(Req r) throws Exception; }

    static final Map<String, H> GET = new HashMap<>(), POST = new HashMap<>();
    static final String COOKIE = "OM_SESSAO";
    static final Set<String> PUBLIC = Set.of("/", "/login", "/cadastro", "/setup", "/contato");

    public static void get(String p, H h) { GET.put(p, h); }
    public static void post(String p, H h) { POST.put(p, h); }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", args.length > 0 ? args[0] : "8080"));
        String dataDir = System.getenv().getOrDefault("ODONTOMATCH_DATA", "data");
        new File(dataDir).mkdirs();
        System.out.println("Banco de dados: " + new File(dataDir).getAbsolutePath());
        Db.open(dataDir);
        Runtime.getRuntime().addShutdownHook(new Thread(Db::close));

        Pages.routes();
        AdminPages.routes();
        ClinicPages.routes();
        DentistPages.routes();
        SharedPages.routes();

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (java.net.BindException e) {
            System.out.println("ERRO: a porta " + port + " ja esta em uso. Feche o outro programa ou rode: iniciar.bat 8081");
            Db.close();
            System.exit(1);
            return;
        }
        server.createContext("/", App::dispatch);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
        String url = "http://localhost:" + port;
        System.out.println("=================================================");
        System.out.println(" OdontoMatch iniciado!  Acesse: " + url);
        System.out.println(Svc.setupDone() ? " Sistema ja configurado. Faca login." : " Primeira execucao: abra o endereco acima para configurar.");
        System.out.println(" Para encerrar: feche esta janela ou pressione Ctrl + C");
        System.out.println("=================================================");
        if (!"false".equals(System.getenv("ODONTOMATCH_ABRIR_NAVEGADOR"))) {
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE))
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            } catch (Throwable ignored) { }
        }
    }

    static String cookie(HttpExchange ex) {
        String c = ex.getRequestHeaders().getFirst("Cookie");
        if (c == null) return null;
        for (String part : c.split(";")) {
            String p = part.trim();
            if (p.startsWith(COOKIE + "=")) return p.substring(COOKIE.length() + 1);
        }
        return null;
    }

    static void setCookie(Req r, Sec.Session s) {
        r.ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + s.id + "; Path=/; HttpOnly; SameSite=Lax");
    }

    public static void switchSession(Req r, String userId) {
        r.ses = Sec.rotate(r.ses, userId);
        setCookie(r, r.ses);
    }

    public static String home(String role) {
        return switch (role) { case "ADMIN" -> "/admin"; case "CLINICA" -> "/clinica"; case "DENTISTA" -> "/dentista"; default -> "/login"; };
    }

    /** Estado em tempo real consultado pelas páginas a cada 8 segundos. */
    static void estado(Req r) throws java.io.IOException {
        StringBuilder j = new StringBuilder("{");
        if (r.user == null) j.append("\"uid\":null");
        else {
            long unread = Db.count("SELECT COUNT(*) FROM notificacoes WHERE usuario_id=? AND lida=FALSE", r.uid());
            Object last = Db.val("SELECT MAX(criado) FROM notificacoes WHERE usuario_id=?", r.uid());
            Row ln = Db.one("SELECT texto, link FROM notificacoes WHERE usuario_id=? AND lida=FALSE ORDER BY criado DESC", r.uid());
            long vez = r.role().equals("ADMIN") ? 0 : Svc.myTurnCount(r);
            j.append("\"uid\":").append(js(r.uid())).append(",\"nome\":").append(js(r.user.s("nome"))).append(",\"n\":").append(unread)
                .append(",\"vez\":").append(vez).append(",\"l\":").append(js(last == null ? "0" : last.toString()))
                .append(",\"texto\":").append(js(ln == null ? "" : ln.s("texto"))).append(",\"link\":").append(js(ln == null ? "" : ln.s("link")));
        }
        r.send(200, "application/json; charset=utf-8", j.append("}").toString().getBytes(StandardCharsets.UTF_8));
    }

    static String js(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 32) b.append(String.format("\\u%04x", (int) c));
            else if (c == '<') b.append("\\u003c");
            else b.append(c);
        }
        return b.append('"').toString();
    }

    static void dispatch(HttpExchange ex) {
        Req r = new Req(ex);
        try {
            if (r.path.equals("/app.css")) {
                ex.getResponseHeaders().set("Cache-Control", "max-age=3600");
                byte[] css = Ui.CSS.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/css; charset=utf-8");
                ex.sendResponseHeaders(200, css.length);
                ex.getResponseBody().write(css);
                ex.close();
                return;
            }
            if (r.path.equals("/favicon.ico")) { r.send(204, "text/plain", new byte[0]); return; }

            r.ses = Sec.get(cookie(ex));
            if (r.ses == null) { r.ses = Sec.create(); setCookie(r, r.ses); }

            if (r.method.equals("POST")) {
                r.readForm();
                if (!r.ses.csrf.equals(r.p("_csrf"))) {
                    String formUser = r.p("_u");
                    String atual = r.ses.userId == null ? null : Svc.nome(r.ses.userId);
                    if (!formUser.isEmpty() && !formUser.equals(r.ses.userId)) {
                        r.html(409, Ui.error("Outra conta entrou neste navegador",
                            "Esta tela era de " + Svc.nome(formUser) + ", mas depois disso " + (atual == null ? "a sessão foi encerrada" : "entrou " + atual)
                            + " em outra aba deste mesmo navegador. Cada navegador mantém apenas UM usuário conectado por vez, por isso nada foi gravado. "
                            + "Para testar clínica e profissional ao mesmo tempo, abra o segundo usuário em uma janela anônima (Ctrl+Shift+N) ou em outro navegador."));
                    } else {
                        r.html(403, Ui.error("Sessão expirada", "Por segurança, o formulário expirou (o sistema pode ter sido reiniciado). Recarregue a página (F5), entre novamente e repita a ação."));
                    }
                    return;
                }
            }

            boolean done = Svc.setupDone();
            if (!done && !r.path.equals("/setup")) { r.redirect("/setup"); return; }
            if (done && r.path.equals("/setup")) { r.redirect("/login"); return; }

            if (r.ses.userId != null) {
                r.user = Svc.user(r.ses.userId);
                if (r.user == null || !r.user.b("ativo")) { Sec.destroy(r.ses); r.ses = Sec.create(); setCookie(r, r.ses); r.user = null; }
            }

            if (r.path.equals("/api/estado")) { estado(r); return; }
            if (r.path.equals("/") && r.user != null) { r.redirect(home(r.role())); return; }
            if (r.user == null && !PUBLIC.contains(r.path)) { r.redirect("/login"); return; }
            if (r.user != null && (r.path.equals("/login") || r.path.equals("/cadastro"))) { r.redirect(home(r.role())); return; }

            if (r.user != null) {
                String role = r.role();
                if ((r.path.startsWith("/admin") && !role.equals("ADMIN")) || (r.path.startsWith("/clinica") && !role.equals("CLINICA"))
                    || (r.path.startsWith("/dentista") && !role.equals("DENTISTA"))) {
                    r.html(403, Ui.error("Acesso negado", "Seu perfil não tem permissão para acessar esta área."));
                    return;
                }
                boolean contaLivre = r.path.equals("/conta/senha") || r.path.equals("/sair");
                if (r.user.b("trocar_senha") && !contaLivre) { r.err("Defina uma nova senha pessoal para continuar."); r.redirect("/conta/senha"); return; }
                if (!role.equals("ADMIN")) {
                    Row s = Svc.assinatura(r.uid());
                    String st = s == null ? "" : s.s("status");
                    if ((st.equals("SUSPENSA") || st.equals("CANCELADA")) && !r.path.startsWith("/conta") && !r.path.equals("/sair")
                        && !r.path.equals("/notificacoes") && !r.path.equals("/contato")) {
                        r.err("Seu acesso está " + (st.equals("SUSPENSA") ? "suspenso" : "cancelado") + ". Regularize o plano ou fale com o suporte.");
                        r.redirect("/conta/plano");
                        return;
                    }
                }
            }

            H h = (r.method.equals("POST") ? POST : GET).get(r.path);
            if (h == null) { r.html(404, Ui.error("Página não encontrada", "O endereço acessado não existe.")); return; }
            if (r.user != null && r.method.equals("GET")) {
                String full = ex.getRequestURI().getRawPath() + (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery());
                Db.x("UPDATE notificacoes SET lida=TRUE WHERE usuario_id=? AND lida=FALSE AND link=?", r.uid(), full);
            }
            h.handle(r);
            if (!r.sent) r.redirect("/");
        } catch (Bad b) {
            try { r.err(b.getMessage()); r.redirect(r.back("/")); } catch (Exception ignored) { }
        } catch (Exception e) {
            e.printStackTrace();
            try { r.html(500, Ui.error("Erro inesperado", "Ocorreu um erro ao processar sua solicitação: " + e.getMessage())); } catch (Exception ignored) { }
        } finally {
            ex.close();
        }
    }
}

package odontomatch;

import java.math.BigDecimal;
import java.util.*;

import static odontomatch.Ui.*;

/** Página inicial, instalação, login, cadastro, conta, plano, notificações e suporte. */
final class Pages {
    private Pages() {}

    private static final String DUMMY = Sec.hash("dummy-password-0");

    static void routes() {
        App.get("/", Pages::landing);
        App.get("/setup", r -> r.html(setupForm(r, Map.of(), null)));
        App.post("/setup", Pages::doSetup);
        App.get("/login", Pages::login);
        App.post("/login", Pages::doLogin);
        App.get("/cadastro", r -> r.html(signupForm(r, Map.of("perfil", r.p("perfil")), null)));
        App.post("/cadastro", Pages::doSignup);
        App.post("/sair", r -> { Svc.audit(r, "LOGOUT", ""); App.switchSession(r, null); r.ok("Você saiu do sistema."); r.redirect("/login"); });
        App.get("/conta/senha", Pages::senha);
        App.post("/conta/senha", Pages::doSenha);
        App.get("/conta/perfil", Pages::perfil);
        App.post("/conta/perfil", Pages::doPerfil);
        App.get("/conta/plano", Pages::plano);
        App.post("/conta/fatura/informar", Pages::informarPagamento);
        App.get("/notificacoes", Pages::notificacoes);
        App.post("/notificacoes/lidas", r -> { Db.x("UPDATE notificacoes SET lida=TRUE WHERE usuario_id=?", r.uid()); r.redirect("/notificacoes"); });
        App.get("/contato", Pages::contato);
        App.post("/contato", Pages::doContato);
    }

    // ================= PÁGINA INICIAL =================
    static void landing(Req r) throws Exception {
        long pros = Db.count("SELECT COUNT(*) FROM usuarios WHERE perfil='DENTISTA' AND ativo=TRUE"), clins = Db.count("SELECT COUNT(*) FROM usuarios WHERE perfil='CLINICA' AND ativo=TRUE");
        long turnos = Db.count("SELECT COUNT(*) FROM oportunidades WHERE status='FECHADA'");
        boolean gratisPro = Svc.cfgNum("preco_dentista", "0").signum() == 0;
        String body = "<div class=\"hero\"><span class=\"badge b-ver\">✔ Registro profissional verificado</span><h1>Encontre o turno perfeito — ou o profissional certo em minutos.</h1>"
            + "<p>O marketplace que conecta dentistas, auxiliares e estudantes às clínicas que precisam de cobertura pontual. Negocie valores como no InDrive: porcentagem, diária, convênio e materiais.</p>"
            + "<a class=\"btn lg\" href=\"/cadastro\">Começar " + (gratisPro ? "grátis" : "agora") + "</a> <a class=\"btn lg gh\" href=\"/login\">Entrar</a></div>"
            + "<div class=\"grid g3 mt\">" + kpi("Profissionais cadastrados", String.valueOf(pros), "🦷") + kpi("Clínicas parceiras", String.valueOf(clins), "🏥") + kpi("Turnos fechados", String.valueOf(turnos), "🤝") + "</div>"
            + "<h2 class=\"center mt\" id=\"como-funciona\">Como funciona</h2><div class=\"grid g3\">"
            + "<div class=\"card step\"><div class=\"n\">1</div><h3>Crie seu perfil</h3><p class=\"mut\">Cadastre-se como dentista, auxiliar, estudante ou clínica. Validamos o CRO/CNPJ e você configura sua disponibilidade e localização.</p></div>"
            + "<div class=\"card step\"><div class=\"n\">2</div><h3>Encontre o match</h3><p class=\"mut\">Nosso algoritmo conecta clínicas a profissionais compatíveis por especialidade, distância, avaliações e disponibilidade.</p></div>"
            + "<div class=\"card step\"><div class=\"n\">3</div><h3>Negocie e confirme</h3><p class=\"mut\">Converse pelo chat seguro, negocie os valores, aceite o turno e receba a confirmação automática por e-mail.</p></div></div>"
            + "<h2 class=\"center mt\">Para quem é</h2><div class=\"grid g2\">"
            + "<div class=\"card\"><h3>🦷 Dentistas, auxiliares e estudantes</h3><ul class=\"ck\"><li>Trabalhe quando e onde quiser</li><li>Turnos compatíveis com sua disponibilidade e raio de distância</li>"
            + "<li>Faça contraproposta de valores</li><li>Reputação com avaliações verificadas e selo de CRO</li><li>Alugue salas e consultórios por hora, turno ou dia</li>"
            + (gratisPro ? "<li><b>100% gratuito para profissionais</b></li>" : "") + "</ul><a class=\"btn\" href=\"/cadastro?perfil=DENTISTA\">Quero encontrar turnos</a></div>"
            + "<div class=\"card\"><h3>🏥 Clínicas e consultórios</h3><ul class=\"ck\"><li>Encontre o profissional certo em minutos com ranking de match</li><li>Agenda, pacientes, prontuário e odontograma</li>"
            + "<li>Chat sem expor contatos pessoais até o fechamento</li><li>Alugue sua sala ociosa para profissionais qualificados</li><li>Confirmações por e-mail e controle de repasses</li></ul>"
            + "<a class=\"btn acc\" href=\"/cadastro?perfil=CLINICA\">Quero contratar profissionais</a></div></div>"
            + "<h2 class=\"center mt\">Tudo que você precisa</h2><div class=\"grid g3\">"
            + feat("🎯", "Match inteligente", "Pontuação por especialidade, distância, avaliações, disponibilidade e confiabilidade.")
            + feat("💬", "Chat seguro", "Telefones, e-mails e links ficam ocultos até o negócio ser fechado.")
            + feat("📧", "Confirmação por e-mail", "Comprovante automático para clínica e profissional ao confirmar o turno.")
            + feat("⭐", "Avaliações mútuas", "Clínica avalia o profissional e vice-versa. Transparência total.")
            + feat("📍", "Perto de você", "Distância calculada pela localização, com raio de atuação configurável.")
            + feat("🛡️", "Política de strikes", "Cancelamentos em cima da hora e faltas geram strikes; o acúmulo bloqueia a conta.") + "</div>"
            + "<p class=\"center mt\"><a href=\"/contato\">Dúvidas? Fale com a equipe</a></p>";
        r.html(head("Marketplace odontológico") + "<body class=\"pub\"><div class=\"pubwrap wide\"><div class=\"bar\"><a href=\"/\" class=\"brand big\" style=\"margin:0;text-decoration:none\">🦷 "
            + h(Svc.appName()) + "</a><div class=\"tools\"><a class=\"btn gh\" href=\"#como-funciona\">Como funciona</a><a class=\"btn gh\" href=\"/login\">Entrar</a><a class=\"btn\" href=\"/cadastro\">Cadastrar</a></div></div>"
            + flash(r) + body + "</div></body></html>");
    }

    static String feat(String ic, String t, String d) { return "<div class=\"card\"><div class=\"feat\">" + ic + "</div><h3>" + h(t) + "</h3><p class=\"mut\">" + h(d) + "</p></div>"; }

    // ================= SETUP =================
    static String v(Map<String, String> m, String k, String def) { String x = m.get(k); return x == null || x.isEmpty() ? def : x; }

    static String setupForm(Req r, Map<String, String> m, String erro) {
        String body = "<div class=\"card\"><div class=\"steps\"><span>1. Administrador</span><span>2. Plataforma</span><span>3. Cobrança</span><span>4. Dados de teste</span></div>"
            + "<h2>Configuração inicial do sistema</h2><p class=\"mut\">Este banco de dados ainda não possui administrador. Preencha os dados abaixo uma única vez.</p>"
            + (erro == null ? "" : "<div class=\"flash err\">" + h(erro) + "</div>")
            + "<form method=\"post\" action=\"/setup\">" + r.csrf()
            + "<h4>1. Administrador do sistema</h4>"
            + input("Seu nome", "admin_nome", v(m, "admin_nome", ""), "text", "required maxlength=\"200\"")
            + input("E-mail de login do administrador", "admin_email", v(m, "admin_email", ""), "email", "required maxlength=\"200\" autocomplete=\"username\"")
            + "<div class=\"row\">" + input("Senha (mín. 10 caracteres, letras e números)", "senha", "", "password", "required minlength=\"10\" autocomplete=\"new-password\"")
            + input("Confirme a senha", "senha2", "", "password", "required minlength=\"10\" autocomplete=\"new-password\"") + "</div>"
            + "<h4>2. Dados da plataforma</h4><div class=\"row\">"
            + input("Nome do sistema", "app_nome", v(m, "app_nome", "OdontoMatch"), "text", "required maxlength=\"60\"")
            + input("E-mail de suporte", "suporte_email", v(m, "suporte_email", ""), "email", "required") + "</div>"
            + input("Chave PIX para receber as mensalidades", "pix_chave", v(m, "pix_chave", ""), "text", "placeholder=\"CNPJ, e-mail, telefone ou chave aleatória\"")
            + "<h4>3. Cobrança (pode alterar depois em Configurações)</h4><div class=\"row\">"
            + input("Mensalidade clínica (R$)", "preco_clinica", v(m, "preco_clinica", "149.90"), "number", "min=\"0\" step=\"0.01\" required")
            + input("Mensalidade profissional (R$) — 0 = gratuito", "preco_dentista", v(m, "preco_dentista", "0"), "number", "min=\"0\" step=\"0.01\" required") + "</div><div class=\"row\">"
            + input("Dias de teste grátis", "dias_teste", v(m, "dias_teste", "30"), "number", "min=\"0\" max=\"365\" required")
            + input("Dia de vencimento das faturas", "dia_vencimento", v(m, "dia_vencimento", "10"), "number", "min=\"1\" max=\"28\" required")
            + input("Comissão da plataforma sobre repasses (%)", "comissao_pct", v(m, "comissao_pct", "0"), "number", "min=\"0\" max=\"50\" step=\"0.01\"") + "</div>"
            + check("Exigir aprovação do administrador (verificação de CRO/CNPJ) para negociar", "exigir_verificacao", !"off".equals(v(m, "exigir_verificacao", "on")))
            + "<h4>4. Dados de demonstração</h4>"
            + check("Criar clínica, profissionais, pacientes, turnos e sala de exemplo para testes", "demo", !"off".equals(v(m, "demo", "on")))
            + "<p class=\"mut\">Contas de exemplo (senha <b>demo123456</b>): clinica@demo.com, ana@demo.com, bruno@demo.com, julia@demo.com (auxiliar) e marcos@demo.com (estudante).</p>"
            + "<button class=\"btn block mt\" type=\"submit\">✅ Concluir configuração e entrar</button></form></div>";
        return pub(r, "Configuração inicial", body);
    }

    static void doSetup(Req r) throws Exception {
        Map<String, String> m = new HashMap<>();
        for (String k : List.of("admin_nome", "admin_email", "app_nome", "suporte_email", "pix_chave", "preco_clinica", "preco_dentista", "dias_teste", "dia_vencimento", "comissao_pct"))
            m.put(k, r.p(k));
        m.put("exigir_verificacao", r.chk("exigir_verificacao") ? "on" : "off");
        m.put("demo", r.chk("demo") ? "on" : "off");
        try {
            String nome = r.req("admin_nome", "Seu nome");
            String email = r.req("admin_email", "E-mail").toLowerCase();
            if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new Bad("E-mail do administrador inválido.");
            String pw = r.p("senha");
            String pol = Sec.checkPolicy(pw);
            if (pol != null) throw new Bad(pol);
            if (!pw.equals(r.p("senha2"))) throw new Bad("As senhas não conferem.");
            r.req("app_nome", "Nome do sistema");
            r.req("suporte_email", "E-mail de suporte");
            BigDecimal pc = r.num("preco_clinica"), pd = r.num("preco_dentista"), com = r.num("comissao_pct");
            int dias = r.intp("dias_teste", -1), dia = r.intp("dia_vencimento", -1);
            if (pc.signum() < 0 || pd.signum() < 0) throw new Bad("Mensalidades não podem ser negativas.");
            if (dias < 0 || dias > 365) throw new Bad("Dias de teste devem estar entre 0 e 365.");
            if (dia < 1 || dia > 28) throw new Bad("Dia de vencimento deve estar entre 1 e 28.");
            if (com.signum() < 0 || com.compareTo(new BigDecimal(50)) > 0) throw new Bad("Comissão deve estar entre 0 e 50%.");
            if (Svc.setupDone()) throw new Bad("O sistema já foi configurado.");
            String hash = Sec.hash(pw);
            String[] adminId = new String[1];
            Db.tx(() -> {
                Db.x("DELETE FROM usuarios WHERE email=?", email);
                adminId[0] = Db.insert("usuarios", Db.m("perfil", "ADMIN", "nome", nome, "email", email, "senha_hash", hash, "ativo", true, "verificado", true,
                    "trocar_senha", false, "tentativas", 0, "bloqueado_ate", 0L, "ultimo_login", System.currentTimeMillis(), "strikes", 0, "cancel_tardios", 0));
                Svc.setCfg("app_nome", r.p("app_nome"));
                Svc.setCfg("suporte_email", r.p("suporte_email"));
                Svc.setCfg("pix_chave", r.p("pix_chave"));
                Svc.setCfg("preco_clinica", pc.toPlainString());
                Svc.setCfg("preco_dentista", pd.toPlainString());
                Svc.setCfg("dias_teste", String.valueOf(dias));
                Svc.setCfg("dia_vencimento", String.valueOf(dia));
                Svc.setCfg("comissao_pct", com.toPlainString());
                Svc.setCfg("exigir_verificacao", String.valueOf(r.chk("exigir_verificacao")));
                Svc.setCfg("strikes_bloqueio", "3");
                Svc.setCfg("fuso", "America/Sao_Paulo");
                Svc.setCfg("setup_ok", "true");
            });
            if (r.chk("demo")) Demo.seed();
            App.switchSession(r, adminId[0]);
            r.user = Svc.user(adminId[0]);
            Svc.audit(r, "SETUP_CONCLUIDO", "Administrador " + email + (r.chk("demo") ? " · com dados de demonstração" : ""));
            r.ok("Configuração concluída! Bem-vindo(a) ao painel administrativo.");
            r.redirect("/admin");
        } catch (Bad b) {
            r.html(setupForm(r, m, b.getMessage()));
        }
    }

    // ================= LOGIN =================
    static void login(Req r) throws Exception {
        String body = "<div class=\"card\"><h2>Entrar</h2><form method=\"post\" action=\"/login\">" + r.csrf()
            + input("E-mail", "email", r.p("email"), "email", "required autocomplete=\"username\" autofocus")
            + input("Senha", "senha", "", "password", "required autocomplete=\"current-password\"")
            + "<button class=\"btn block mt\">Entrar</button></form>"
            + "<p class=\"center\">Ainda não tem conta? <a href=\"/cadastro\">Cadastre-se</a></p>"
            + "<div class=\"banner\" style=\"background:#ecfeff;color:#155e75;border-color:#a5f3fc;font-size:13px\">💡 <b>Testando clínica e profissional ao mesmo tempo?</b> "
            + "Cada navegador mantém um usuário por vez. Entre com o segundo usuário em uma <b>janela anônima (Ctrl+Shift+N)</b> ou em outro navegador.</div>"
            + "<p class=\"mut center\">Esqueceu a senha? <a href=\"/contato\">Fale com o suporte</a> para receber uma senha temporária.</p></div>";
        r.html(pub(r, "Entrar", "<div class=\"pubwrap narrow\" style=\"padding:0\">" + body + "</div>"));
    }

    static void doLogin(Req r) throws Exception {
        String email = r.p("email").toLowerCase();
        String pw = r.p("senha");
        Row u = Db.one("SELECT * FROM usuarios WHERE email=?", email);
        long now = System.currentTimeMillis();
        if (u == null) {
            Sec.verify(pw, DUMMY);
            Svc.auditAs(r, email, "LOGIN_FALHOU", "Usuário inexistente");
            r.err("E-mail ou senha inválidos.");
            r.redirect("/login");
            return;
        }
        if (u.l("bloqueado_ate") > now) { r.err("Muitas tentativas incorretas. Aguarde alguns minutos e tente novamente."); r.redirect("/login"); return; }
        if (!Sec.verify(pw, u.s("senha_hash"))) {
            int t = u.i("tentativas") + 1;
            long lock = t >= 5 ? now + 15 * 60_000L : 0L;
            Db.x("UPDATE usuarios SET tentativas=?, bloqueado_ate=? WHERE id=?", t >= 5 ? 0 : t, lock, u.s("id"));
            Svc.auditAs(r, email, "LOGIN_FALHOU", "Senha incorreta" + (lock > 0 ? " · conta travada por 15 min" : ""));
            r.err(lock > 0 ? "Muitas tentativas incorretas. Conta travada por 15 minutos." : "E-mail ou senha inválidos.");
            r.redirect("/login");
            return;
        }
        if (!u.b("ativo")) {
            r.err(u.i("strikes") >= Svc.strikesBloqueio() ? "Sua conta foi bloqueada por acúmulo de strikes (cancelamentos em cima da hora ou faltas). Fale com o suporte." : "Sua conta está bloqueada. Entre em contato com o suporte.");
            r.redirect("/login");
            return;
        }
        Db.x("UPDATE usuarios SET tentativas=0, bloqueado_ate=0, ultimo_login=? WHERE id=?", now, u.s("id"));
        App.switchSession(r, u.s("id"));
        r.user = u;
        Svc.audit(r, "LOGIN", "");
        r.redirect(App.home(u.s("perfil")));
    }

    // ================= CADASTRO =================
    static final String[][] TIPOS_CADASTRO = {{"CLINICA", "Clínica / consultório"}, {"DENTISTA", "Cirurgião(ã)-dentista"}, {"AUXILIAR", "Auxiliar ou técnico(a) em saúde bucal (ASB/TSB)"},
        {"ESTUDANTE", "Estudante de Odontologia"}};

    static String signupForm(Req r, Map<String, String> m, String erro) {
        BigDecimal pd = Svc.cfgNum("preco_dentista", "0");
        String body = "<div class=\"card\"><h2>Criar conta</h2>"
            + "<p class=\"mut\">Clínica: teste grátis por " + h(Svc.cfg("dias_teste", "30")) + " dias, depois " + money(Svc.cfgNum("preco_clinica", "149.90")) + "/mês · Profissionais: "
            + (pd.signum() == 0 ? "<b>100% gratuito</b>" : money(pd) + "/mês") + ".</p>"
            + (erro == null ? "" : "<div class=\"flash err\">" + h(erro) + "</div>")
            + "<form method=\"post\" action=\"/cadastro\">" + r.csrf()
            + select("Eu sou", "perfil", TIPOS_CADASTRO, v(m, "perfil", "CLINICA"), "")
            + input("Nome (clínica ou profissional)", "nome", v(m, "nome", ""), "text", "required maxlength=\"200\"")
            + "<div class=\"row\">" + input("CNPJ (clínica) · CRO (dentista/ASB/TSB) · matrícula (estudante)", "documento", v(m, "documento", ""), "text", "required maxlength=\"60\"")
            + input("Celular / WhatsApp", "telefone", v(m, "telefone", ""), "tel", "required maxlength=\"40\"") + "</div>"
            + input("Cidade / UF", "cidade", v(m, "cidade", ""), "text", "required maxlength=\"120\" placeholder=\"Ex.: Brasília/DF\"")
            + input("E-mail", "email", v(m, "email", ""), "email", "required maxlength=\"200\" autocomplete=\"username\"")
            + "<div class=\"row\">" + input("Senha (mín. 10, letras e números)", "senha", "", "password", "required minlength=\"10\" autocomplete=\"new-password\"")
            + input("Confirme a senha", "senha2", "", "password", "required minlength=\"10\" autocomplete=\"new-password\"") + "</div>"
            + check("Li e aceito os termos de uso, a política de privacidade (LGPD) e a política de cancelamento e strikes", "aceite", false)
            + "<p class=\"mut\">Política de cancelamento: mais de 24h antes do turno é livre; entre 24h e 6h fica registrado na reputação; com menos de 6h ou não comparecimento gera strike. "
            + Svc.strikesBloqueio() + " strikes bloqueiam a conta automaticamente.</p>"
            + "<button class=\"btn block mt\">Criar conta</button></form><p class=\"center\">Já tem conta? <a href=\"/login\">Entrar</a></p></div>";
        return pub(r, "Criar conta", body);
    }

    static void doSignup(Req r) throws Exception {
        Map<String, String> m = new HashMap<>();
        for (String k : List.of("perfil", "nome", "documento", "telefone", "cidade", "email")) m.put(k, r.p(k));
        try {
            String tipo = r.p("perfil");
            if (Svc.label(TIPOS_CADASTRO, tipo).equals(tipo)) throw new Bad("Perfil inválido.");
            String perfil = tipo.equals("CLINICA") ? "CLINICA" : "DENTISTA";
            String categoria = tipo.equals("CLINICA") ? "" : tipo;
            String nome = r.req("nome", "Nome"), doc = r.req("documento", "Documento"), tel = r.req("telefone", "Celular"), cid = r.req("cidade", "Cidade");
            String email = r.req("email", "E-mail").toLowerCase();
            if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new Bad("E-mail inválido.");
            String pol = Sec.checkPolicy(r.p("senha"));
            if (pol != null) throw new Bad(pol);
            if (!r.p("senha").equals(r.p("senha2"))) throw new Bad("As senhas não conferem.");
            if (!r.chk("aceite")) throw new Bad("É necessário aceitar os termos de uso.");
            if (Db.count("SELECT COUNT(*) FROM usuarios WHERE email=?", email) > 0) throw new Bad("Este e-mail já está cadastrado.");
            String hash = Sec.hash(r.p("senha"));
            String[] id = new String[1];
            Db.tx(() -> {
                id[0] = Db.insert("usuarios", Db.m("perfil", perfil, "categoria", categoria, "nome", nome, "email", email, "senha_hash", hash, "ativo", true,
                    "verificado", !Svc.exigeVerificacao(), "trocar_senha", false, "documento", doc, "telefone", tel, "cidade", cid, "cadeiras", 1,
                    "especialidades", tipo.equals("DENTISTA") ? "Clínico Geral" : "", "materiais_proprios", false, "experiencia", 0, "tentativas", 0, "bloqueado_ate", 0L,
                    "ultimo_login", System.currentTimeMillis(), "raio_km", 30, "strikes", 0, "cancel_tardios", 0, "disponibilidade", ""));
                Svc.criarAssinatura(id[0], perfil);
            });
            Svc.notifyAdmins("Novo cadastro (" + Svc.label(TIPOS_CADASTRO, tipo) + "): " + nome + (Svc.exigeVerificacao() ? " — verificar " + (perfil.equals("CLINICA") ? "CNPJ" : "registro") + " e aprovar." : "."), "/admin/usuario?id=" + id[0]);
            Mail.queue(email, nome, "Bem-vindo(a) ao " + Svc.appName(), "Seu cadastro foi criado com sucesso." + (Svc.exigeVerificacao() ? " Nossa equipe vai verificar seu registro e você será avisado(a) na aprovação." : "")
                + "\n\nPróximo passo: complete seu perfil com localização e disponibilidade para receber os melhores matches.", "CADASTRO:" + id[0]);
            App.switchSession(r, id[0]);
            r.user = Svc.user(id[0]);
            Svc.audit(r, "CADASTRO", tipo + " " + email);
            r.ok("Conta criada com sucesso! Complete seu perfil" + (perfil.equals("DENTISTA") ? ", sua localização e sua disponibilidade." : " e a localização da clínica."));
            r.redirect("/conta/perfil");
        } catch (Bad b) {
            r.html(signupForm(r, m, b.getMessage()));
        }
    }

    // ================= MINHA CONTA =================
    static void senha(Req r) throws Exception {
        String body = "<div class=\"card\" style=\"max-width:520px\"><h2>Alterar senha</h2>"
            + (r.user.b("trocar_senha") ? "<div class=\"banner\">Você entrou com uma senha temporária. Defina agora sua senha pessoal.</div>" : "")
            + "<form method=\"post\" action=\"/conta/senha\">" + r.csrf()
            + input("Senha atual", "atual", "", "password", "required autocomplete=\"current-password\"")
            + input("Nova senha (mín. 10, letras e números)", "nova", "", "password", "required minlength=\"10\" autocomplete=\"new-password\"")
            + input("Confirme a nova senha", "nova2", "", "password", "required minlength=\"10\" autocomplete=\"new-password\"")
            + "<button class=\"btn mt\">Salvar nova senha</button></form>"
            + "<p class=\"mut\">Sua senha é armazenada apenas como hash irreversível. Nem os administradores conseguem visualizá-la.</p></div>";
        r.html(page(r, "Alterar senha", body));
    }

    static void doSenha(Req r) throws Exception {
        if (!Sec.verify(r.p("atual"), r.user.s("senha_hash"))) throw new Bad("Senha atual incorreta.");
        String pol = Sec.checkPolicy(r.p("nova"));
        if (pol != null) throw new Bad(pol);
        if (!r.p("nova").equals(r.p("nova2"))) throw new Bad("As senhas não conferem.");
        if (r.p("nova").equals(r.p("atual"))) throw new Bad("A nova senha deve ser diferente da atual.");
        Db.x("UPDATE usuarios SET senha_hash=?, trocar_senha=FALSE WHERE id=?", Sec.hash(r.p("nova")), r.uid());
        Sec.logoutUser(r.uid());
        App.switchSession(r, r.uid());
        Svc.audit(r, "SENHA_ALTERADA", "");
        r.ok("Senha alterada com sucesso.");
        r.redirect(App.home(r.role()));
    }

    static void perfil(Req r) throws Exception {
        Row u = r.user;
        String role = r.role();
        String cat = Svc.cat(u);
        StringBuilder f = new StringBuilder("<form method=\"post\" action=\"/conta/perfil\">").append(r.csrf());
        f.append("<div class=\"card\"><h2>Dados ").append(role.equals("CLINICA") ? "da clínica" : "pessoais").append("</h2>");
        f.append(input(role.equals("CLINICA") ? "Nome da clínica" : "Nome", "nome", u.s("nome"), "text", "required maxlength=\"200\""));
        f.append("<div class=\"row\">");
        if (!role.equals("ADMIN")) f.append(input(Svc.docLabel(role.equals("CLINICA") ? "CLINICA" : cat), "documento", u.s("documento"), "text", "maxlength=\"60\""));
        f.append(input("Celular / WhatsApp", "telefone", u.s("telefone"), "tel", "maxlength=\"40\"")).append("</div>");
        if (!role.equals("ADMIN")) f.append(input("Cidade / UF", "cidade", u.s("cidade"), "text", "maxlength=\"120\""));
        if (role.equals("CLINICA")) {
            f.append(input("Endereço completo", "endereco", u.s("endereco"), "text", "maxlength=\"300\""));
            f.append(input("Número de cadeiras", "cadeiras", Math.max(1, u.i("cadeiras")), "number", "min=\"1\" max=\"50\""));
        }
        if (role.equals("DENTISTA")) {
            f.append(select("Categoria", "categoria", Svc.CATEGORIAS, cat, ""));
            if (cat.equals("DENTISTA")) {
                f.append("<label>Especialidades</label><div>");
                List<String> sp = Arrays.asList(u.s("especialidades").split(";"));
                for (String s : Svc.ESPECIALIDADES) f.append(check(s, "esp_" + s, sp.contains(s)));
                f.append("</div>");
            }
            if (cat.equals("ESTUDANTE")) f.append("<div class=\"row\">").append(input("Faculdade", "faculdade", u.s("faculdade"))).append(input("Semestre", "semestre", u.s("semestre"))).append("</div>");
            f.append("<div class=\"row\">").append(input("Anos de experiência", "experiencia", u.i("experiencia"), "number", "min=\"0\" max=\"70\""))
                .append(input("Chave PIX para receber repasses", "pix", u.s("pix"), "text", "maxlength=\"200\"")).append("</div>")
                .append(area("Mini currículo / apresentação", "bio", u.s("bio"), 3)).append(area("Formação, cursos e certificações", "formacao", u.s("formacao"), 3))
                .append(check("Possuo materiais/instrumentais próprios", "materiais_proprios", u.b("materiais_proprios")));
        }
        f.append("</div>");
        if (!role.equals("ADMIN")) {
            f.append("<div class=\"card\"><h2>📍 Localização</h2>").append(geoFields(u));
            if (role.equals("DENTISTA")) f.append(input("Raio de atuação (km)", "raio_km", u.i("raio_km") > 0 ? u.i("raio_km") : 30, "number", "min=\"1\" max=\"300\""));
            f.append("</div>");
        }
        if (role.equals("DENTISTA"))
            f.append("<div class=\"card\"><h2>🗓 Disponibilidade semanal</h2><p class=\"mut\">Marque quando você costuma estar livre. Usamos isso no match e para te avisar dos turnos certos.</p>")
                .append(dispGrid(u.s("disponibilidade"))).append("</div>");
        f.append("<p class=\"mut\">E-mail de login: <b>").append(h(u.s("email"))).append("</b> (para alterar, contate o suporte).</p><button class=\"btn\">Salvar perfil</button></form>");
        String rep = "";
        if (!role.equals("ADMIN")) {
            List<String[]> rows = new ArrayList<>();
            for (Row a : Db.q("SELECT * FROM avaliacoes WHERE para_id=? ORDER BY criado DESC", r.uid()))
                rows.add(new String[]{"<span class=\"stars\">" + "★".repeat(a.i("nota")) + "</span>", h(Svc.nome(a.s("de_id"))), h(a.s("texto")), ts(a.l("criado"))});
            List<String[]> oc = new ArrayList<>();
            for (Row o : Db.q("SELECT * FROM ocorrencias WHERE usuario_id=? ORDER BY criado DESC", r.uid()))
                oc.add(new String[]{ts(o.l("criado")), h(Svc.label(Svc.TIPOS_OCORRENCIA, o.s("tipo"))), o.b("anulada") ? badge("Anulada", "b-m") : o.b("strike") ? badge("Strike", "b-e") : badge("Registro", "b-w"), h(o.s("detalhe"))});
            rep = "<div class=\"card\"><div class=\"bar\"><h2>Minha reputação</h2><div>" + Svc.stars(r.uid()) + "</div></div><p>" + Svc.selos(u) + "</p>"
                + table(new String[]{"Nota", "De", "Comentário", "Data"}, rows, "Você ainda não recebeu avaliações.") + "</div>"
                + "<div class=\"card\"><h2>Histórico de cancelamentos e strikes</h2><p class=\"mut\">Strikes: <b>" + u.i("strikes") + " de " + Svc.strikesBloqueio() + "</b> · cancelamentos tardios: " + u.i("cancel_tardios") + "</p>"
                + table(new String[]{"Data", "Tipo", "Efeito", "Detalhe"}, oc, "Nenhuma ocorrência. Continue assim! 👏") + "</div>";
        }
        r.html(page(r, "Perfil", "<div class=\"grid g2\"><div>" + f + "</div><div>" + rep + "</div></div>"));
    }

    static void doPerfil(Req r) throws Exception {
        String role = r.role();
        String nome = r.req("nome", "Nome");
        String lat = r.p("lat").replace(',', '.'), lng = r.p("lng").replace(',', '.');
        if (!lat.isEmpty() || !lng.isEmpty()) { if (!Svc.validCoord(lat, lng)) throw new Bad("Latitude/longitude inválidas. Exemplo: -15.7998 e -47.8919."); }
        if (role.equals("DENTISTA")) {
            String cat = r.p("categoria");
            if (Svc.label(Svc.CATEGORIAS, cat).equals(cat)) cat = Svc.cat(r.user);
            List<String> sp = new ArrayList<>();
            for (String s : Svc.ESPECIALIDADES) if (r.chk("esp_" + s)) sp.add(s);
            if (cat.equals("DENTISTA") && sp.isEmpty()) sp.add("Clínico Geral");
            List<String> disp = new ArrayList<>();
            for (int d = 1; d <= 7; d++) for (String t : new String[]{"M", "T", "N"}) if (r.chk("disp_" + d + t)) disp.add(d + t);
            int raio = Math.max(1, Math.min(300, r.intp("raio_km", 30)));
            Db.x("UPDATE usuarios SET nome=?, documento=?, telefone=?, cidade=?, especialidades=?, experiencia=?, pix=?, bio=?, materiais_proprios=?, categoria=?, faculdade=?, semestre=?, formacao=?, lat=?, lng=?, raio_km=?, disponibilidade=? WHERE id=?",
                nome, r.p("documento"), r.p("telefone"), r.p("cidade"), String.join(";", sp), Math.max(0, r.intp("experiencia", 0)), r.p("pix"), r.p("bio"), r.chk("materiais_proprios"),
                cat, r.p("faculdade"), r.p("semestre"), r.p("formacao"), lat, lng, raio, String.join(",", disp), r.uid());
        } else if (role.equals("CLINICA")) {
            Db.x("UPDATE usuarios SET nome=?, documento=?, telefone=?, cidade=?, endereco=?, cadeiras=?, lat=?, lng=? WHERE id=?",
                nome, r.p("documento"), r.p("telefone"), r.p("cidade"), r.p("endereco"), Math.max(1, r.intp("cadeiras", 1)), lat, lng, r.uid());
        } else {
            Db.x("UPDATE usuarios SET nome=?, telefone=? WHERE id=?", nome, r.p("telefone"), r.uid());
        }
        Svc.audit(r, "PERFIL_ATUALIZADO", "");
        r.ok("Perfil atualizado.");
        r.redirect("/conta/perfil");
    }

    static void plano(Req r) throws Exception {
        if (r.role().equals("ADMIN")) { r.redirect("/admin/assinaturas"); return; }
        Row s = Svc.assinatura(r.uid());
        if (s == null) { Svc.criarAssinatura(r.uid(), r.role()); s = Svc.assinatura(r.uid()); }
        List<String[]> rows = new ArrayList<>();
        String hoje = today();
        for (Row f : Db.q("SELECT * FROM faturas WHERE usuario_id=? ORDER BY vencimento DESC", r.uid())) {
            String st = f.s("status");
            String badge = st.equals("PAGA") ? badge("Paga em " + d(f.s("pago_em")), "b-ok") : st.equals("CANCELADA") ? badge("Cancelada", "b-m")
                : f.s("vencimento").compareTo(hoje) < 0 ? badge("Vencida", "b-e") : badge("Em aberto", "b-w");
            String acao = st.equals("ABERTA") ? (f.l("informado_em") > 0 ? badge("Pagamento informado — em conferência", "b-p")
                : post(r, "/conta/fatura/informar", "Já paguei (informar)", "sm sec", "Confirma que realizou o pagamento desta fatura?", "id", f.s("id"))) : "";
            rows.add(new String[]{h(f.s("descricao")), money(f.d("valor")), d(f.s("vencimento")), badge, acao});
        }
        String pix = Svc.cfg("pix_chave", "");
        boolean gratis = s.d("valor").signum() == 0;
        String body = "<div class=\"grid g2\"><div class=\"card\"><h2>" + h(s.s("plano")) + "</h2><div class=\"terms\">" + (gratis ? "Gratuito" : money(s.d("valor")) + " <span class=\"mut\">/mês</span>") + "</div>"
            + "<p>Situação: " + Svc.stBadge(s.s("status")) + "</p>"
            + (s.s("status").equals("TESTE") ? "<p>Teste grátis até <b>" + d(s.s("teste_ate")) + "</b>.</p>" : "")
            + "<h4>O que está incluído</h4><ul class=\"ck\">" + (r.role().equals("CLINICA")
                ? "<li>Agenda, pacientes, prontuário e odontograma</li><li>Turnos ilimitados, match inteligente e negociação</li><li>Aluguel de salas</li><li>Confirmações por e-mail e repasses</li>"
                : "<li>Acesso a todos os turnos compatíveis</li><li>Propostas e contrapropostas ilimitadas</li><li>Aluguel de salas</li><li>Agenda, prontuários e extrato de ganhos</li>")
            + "</ul></div><div class=\"card\"><h2>Como pagar</h2>"
            + (gratis ? "<p>Seu plano é gratuito. Nada a pagar. 🎉</p>" : pix.isEmpty() ? "<p>Fale com o suporte para receber os dados de pagamento.</p>" : "<p>Pague via PIX usando a chave:</p><div class=\"secret\">" + h(pix) + "</div>")
            + "<p class=\"mut\">Após pagar, clique em <b>Já paguei</b> na fatura. A administração confirma o recebimento e sua assinatura é atualizada automaticamente.</p>"
            + "<p class=\"mut\"><a href=\"/contato\">Falar com o suporte</a></p></div></div>"
            + "<div class=\"card\"><h2>Minhas faturas</h2>" + table(new String[]{"Descrição", "Valor", "Vencimento", "Situação", ""}, rows, "Nenhuma fatura gerada ainda.") + "</div>";
        r.html(page(r, "Meu plano", body));
    }

    static void informarPagamento(Req r) throws Exception {
        Row f = Db.one("SELECT * FROM faturas WHERE id=? AND usuario_id=?", r.p("id"), r.uid());
        if (f == null || !f.s("status").equals("ABERTA")) throw new Bad("Fatura não encontrada.");
        Db.x("UPDATE faturas SET informado_em=? WHERE id=?", System.currentTimeMillis(), f.s("id"));
        Svc.notifyAdmins(r.user.s("nome") + " informou pagamento da fatura \"" + f.s("descricao") + "\" (" + money(f.d("valor")) + ").", "/admin/faturas?status=INFORMADA");
        Svc.audit(r, "PAGAMENTO_INFORMADO", f.s("descricao"));
        r.ok("Pagamento informado. Assim que for confirmado, você será notificado.");
        r.redirect("/conta/plano");
    }

    static void notificacoes(Req r) throws Exception {
        List<Row> l = Db.q("SELECT * FROM notificacoes WHERE usuario_id=? ORDER BY criado DESC", r.uid());
        StringBuilder b = new StringBuilder("<div class=\"bar\"><span class=\"mut\">" + l.size() + " notificação(ões)</span>" + post(r, "/notificacoes/lidas", "Marcar todas como lidas", "sm gh", null) + "</div><div class=\"card\">");
        if (l.isEmpty()) b.append(empty("Sem notificações."));
        int i = 0;
        for (Row n : l) {
            if (i++ >= 200) break;
            b.append("<div class=\"item\" style=\"").append(n.b("lida") ? "" : "background:#f0fdfa").append("\"><div class=\"info\">").append(h(n.s("texto")))
                .append("<div class=\"mut\">").append(ts(n.l("criado"))).append("</div></div>")
                .append(n.has("link") ? "<a class=\"btn sm sec\" href=\"" + h(n.s("link")) + "\">Abrir</a>" : "").append(n.b("lida") ? "" : badge("Nova", "b-p")).append("</div>");
        }
        b.append("</div>");
        r.html(page(r, "Notificações", b.toString()));
    }

    // ================= CONTATO E SUPORTE =================
    static void contato(Req r) throws Exception {
        boolean logado = r.user != null;
        String form = "<div class=\"card\"><h2>Como podemos ajudar?</h2><p class=\"mut\">Respondemos em até 1 dia útil. Você também pode escrever para " + h(Svc.cfg("suporte_email", "")) + ".</p>"
            + "<form method=\"post\" action=\"/contato\">" + r.csrf()
            + (logado ? "" : "<div class=\"row\">" + input("Nome", "nome", "", "text", "required maxlength=\"200\"") + input("E-mail", "email", "", "email", "required maxlength=\"200\"") + "</div>")
            + "<div class=\"row\">" + select("Categoria", "categoria", opts(Svc.CAT_SUPORTE), "", "") + input("Assunto", "assunto", "", "text", "required maxlength=\"200\"") + "</div>"
            + area("Mensagem", "mensagem", "", 5) + "<button class=\"btn mt\">Enviar mensagem</button></form></div>";
        if (!logado) { r.html(pub(r, "Contato e suporte", form)); return; }
        List<String[]> rows = new ArrayList<>();
        for (Row t : Db.q("SELECT * FROM tickets WHERE usuario_id=? ORDER BY criado DESC", r.uid()))
            rows.add(new String[]{ts(t.l("criado")), h(t.s("categoria")), "<b>" + h(t.s("assunto")) + "</b><div class=\"mut\">" + h(t.s("mensagem")) + "</div>",
                t.has("resposta") ? "<div style=\"white-space:pre-wrap\">" + h(t.s("resposta")) + "</div>" : "<span class=\"mut\">Aguardando resposta</span>",
                t.s("status").equals("ABERTO") ? badge("Aberto", "b-w") : t.s("status").equals("RESPONDIDO") ? badge("Respondido", "b-ok") : badge("Fechado", "b-m")});
        r.html(page(r, "Suporte", "<div class=\"grid g2\"><div>" + form + "</div><div class=\"card\"><h2>Meus chamados</h2>"
            + table(new String[]{"Data", "Categoria", "Assunto", "Resposta", "Situação"}, rows, "Você ainda não abriu chamados.") + "</div></div>"));
    }

    static void doContato(Req r) throws Exception {
        String nome = r.user != null ? r.user.s("nome") : r.req("nome", "Nome");
        String email = r.user != null ? r.user.s("email") : r.req("email", "E-mail").toLowerCase();
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new Bad("E-mail inválido.");
        String cat = r.p("categoria");
        if (!Arrays.asList(Svc.CAT_SUPORTE).contains(cat)) cat = "Outro";
        String assunto = r.req("assunto", "Assunto"), msg = r.req("mensagem", "Mensagem");
        if (msg.length() > 2000) msg = msg.substring(0, 2000);
        Db.insert("tickets", Db.m("usuario_id", r.uid(), "nome", nome, "email", email, "categoria", cat, "assunto", assunto, "mensagem", msg, "resposta", "", "status", "ABERTO", "respondido_em", 0L));
        Svc.notifyAdmins("💬 Novo chamado de " + nome + ": " + assunto, "/admin/suporte");
        r.ok("Mensagem enviada! Responderemos em até 1 dia útil" + (r.user == null ? " no e-mail informado." : " — acompanhe aqui."));
        r.redirect("/contato");
    }
}

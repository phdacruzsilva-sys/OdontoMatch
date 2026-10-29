package odontomatch;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.*;

import static odontomatch.Ui.*;

/** Módulo exclusivo do administrador. */
final class AdminPages {
    private AdminPages() {}

    static void routes() {
        App.get("/admin", AdminPages::dashboard);
        App.get("/admin/usuarios", AdminPages::usuarios);
        App.get("/admin/usuario/novo", AdminPages::novoUsuario);
        App.post("/admin/usuario/criar", AdminPages::criarUsuario);
        App.get("/admin/usuario", AdminPages::usuario);
        App.post("/admin/usuario/salvar", AdminPages::salvarUsuario);
        App.post("/admin/usuario/verificar", AdminPages::verificar);
        App.post("/admin/usuario/bloquear", AdminPages::bloquear);
        App.post("/admin/usuario/reset-senha", AdminPages::resetSenha);
        App.post("/admin/assinatura/salvar", AdminPages::salvarAssinatura);
        App.get("/admin/assinaturas", AdminPages::assinaturas);
        App.get("/admin/faturas", AdminPages::faturas);
        App.post("/admin/faturas/gerar", AdminPages::gerarFaturas);
        App.post("/admin/fatura/pagar", AdminPages::pagarFatura);
        App.post("/admin/fatura/cancelar", AdminPages::cancelarFatura);
        App.post("/admin/inadimplencia", r -> { int n = Svc.atualizarInadimplencia(); Svc.audit(r, "INADIMPLENCIA_ATUALIZADA", n + " alteração(ões)"); r.ok(n + " assinatura(s) atualizada(s)."); r.redirect(r.back("/admin/assinaturas")); });
        App.get("/admin/financeiro", AdminPages::financeiro);
        App.get("/admin/financeiro/csv", AdminPages::csv);
        App.post("/admin/lancamento/criar", AdminPages::criarLancamento);
        App.post("/admin/lancamento/pago", AdminPages::togglePago);
        App.post("/admin/lancamento/excluir", AdminPages::excluirLancamento);
        App.get("/admin/marketplace", AdminPages::marketplace);
        App.get("/admin/ocorrencias", AdminPages::ocorrencias);
        App.post("/admin/ocorrencia/anular", AdminPages::anularOcorrencia);
        App.post("/admin/ocorrencia/strike", AdminPages::strikeManual);
        App.get("/admin/suporte", AdminPages::suporte);
        App.post("/admin/suporte/responder", AdminPages::responder);
        App.get("/admin/emails", AdminPages::emails);
        App.post("/admin/emails/reenviar", r -> { Mail.resend(r.p("id")); r.ok("Reenvio solicitado."); r.redirect("/admin/emails"); });
        App.post("/admin/emails/testar", AdminPages::testarSmtp);
        App.get("/admin/config", AdminPages::config);
        App.post("/admin/config", AdminPages::salvarConfig);
        App.get("/admin/auditoria", AdminPages::auditoria);
    }

    static String perfilBadge(Row u) {
        return switch (u.s("perfil")) {
            case "ADMIN" -> badge("Admin", "b-e");
            case "CLINICA" -> badge("Clínica", "b-p");
            default -> badge(switch (Svc.cat(u)) { case "AUXILIAR" -> "Auxiliar/TSB"; case "ESTUDANTE" -> "Estudante"; default -> "Dentista"; }, "");
        };
    }

    static BigDecimal sum(String sql, Object... p) {
        BigDecimal t = BigDecimal.ZERO;
        for (Row r : Db.q(sql, p)) t = t.add(r.d("valor"));
        return t;
    }

    // ================= DASHBOARD =================
    static void dashboard(Req r) throws Exception {
        Svc.atualizarInadimplencia();
        String mes = month(), ini = mes + "-01", fim = mes + "-31";
        long clin = Db.count("SELECT COUNT(*) FROM usuarios WHERE perfil='CLINICA'"), dent = Db.count("SELECT COUNT(*) FROM usuarios WHERE perfil='DENTISTA'");
        long pend = Db.count("SELECT COUNT(*) FROM usuarios WHERE perfil<>'ADMIN' AND verificado=FALSE AND ativo=TRUE");
        long ativas = Db.count("SELECT COUNT(*) FROM assinaturas WHERE status='ATIVA'"), teste = Db.count("SELECT COUNT(*) FROM assinaturas WHERE status='TESTE'");
        long inad = Db.count("SELECT COUNT(*) FROM assinaturas WHERE status='INADIMPLENTE'");
        BigDecimal mrr = sum("SELECT valor FROM assinaturas WHERE status IN ('ATIVA','INADIMPLENTE')");
        BigDecimal potencial = sum("SELECT valor FROM assinaturas WHERE status='TESTE'");
        BigDecimal rec = sum("SELECT valor FROM lancamentos WHERE tipo='RECEITA' AND pago=TRUE AND data>=? AND data<=?", ini, fim);
        BigDecimal desp = sum("SELECT valor FROM lancamentos WHERE tipo='DESPESA' AND pago=TRUE AND data>=? AND data<=?", ini, fim);
        BigDecimal aReceber = sum("SELECT valor FROM faturas WHERE status='ABERTA'");
        long vencidas = Db.count("SELECT COUNT(*) FROM faturas WHERE status='ABERTA' AND vencimento<?", today());
        long abertas = Db.count("SELECT COUNT(*) FROM oportunidades WHERE status='ABERTA' AND data>=?", today());
        long tickets = Db.count("SELECT COUNT(*) FROM tickets WHERE status='ABERTO'");
        long strikesMes = Db.count("SELECT COUNT(*) FROM ocorrencias WHERE strike=TRUE AND anulada=FALSE AND criado>=?", System.currentTimeMillis() - 30L * 86400000);
        List<Row> fechadasMes = Db.q("SELECT * FROM oportunidades WHERE status='FECHADA' AND data>=? AND data<=?", ini, fim);
        BigDecimal gmv = BigDecimal.ZERO;
        for (Row o : fechadasMes) gmv = gmv.add(Svc.settle(o).prod());

        StringBuilder b = new StringBuilder();
        b.append("<div class=\"grid g4\">").append(kpi("Clínicas cadastradas", String.valueOf(clin), "🏥")).append(kpi("Profissionais cadastrados", String.valueOf(dent), "🦷"))
            .append(kpi("Aguardando verificação", String.valueOf(pend), "⏳")).append(kpi("Turnos abertos", String.valueOf(abertas), "📣")).append("</div>");
        b.append("<h4>Receita recorrente</h4><div class=\"grid g4\">").append(kpi("MRR (assinaturas ativas)", money(mrr), "📈")).append(kpi("ARR projetado", money(mrr.multiply(new BigDecimal(12))), "🚀"))
            .append(kpi("Potencial dos trials", money(potencial), "🎁")).append(kpi("Ativas · Teste · Inadimpl.", ativas + " · " + teste + " · " + inad, "💳")).append("</div>");
        b.append("<h4>Financeiro de ").append(monthName(mes)).append("</h4><div class=\"grid g4\">").append(kpi("Receitas recebidas", money(rec), "💵"))
            .append(kpi("Despesas pagas", money(desp), "📤")).append(kpi("Resultado", "<span style=\"color:" + (rec.compareTo(desp) >= 0 ? "#16a34a" : "#dc2626") + "\">" + money(rec.subtract(desp)) + "</span>", "📊"))
            .append(kpi("A receber (" + vencidas + " vencidas)", money(aReceber), "🧾")).append("</div>");
        b.append("<h4>Operação</h4><div class=\"grid g4\">").append(kpi("Chamados de suporte abertos", String.valueOf(tickets), "💬"))
            .append(kpi("Strikes nos últimos 30 dias", String.valueOf(strikesMes), "⚠️"))
            .append(kpi("Reservas de sala pendentes", String.valueOf(Db.count("SELECT COUNT(*) FROM reservas WHERE status='PENDENTE'")), "🚪"))
            .append(kpi("E-mails com erro", String.valueOf(Db.count("SELECT COUNT(*) FROM emails WHERE status='ERRO'")), "📧")).append("</div>");

        StringBuilder chart = new StringBuilder("<div class=\"bars\">");
        List<BigDecimal[]> vals = new ArrayList<>();
        BigDecimal max = BigDecimal.ONE;
        YearMonth ym = YearMonth.parse(mes);
        for (int i = 5; i >= 0; i--) {
            YearMonth m = ym.minusMonths(i);
            BigDecimal ri = sum("SELECT valor FROM lancamentos WHERE tipo='RECEITA' AND pago=TRUE AND data>=? AND data<=?", m + "-01", m + "-31");
            BigDecimal di = sum("SELECT valor FROM lancamentos WHERE tipo='DESPESA' AND pago=TRUE AND data>=? AND data<=?", m + "-01", m + "-31");
            vals.add(new BigDecimal[]{ri, di});
            max = max.max(ri).max(di);
        }
        for (int i = 0; i < 6; i++) {
            BigDecimal[] v = vals.get(i);
            int hi = v[0].multiply(new BigDecimal(100)).divide(max, 0, java.math.RoundingMode.HALF_UP).intValue();
            int ho = v[1].multiply(new BigDecimal(100)).divide(max, 0, java.math.RoundingMode.HALF_UP).intValue();
            chart.append("<div class=\"col\"><div class=\"pair\"><div class=\"bi\" style=\"height:").append(hi).append("%\" title=\"Receitas ").append(money(v[0]))
                .append("\"></div><div class=\"bo\" style=\"height:").append(ho).append("%\" title=\"Despesas ").append(money(v[1])).append("\"></div></div><small>")
                .append(monthName(ym.minusMonths(5 - i).toString())).append("</small></div>");
        }
        chart.append("</div><p class=\"mut\">🟩 Receitas · 🟥 Despesas (valores liquidados)</p>");

        StringBuilder pendL = new StringBuilder();
        for (Row u : Db.q("SELECT * FROM usuarios WHERE perfil<>'ADMIN' AND verificado=FALSE AND ativo=TRUE ORDER BY criado")) {
            pendL.append("<div class=\"item\"><div class=\"info\"><b>").append(h(u.s("nome"))).append("</b> ").append(perfilBadge(u))
                .append("<div class=\"mut\">").append(h(u.s("documento"))).append(" · ").append(h(u.s("cidade"))).append(" · ").append(ts(u.l("criado"))).append("</div></div>")
                .append("<a class=\"btn sm gh\" href=\"/admin/usuario?id=").append(u.s("id")).append("\">Ver</a>")
                .append(post(r, "/admin/usuario/verificar", "✔ Verificar e aprovar", "sm ok", null, "id", u.s("id"))).append("</div>");
        }
        StringBuilder inf = new StringBuilder();
        for (Row f : Db.q("SELECT f.*, u.nome FROM faturas f JOIN usuarios u ON u.id=f.usuario_id WHERE f.status='ABERTA' AND f.informado_em>0 ORDER BY f.informado_em")) {
            inf.append("<div class=\"item\"><div class=\"info\"><b>").append(h(f.s("nome"))).append("</b><div class=\"mut\">").append(h(f.s("descricao"))).append(" · ")
                .append(money(f.d("valor"))).append("</div></div>").append(post(r, "/admin/fatura/pagar", "Confirmar recebimento", "sm ok", null, "id", f.s("id"), "metodo", "PIX", "data", today())).append("</div>");
        }
        StringBuilder aud = new StringBuilder();
        int i = 0;
        for (Row a : Db.q("SELECT * FROM auditoria ORDER BY criado DESC")) {
            if (i++ >= 8) break;
            aud.append("<div class=\"item\"><div class=\"info\"><b>").append(h(a.s("acao"))).append("</b> <span class=\"mut\">").append(h(a.s("email"))).append("</span><div class=\"mut\">")
                .append(h(a.s("detalhe"))).append(" · ").append(ts(a.l("criado"))).append("</div></div></div>");
        }
        b.append("<div class=\"grid g2 mt\"><div class=\"card\"><h2>Receitas × despesas (6 meses)</h2>").append(chart).append("</div>")
            .append("<div class=\"card\"><h2>Marketplace no mês</h2><div class=\"grid g2\">").append(kpi("Turnos fechados", String.valueOf(fechadasMes.size()), "🤝"))
            .append(kpi("Produção intermediada (GMV)", money(gmv), "🦷")).append("</div><p class=\"mut\">GMV: valor dos procedimentos agendados nos turnos fechados pela plataforma.</p>")
            .append("<a class=\"btn sm sec\" href=\"/admin/marketplace\">Ver marketplace</a></div></div>");
        b.append("<div class=\"grid g2\"><div class=\"card\"><div class=\"bar\"><h2>⏳ Verificação de CRO/CNPJ pendente</h2><a class=\"btn sm gh\" href=\"/admin/usuarios?status=PENDENTE\">Ver todos</a></div>")
            .append(pendL.length() == 0 ? empty("Nenhum cadastro aguardando verificação.") : pendL).append("</div>")
            .append("<div class=\"card\"><div class=\"bar\"><h2>💸 Pagamentos informados</h2><a class=\"btn sm gh\" href=\"/admin/faturas\">Faturas</a></div>")
            .append(inf.length() == 0 ? empty("Nenhum pagamento aguardando conferência.") : inf).append("</div></div>");
        b.append("<div class=\"card\"><div class=\"bar\"><h2>🛡️ Atividade recente</h2><a class=\"btn sm gh\" href=\"/admin/auditoria\">Auditoria completa</a></div>").append(aud).append("</div>");
        r.html(page(r, "Painel administrativo", b.toString()));
    }

    // ================= USUÁRIOS =================
    static void usuarios(Req r) throws Exception {
        String perfil = r.p("perfil"), status = r.p("status"), q = r.p("q").toLowerCase();
        List<String[]> rows = new ArrayList<>();
        for (Row u : Db.q("SELECT u.*, a.status AS ast FROM usuarios u LEFT JOIN assinaturas a ON a.usuario_id=u.id ORDER BY u.criado DESC")) {
            if (!perfil.isEmpty()) {
                if (perfil.equals("CLINICA") || perfil.equals("ADMIN")) { if (!u.s("perfil").equals(perfil)) continue; }
                else if (!u.s("perfil").equals("DENTISTA") || !Svc.cat(u).equals(perfil)) continue;
            }
            if (status.equals("PENDENTE") && (u.b("verificado") || u.s("perfil").equals("ADMIN"))) continue;
            if (status.equals("BLOQUEADO") && u.b("ativo")) continue;
            if (status.equals("ATIVO") && !u.b("ativo")) continue;
            if (status.equals("STRIKES") && u.i("strikes") == 0) continue;
            if (!q.isEmpty() && !(u.s("nome") + " " + u.s("email") + " " + u.s("documento") + " " + u.s("cidade")).toLowerCase().contains(q)) continue;
            String sit = (u.b("ativo") ? badge("Ativo", "b-ok") : badge("Bloqueado", "b-e")) + " "
                + (u.s("perfil").equals("ADMIN") ? "" : u.b("verificado") ? badge("Verificado", "b-ver") : badge("Pendente", "b-w"))
                + (u.i("strikes") > 0 ? " " + badge(u.i("strikes") + " strike(s)", "b-e") : "");
            rows.add(new String[]{"<b>" + h(u.s("nome")) + "</b><div class=\"mut\">" + h(u.s("email")) + "</div>", perfilBadge(u), h(u.s("documento")),
                h(u.s("cidade")), sit, u.has("ast") ? Svc.stBadge(u.s("ast")) : "—", ts(u.l("ultimo_login")),
                "<a class=\"btn sm sec\" href=\"/admin/usuario?id=" + u.s("id") + "\">Gerenciar</a>"});
        }
        String filtros = "<div class=\"card\"><form method=\"get\" action=\"/admin/usuarios\" class=\"row\" style=\"align-items:flex-end\">"
            + input("Buscar", "q", r.p("q"), "search", "placeholder=\"Nome, e-mail, CNPJ/CRO, cidade\"")
            + select("Perfil", "perfil", new String[][]{{"", "Todos"}, {"CLINICA", "Clínicas"}, {"DENTISTA", "Dentistas"}, {"AUXILIAR", "Auxiliares/TSB"}, {"ESTUDANTE", "Estudantes"}, {"ADMIN", "Administradores"}}, perfil, "")
            + select("Situação", "status", new String[][]{{"", "Todas"}, {"PENDENTE", "Aguardando verificação"}, {"ATIVO", "Ativos"}, {"BLOQUEADO", "Bloqueados"}, {"STRIKES", "Com strikes"}}, status, "")
            + "<div style=\"flex:0\"><button class=\"btn\">Filtrar</button></div></form></div>";
        String body = "<div class=\"bar\"><span class=\"mut\">" + rows.size() + " usuário(s)</span><a class=\"btn acc\" href=\"/admin/usuario/novo\">+ Cadastrar clínica, profissional ou admin</a></div>"
            + filtros + "<div class=\"card\">" + table(new String[]{"Nome", "Perfil", "CNPJ/CRO", "Cidade", "Situação", "Assinatura", "Último acesso", ""}, rows, "Nenhum usuário encontrado.") + "</div>";
        r.html(page(r, "Clínicas e pessoas", body));
    }

    static void novoUsuario(Req r) throws Exception {
        String body = "<div class=\"card\" style=\"max-width:760px\"><h2>Novo cadastro</h2><form method=\"post\" action=\"/admin/usuario/criar\">" + r.csrf()
            + select("Perfil", "perfil", new String[][]{{"CLINICA", "Clínica"}, {"DENTISTA", "Dentista"}, {"AUXILIAR", "Auxiliar/TSB"}, {"ESTUDANTE", "Estudante"}, {"ADMIN", "Administrador"}}, "CLINICA", "")
            + input("Nome", "nome", "", "text", "required maxlength=\"200\"")
            + "<div class=\"row\">" + input("E-mail (login)", "email", "", "email", "required maxlength=\"200\"") + input("CNPJ / CRO / matrícula", "documento", "", "text", "maxlength=\"60\"") + "</div>"
            + "<div class=\"row\">" + input("Celular", "telefone", "", "tel", "maxlength=\"40\"") + input("Cidade / UF", "cidade", "", "text", "maxlength=\"120\"") + "</div>"
            + input("Especialidades do dentista (separadas por ;)", "especialidades", "Clínico Geral", "text", "")
            + check("Registro (CRO/CNPJ) já verificado", "verificado", true)
            + "<p class=\"mut\">O sistema gera uma <b>senha temporária</b>, exibida uma única vez. No primeiro acesso a pessoa é obrigada a definir uma senha própria.</p>"
            + "<button class=\"btn mt\">Cadastrar</button></form></div>";
        r.html(page(r, "Novo cadastro", body));
    }

    static void criarUsuario(Req r) throws Exception {
        String tipo = r.p("perfil");
        if (!List.of("CLINICA", "DENTISTA", "AUXILIAR", "ESTUDANTE", "ADMIN").contains(tipo)) throw new Bad("Perfil inválido.");
        String perfil = tipo.equals("CLINICA") || tipo.equals("ADMIN") ? tipo : "DENTISTA";
        String nome = r.req("nome", "Nome"), email = r.req("email", "E-mail").toLowerCase();
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new Bad("E-mail inválido.");
        if (Db.count("SELECT COUNT(*) FROM usuarios WHERE email=?", email) > 0) throw new Bad("E-mail já cadastrado.");
        String temp = Sec.tempPassword();
        String hash = Sec.hash(temp);
        String[] id = new String[1];
        Db.tx(() -> {
            id[0] = Db.insert("usuarios", Db.m("perfil", perfil, "categoria", perfil.equals("DENTISTA") ? tipo : "", "nome", nome, "email", email, "senha_hash", hash, "ativo", true,
                "verificado", perfil.equals("ADMIN") || r.chk("verificado"), "trocar_senha", true, "documento", r.p("documento"), "telefone", r.p("telefone"),
                "cidade", r.p("cidade"), "cadeiras", 1, "especialidades", tipo.equals("DENTISTA") ? r.p("especialidades") : "", "materiais_proprios", false,
                "experiencia", 0, "tentativas", 0, "bloqueado_ate", 0L, "ultimo_login", 0L, "raio_km", 30, "strikes", 0, "cancel_tardios", 0, "disponibilidade", ""));
            if (!perfil.equals("ADMIN")) Svc.criarAssinatura(id[0], perfil);
        });
        Svc.audit(r, "USUARIO_CRIADO", tipo + " " + email);
        r.html(page(r, "Cadastro concluído", senhaTemp(nome, email, temp, id[0])));
    }

    static String senhaTemp(String nome, String email, String temp, String id) {
        return "<div class=\"card\" style=\"max-width:640px\"><h2>✅ Acesso de " + h(nome) + "</h2><p>Login: <b>" + h(email) + "</b></p><p>Senha temporária:</p><div class=\"secret\">"
            + h(temp) + "</div><p class=\"mut\">Copie e envie por um canal seguro. <b>Ela não será exibida novamente</b> — o sistema guarda apenas o hash. "
            + "No primeiro acesso a pessoa deverá criar a própria senha.</p><a class=\"btn\" href=\"/admin/usuario?id=" + h(id) + "\">Ir para o cadastro</a> "
            + "<a class=\"btn gh\" href=\"/admin/usuarios\">Voltar à lista</a></div>";
    }

    static Row needUser(Req r) {
        Row u = Svc.user(r.p("id"));
        if (u == null) throw new Bad("Usuário não encontrado.");
        return u;
    }

    static void usuario(Req r) throws Exception {
        Row u = needUser(r);
        String id = u.s("id");
        boolean admin = u.s("perfil").equals("ADMIN");
        StringBuilder b = new StringBuilder("<div class=\"bar\"><a class=\"btn gh sm\" href=\"/admin/usuarios\">← Voltar</a><div class=\"tools\">");
        if (!admin && !u.b("verificado")) b.append(post(r, "/admin/usuario/verificar", "✔ Registro verificado — aprovar", "sm ok", null, "id", id));
        if (!id.equals(r.uid()))
            b.append(post(r, "/admin/usuario/bloquear", u.b("ativo") ? "🚫 Bloquear acesso" : "✅ Desbloquear", u.b("ativo") ? "sm red" : "sm ok",
                u.b("ativo") ? "Bloquear o acesso deste usuário? As sessões abertas serão encerradas." : null, "id", id));
        b.append(post(r, "/admin/usuario/reset-senha", "🔑 Gerar senha temporária", "sm gh", "Gerar uma nova senha temporária? A senha atual deixará de funcionar.", "id", id));
        b.append("</div></div><div class=\"grid g2\"><div class=\"card\"><h2>").append(h(u.s("nome"))).append(" ").append(perfilBadge(u)).append("</h2><p>").append(Svc.selos(u)).append("</p>")
            .append("<p class=\"mut\">Cadastrado em ").append(ts(u.l("criado"))).append(" · último acesso ").append(ts(u.l("ultimo_login"))).append("</p>")
            .append(u.s("perfil").equals("DENTISTA") ? "<p class=\"mut\">📍 " + (u.has("lat") ? h(u.s("lat") + ", " + u.s("lng")) + " · raio " + u.i("raio_km") + " km" : "localização não informada")
                + " · 🗓 " + (u.has("disponibilidade") ? u.s("disponibilidade").split(",").length + " turno(s) por semana" : "disponibilidade não informada")
                + (u.has("faculdade") ? " · 🎓 " + h(u.s("faculdade") + " " + u.s("semestre")) : "") + "</p>" : "")
            .append("<form method=\"post\" action=\"/admin/usuario/salvar\">").append(r.csrf()).append("<input type=\"hidden\" name=\"id\" value=\"").append(h(id)).append("\">")
            .append(input("Nome", "nome", u.s("nome"), "text", "required"))
            .append("<div class=\"row\">").append(input("E-mail (login)", "email", u.s("email"), "email", "required")).append(input("CNPJ / CRO / matrícula", "documento", u.s("documento"))).append("</div>")
            .append("<div class=\"row\">").append(input("Celular", "telefone", u.s("telefone"))).append(input("Cidade / UF", "cidade", u.s("cidade"))).append("</div>");
        if (u.s("perfil").equals("CLINICA")) b.append(input("Endereço", "endereco", u.s("endereco"))).append(input("Cadeiras", "cadeiras", Math.max(1, u.i("cadeiras")), "number", "min=\"1\""));
        if (u.s("perfil").equals("DENTISTA")) b.append(input("Especialidades (separadas por ;)", "especialidades", u.s("especialidades")));
        b.append(area("Anotações internas (visíveis só para administradores)", "notas_admin", u.s("notas_admin"), 3))
            .append("<button class=\"btn mt\">Salvar dados</button></form>")
            .append("<p class=\"mut\">🔒 A senha deste usuário não pode ser visualizada. Use \"Gerar senha temporária\" para redefinir o acesso.</p></div><div>");
        if (!admin) {
            Row s = Svc.assinatura(id);
            if (s == null) { Svc.criarAssinatura(id, u.s("perfil")); s = Svc.assinatura(id); }
            b.append("<div class=\"card\"><h2>Assinatura</h2><p>").append(Svc.stBadge(s.s("status"))).append("</p><form method=\"post\" action=\"/admin/assinatura/salvar\">")
                .append(r.csrf()).append("<input type=\"hidden\" name=\"id\" value=\"").append(h(s.s("id"))).append("\">")
                .append("<div class=\"row\">").append(input("Plano", "plano", s.s("plano"))).append(input("Mensalidade (R$)", "valor", inputNum(s.d("valor")), "number", "min=\"0\" step=\"0.01\"")).append("</div>")
                .append("<div class=\"row\">").append(select("Situação", "status", Svc.ST_ASSINATURA, s.s("status"), "")).append(input("Teste até", "teste_ate", s.s("teste_ate"), "date", "")).append("</div>")
                .append("<p class=\"mut\">Suspensa/Cancelada: o usuário só acessa a área \"Meu plano\".</p><button class=\"btn mt\">Atualizar assinatura</button></form></div>");
            List<String[]> oc = new ArrayList<>();
            for (Row o : Db.q("SELECT * FROM ocorrencias WHERE usuario_id=? ORDER BY criado DESC", id))
                oc.add(new String[]{ts(o.l("criado")), h(Svc.label(Svc.TIPOS_OCORRENCIA, o.s("tipo"))), o.b("anulada") ? badge("Anulada", "b-m") : o.b("strike") ? badge("Strike", "b-e") : badge("Registro", "b-w"),
                    h(o.s("detalhe")), o.b("strike") && !o.b("anulada") ? post(r, "/admin/ocorrencia/anular", "Anular", "sm gh", "Anular este strike?", "id", o.s("id")) : ""});
            b.append("<div class=\"card\"><h2>Cancelamentos e strikes (").append(u.i("strikes")).append(" de ").append(Svc.strikesBloqueio()).append(")</h2>")
                .append(table(new String[]{"Data", "Tipo", "Efeito", "Detalhe", ""}, oc, "Nenhuma ocorrência."))
                .append("<form method=\"post\" action=\"/admin/ocorrencia/strike\" class=\"tools mt\">").append(r.csrf()).append("<input type=\"hidden\" name=\"usuario\" value=\"").append(h(id))
                .append("\"><input name=\"motivo\" placeholder=\"Motivo do strike manual\" required style=\"flex:1\"><button class=\"btn sm red\">Aplicar strike</button></form></div>");
            List<String[]> fr = new ArrayList<>();
            for (Row f : Db.q("SELECT * FROM faturas WHERE usuario_id=? ORDER BY vencimento DESC", id))
                fr.add(new String[]{h(f.s("descricao")), money(f.d("valor")), d(f.s("vencimento")), faturaBadge(f), faturaAcoes(r, f)});
            b.append("<div class=\"card\"><h2>Faturas</h2>").append(table(new String[]{"Descrição", "Valor", "Vencimento", "Situação", ""}, fr, "Sem faturas.")).append("</div>");
            boolean cl = u.s("perfil").equals("CLINICA");
            long nOpp = cl ? Db.count("SELECT COUNT(*) FROM oportunidades WHERE clinica_id=?", id) : Db.count("SELECT COUNT(*) FROM negociacoes WHERE dentista_id=?", id);
            long nFech = cl ? Db.count("SELECT COUNT(*) FROM oportunidades WHERE clinica_id=? AND status='FECHADA'", id) : Db.count("SELECT COUNT(*) FROM oportunidades WHERE dentista_id=? AND status='FECHADA'", id);
            b.append("<div class=\"card\"><h2>Atividade</h2><div class=\"grid g2\">").append(kpi(cl ? "Turnos publicados" : "Negociações", String.valueOf(nOpp), "📣"))
                .append(kpi("Turnos fechados", String.valueOf(nFech), "🤝")).append("</div><p>Reputação: ").append(Svc.stars(id)).append("</p></div>");
        } else b.append("<div class=\"card\"><h2>Administrador</h2><p class=\"mut\">Administradores não possuem assinatura.</p></div>");
        b.append("</div></div>");
        r.html(page(r, "Gerenciar usuário", b.toString()));
    }

    static void salvarUsuario(Req r) throws Exception {
        Row u = needUser(r);
        String email = r.req("email", "E-mail").toLowerCase();
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new Bad("E-mail inválido.");
        if (Db.count("SELECT COUNT(*) FROM usuarios WHERE email=? AND id<>?", email, u.s("id")) > 0) throw new Bad("E-mail já usado por outro usuário.");
        Db.x("UPDATE usuarios SET nome=?, email=?, documento=?, telefone=?, cidade=?, endereco=?, cadeiras=?, especialidades=?, notas_admin=? WHERE id=?",
            r.req("nome", "Nome"), email, r.p("documento"), r.p("telefone"), r.p("cidade"),
            r.p("endereco").isEmpty() ? u.s("endereco") : r.p("endereco"), Math.max(1, r.intp("cadeiras", Math.max(1, u.i("cadeiras")))),
            r.p("especialidades").isEmpty() ? u.s("especialidades") : r.p("especialidades"), r.p("notas_admin"), u.s("id"));
        Svc.audit(r, "USUARIO_EDITADO", u.s("email") + (email.equals(u.s("email")) ? "" : " → " + email));
        r.ok("Dados salvos.");
        r.redirect("/admin/usuario?id=" + u.s("id"));
    }

    static void verificar(Req r) throws Exception {
        Row u = needUser(r);
        Db.x("UPDATE usuarios SET verificado=TRUE WHERE id=?", u.s("id"));
        Svc.notifyMail(u.s("id"), "✅ Seu cadastro foi verificado e aprovado! Você já pode " + (u.s("perfil").equals("CLINICA") ? "publicar turnos." : "enviar propostas."), App.home(u.s("perfil")),
            "Cadastro aprovado — " + Svc.appName(), "Seu registro foi verificado pela nossa equipe e sua conta está liberada.\n\nAgora você já pode "
                + (u.s("perfil").equals("CLINICA") ? "publicar turnos e encontrar profissionais." : "enviar propostas para os turnos disponíveis."), "APROVACAO:" + u.s("id"));
        Svc.audit(r, "CREDENCIAMENTO_APROVADO", u.s("email"));
        r.ok(u.s("nome") + " verificado(a) e aprovado(a).");
        r.redirect(r.back("/admin/usuarios"));
    }

    static void bloquear(Req r) throws Exception {
        Row u = needUser(r);
        if (u.s("id").equals(r.uid())) throw new Bad("Você não pode bloquear a si mesmo.");
        boolean novo = !u.b("ativo");
        if (!novo && u.s("perfil").equals("ADMIN") && Db.count("SELECT COUNT(*) FROM usuarios WHERE perfil='ADMIN' AND ativo=TRUE") <= 1) throw new Bad("Não é possível bloquear o último administrador.");
        Db.x("UPDATE usuarios SET ativo=? WHERE id=?", novo, u.s("id"));
        if (!novo) Sec.logoutUser(u.s("id"));
        Svc.audit(r, novo ? "USUARIO_DESBLOQUEADO" : "USUARIO_BLOQUEADO", u.s("email"));
        r.ok(novo ? "Acesso liberado." + (u.i("strikes") >= Svc.strikesBloqueio() ? " Atenção: o usuário ainda tem " + u.i("strikes") + " strikes; anule algum para evitar novo bloqueio no próximo strike." : "") : "Acesso bloqueado.");
        r.redirect("/admin/usuario?id=" + u.s("id"));
    }

    static void resetSenha(Req r) throws Exception {
        Row u = needUser(r);
        String temp = Sec.tempPassword();
        Db.x("UPDATE usuarios SET senha_hash=?, trocar_senha=TRUE, tentativas=0, bloqueado_ate=0 WHERE id=?", Sec.hash(temp), u.s("id"));
        Sec.logoutUser(u.s("id"));
        Svc.audit(r, "SENHA_REDEFINIDA_ADMIN", u.s("email"));
        r.html(page(r, "Senha temporária", senhaTemp(u.s("nome"), u.s("email"), temp, u.s("id"))));
    }

    static void salvarAssinatura(Req r) throws Exception {
        Row s = Db.one("SELECT * FROM assinaturas WHERE id=?", r.p("id"));
        if (s == null) throw new Bad("Assinatura não encontrada.");
        String st = r.p("status");
        if (Svc.label(Svc.ST_ASSINATURA, st).equals(st)) throw new Bad("Situação inválida.");
        String teste = r.p("teste_ate");
        if (!teste.isEmpty() && !Svc.isDate(teste)) throw new Bad("Data de teste inválida.");
        BigDecimal valor = r.num("valor");
        if (valor.signum() < 0) throw new Bad("Valor inválido.");
        Db.x("UPDATE assinaturas SET plano=?, valor=?, status=?, teste_ate=?, atualizado=? WHERE id=?", r.req("plano", "Plano"), valor, st, teste, System.currentTimeMillis(), s.s("id"));
        if (!st.equals(s.s("status"))) Svc.notify(s.s("usuario_id"), "Situação da sua assinatura: " + Svc.label(Svc.ST_ASSINATURA, st) + ".", "/conta/plano");
        Svc.audit(r, "ASSINATURA_ALTERADA", Svc.nome(s.s("usuario_id")) + ": " + s.s("status") + " → " + st + ", " + money(valor));
        r.ok("Assinatura atualizada.");
        r.redirect(r.back("/admin/assinaturas"));
    }

    // ================= ASSINATURAS =================
    static void assinaturas(Req r) throws Exception {
        String st = r.p("status");
        List<String[]> rows = new ArrayList<>();
        Map<String, BigDecimal> porSt = new LinkedHashMap<>();
        Map<String, Integer> qtd = new LinkedHashMap<>();
        for (String[] s : Svc.ST_ASSINATURA) { porSt.put(s[0], BigDecimal.ZERO); qtd.put(s[0], 0); }
        for (Row s : Db.q("SELECT a.*, u.nome, u.email, u.perfil, u.categoria FROM assinaturas a JOIN usuarios u ON u.id=a.usuario_id ORDER BY u.nome")) {
            porSt.merge(s.s("status"), s.d("valor"), BigDecimal::add);
            qtd.merge(s.s("status"), 1, Integer::sum);
            if (!st.isEmpty() && !st.equals(s.s("status"))) continue;
            long abertas = Db.count("SELECT COUNT(*) FROM faturas WHERE assinatura_id=? AND status='ABERTA'", s.s("id"));
            rows.add(new String[]{"<b>" + h(s.s("nome")) + "</b><div class=\"mut\">" + h(s.s("email")) + "</div>", perfilBadge(s), h(s.s("plano")), money(s.d("valor")),
                Svc.stBadge(s.s("status")), s.s("status").equals("TESTE") ? d(s.s("teste_ate")) : "—", abertas > 0 ? badge(abertas + " em aberto", "b-w") : "—",
                "<a class=\"btn sm sec\" href=\"/admin/usuario?id=" + s.s("usuario_id") + "\">Gerenciar</a>"});
        }
        StringBuilder k = new StringBuilder("<div class=\"grid g4\">");
        k.append(kpi("MRR", money(porSt.get("ATIVA").add(porSt.get("INADIMPLENTE"))), "📈")).append(kpi("Ativas", String.valueOf(qtd.get("ATIVA")), "✅"))
            .append(kpi("Em teste", String.valueOf(qtd.get("TESTE")), "🎁")).append(kpi("Inadimplentes / suspensas", qtd.get("INADIMPLENTE") + " / " + qtd.get("SUSPENSA"), "⚠️")).append("</div>");
        String body = k + "<div class=\"card mt\"><div class=\"bar\"><form method=\"get\" action=\"/admin/assinaturas\" class=\"tools\">" + select("Situação", "status", withAll("Todas", Svc.ST_ASSINATURA), st, "onchange=\"this.form.submit()\"")
            + "</form><div class=\"tools\">" + post(r, "/admin/inadimplencia", "Atualizar inadimplência", "sm gh", null)
            + "<a class=\"btn sm acc\" href=\"/admin/faturas\">Gerar / ver faturas</a></div></div>"
            + table(new String[]{"Cliente", "Perfil", "Plano", "Mensalidade", "Situação", "Teste até", "Faturas", ""}, rows, "Nenhuma assinatura.") + "</div>"
            + "<p class=\"mut\">Preços padrão para novos cadastros: clínica " + money(Svc.cfgNum("preco_clinica", "0")) + " · profissional " + money(Svc.cfgNum("preco_dentista", "0"))
            + " — altere em <a href=\"/admin/config\">Configurações</a>.</p>";
        r.html(page(r, "Assinaturas", body));
    }

    // ================= FATURAS =================
    static String faturaBadge(Row f) {
        String st = f.s("status");
        if (st.equals("PAGA")) return badge("Paga " + d(f.s("pago_em")), "b-ok");
        if (st.equals("CANCELADA")) return badge("Cancelada", "b-m");
        String b = f.s("vencimento").compareTo(today()) < 0 ? badge("Vencida", "b-e") : badge("Em aberto", "b-w");
        return f.l("informado_em") > 0 ? b + " " + badge("Pagamento informado", "b-p") : b;
    }

    static String faturaAcoes(Req r, Row f) {
        if (!f.s("status").equals("ABERTA")) return "";
        return "<form method=\"post\" action=\"/admin/fatura/pagar\" class=\"inl\">" + r.csrf() + "<input type=\"hidden\" name=\"id\" value=\"" + h(f.s("id")) + "\">"
            + "<select name=\"metodo\" style=\"width:auto;display:inline-block;padding:6px\"><option>PIX</option><option>Boleto</option><option>Cartão</option><option>Transferência</option><option>Dinheiro</option></select> "
            + "<input type=\"date\" name=\"data\" value=\"" + today() + "\" style=\"width:auto;display:inline-block;padding:6px\"> <button class=\"btn sm ok\">Baixar</button></form> "
            + post(r, "/admin/fatura/cancelar", "Cancelar", "sm red", "Cancelar esta fatura?", "id", f.s("id"));
    }

    static void faturas(Req r) throws Exception {
        String st = r.p("status"), comp = r.p("competencia");
        List<String[]> rows = new ArrayList<>();
        BigDecimal tot = BigDecimal.ZERO;
        for (Row f : Db.q("SELECT f.*, u.nome, u.email FROM faturas f JOIN usuarios u ON u.id=f.usuario_id ORDER BY f.vencimento DESC, u.nome")) {
            boolean venc = f.s("status").equals("ABERTA") && f.s("vencimento").compareTo(today()) < 0;
            if (st.equals("VENCIDA") && !venc) continue;
            if (st.equals("INFORMADA") && !(f.s("status").equals("ABERTA") && f.l("informado_em") > 0)) continue;
            if (List.of("ABERTA", "PAGA", "CANCELADA").contains(st) && !f.s("status").equals(st)) continue;
            if (!comp.isEmpty() && !f.s("competencia").equals(comp)) continue;
            tot = tot.add(f.d("valor"));
            rows.add(new String[]{"<b>" + h(f.s("nome")) + "</b><div class=\"mut\">" + h(f.s("email")) + "</div>", h(f.s("descricao")), money(f.d("valor")),
                d(f.s("vencimento")), faturaBadge(f), faturaAcoes(r, f)});
        }
        String body = "<div class=\"card\"><h2>Gerar faturas mensais</h2><form method=\"post\" action=\"/admin/faturas/gerar\" class=\"tools\">" + r.csrf()
            + input("Competência", "competencia", month(), "month", "required") + "<button class=\"btn acc\">Gerar faturas da competência</button></form>"
            + "<p class=\"mut\">Gera uma fatura para cada assinatura cobrável (não duplica). Vencimento no dia " + h(Svc.cfg("dia_vencimento", "10")) + ". Ao dar baixa, a receita entra no financeiro.</p></div>"
            + "<div class=\"card\"><div class=\"bar\"><form method=\"get\" action=\"/admin/faturas\" class=\"tools\">"
            + select("Situação", "status", new String[][]{{"", "Todas"}, {"ABERTA", "Em aberto"}, {"VENCIDA", "Vencidas"}, {"INFORMADA", "Pagamento informado"}, {"PAGA", "Pagas"}, {"CANCELADA", "Canceladas"}}, st, "")
            + input("Competência", "competencia", comp, "month", "") + "<button class=\"btn sm\">Filtrar</button></form><b>Total: " + money(tot) + "</b></div>"
            + table(new String[]{"Cliente", "Descrição", "Valor", "Vencimento", "Situação", "Ações"}, rows, "Nenhuma fatura encontrada.") + "</div>";
        r.html(page(r, "Faturas", body));
    }

    static void gerarFaturas(Req r) throws Exception {
        String c = r.p("competencia");
        if (!c.matches("\\d{4}-\\d{2}")) throw new Bad("Competência inválida.");
        int n = Svc.gerarFaturas(r, c);
        r.ok(n + " fatura(s) gerada(s) para " + monthName(c) + ".");
        r.redirect("/admin/faturas?competencia=" + c);
    }

    static void pagarFatura(Req r) throws Exception {
        String data = r.p("data").isEmpty() ? today() : r.p("data");
        if (!Svc.isDate(data)) throw new Bad("Data inválida.");
        Svc.pagarFatura(r, r.p("id"), r.p("metodo").isEmpty() ? "PIX" : r.p("metodo"), data);
        r.ok("Pagamento registrado e lançado no financeiro.");
        r.redirect(r.back("/admin/faturas"));
    }

    static void cancelarFatura(Req r) throws Exception {
        Row f = Db.one("SELECT * FROM faturas WHERE id=?", r.p("id"));
        if (f == null || !f.s("status").equals("ABERTA")) throw new Bad("Fatura não pode ser cancelada.");
        Db.x("UPDATE faturas SET status='CANCELADA' WHERE id=?", f.s("id"));
        Svc.audit(r, "FATURA_CANCELADA", f.s("descricao") + " · " + Svc.nome(f.s("usuario_id")));
        r.ok("Fatura cancelada.");
        r.redirect(r.back("/admin/faturas"));
    }

    // ================= FINANCEIRO =================
    static void financeiro(Req r) throws Exception {
        String mes = r.p("mes").matches("\\d{4}-\\d{2}") ? r.p("mes") : month();
        String tipo = r.p("tipo");
        List<Row> l = Db.q("SELECT * FROM lancamentos WHERE data>=? AND data<=? ORDER BY data DESC, criado DESC", mes + "-01", mes + "-31");
        BigDecimal rp = BigDecimal.ZERO, ra = BigDecimal.ZERO, dp = BigDecimal.ZERO, da = BigDecimal.ZERO;
        Map<String, BigDecimal> cat = new TreeMap<>();
        List<String[]> rows = new ArrayList<>();
        for (Row x : l) {
            boolean rec = x.s("tipo").equals("RECEITA");
            if (rec) { if (x.b("pago")) rp = rp.add(x.d("valor")); else ra = ra.add(x.d("valor")); }
            else { if (x.b("pago")) dp = dp.add(x.d("valor")); else da = da.add(x.d("valor")); }
            cat.merge((rec ? "↑ " : "↓ ") + x.s("categoria"), x.d("valor"), BigDecimal::add);
            if (!tipo.isEmpty() && !x.s("tipo").equals(tipo)) continue;
            String acoes = post(r, "/admin/lancamento/pago", x.b("pago") ? "Marcar pendente" : (rec ? "Marcar recebido" : "Marcar pago"), "sm gh", null, "id", x.s("id"))
                + (x.b("automatico") ? "" : " " + post(r, "/admin/lancamento/excluir", "Excluir", "sm red", "Excluir este lançamento?", "id", x.s("id")));
            rows.add(new String[]{d(x.s("data")), rec ? badge("Receita", "b-ok") : badge("Despesa", "b-e"), h(x.s("categoria")),
                h(x.s("descricao")) + (x.has("usuario_id") ? "<div class=\"mut\">" + h(Svc.nome(x.s("usuario_id"))) + "</div>" : "") + (x.b("automatico") ? " " + badge("auto", "b-m") : ""),
                "<b style=\"color:" + (rec ? "#16a34a" : "#dc2626") + "\">" + (rec ? "+ " : "− ") + money(x.d("valor")) + "</b>",
                x.b("pago") ? badge(rec ? "Recebido" : "Pago", "b-ok") : badge("Pendente", "b-w"), acoes});
        }
        StringBuilder catT = new StringBuilder();
        for (var e : cat.entrySet()) catT.append("<div class=\"bar\" style=\"margin:0;padding:6px 0;border-bottom:1px solid var(--bd)\"><span>").append(h(e.getKey())).append("</span><b>").append(money(e.getValue())).append("</b></div>");
        String[][] cats = new String[Svc.CATEG_RECEITA.length + Svc.CATEG_DESPESA.length][];
        int i = 0;
        for (String c : Svc.CATEG_RECEITA) cats[i++] = new String[]{"RECEITA|" + c, "Receita · " + c};
        for (String c : Svc.CATEG_DESPESA) cats[i++] = new String[]{"DESPESA|" + c, "Despesa · " + c};
        BigDecimal aReceberFat = sum("SELECT valor FROM faturas WHERE status='ABERTA'");
        String body = "<div class=\"card\"><form method=\"get\" action=\"/admin/financeiro\" class=\"tools\">" + input("Mês", "mes", mes, "month", "")
            + select("Tipo", "tipo", new String[][]{{"", "Receitas e despesas"}, {"RECEITA", "Somente receitas"}, {"DESPESA", "Somente despesas"}}, tipo, "")
            + "<button class=\"btn sm\">Aplicar</button><a class=\"btn sm gh\" href=\"/admin/financeiro/csv?mes=" + mes + "\">⬇ Exportar CSV</a></form></div>"
            + "<div class=\"grid g4\">" + kpi("Receitas recebidas", money(rp), "💵") + kpi("Despesas pagas", money(dp), "📤")
            + kpi("Resultado (caixa)", "<span style=\"color:" + (rp.compareTo(dp) >= 0 ? "#16a34a" : "#dc2626") + "\">" + money(rp.subtract(dp)) + "</span>", "📊")
            + kpi("Previsto (pendentes)", "+" + money(ra) + " / −" + money(da), "🗓️") + "</div>"
            + "<div class=\"grid g2 mt\"><div class=\"card\"><h2>Novo lançamento</h2><form method=\"post\" action=\"/admin/lancamento/criar\">" + r.csrf()
            + select("Categoria", "categoria", cats, "DESPESA|Infraestrutura/Hospedagem", "")
            + input("Descrição", "descricao", "", "text", "required maxlength=\"300\"")
            + "<div class=\"row\">" + input("Valor (R$)", "valor", "", "number", "required min=\"0.01\" step=\"0.01\"") + input("Data", "data", today(), "date", "required") + "</div>"
            + input("Referência / NF (opcional)", "referencia", "", "text", "maxlength=\"100\"")
            + check("Já foi pago/recebido", "pago", true) + "<button class=\"btn mt\">Lançar</button></form></div>"
            + "<div class=\"card\"><h2>Resumo por categoria — " + monthName(mes) + "</h2>" + (catT.length() == 0 ? empty("Sem lançamentos no mês.") : catT)
            + "<p class=\"mut mt\">Faturas de assinatura em aberto: <b>" + money(aReceberFat) + "</b> — <a href=\"/admin/faturas?status=ABERTA\">ver</a></p></div></div>"
            + "<div class=\"card\"><h2>Livro-caixa — " + monthName(mes) + "</h2>" + table(new String[]{"Data", "Tipo", "Categoria", "Descrição", "Valor", "Situação", "Ações"}, rows, "Nenhum lançamento neste mês.") + "</div>";
        r.html(page(r, "Financeiro da plataforma", body));
    }

    static void criarLancamento(Req r) throws Exception {
        String[] c = r.p("categoria").split("\\|", 2);
        if (c.length != 2 || !(c[0].equals("RECEITA") || c[0].equals("DESPESA"))) throw new Bad("Categoria inválida.");
        BigDecimal v = r.num("valor");
        if (v.signum() <= 0) throw new Bad("Informe um valor maior que zero.");
        String data = r.req("data", "Data");
        if (!Svc.isDate(data)) throw new Bad("Data inválida.");
        Db.insert("lancamentos", Db.m("tipo", c[0], "categoria", c[1], "descricao", r.req("descricao", "Descrição"), "valor", v, "data", data, "pago", r.chk("pago"),
            "referencia", r.p("referencia"), "automatico", false));
        Svc.audit(r, "LANCAMENTO_CRIADO", c[0] + " " + c[1] + " " + money(v));
        r.ok("Lançamento registrado.");
        r.redirect("/admin/financeiro?mes=" + data.substring(0, 7));
    }

    static void togglePago(Req r) throws Exception {
        Row x = Db.one("SELECT * FROM lancamentos WHERE id=?", r.p("id"));
        if (x == null) throw new Bad("Lançamento não encontrado.");
        Db.x("UPDATE lancamentos SET pago=? WHERE id=?", !x.b("pago"), x.s("id"));
        Svc.audit(r, "LANCAMENTO_SITUACAO", x.s("descricao") + " → " + (!x.b("pago") ? "liquidado" : "pendente"));
        r.redirect(r.back("/admin/financeiro"));
    }

    static void excluirLancamento(Req r) throws Exception {
        Row x = Db.one("SELECT * FROM lancamentos WHERE id=?", r.p("id"));
        if (x == null) throw new Bad("Lançamento não encontrado.");
        if (x.b("automatico")) throw new Bad("Lançamentos automáticos (de faturas) não podem ser excluídos.");
        Db.x("DELETE FROM lancamentos WHERE id=?", x.s("id"));
        Svc.audit(r, "LANCAMENTO_EXCLUIDO", x.s("tipo") + " " + x.s("descricao") + " " + money(x.d("valor")));
        r.ok("Lançamento excluído.");
        r.redirect(r.back("/admin/financeiro"));
    }

    static void csv(Req r) throws Exception {
        String mes = r.p("mes").matches("\\d{4}-\\d{2}") ? r.p("mes") : month();
        StringBuilder s = new StringBuilder("\ufeffData;Tipo;Categoria;Descrição;Cliente;Valor;Situação;Referência\n");
        for (Row x : Db.q("SELECT * FROM lancamentos WHERE data>=? AND data<=? ORDER BY data", mes + "-01", mes + "-31")) {
            s.append(d(x.s("data"))).append(';').append(x.s("tipo")).append(';').append(c(x.s("categoria"))).append(';').append(c(x.s("descricao"))).append(';')
                .append(c(x.has("usuario_id") ? Svc.nome(x.s("usuario_id")) : "")).append(';').append(x.d("valor").toPlainString().replace('.', ',')).append(';')
                .append(x.b("pago") ? "Liquidado" : "Pendente").append(';').append(c(x.s("referencia"))).append('\n');
        }
        Svc.audit(r, "FINANCEIRO_EXPORTADO", mes);
        r.download("financeiro_" + mes + ".csv", "text/csv; charset=utf-8", s.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String c(String v) { return "\"" + v.replace("\"", "\"\"") + "\""; }

    // ================= MARKETPLACE =================
    static void marketplace(Req r) throws Exception {
        String st = r.p("status");
        List<String[]> rows = new ArrayList<>();
        for (Row o : Db.q("SELECT * FROM oportunidades ORDER BY data DESC")) {
            if (!st.isEmpty() && !o.s("status").equals(st)) continue;
            boolean fech = o.s("status").equals("FECHADA");
            Svc.T t = fech ? Svc.T.of(o, "f_") : Svc.T.of(o, "");
            Svc.Calc c = fech ? Svc.settle(o) : Svc.calc(t, Svc.oppAppts(o));
            long negs = Db.count("SELECT COUNT(*) FROM negociacoes WHERE oportunidade_id=?", o.s("id"));
            rows.add(new String[]{dw(o.s("data")) + "<div class=\"mut\">" + o.s("hora_ini") + "–" + o.s("hora_fim") + "</div>", "<b>" + h(o.s("titulo")) + "</b><div class=\"mut\">"
                + h(Svc.label(Svc.CATEGORIAS, Svc.catOpp(o))) + (Svc.catOpp(o).equals("DENTISTA") ? " · " + h(o.s("especialidade")) : "") + "</div>",
                h(Svc.nome(o.s("clinica_id"))), o.has("dentista_id") ? h(Svc.nome(o.s("dentista_id"))) : "—", h(Svc.termsText(t)), String.valueOf(negs), money(c.prod()), money(c.rep()),
                Svc.oppBadge(o) + (o.has("motivo_cancel") ? "<div class=\"mut\">" + h(o.s("motivo_cancel")) + "</div>" : "")});
        }
        List<String[]> res = new ArrayList<>();
        for (Row x : Db.q("SELECT r.*, s.titulo FROM reservas r JOIN salas s ON s.id=r.sala_id ORDER BY r.criado DESC")) {
            if (res.size() >= 100) break;
            res.add(new String[]{dw(x.s("data")) + " " + x.s("hora_ini") + "–" + x.s("hora_fim"), h(x.s("titulo")), h(Svc.nome(x.s("clinica_id"))), h(Svc.nome(x.s("profissional_id"))), money(x.d("valor")), badge(x.s("status"), x.s("status").equals("ACEITA") ? "b-ok" : x.s("status").equals("PENDENTE") ? "b-w" : "b-m")});
        }
        long negAb = Db.count("SELECT COUNT(*) FROM negociacoes WHERE status='ABERTA'");
        long fech = Db.count("SELECT COUNT(*) FROM oportunidades WHERE status='FECHADA'");
        long total = Db.count("SELECT COUNT(*) FROM oportunidades");
        String conv = total == 0 ? "0%" : (fech * 100 / total) + "%";
        String body = "<div class=\"grid g4\">" + kpi("Turnos publicados", String.valueOf(total), "📣") + kpi("Negociações em andamento", String.valueOf(negAb), "💬")
            + kpi("Turnos fechados", String.valueOf(fech), "🤝") + kpi("Taxa de conversão", conv, "🎯") + "</div>"
            + "<div class=\"card mt\"><form method=\"get\" action=\"/admin/marketplace\" class=\"tools\">"
            + select("Situação", "status", new String[][]{{"", "Todas"}, {"ABERTA", "Abertas"}, {"FECHADA", "Fechadas"}, {"CANCELADA", "Canceladas"}}, st, "onchange=\"this.form.submit()\"")
            + "</form>" + table(new String[]{"Data", "Turno", "Clínica", "Profissional", "Termos", "Propostas", "Produção", "Repasse", "Situação"}, rows, "Nenhum turno.") + "</div>"
            + negociacoesAdmin()
            + "<div class=\"card\"><h2>Reservas de salas</h2>" + table(new String[]{"Data", "Sala", "Clínica", "Profissional", "Valor", "Situação"}, res, "Nenhuma reserva.") + "</div>"
            + "<p class=\"mut\">🔒 O administrador acompanha a operação, mas não acessa prontuários de pacientes nem senhas.</p>";
        r.html(page(r, "Marketplace", body));
    }

    static String negociacoesAdmin() {
        List<String[]> rows = new ArrayList<>();
        for (Row n : Db.q("SELECT n.*, o.titulo, o.clinica_id, o.status AS ostatus FROM negociacoes n JOIN oportunidades o ON o.id=n.oportunidade_id ORDER BY n.atualizado DESC")) {
            if (rows.size() >= 200) break;
            Row lo = Svc.lastOffer(n.s("id"));
            String vez = !n.s("status").equals("ABERTA") || !n.s("ostatus").equals("ABERTA") ? "—"
                : lo == null ? "—" : lo.s("autor").equals("DENTISTA") ? badge("Clínica responder", "b-w") : badge("Profissional responder", "b-p");
            String st = switch (n.s("status")) {
                case "FECHADA" -> badge("Fechada", "b-ok");
                case "CANCELADA" -> badge("Turno cancelado", "b-e");
                case "ABERTA" -> n.s("ostatus").equals("ABERTA") ? badge("Em andamento", "b-p") : badge("Encerrada", "b-m");
                default -> badge("Encerrada", "b-m");
            };
            rows.add(new String[]{ts(n.l("atualizado") > 0 ? n.l("atualizado") : n.l("criado")), h(n.s("titulo")), h(Svc.nome(n.s("clinica_id"))), h(Svc.nome(n.s("dentista_id"))),
                lo == null ? "—" : h(Svc.termsText(Svc.T.of(lo, ""))) + "<div class=\"mut\">por " + (lo.s("autor").equals("DENTISTA") ? "profissional" : "clínica") + "</div>", vez, st});
        }
        return "<div class=\"card\"><h2>Negociações e propostas</h2><p class=\"mut\">Todas as propostas enviadas pelos profissionais e as respostas das clínicas.</p>"
            + table(new String[]{"Atualização", "Turno", "Clínica", "Profissional", "Última oferta", "Próximo passo", "Situação"}, rows, "Nenhuma proposta enviada ainda.") + "</div>";
    }

    // ================= CANCELAMENTOS E STRIKES =================
    static void ocorrencias(Req r) throws Exception {
        String tipo = r.p("tipo");
        List<String[]> rows = new ArrayList<>();
        for (Row o : Db.q("SELECT o.*, u.nome, u.strikes, u.ativo FROM ocorrencias o JOIN usuarios u ON u.id=o.usuario_id ORDER BY o.criado DESC")) {
            if (!tipo.isEmpty() && !o.s("tipo").equals(tipo)) continue;
            if (rows.size() >= 300) break;
            rows.add(new String[]{ts(o.l("criado")), "<a href=\"/admin/usuario?id=" + o.s("usuario_id") + "\"><b>" + h(o.s("nome")) + "</b></a><div class=\"mut\">" + o.i("strikes") + " strike(s)"
                + (o.b("ativo") ? "" : " · bloqueado") + "</div>", h(Svc.label(Svc.TIPOS_OCORRENCIA, o.s("tipo"))),
                o.b("anulada") ? badge("Anulada", "b-m") : o.b("strike") ? badge("Strike", "b-e") : badge("Registro", "b-w"), h(o.s("detalhe")),
                o.b("anulada") ? "" : (o.b("strike") ? post(r, "/admin/ocorrencia/anular", "Anular strike", "sm gh", "Anular este strike?", "id", o.s("id"))
                    : o.s("tipo").equals("DENUNCIA") ? post(r, "/admin/ocorrencia/strike", "Converter em strike", "sm red", "Aplicar strike?", "usuario", o.s("usuario_id"), "motivo", "Denúncia procedente: " + o.s("detalhe"), "ocorrencia", o.s("id")) : "")});
        }
        String body = "<div class=\"card\"><h2>Política aplicada</h2><ul class=\"ck\"><li>Cancelamento com mais de 24h de antecedência: livre (apenas registrado).</li>"
            + "<li>Entre 24h e 6h: registrado como cancelamento tardio e impacta a reputação e o match.</li><li>Com menos de 6h ou não comparecimento (no-show): <b>strike</b>.</li>"
            + "<li>Ao atingir <b>" + Svc.strikesBloqueio() + " strikes</b> a conta é bloqueada automaticamente (altere em Configurações).</li></ul></div>"
            + "<div class=\"card\"><form method=\"get\" action=\"/admin/ocorrencias\" class=\"tools\">" + select("Tipo", "tipo", withAll("Todos os tipos", Svc.TIPOS_OCORRENCIA), tipo, "onchange=\"this.form.submit()\"")
            + "</form>" + table(new String[]{"Data", "Usuário", "Tipo", "Efeito", "Detalhe", ""}, rows, "Nenhuma ocorrência registrada.") + "</div>";
        r.html(page(r, "Cancelamentos e strikes", body));
    }

    static void anularOcorrencia(Req r) throws Exception {
        Row o = Db.one("SELECT * FROM ocorrencias WHERE id=?", r.p("id"));
        if (o == null || o.b("anulada") || !o.b("strike")) throw new Bad("Ocorrência inválida.");
        Row u = Svc.user(o.s("usuario_id"));
        Db.x("UPDATE ocorrencias SET anulada=TRUE WHERE id=?", o.s("id"));
        Db.x("UPDATE usuarios SET strikes=? WHERE id=?", Math.max(0, u.i("strikes") - 1), u.s("id"));
        Svc.notify(u.s("id"), "Um strike da sua conta foi anulado pela administração.", "/conta/perfil");
        Svc.audit(r, "STRIKE_ANULADO", u.s("email") + " · " + o.s("detalhe"));
        r.ok("Strike anulado." + (!u.b("ativo") ? " O usuário continua bloqueado; desbloqueie em Gerenciar, se for o caso." : ""));
        r.redirect(r.back("/admin/ocorrencias"));
    }

    static void strikeManual(Req r) throws Exception {
        Row u = needUserBy(r.p("usuario"));
        if (u.s("perfil").equals("ADMIN")) throw new Bad("Não se aplica a administradores.");
        if (!r.p("ocorrencia").isEmpty()) Db.x("UPDATE ocorrencias SET anulada=TRUE WHERE id=?", r.p("ocorrencia"));
        Svc.ocorrencia(r, u.s("id"), null, "STRIKE_MANUAL", true, r.req("motivo", "Motivo"));
        Svc.audit(r, "STRIKE_MANUAL", u.s("email") + " · " + r.p("motivo"));
        r.ok("Strike aplicado a " + u.s("nome") + ".");
        r.redirect(r.back("/admin/ocorrencias"));
    }

    static Row needUserBy(String id) {
        Row u = Svc.user(id);
        if (u == null) throw new Bad("Usuário não encontrado.");
        return u;
    }

    // ================= SUPORTE =================
    static void suporte(Req r) throws Exception {
        String st = r.p("status").isEmpty() ? "ABERTO" : r.p("status");
        StringBuilder b = new StringBuilder("<div class=\"tools\" style=\"margin-bottom:14px\">");
        for (String[] s : new String[][]{{"ABERTO", "Abertos"}, {"RESPONDIDO", "Respondidos"}, {"TODOS", "Todos"}})
            b.append("<a class=\"btn sm ").append(s[0].equals(st) ? "" : "gh").append("\" href=\"/admin/suporte?status=").append(s[0]).append("\">").append(s[1])
                .append(s[0].equals("ABERTO") ? " (" + Db.count("SELECT COUNT(*) FROM tickets WHERE status='ABERTO'") + ")" : "").append("</a>");
        b.append("</div>");
        List<Row> l = Db.q("SELECT * FROM tickets ORDER BY criado DESC");
        int n = 0;
        for (Row t : l) {
            if (!st.equals("TODOS") && !t.s("status").equals(st)) continue;
            n++;
            b.append("<div class=\"card\"><div class=\"bar\"><div><b>").append(h(t.s("assunto"))).append("</b> ").append(badge(t.s("categoria"), "b-m")).append("<div class=\"mut\">")
                .append(h(t.s("nome"))).append(" · ").append(h(t.s("email"))).append(t.has("usuario_id") ? " · <a href=\"/admin/usuario?id=" + t.s("usuario_id") + "\">ver cadastro</a>" : " · visitante")
                .append(" · ").append(ts(t.l("criado"))).append("</div></div>").append(t.s("status").equals("ABERTO") ? badge("Aberto", "b-w") : badge("Respondido", "b-ok")).append("</div>")
                .append("<p style=\"white-space:pre-wrap\">").append(h(t.s("mensagem"))).append("</p>");
            if (t.has("resposta")) b.append("<div class=\"msg me\" style=\"max-width:100%\"><b>Resposta:</b> <span style=\"white-space:pre-wrap\">").append(h(t.s("resposta"))).append("</span><div class=\"t\">").append(ts(t.l("respondido_em"))).append("</div></div>");
            b.append("<form method=\"post\" action=\"/admin/suporte/responder\">").append(r.csrf()).append("<input type=\"hidden\" name=\"id\" value=\"").append(t.s("id")).append("\">")
                .append(area(t.has("resposta") ? "Nova resposta" : "Responder", "resposta", "", 3)).append("<button class=\"btn sm mt\">Enviar resposta por e-mail e notificação</button></form></div>");
        }
        if (n == 0) b.append("<div class=\"card\">").append(empty("Nenhum chamado nesta situação.")).append("</div>");
        r.html(page(r, "Suporte", b.toString()));
    }

    static void responder(Req r) throws Exception {
        Row t = Db.one("SELECT * FROM tickets WHERE id=?", r.p("id"));
        if (t == null) throw new Bad("Chamado não encontrado.");
        String resp = r.req("resposta", "Resposta");
        Db.x("UPDATE tickets SET resposta=?, status='RESPONDIDO', respondido_em=? WHERE id=?", resp.length() > 2000 ? resp.substring(0, 2000) : resp, System.currentTimeMillis(), t.s("id"));
        if (t.has("usuario_id")) Svc.notify(t.s("usuario_id"), "💬 O suporte respondeu: \"" + t.s("assunto") + "\".", "/contato");
        Mail.queue(t.s("email"), t.s("nome"), "Re: " + t.s("assunto"), resp + "\n\n— Sua mensagem —\n" + t.s("mensagem"), "TICKET:" + t.s("id"));
        Svc.audit(r, "SUPORTE_RESPONDIDO", t.s("assunto"));
        r.ok("Resposta enviada.");
        r.redirect("/admin/suporte");
    }

    // ================= E-MAILS =================
    static void emails(Req r) throws Exception {
        List<String[]> rows = new ArrayList<>();
        Row ver = r.p("ver").isEmpty() ? null : Db.one("SELECT * FROM emails WHERE id=?", r.p("ver"));
        for (Row e : Db.q("SELECT * FROM emails ORDER BY criado DESC")) {
            if (rows.size() >= 300) break;
            String st = switch (e.s("status")) { case "ENVIADO" -> badge("Enviado", "b-ok"); case "ERRO" -> badge("Erro", "b-e"); case "PENDENTE" -> badge("Enviando", "b-p"); default -> badge("SMTP não configurado", "b-m"); };
            rows.add(new String[]{ts(e.l("criado")), h(e.s("para")), "<a href=\"/admin/emails?ver=" + e.s("id") + "\">" + h(e.s("assunto")) + "</a>", st + (e.has("erro") ? "<div class=\"mut\">" + h(e.s("erro")) + "</div>" : ""),
                Mail.configured() && !e.s("status").equals("ENVIADO") ? post(r, "/admin/emails/reenviar", "Reenviar", "sm gh", null, "id", e.s("id")) : ""});
        }
        String body = (Mail.configured() ? "<div class=\"flash ok\">SMTP configurado (" + h(Svc.cfg("smtp_host", "")) + "). Os e-mails são enviados automaticamente.</div>"
            : "<div class=\"banner\">📧 SMTP ainda não configurado: os e-mails ficam registrados aqui, mas não são enviados. Configure em <a href=\"/admin/config\">Configurações</a>.</div>")
            + (ver == null ? "" : "<div class=\"card\"><h2>" + h(ver.s("assunto")) + "</h2><p class=\"mut\">Para: " + h(ver.s("para")) + " · " + ts(ver.l("criado")) + "</p><pre class=\"mail\">" + h(ver.s("corpo")) + "</pre></div>")
            + "<div class=\"card\"><h2>Caixa de saída</h2>" + table(new String[]{"Data", "Para", "Assunto", "Situação", ""}, rows, "Nenhum e-mail gerado ainda.") + "</div>";
        r.html(page(r, "E-mails automáticos", body));
    }

    static void testarSmtp(Req r) throws Exception {
        String para = r.req("para", "E-mail de teste");
        try {
            Mail.testNow(para);
            r.ok("E-mail de teste enviado para " + para + ". Verifique a caixa de entrada (e o spam).");
        } catch (Exception e) {
            r.err("Falha no envio: " + e.getMessage());
        }
        r.redirect("/admin/config");
    }

    // ================= CONFIGURAÇÕES =================
    static void config(Req r) throws Exception {
        String body = "<div class=\"card\" style=\"max-width:860px\"><form method=\"post\" action=\"/admin/config\">" + r.csrf()
            + "<h4>Plataforma</h4><div class=\"row\">" + input("Nome do sistema", "app_nome", Svc.appName(), "text", "required maxlength=\"60\"")
            + input("E-mail de suporte", "suporte_email", Svc.cfg("suporte_email", ""), "email", "required") + "</div>"
            + input("Chave PIX para recebimento das mensalidades", "pix_chave", Svc.cfg("pix_chave", ""), "text", "")
            + "<h4>Planos e cobrança</h4><div class=\"row\">"
            + input("Mensalidade clínica (R$)", "preco_clinica", Svc.cfg("preco_clinica", "149.90"), "number", "min=\"0\" step=\"0.01\" required")
            + input("Mensalidade profissional (R$) — 0 = gratuito", "preco_dentista", Svc.cfg("preco_dentista", "0"), "number", "min=\"0\" step=\"0.01\" required") + "</div><div class=\"row\">"
            + input("Dias de teste grátis", "dias_teste", Svc.cfg("dias_teste", "30"), "number", "min=\"0\" max=\"365\" required")
            + input("Dia de vencimento", "dia_vencimento", Svc.cfg("dia_vencimento", "10"), "number", "min=\"1\" max=\"28\" required")
            + input("Comissão sobre repasses (%)", "comissao_pct", Svc.cfg("comissao_pct", "0"), "number", "min=\"0\" max=\"50\" step=\"0.01\"") + "</div>"
            + "<h4>Regras do marketplace</h4>" + check("Exigir verificação de CRO/CNPJ pelo administrador para negociar", "exigir_verificacao", Svc.exigeVerificacao())
            + input("Strikes para bloqueio automático", "strikes_bloqueio", String.valueOf(Svc.strikesBloqueio()), "number", "min=\"1\" max=\"20\"")
            + "<h4>E-mail (SMTP) para confirmações automáticas</h4><div class=\"row\">"
            + input("Servidor SMTP", "smtp_host", Svc.cfg("smtp_host", ""), "text", "placeholder=\"smtp.gmail.com / smtp.office365.com\"")
            + input("Porta", "smtp_port", Svc.cfg("smtp_port", ""), "number", "placeholder=\"587\"")
            + select("Segurança", "smtp_seg", new String[][]{{"STARTTLS", "STARTTLS (porta 587)"}, {"SSL", "SSL/TLS (porta 465)"}, {"NENHUMA", "Nenhuma (somente testes)"}}, Svc.cfg("smtp_seg", "STARTTLS"), "") + "</div><div class=\"row\">"
            + input("Usuário", "smtp_user", Svc.cfg("smtp_user", ""), "text", "autocomplete=\"off\"")
            + input("Senha (deixe em branco para manter)", "smtp_pass", "", "password", "autocomplete=\"new-password\" placeholder=\"" + (Svc.cfg("smtp_pass", "").isEmpty() ? "" : "•••••• (salva)") + "\"")
            + input("Remetente (From)", "smtp_from", Svc.cfg("smtp_from", ""), "email", "placeholder=\"nao-responda@seudominio.com.br\"") + "</div>"
            + "<p class=\"mut\">Gmail/Outlook exigem \"senha de app\". Diferente das senhas dos usuários, a senha do SMTP precisa ser reutilizável e fica gravada no banco local; use uma conta exclusiva para envio.</p>"
            + "<button class=\"btn mt\">Salvar configurações</button></form>"
            + "<form method=\"post\" action=\"/admin/emails/testar\" class=\"tools mt\">" + r.csrf() + "<input type=\"email\" name=\"para\" placeholder=\"Enviar e-mail de teste para...\" required style=\"flex:1\"><button class=\"btn sm sec\">📧 Testar SMTP</button></form></div>"
            + "<div class=\"card\" style=\"max-width:860px\"><h2>Banco de dados</h2><p>Arquivo local HSQLDB em <code>" + h(new java.io.File(System.getenv().getOrDefault("ODONTOMATCH_DATA", "data")).getAbsolutePath())
            + "</code>.</p><p class=\"mut\">Para backup, pare o sistema e copie a pasta <b>data</b>. Para recomeçar do zero, pare o sistema e apague essa pasta.</p></div>";
        r.html(page(r, "Configurações", body));
    }

    static void salvarConfig(Req r) throws Exception {
        BigDecimal pc = r.num("preco_clinica"), pd = r.num("preco_dentista"), com = r.num("comissao_pct");
        int dias = r.intp("dias_teste", -1), dia = r.intp("dia_vencimento", -1), sb = r.intp("strikes_bloqueio", 3);
        if (pc.signum() < 0 || pd.signum() < 0) throw new Bad("Preços inválidos.");
        if (dias < 0 || dias > 365) throw new Bad("Dias de teste devem estar entre 0 e 365.");
        if (dia < 1 || dia > 28) throw new Bad("Dia de vencimento deve estar entre 1 e 28.");
        if (com.signum() < 0 || com.compareTo(new BigDecimal(50)) > 0) throw new Bad("Comissão deve estar entre 0 e 50%.");
        if (sb < 1 || sb > 20) throw new Bad("Strikes para bloqueio: entre 1 e 20.");
        String porta = r.p("smtp_port");
        if (!porta.isEmpty() && !porta.matches("\\d{1,5}")) throw new Bad("Porta SMTP inválida.");
        Svc.setCfg("app_nome", r.req("app_nome", "Nome do sistema"));
        Svc.setCfg("suporte_email", r.req("suporte_email", "E-mail de suporte"));
        Svc.setCfg("pix_chave", r.p("pix_chave"));
        Svc.setCfg("preco_clinica", pc.toPlainString());
        Svc.setCfg("preco_dentista", pd.toPlainString());
        Svc.setCfg("dias_teste", String.valueOf(dias));
        Svc.setCfg("dia_vencimento", String.valueOf(dia));
        Svc.setCfg("comissao_pct", com.toPlainString());
        Svc.setCfg("exigir_verificacao", String.valueOf(r.chk("exigir_verificacao")));
        Svc.setCfg("strikes_bloqueio", String.valueOf(sb));
        Svc.setCfg("smtp_host", r.p("smtp_host"));
        Svc.setCfg("smtp_port", porta);
        Svc.setCfg("smtp_seg", List.of("STARTTLS", "SSL", "NENHUMA").contains(r.p("smtp_seg")) ? r.p("smtp_seg") : "STARTTLS");
        Svc.setCfg("smtp_user", r.p("smtp_user"));
        if (!r.p("smtp_pass").isEmpty()) Svc.setCfg("smtp_pass", r.p("smtp_pass"));
        Svc.setCfg("smtp_from", r.p("smtp_from"));
        Svc.audit(r, "CONFIGURACOES_ALTERADAS", "Clínica " + money(pc) + " · Profissional " + money(pd) + " · teste " + dias + " dias · comissão " + com + "% · strikes " + sb + " · SMTP " + r.p("smtp_host"));
        r.ok("Configurações salvas.");
        r.redirect("/admin/config");
    }

    // ================= AUDITORIA =================
    static void auditoria(Req r) throws Exception {
        String q = r.p("q").toLowerCase();
        List<String[]> rows = new ArrayList<>();
        for (Row a : Db.q("SELECT * FROM auditoria ORDER BY criado DESC")) {
            if (!q.isEmpty() && !(a.s("acao") + " " + a.s("email") + " " + a.s("detalhe")).toLowerCase().contains(q)) continue;
            if (rows.size() >= 500) break;
            String ac = a.s("acao");
            String cls = ac.contains("FALHOU") || ac.contains("BLOQUEADO") || ac.contains("STRIKE") ? "b-e" : ac.contains("LOGIN") || ac.contains("LOGOUT") ? "b-m" : "b-p";
            rows.add(new String[]{ts(a.l("criado")), h(a.s("email")), badge(ac, cls), h(a.s("detalhe")), h(a.s("ip"))});
        }
        String body = "<div class=\"card\"><form method=\"get\" action=\"/admin/auditoria\" class=\"tools\">" + input("Pesquisar", "q", r.p("q"), "search", "placeholder=\"Ação, e-mail ou detalhe\"")
            + "<button class=\"btn sm\">Buscar</button></form><p class=\"mut\">Logins, falhas de acesso, alterações administrativas, financeiras, negociações e cancelamentos (últimos 500).</p>"
            + table(new String[]{"Quando", "Usuário", "Ação", "Detalhe", "IP"}, rows, "Nenhum registro.") + "</div>";
        r.html(page(r, "Auditoria", body));
    }
}

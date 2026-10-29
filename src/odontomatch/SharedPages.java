package odontomatch;

import java.util.*;

import static odontomatch.Ui.*;

/** Páginas comuns a clínica e profissional: negociações (chat seguro), cancelamento, prontuário, termo e avaliações. */
final class SharedPages {
    private SharedPages() {}

    static void routes() {
        App.get("/negociacoes", SharedPages::negociacoes);
        App.get("/negociacao", SharedPages::negociacao);
        App.post("/negociacao/aceitar", SharedPages::aceitar);
        App.post("/negociacao/contra", SharedPages::contra);
        App.post("/negociacao/msg", SharedPages::msg);
        App.post("/negociacao/encerrar", SharedPages::encerrar);
        App.post("/turno/cancelar", SharedPages::cancelarTurno);
        App.get("/prontuario", SharedPages::prontuario);
        App.post("/prontuario/evolucao", SharedPages::evolucao);
        App.post("/prontuario/odontograma", SharedPages::odontograma);
        App.post("/agendamento/status", SharedPages::apStatus);
        App.get("/termo", SharedPages::termo);
        App.post("/avaliar", SharedPages::avaliar);
    }

    static void onlyParties(Req r) { if (r.role().equals("ADMIN")) throw new Bad("Área destinada a clínicas e profissionais."); }

    static Row neg(Req r, String id) {
        Row n = Db.one("SELECT n.*, o.titulo, o.data, o.hora_ini, o.hora_fim, o.status AS ostatus, o.clinica_id, o.aceita_contra, o.especialidade FROM negociacoes n JOIN oportunidades o ON o.id=n.oportunidade_id WHERE n.id=?", id);
        if (n == null) throw new Bad("Negociação não encontrada.");
        boolean ok = r.role().equals("CLINICA") ? n.s("clinica_id").equals(r.uid()) : n.s("dentista_id").equals(r.uid());
        if (!ok) throw new Bad("Sem permissão para esta negociação.");
        return n;
    }

    static String other(Req r, Row n) { return r.role().equals("CLINICA") ? n.s("dentista_id") : n.s("clinica_id"); }

    static String negMini(Req r, Row n) {
        Row lo = Svc.lastOffer(n.s("id"));
        String o = other(r, n);
        return "<a class=\"item\" style=\"text-decoration:none;color:inherit\" href=\"/negociacao?id=" + n.s("id") + "\"><div class=\"info\"><b>" + h(Svc.nome(o)) + "</b> " + Svc.stars(o)
            + "<div class=\"mut\">" + h(n.s("titulo")) + " · " + dw(n.s("data")) + "</div><div><b style=\"color:#6366f1\">" + (lo == null ? "" : h(Svc.termsText(Svc.T.of(lo, ""))))
            + "</b> <span class=\"mut\">" + (lo == null ? "" : Svc.matText(lo.s("materiais"))) + "</span></div></div><span class=\"btn sm\">Responder</span></a>";
    }

    /** Formulário de cancelamento de turno contratado, informando a consequência pela antecedência. */
    static String cancelForm(Req r, Row o, double hrs) {
        String aviso = hrs >= 24 ? "Faltam mais de 24h: cancelamento livre." : hrs >= 6 ? "Faltam menos de 24h: ficará registrado como cancelamento tardio na sua reputação."
            : "Faltam menos de 6h: este cancelamento gera STRIKE na sua conta.";
        return "<details class=\"inl\" style=\"display:inline-block\"><summary class=\"btn sm red\" style=\"list-style:none\">Cancelar turno</summary><form method=\"post\" action=\"/turno/cancelar\" class=\"card mt\" style=\"max-width:380px\""
            + " onsubmit=\"return confirm('" + h(aviso.replace("'", "")) + " Confirmar cancelamento?')\">" + r.csrf() + "<input type=\"hidden\" name=\"id\" value=\"" + o.s("id") + "\">"
            + "<p class=\"mut\" style=\"margin-top:0\">" + (hrs < 6 ? "⚠️ " : "") + h(aviso) + "</p>" + input("Motivo", "motivo", "", "text", "required maxlength=\"400\"")
            + "<button class=\"btn sm red mt\">Confirmar cancelamento</button></form></details>";
    }

    static void cancelarTurno(Req r) throws Exception {
        onlyParties(r);
        String tipo = Svc.cancelarTurno(r, r.p("id"), r.p("motivo"));
        r.ok(switch (tipo) {
            case "CANCEL_LIVRE" -> "Turno cancelado com antecedência (sem penalidade). A outra parte foi avisada por notificação e e-mail.";
            case "CANCEL_TARDIO" -> "Turno cancelado. Por ter menos de 24h de antecedência, ficou registrado como cancelamento tardio na sua reputação.";
            default -> "Turno cancelado com menos de 6h de antecedência: um strike foi registrado na sua conta.";
        });
        r.redirect(r.role().equals("CLINICA") ? "/clinica/oportunidades" : "/dentista/agenda");
    }

    // ================= LISTA =================
    static void negociacoes(Req r) throws Exception {
        onlyParties(r);
        String aba = r.p("aba").isEmpty() ? "andamento" : r.p("aba"), op = r.p("op");
        List<Row> all = Svc.myNegs(r);
        Map<String, List<Row>> tabs = new LinkedHashMap<>();
        tabs.put("andamento", new ArrayList<>());
        tabs.put("fechadas", new ArrayList<>());
        tabs.put("encerradas", new ArrayList<>());
        for (Row n : all) {
            if (!op.isEmpty() && !n.s("oportunidade_id").equals(op)) continue;
            if (n.s("status").equals("FECHADA")) tabs.get("fechadas").add(n);
            else if (n.s("status").equals("ABERTA") && n.s("ostatus").equals("ABERTA") && n.s("data").compareTo(today()) >= 0) tabs.get("andamento").add(n);
            else tabs.get("encerradas").add(n);
        }
        StringBuilder b = new StringBuilder();
        if (!op.isEmpty()) {
            Row o = Db.one("SELECT * FROM oportunidades WHERE id=?", op);
            if (o != null) b.append("<div class=\"card\"><b>Filtrando: ").append(h(o.s("titulo"))).append("</b> · ").append(dLong(o.s("data"))).append(" · Sua oferta: <b>")
                .append(h(Svc.termsText(Svc.T.of(o, "")))).append("</b> <a class=\"btn sm gh\" href=\"/negociacoes\">Ver todas</a>")
                .append(r.role().equals("CLINICA") ? " <a class=\"btn sm acc\" href=\"/clinica/match?op=" + op + "\">🎯 Melhores matches</a>" : "").append("</div>");
        }
        b.append("<div class=\"tools\" style=\"margin-bottom:14px\">");
        for (var e : tabs.entrySet())
            b.append("<a class=\"btn sm ").append(e.getKey().equals(aba) ? "" : "gh").append("\" href=\"/negociacoes?aba=").append(e.getKey()).append(op.isEmpty() ? "" : "&op=" + op).append("\">")
                .append(switch (e.getKey()) { case "andamento" -> "Em andamento"; case "fechadas" -> "Fechadas"; default -> "Encerradas"; }).append(" (").append(e.getValue().size()).append(")</a>");
        b.append("</div>");
        List<Row> list = tabs.getOrDefault(aba, List.of());
        if (list.isEmpty()) b.append("<div class=\"card\">").append(empty(r.role().equals("CLINICA") ? "Nenhuma negociação aqui. Publique um turno para receber propostas." : "Nenhuma negociação aqui. Veja os turnos compatíveis.")).append("</div>");
        else if (r.role().equals("CLINICA") && aba.equals("andamento")) {
            Map<String, List<Row>> g = new LinkedHashMap<>();
            for (Row n : list) g.computeIfAbsent(n.s("oportunidade_id"), k -> new ArrayList<>()).add(n);
            for (var e : g.entrySet()) {
                Row o = Db.one("SELECT * FROM oportunidades WHERE id=?", e.getKey());
                List<Row> ap = Svc.oppAppts(o);
                List<Row> ns = e.getValue();
                ns.sort((x, y) -> Integer.compare(Svc.match(Svc.user(y.s("dentista_id")), o, r.user).score(), Svc.match(Svc.user(x.s("dentista_id")), o, r.user).score()));
                b.append("<div class=\"card\"><div class=\"bar\"><div><h2 style=\"margin:0\">").append(h(o.s("titulo"))).append("</h2><div class=\"mut\">").append(dLong(o.s("data"))).append(" · ")
                    .append(o.s("hora_ini")).append("–").append(o.s("hora_fim")).append(" · sua oferta: <b>").append(h(Svc.termsText(Svc.T.of(o, "")))).append("</b></div></div>")
                    .append(badge(ns.size() + " proposta(s)", "")).append("</div><p class=\"mut\">Compare lado a lado — ordenado pelo melhor match.</p><div class=\"grid g3\">");
                for (Row n : ns) b.append(negCard(r, n, ap, o));
                b.append("</div></div>");
            }
        } else {
            b.append("<div class=\"grid g3\">");
            for (Row n : list) {
                Row o = Db.one("SELECT * FROM oportunidades WHERE id=?", n.s("oportunidade_id"));
                b.append(negCard(r, n, Svc.oppAppts(o), o));
            }
            b.append("</div>");
        }
        r.html(page(r, "Negociações", b.toString()));
    }

    static String negCard(Req r, Row n, List<Row> ap, Row opp) {
        Row lo = Svc.lastOffer(n.s("id"));
        String o = other(r, n);
        Row ou = Svc.user(o);
        boolean turn = Svc.myTurn(r, n);
        Svc.T t = Svc.T.of(lo, "");
        String st = n.s("status").equals("FECHADA") ? badge("✅ Fechada", "b-ok") : n.s("status").equals("CANCELADA") ? badge("Turno cancelado", "b-e")
            : !n.s("status").equals("ABERTA") || !n.s("ostatus").equals("ABERTA") ? badge("Encerrada", "b-m") : turn ? badge("Sua vez", "b-w") : badge("Aguardando", "b-m");
        Svc.Match m = Svc.match(Svc.user(n.s("dentista_id")), opp, Svc.user(opp.s("clinica_id")));
        return "<div class=\"card\"><div class=\"bar\" style=\"margin-bottom:6px\"><div><b>" + h(ou.s("nome")) + "</b><div>" + Svc.stars(o) + " " + Svc.selos(ou) + "</div></div>" + st + "</div>"
            + (r.role().equals("CLINICA") ? "<div class=\"mut\">" + h(ou.s("documento")) + " · " + ou.i("experiencia") + " anos · " + h(ou.s("especialidades").replace(";", ", ")) + "</div>"
            : "<div class=\"mut\">" + h(n.s("titulo")) + " · " + dw(n.s("data")) + " " + n.s("hora_ini") + "–" + n.s("hora_fim") + "</div>")
            + "<div class=\"mt\">" + Svc.matchBadge(m) + "</div>"
            + "<div class=\"mut mt\">" + (lo.s("autor").equals(r.role()) ? "Sua última oferta" : "Oferta " + (r.role().equals("CLINICA") ? "do profissional" : "da clínica"))
            + (t.same(Svc.T.of(opp, "")) ? " " + badge("= oferta da clínica", "b-ok") : "") + "</div><div class=\"terms\" style=\"color:#6366f1\">" + h(Svc.termsText(t)) + "</div>"
            + "<div class=\"mut\">" + Svc.matText(t.mat()) + "</div><div class=\"gain\"><span class=\"mut\">" + (r.role().equals("CLINICA") ? "Custo estimado" : "Seu ganho estimado") + "</span><b>"
            + money(Svc.calc(t, ap).rep()) + "</b></div><a class=\"btn block " + (turn ? "" : "gh") + "\" href=\"/negociacao?id=" + n.s("id") + "\">" + (turn ? "Responder" : "Abrir negociação") + "</a></div>";
    }

    // ================= DETALHE =================
    static void negociacao(Req r) throws Exception {
        onlyParties(r);
        Row n = neg(r, r.p("id"));
        Row opp = Db.one("SELECT * FROM oportunidades WHERE id=?", n.s("oportunidade_id"));
        Row c = Svc.user(n.s("clinica_id")), dd = Svc.user(n.s("dentista_id"));
        List<Row> ap = Svc.oppAppts(opp);
        String me = r.role();
        boolean fechada = n.s("status").equals("FECHADA");
        Svc.Match m = Svc.match(dd, opp, c);
        StringBuilder tl = new StringBuilder("<div class=\"tl\">");
        for (Row msg : Db.q("SELECT * FROM mensagens WHERE negociacao_id=? ORDER BY criado", n.s("id"))) {
            String tipo = msg.s("tipo"), autor = msg.s("autor");
            if (tipo.equals("SISTEMA")) { tl.append("<div class=\"msg sys\">— ").append(h(msg.s("texto"))).append(" —</div>"); continue; }
            String quem = autor.equals(me) ? "Você" : autor.equals("CLINICA") ? c.s("nome") : dd.s("nome");
            String cls = autor.equals(me) ? " me" : "";
            if (tipo.equals("OFERTA")) {
                Svc.T t = Svc.T.of(msg, "");
                tl.append("<div class=\"msg of").append(cls).append("\"><div class=\"mut\">💰 ").append(h(quem)).append(" ofereceu</div><div class=\"ot\">").append(h(Svc.termsText(t)))
                    .append("</div><div class=\"mut\">").append(Svc.matText(t.mat())).append(" · repasse estimado ").append(money(Svc.calc(t, ap).rep())).append("</div>")
                    .append(msg.has("texto") ? "<div style=\"margin-top:6px\">" + h(msg.s("texto")) + "</div>" : "").append("<div class=\"t\">").append(ts(msg.l("criado"))).append("</div></div>");
            } else if (tipo.equals("ACEITE")) {
                tl.append("<div class=\"msg").append(cls).append("\" style=\"background:#dcfce7\">✅ <b>").append(h(quem)).append(" aceitou a proposta.</b><div class=\"t\">").append(ts(msg.l("criado"))).append("</div></div>");
            } else if (tipo.equals("RECUSA")) {
                tl.append("<div class=\"msg").append(cls).append("\" style=\"background:#fee2e2\">❌ <b>").append(h(quem)).append(" encerrou a negociação.</b> ").append(h(msg.s("texto")))
                    .append("<div class=\"t\">").append(ts(msg.l("criado"))).append("</div></div>");
            } else {
                tl.append("<div class=\"msg").append(cls).append("\"><b>").append(h(quem)).append(":</b> ").append(h(msg.s("texto"))).append("<div class=\"t\">").append(ts(msg.l("criado"))).append("</div></div>");
            }
        }
        tl.append("</div>");
        boolean open = n.s("status").equals("ABERTA") && n.s("ostatus").equals("ABERTA") && n.s("data").compareTo(today()) >= 0;
        boolean turn = Svc.myTurn(r, n) && open;
        Row lo = Svc.lastOffer(n.s("id"));
        StringBuilder act = new StringBuilder();
        if (open) {
            if (turn) {
                boolean podeContra = me.equals("CLINICA") || n.b("aceita_contra");
                act.append("<div class=\"tools\">").append(post(r, "/negociacao/aceitar", "✅ Aceitar " + h(Svc.termsText(Svc.T.of(lo, ""))), "ok",
                        "Confirmar o fechamento com " + Svc.termsText(Svc.T.of(lo, "")) + "? Ambos receberão a confirmação por e-mail.", "id", n.s("id")))
                    .append(post(r, "/negociacao/encerrar", me.equals("CLINICA") ? "Recusar" : "Desistir", "red", "Encerrar esta negociação?", "id", n.s("id"))).append("</div>");
                if (podeContra)
                    act.append("<div class=\"card mt\" style=\"background:#f8fafc\"><h3>💬 Contraproposta</h3><form method=\"post\" action=\"/negociacao/contra\">").append(r.csrf())
                        .append("<input type=\"hidden\" name=\"id\" value=\"").append(n.s("id")).append("\">").append(Svc.termsFields(Svc.T.of(lo, "")))
                        .append(input("Mensagem (opcional)", "texto", "", "text", "maxlength=\"500\"")).append("<button class=\"btn acc mt\">Enviar contraproposta</button></form></div>");
            } else {
                act.append("<div class=\"banner\" style=\"background:#f1f5f9;color:#334155;border-color:#e2e8f0\">⏳ Sua oferta foi entregue. ").append(me.equals("CLINICA") ? "O profissional" : "A clínica")
                    .append(" já foi notificado(a) e você será avisado(a) aqui assim que houver resposta. ").append(me.equals("DENTISTA") ? post(r, "/negociacao/encerrar", "Retirar proposta", "sm red", "Retirar sua proposta?", "id", n.s("id")) : "").append("</div>");
            }
            act.append("<form method=\"post\" action=\"/negociacao/msg\" class=\"tools mt\">").append(r.csrf()).append("<input type=\"hidden\" name=\"id\" value=\"").append(n.s("id"))
                .append("\"><input name=\"texto\" maxlength=\"1000\" placeholder=\"Enviar mensagem...\" style=\"flex:1\" required><button class=\"btn gh\">Enviar</button></form>")
                .append("<p class=\"mut\">🔒 Chat seguro: telefones, e-mails, links e @perfis são ocultados automaticamente até o negócio ser fechado.</p>");
        } else if (fechada) {
            Row outro = me.equals("CLINICA") ? dd : c;
            act.append("<div class=\"banner\" style=\"background:#dcfce7;color:#166534;border-color:#bbf7d0\">🎉 <b>Negócio fechado!</b> A confirmação foi enviada por e-mail para as duas partes. ")
                .append(me.equals("DENTISTA") ? "A agenda e os prontuários dos pacientes já estão liberados." : "Os pacientes do período foram vinculados ao profissional.")
                .append("<div class=\"mt\"><b>Contato liberado:</b> ").append(h(outro.s("nome"))).append(" · 📞 ").append(h(outro.s("telefone"))).append(" · ✉ ").append(h(outro.s("email")))
                .append(me.equals("DENTISTA") ? " · 📍 " + h(c.s("endereco") + " — " + c.s("cidade")) : "").append("</div>")
                .append("<div class=\"tools mt\"><a class=\"btn sm\" href=\"/termo?id=").append(opp.s("id")).append("\">📄 Termo</a> <a class=\"btn sm gh\" href=\"")
                .append(me.equals("DENTISTA") ? "/dentista/agenda" : "/clinica/agenda?dia=" + opp.s("data")).append("\">📅 Agenda</a></div></div>");
        } else if (n.s("status").equals("CANCELADA")) {
            act.append("<div class=\"banner\">❌ O turno foi cancelado após o fechamento").append(opp.has("motivo_cancel") ? ". Motivo: " + h(opp.s("motivo_cancel")) : "").append(".</div>");
        } else act.append("<div class=\"banner\" style=\"background:#f1f5f9;color:#334155;border-color:#e2e8f0\">Esta negociação está encerrada.</div>");
        String body = "<a class=\"btn sm gh\" href=\"/negociacoes\">← Negociações</a><div class=\"card mt\"><div class=\"grid g2\"><div><h2 style=\"margin:0\">" + h(n.s("titulo")) + " " + Svc.oppBadge(opp) + "</h2>"
            + "<div class=\"mut\">📅 " + dLong(n.s("data")) + " · " + n.s("hora_ini") + "–" + n.s("hora_fim") + " · " + h(Svc.label(Svc.CATEGORIAS, Svc.catOpp(opp)))
            + (Svc.catOpp(opp).equals("DENTISTA") ? " · " + h(n.s("especialidade")) : "") + "</div>"
            + "<div class=\"mut\">🏥 " + h(c.s("nome")) + " " + Svc.stars(c.s("id")) + " " + Svc.selos(c) + " · " + h(c.s("cidade")) + "</div>"
            + "<div class=\"mut\">Oferta original da clínica: <b>" + h(Svc.termsText(Svc.T.of(opp, ""))) + "</b> · " + Svc.matText(opp.s("materiais")) + "</div></div>"
            + "<div><div class=\"mut\">🦷 <b>" + h(dd.s("nome")) + "</b> " + Svc.stars(dd.s("id")) + " " + Svc.selos(dd) + "</div><div class=\"mut\">" + h(dd.s("documento")) + " · " + dd.i("experiencia") + " anos · "
            + (dd.b("materiais_proprios") ? "possui materiais próprios" : "sem materiais próprios") + "</div><div class=\"mut\">" + ap.size() + " paciente(s) · produção "
            + money(Svc.calc(null, ap).prod()) + "</div><div class=\"mt\">" + Svc.matchBadge(m) + "</div>" + Svc.matchWhy(m) + "</div></div></div><div class=\"card\">" + tl + act + "</div>";
        r.html(page(r, "Negociação", body));
    }

    static void aceitar(Req r) throws Exception {
        onlyParties(r);
        Row n = neg(r, r.p("id"));
        if (r.role().equals("DENTISTA") && Svc.exigeVerificacao() && !r.user.b("verificado")) throw new Bad("Cadastro em verificação.");
        Svc.aceitar(r, n.s("id"));
        r.ok("🎉 Negócio fechado! A agenda foi liberada e a confirmação foi enviada por e-mail para as duas partes.");
        r.redirect("/negociacao?id=" + n.s("id"));
    }

    static void contra(Req r) throws Exception {
        onlyParties(r);
        Row n = neg(r, r.p("id"));
        if (!Svc.myTurn(r, n)) throw new Bad("Aguarde a resposta da outra parte.");
        if (r.role().equals("DENTISTA") && !n.b("aceita_contra")) throw new Bad("Este turno não aceita contraproposta.");
        Svc.T t = Svc.termsFrom(r);
        Db.insert("mensagens", Db.m("negociacao_id", n.s("id"), "autor", r.role(), "tipo", "OFERTA", "modelo", t.modelo(), "pct_part", t.pp(), "pct_conv", t.pc(),
            "diaria", t.diaria(), "materiais", t.mat(), "texto", Svc.mask(r.p("texto"))));
        Db.x("UPDATE negociacoes SET atualizado=? WHERE id=?", System.currentTimeMillis(), n.s("id"));
        Svc.notify(other(r, n), r.user.s("nome") + " enviou contraproposta em \"" + n.s("titulo") + "\": " + Svc.termsText(t) + ".", "/negociacao?id=" + n.s("id"));
        Svc.audit(r, "CONTRAPROPOSTA", n.s("titulo") + " · " + Svc.termsText(t));
        r.ok("Contraproposta enviada.");
        r.redirect("/negociacao?id=" + n.s("id"));
    }

    static void msg(Req r) throws Exception {
        onlyParties(r);
        Row n = neg(r, r.p("id"));
        if (!n.s("status").equals("ABERTA")) throw new Bad("Negociação encerrada.");
        String orig = r.req("texto", "Mensagem");
        String t = Svc.mask(orig.length() > 1000 ? orig.substring(0, 1000) : orig);
        Db.insert("mensagens", Db.m("negociacao_id", n.s("id"), "autor", r.role(), "tipo", "MSG", "texto", t));
        Db.x("UPDATE negociacoes SET atualizado=? WHERE id=?", System.currentTimeMillis(), n.s("id"));
        Svc.notify(other(r, n), "Nova mensagem de " + r.user.s("nome") + ": \"" + (t.length() > 60 ? t.substring(0, 60) + "…" : t) + "\"", "/negociacao?id=" + n.s("id"));
        if (!t.equals(orig)) r.err("Por segurança, contatos pessoais foram ocultados. Eles serão liberados automaticamente após o fechamento.");
        r.redirect("/negociacao?id=" + n.s("id"));
    }

    static void encerrar(Req r) throws Exception {
        onlyParties(r);
        Row n = neg(r, r.p("id"));
        if (!n.s("status").equals("ABERTA")) throw new Bad("Negociação já encerrada.");
        Db.x("UPDATE negociacoes SET status='ENCERRADA', atualizado=? WHERE id=?", System.currentTimeMillis(), n.s("id"));
        Db.insert("mensagens", Db.m("negociacao_id", n.s("id"), "autor", r.role(), "tipo", "RECUSA", "texto", ""));
        Svc.notify(other(r, n), r.user.s("nome") + " encerrou a negociação de \"" + n.s("titulo") + "\".", "/negociacao?id=" + n.s("id"));
        Svc.audit(r, "NEGOCIACAO_ENCERRADA", n.s("titulo"));
        r.ok("Negociação encerrada.");
        r.redirect("/negociacoes");
    }

    // ================= PRONTUÁRIO =================
    static final int[] UP = {18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28};
    static final int[] LO = {48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38};

    static Row pac(Req r, String id) {
        Row p = Db.one("SELECT * FROM pacientes WHERE id=?", id);
        if (!Svc.canSeePatient(r, p)) throw new Bad("Sem permissão para este prontuário.");
        return p;
    }

    static void prontuario(Req r) throws Exception {
        onlyParties(r);
        Row p = pac(r, r.p("id"));
        String aba = r.p("aba").isEmpty() ? "resumo" : r.p("aba");
        String[][] abas = {{"resumo", "Resumo"}, {"anamnese", "Anamnese"}, {"odontograma", "Odontograma"}, {"evolucao", "Evolução clínica"}, {"historico", "Histórico"}};
        StringBuilder b = new StringBuilder("<div class=\"bar\"><a class=\"btn sm gh\" href=\"").append(r.role().equals("CLINICA") ? "/clinica/pacientes" : "/dentista/agenda").append("\">← Voltar</a>");
        if (r.role().equals("CLINICA")) b.append("<div class=\"tools\"><a class=\"btn sm gh\" href=\"/clinica/paciente?id=").append(p.s("id")).append("\">Editar cadastro</a><a class=\"btn sm\" href=\"/clinica/agendamento?paciente=")
            .append(p.s("id")).append("\">Agendar</a></div>");
        b.append("</div><div class=\"tools\" style=\"margin-bottom:12px\">");
        for (String[] a : abas) b.append("<a class=\"btn sm ").append(a[0].equals(aba) ? "" : "gh").append("\" href=\"/prontuario?id=").append(p.s("id")).append("&aba=").append(a[0]).append("\">").append(a[1]).append("</a>");
        b.append("</div>");
        List<String> al = Svc.alertas(p);
        if (!al.isEmpty()) b.append("<div class=\"alert\">⚠️ <b>Atenção:</b> ").append(h(String.join(" · ", al))).append("</div>");
        b.append("<div class=\"card\">");
        switch (aba) {
            case "anamnese" -> {
                String cond = p.s("condicoes");
                b.append(table(new String[]{"Item", "Informação"}, List.of(
                    new String[]{"Alergias", p.has("alergias") ? h(p.s("alergias")) : "Nenhuma informada"}, new String[]{"Medicamentos", h(p.s("medicamentos"))},
                    new String[]{"Doenças / cirurgias", h(p.s("doencas"))}, new String[]{"Hipertensão", yn(cond.contains("HIPERTENSO"))}, new String[]{"Diabetes", yn(cond.contains("DIABETICO"))},
                    new String[]{"Gestante", yn(cond.contains("GESTANTE"))}, new String[]{"Anticoagulante", yn(cond.contains("ANTICOAGULANTE"))}, new String[]{"Cardiopatia", yn(cond.contains("CARDIOPATA"))},
                    new String[]{"Fumante", yn(cond.contains("FUMANTE"))}, new String[]{"Observações", h(p.s("obs"))}), ""));
            }
            case "odontograma" -> {
                Map<String, String> od = new HashMap<>();
                for (String x : p.s("odontograma").split(";")) { String[] kv = x.split(":"); if (kv.length == 2) od.put(kv[0], kv[1]); }
                b.append("<p class=\"mut\">Notação FDI. Selecione a condição de cada dente e salve.</p><form method=\"post\" action=\"/prontuario/odontograma\">").append(r.csrf())
                    .append("<input type=\"hidden\" name=\"id\" value=\"").append(p.s("id")).append("\"><div class=\"mut\">Arcada superior</div><div class=\"odo\">");
                for (int t : UP) b.append(tooth(t, od.getOrDefault(String.valueOf(t), "")));
                b.append("</div><div class=\"mut mt\">Arcada inferior</div><div class=\"odo\">");
                for (int t : LO) b.append(tooth(t, od.getOrDefault(String.valueOf(t), "")));
                b.append("</div><button class=\"btn mt\">Salvar odontograma</button></form>");
            }
            case "evolucao" -> {
                b.append("<form method=\"post\" action=\"/prontuario/evolucao\">").append(r.csrf()).append("<input type=\"hidden\" name=\"id\" value=\"").append(p.s("id")).append("\">")
                    .append(area("Nova evolução", "texto", "", 3)).append("<button class=\"btn mt\">Registrar evolução</button></form><h4>Registros</h4>");
                List<Row> ev = Db.q("SELECT * FROM evolucoes WHERE paciente_id=? ORDER BY criado DESC", p.s("id"));
                if (ev.isEmpty()) b.append(empty("Nenhuma evolução registrada."));
                for (Row e : ev) b.append("<div class=\"item\"><div class=\"info\"><b>").append(h(e.s("autor_nome"))).append("</b> <span class=\"mut\">").append(ts(e.l("criado")))
                    .append("</span><div style=\"white-space:pre-wrap\">").append(h(e.s("texto"))).append("</div></div></div>");
            }
            case "historico" -> {
                List<String[]> rows = new ArrayList<>();
                for (Row a : Db.q("SELECT * FROM agendamentos WHERE paciente_id=? ORDER BY data DESC, hora DESC", p.s("id")))
                    rows.add(new String[]{dw(a.s("data")) + " " + a.s("hora"), h(a.s("procedimento")), a.has("dentista_id") ? h(Svc.nome(a.s("dentista_id"))) : "—", badge(Svc.label(Svc.ST_AGENDA, a.s("status")), "b-m")});
                b.append(table(new String[]{"Data", "Procedimento", "Profissional", "Situação"}, rows, "Sem consultas."));
            }
            default -> {
                int age = age(p.s("nascimento"));
                long pend = Arrays.stream(p.s("odontograma").split(";")).filter(x -> x.endsWith(":C") || x.endsWith(":X")).count();
                b.append("<h2>").append(h(p.s("nome"))).append("</h2><div class=\"grid g2\"><div><p><b>Idade:</b> ").append(age < 0 ? "—" : age + " anos").append("</p><p><b>CPF:</b> ").append(h(p.s("cpf")))
                    .append("</p><p><b>Celular:</b> ").append(h(p.s("telefone"))).append("</p></div><div><p><b>Convênio:</b> ").append(p.has("convenio") ? h(p.s("convenio") + " · " + p.s("carteira")) : "Particular")
                    .append("</p><p><b>Clínica:</b> ").append(h(Svc.nome(p.s("clinica_id")))).append("</p><p><b>Dentes com pendência:</b> ").append(pend).append("</p></div></div>");
            }
        }
        b.append("</div>");
        r.html(page(r, "Prontuário", b.toString()));
    }

    static String yn(boolean b) { return b ? badge("Sim", "b-e") : badge("Não", "b-m"); }

    static String tooth(int t, String v) {
        StringBuilder b = new StringBuilder("<label>" + t + "<select name=\"d" + t + "\">");
        for (String[] o : Svc.DENTE) b.append("<option value=\"").append(o[0]).append("\"").append(o[0].equals(v) ? " selected" : "").append(">").append(o[1]).append("</option>");
        return b.append("</select></label>").toString();
    }

    static void odontograma(Req r) throws Exception {
        onlyParties(r);
        Row p = pac(r, r.p("id"));
        List<String> parts = new ArrayList<>();
        for (int[] arc : new int[][]{UP, LO})
            for (int t : arc) {
                String v = r.p("d" + t);
                if (!v.isEmpty() && "CREXAPI".contains(v) && v.length() == 1) parts.add(t + ":" + v);
            }
        Db.x("UPDATE pacientes SET odontograma=? WHERE id=?", String.join(";", parts), p.s("id"));
        r.ok("Odontograma salvo.");
        r.redirect("/prontuario?id=" + p.s("id") + "&aba=odontograma");
    }

    static void evolucao(Req r) throws Exception {
        onlyParties(r);
        Row p = pac(r, r.p("id"));
        String t = r.req("texto", "Evolução");
        Db.insert("evolucoes", Db.m("paciente_id", p.s("id"), "autor_id", r.uid(), "autor_nome", r.user.s("nome"), "texto", t.length() > 4000 ? t.substring(0, 4000) : t));
        r.ok("Evolução registrada.");
        r.redirect("/prontuario?id=" + p.s("id") + "&aba=evolucao");
    }

    static void apStatus(Req r) throws Exception {
        onlyParties(r);
        Row a = Db.one("SELECT * FROM agendamentos WHERE id=?", r.p("id"));
        if (a == null) throw new Bad("Agendamento não encontrado.");
        boolean ok = r.role().equals("CLINICA") ? a.s("clinica_id").equals(r.uid()) : r.uid().equals(a.s("dentista_id"));
        if (!ok) throw new Bad("Sem permissão.");
        String st = r.p("status");
        if (Svc.label(Svc.ST_AGENDA, st).equals(st)) throw new Bad("Situação inválida.");
        Db.x("UPDATE agendamentos SET status=? WHERE id=?", st, a.s("id"));
        if (r.role().equals("DENTISTA")) {
            Row p = Db.one("SELECT nome FROM pacientes WHERE id=?", a.s("paciente_id"));
            Svc.notify(a.s("clinica_id"), r.user.s("nome") + " marcou " + (p == null ? "paciente" : p.s("nome")) + " como \"" + Svc.label(Svc.ST_AGENDA, st) + "\".", "/clinica/agenda?dia=" + a.s("data"));
        }
        r.ok("Situação atualizada: " + Svc.label(Svc.ST_AGENDA, st) + ".");
        String back = r.p("voltar");
        r.redirect(back.startsWith("/") && !back.startsWith("//") ? back : "/");
    }

    // ================= TERMO =================
    static void termo(Req r) throws Exception {
        onlyParties(r);
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=? AND status='FECHADA'", r.p("id"));
        if (o == null || !(o.s("clinica_id").equals(r.uid()) || o.s("dentista_id").equals(r.uid()))) throw new Bad("Termo não disponível.");
        Row c = Svc.user(o.s("clinica_id")), d = Svc.user(o.s("dentista_id"));
        Svc.T t = Svc.T.of(o, "f_");
        String body = "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>Termo</title>"
            + "<style>body{font-family:Arial,sans-serif;max-width:800px;margin:30px auto;padding:0 20px;line-height:1.55;color:#111}h2{text-align:center}.np{margin-bottom:20px}@media print{.np{display:none}}</style></head><body>"
            + "<div class=\"np\"><button onclick=\"window.print()\">🖨 Imprimir / salvar PDF</button> <a href=\"javascript:history.back()\">Voltar</a></div>"
            + "<h2>TERMO DE PRESTAÇÃO DE SERVIÇOS ODONTOLÓGICOS AUTÔNOMOS</h2>"
            + "<p><b>CONTRATANTE:</b> " + h(c.s("nome")) + ", CNPJ " + h(c.s("documento")) + ", " + h(c.s("endereco")) + " — " + h(c.s("cidade")) + ".</p>"
            + "<p><b>CONTRATADO(A):</b> " + h(d.s("nome")) + " (" + h(Svc.label(Svc.CATEGORIAS, Svc.cat(d))) + "), " + h(d.s("documento")) + ".</p>"
            + "<p><b>1. Objeto:</b> atendimento odontológico (" + h(Svc.catOpp(o).equals("DENTISTA") ? o.s("especialidade") : Svc.label(Svc.CATEGORIAS, Svc.catOpp(o))) + ") aos pacientes agendados pela CONTRATANTE em " + dLong(o.s("data")) + ", das " + o.s("hora_ini") + " às " + o.s("hora_fim") + ".</p>"
            + "<p><b>2. Remuneração:</b> " + h(Svc.termsText(t)) + " (" + h(Svc.label(Svc.MODELOS, t.modelo())) + "). Percentuais incidem sobre os procedimentos efetivamente realizados"
            + (t.modelo().equals("DIARIA") ? "" : "; para convênios, sobre o valor da tabela do convênio") + ".</p>"
            + "<p><b>3. Materiais:</b> " + h(Svc.label(Svc.MATERIAIS, t.mat())) + ". " + h(o.s("obs_materiais")) + "</p>"
            + "<p><b>4. Responsabilidades:</b> o(a) CONTRATADO(A) responde tecnicamente pelos procedimentos executados e pelo registro em prontuário; a CONTRATANTE fornece estrutura, recepção, agendamento e esterilização, salvo disposição em contrário.</p>"
            + "<p><b>5. Pagamento:</b> o repasse será feito após o atendimento, mediante conferência dos procedimentos realizados" + (d.has("pix") ? ", via PIX (" + h(d.s("pix")) + ")" : "") + ".</p>"
            + "<p><b>6. Cancelamento:</b> com mais de 24h de antecedência, livre; entre 24h e 6h, registrado na reputação; com menos de 6h ou não comparecimento, strike na plataforma.</p>"
            + "<p><b>7. Natureza:</b> prestação de serviço autônoma, sem vínculo empregatício, observadas as normas do CFO e a LGPD quanto aos dados dos pacientes. A plataforma " + h(Svc.appName()) + " atua apenas como intermediadora tecnológica.</p>"
            + "<p style=\"color:#555\">Aceite eletrônico registrado na plataforma em " + ts(o.l("fechado_em")) + ".</p><br><br>"
            + "<p>______________________________<br>" + h(c.s("nome")) + "</p><br><p>______________________________<br>" + h(d.s("nome")) + "</p></body></html>";
        r.html(body);
    }

    // ================= AVALIAÇÕES =================
    static String reviewButton(Req r, Row o, String para) {
        if (Db.count("SELECT COUNT(*) FROM avaliacoes WHERE oportunidade_id=? AND de_id=?", o.s("id"), r.uid()) > 0) return badge("⭐ Avaliado", "b-m");
        return "<form method=\"post\" action=\"/avaliar\" class=\"tools\">" + r.csrf() + "<input type=\"hidden\" name=\"id\" value=\"" + o.s("id") + "\">"
            + "<select name=\"nota\" style=\"width:auto\"><option value=\"5\">★★★★★</option><option value=\"4\">★★★★</option><option value=\"3\">★★★</option><option value=\"2\">★★</option><option value=\"1\">★</option></select>"
            + "<input name=\"texto\" placeholder=\"Comentário\" maxlength=\"500\" style=\"width:160px\"><button class=\"btn sm sec\">⭐ Avaliar " + (para.equals(o.s("clinica_id")) ? "clínica" : "profissional") + "</button></form>";
    }

    static void avaliar(Req r) throws Exception {
        onlyParties(r);
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=? AND status='FECHADA'", r.p("id"));
        if (o == null || o.s("data").compareTo(today()) > 0) throw new Bad("Avaliação disponível após o turno.");
        String para;
        if (o.s("clinica_id").equals(r.uid())) para = o.s("dentista_id");
        else if (o.s("dentista_id").equals(r.uid())) para = o.s("clinica_id");
        else throw new Bad("Sem permissão.");
        if (Db.count("SELECT COUNT(*) FROM avaliacoes WHERE oportunidade_id=? AND de_id=?", o.s("id"), r.uid()) > 0) throw new Bad("Você já avaliou este turno.");
        int nota = Math.max(1, Math.min(5, r.intp("nota", 5)));
        Db.insert("avaliacoes", Db.m("de_id", r.uid(), "para_id", para, "oportunidade_id", o.s("id"), "nota", nota, "texto", Svc.mask(r.p("texto"))));
        Svc.notify(para, r.user.s("nome") + " avaliou você com " + nota + " estrela(s).", "/conta/perfil");
        r.ok("Avaliação enviada. Obrigado!");
        r.redirect(r.back("/"));
    }
}

package odontomatch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static odontomatch.Ui.*;

/** Área do profissional (dentista, auxiliar ou estudante): turnos, propostas, agenda, salas e ganhos. */
final class DentistPages {
    private DentistPages() {}

    static void routes() {
        App.get("/dentista", DentistPages::dashboard);
        App.get("/dentista/oportunidades", DentistPages::feed);
        App.get("/dentista/oportunidade", DentistPages::detalhe);
        App.post("/dentista/proposta", DentistPages::proposta);
        App.get("/dentista/agenda", DentistPages::agenda);
        App.post("/dentista/checkin", DentistPages::checkin);
        App.post("/dentista/denunciar", DentistPages::denunciar);
        App.get("/dentista/ganhos", DentistPages::ganhos);
        App.get("/dentista/salas", DentistPages::salas);
        App.post("/dentista/reserva", DentistPages::reservar);
        App.post("/dentista/reserva/cancelar", DentistPages::cancelarReserva);
    }

    /** Turnos abertos compatíveis com a categoria do profissional. */
    static List<Row> abertas(Req r) {
        String cat = Svc.cat(r.user);
        List<Row> out = new ArrayList<>();
        for (Row o : Db.q("SELECT * FROM oportunidades WHERE status='ABERTA' AND data>=? ORDER BY data, hora_ini", today())) if (Svc.catOpp(o).equals(cat)) out.add(o);
        return out;
    }

    static boolean jaNegociou(Req r, Row o) { return Db.count("SELECT COUNT(*) FROM negociacoes WHERE oportunidade_id=? AND dentista_id=?", o.s("id"), r.uid()) > 0; }

    // ================= INÍCIO =================
    static void dashboard(Req r) throws Exception {
        List<Object[]> paraMim = new ArrayList<>();
        for (Row o : abertas(r)) if (!jaNegociou(r, o)) paraMim.add(new Object[]{o, Svc.match(r.user, o, Svc.user(o.s("clinica_id")))});
        paraMim.sort((a, b) -> Integer.compare(((Svc.Match) b[1]).score(), ((Svc.Match) a[1]).score()));
        List<Row> turn = new ArrayList<>();
        for (Row n : Svc.myNegs(r)) if (Svc.myTurn(r, n)) turn.add(n);
        long hojePac = Db.count("SELECT COUNT(*) FROM agendamentos WHERE dentista_id=? AND data=? AND status<>'CANCELADO'", r.uid(), today());
        BigDecimal aReceber = BigDecimal.ZERO;
        List<Row> fut = new ArrayList<>();
        for (Row o : Db.q("SELECT * FROM oportunidades WHERE dentista_id=? AND status='FECHADA' ORDER BY data", r.uid())) {
            if (!o.b("repasse_pago") && o.s("data").compareTo(today()) <= 0) aReceber = aReceber.add(Svc.settle(o).rep());
            if (o.s("data").compareTo(today()) >= 0) fut.add(o);
        }
        StringBuilder b = new StringBuilder("<div class=\"grid g4\">").append(kpi("Turnos compatíveis", String.valueOf(paraMim.size()), "🎯"))
            .append(kpi("Negociações aguardando você", String.valueOf(turn.size()), "🤝")).append(kpi("Pacientes hoje", String.valueOf(hojePac), "🦷"))
            .append(kpi("A receber", money(aReceber), "💰")).append("</div>");
        if (!turn.isEmpty()) {
            b.append("<div class=\"card mt\" style=\"border-left:4px solid #f59e0b\"><h2>🤝 Clínicas aguardando sua resposta</h2>");
            for (Row n : turn) b.append(SharedPages.negMini(r, n));
            b.append("</div>");
        }
        StringBuilder pl = new StringBuilder();
        for (Row o : fut) {
            Svc.Calc c = Svc.settle(o);
            pl.append("<a class=\"item\" style=\"text-decoration:none;color:inherit\" href=\"/dentista/agenda\"><div class=\"tm\">").append(o.s("data").substring(8)).append("/").append(o.s("data"), 5, 7)
                .append("<small>").append(o.s("hora_ini")).append("</small></div><div class=\"info\"><b>").append(h(Svc.nome(o.s("clinica_id")))).append("</b>")
                .append(o.s("data").equals(today()) ? " " + badge("Hoje", "b-p") : "").append("<div class=\"mut\">").append(c.n()).append(" paciente(s) · ").append(h(Svc.termsText(Svc.T.of(o, "f_"))))
                .append("</div></div><b style=\"color:#6366f1\">").append(money(c.rep())).append("</b></a>");
        }
        for (Row x : Db.q("SELECT r.*, s.titulo FROM reservas r JOIN salas s ON s.id=r.sala_id WHERE r.profissional_id=? AND r.status='ACEITA' AND r.data>=? ORDER BY r.data", r.uid(), today()))
            pl.append("<a class=\"item\" style=\"text-decoration:none;color:inherit;border-left-color:#0ea5e9\" href=\"/dentista/salas\"><div class=\"tm\">").append(x.s("data").substring(8)).append("/").append(x.s("data"), 5, 7)
                .append("<small>").append(x.s("hora_ini")).append("</small></div><div class=\"info\"><b>🚪 ").append(h(x.s("titulo"))).append("</b><div class=\"mut\">Sala alugada · ")
                .append(h(Svc.nome(x.s("clinica_id")))).append("</div></div><b>").append(money(x.d("valor"))).append("</b></a>");
        StringBuilder op = new StringBuilder();
        for (int i = 0; i < Math.min(4, paraMim.size()); i++) {
            Row o = (Row) paraMim.get(i)[0];
            Svc.Match m = (Svc.Match) paraMim.get(i)[1];
            Svc.Calc c = Svc.calc(Svc.T.of(o, ""), Svc.oppAppts(o));
            op.append("<a class=\"item\" style=\"text-decoration:none;color:inherit\" href=\"/dentista/oportunidade?id=").append(o.s("id")).append("\"><div class=\"info\"><b>").append(h(o.s("titulo"))).append("</b> ")
                .append(Svc.matchBadge(m)).append(o.b("urgente") ? " " + badge("🔥", "b-hot") : "").append("<div class=\"mut\">").append(h(Svc.nome(o.s("clinica_id")))).append(" · ").append(dw(o.s("data"))).append(" ")
                .append(o.s("hora_ini")).append("–").append(o.s("hora_fim")).append(m.km() != null ? " · " + Svc.kmText(m.km()) : "").append("</div><div class=\"mut\">").append(h(Svc.termsText(Svc.T.of(o, ""))))
                .append("</div></div><b style=\"color:#6366f1\">").append(money(c.rep())).append("</b></a>");
        }
        b.append("<div class=\"grid g2 mt\"><div class=\"card\"><div class=\"bar\"><h2>Próximos compromissos</h2><a class=\"btn sm sec\" href=\"/dentista/agenda\">Minha agenda</a></div>")
            .append(pl.length() == 0 ? empty("Nenhum turno confirmado. Que tal negociar um turno?") : pl)
            .append("</div><div class=\"card\"><div class=\"bar\"><h2>🎯 Melhores matches para você</h2><a class=\"btn sm sec\" href=\"/dentista/oportunidades\">Ver todos</a></div>")
            .append(op.length() == 0 ? empty("Sem novos turnos compatíveis agora. Você será notificado quando surgirem.") : op).append("</div></div>");
        r.html(page(r, "Início", b.toString()));
    }

    // ================= FEED =================
    static void feed(Req r) throws Exception {
        String esp = r.p("esp"), modelo = r.p("modelo"), mat = r.p("mat"), ordem = r.p("ordem").isEmpty() ? "match" : r.p("ordem");
        int raio = r.intp("raio", 0);
        boolean todas = r.chk("todas");
        boolean dent = Svc.cat(r.user).equals("DENTISTA");
        List<String> sp = Arrays.asList(r.user.s("especialidades").split(";"));
        List<Object[]> l = new ArrayList<>();
        for (Row o : abertas(r)) {
            if (dent && !todas && esp.isEmpty() && !sp.contains(o.s("especialidade"))) continue;
            if (dent && !esp.isEmpty() && !o.s("especialidade").equals(esp)) continue;
            if (!modelo.isEmpty() && !o.s("modelo").equals(modelo)) continue;
            if (!mat.isEmpty() && !o.s("materiais").equals(mat)) continue;
            Svc.Match m = Svc.match(r.user, o, Svc.user(o.s("clinica_id")));
            if (raio > 0 && (m.km() == null || m.km() > raio)) continue;
            l.add(new Object[]{o, Svc.calc(Svc.T.of(o, ""), Svc.oppAppts(o)), m});
        }
        if (ordem.equals("ganho")) l.sort((a, b) -> ((Svc.Calc) b[1]).rep().compareTo(((Svc.Calc) a[1]).rep()));
        else if (ordem.equals("distancia")) l.sort(Comparator.comparingDouble(a -> ((Svc.Match) a[2]).km() == null ? 1e9 : ((Svc.Match) a[2]).km()));
        else if (ordem.equals("match")) l.sort((a, b) -> Integer.compare(((Svc.Match) b[2]).score(), ((Svc.Match) a[2]).score()));
        String[][] mo = {{"", "Todos os modelos"}, Svc.MODELOS[0], Svc.MODELOS[1], Svc.MODELOS[2]};
        String[][] ma = {{"", "Qualquer material"}, {"CLINICA", "Materiais da clínica"}, {"DENTISTA", "Profissional traz"}, {"COMPARTILHADO", "Compartilhado"}};
        StringBuilder b = new StringBuilder("<div class=\"card\"><form method=\"get\" action=\"/dentista/oportunidades\"><div class=\"row\">");
        if (dent) b.append(select("Especialidade", "esp", withAll("Minhas especialidades", opts(Svc.ESPECIALIDADES)), esp, ""));
        b.append(select("Modelo", "modelo", mo, modelo, "")).append(select("Materiais", "mat", ma, mat, ""))
            .append(select("Distância", "raio", new String[][]{{"0", "Qualquer"}, {"5", "Até 5 km"}, {"10", "Até 10 km"}, {"20", "Até 20 km"}, {"50", "Até 50 km"}}, String.valueOf(raio), ""))
            .append(select("Ordenar", "ordem", new String[][]{{"match", "Melhor match"}, {"data", "Mais próximos (data)"}, {"distancia", "Mais perto"}, {"ganho", "Maior ganho"}}, ordem, "")).append("</div>")
            .append(dent ? check("Mostrar todas as especialidades", "todas", todas) : "").append(" <button class=\"btn sm\">Filtrar</button></form></div><div class=\"grid g2\">");
        for (Object[] x : l) b.append(card(r, (Row) x[0], (Svc.Calc) x[1], (Svc.Match) x[2]));
        b.append("</div>");
        if (l.isEmpty()) b.append(empty("Nenhum turno com esses filtros. Você será notificado quando surgirem turnos compatíveis."));
        r.html(page(r, "Turnos para mim", b.toString()));
    }

    static String card(Req r, Row o, Svc.Calc c, Svc.Match m) {
        Row n = Db.one("SELECT * FROM negociacoes WHERE oportunidade_id=? AND dentista_id=?", o.s("id"), r.uid());
        long concorr = Db.count("SELECT COUNT(*) FROM negociacoes WHERE oportunidade_id=? AND status='ABERTA'", o.s("id"));
        Svc.T t = Svc.T.of(o, "");
        Row cl = Svc.user(o.s("clinica_id"));
        String acao;
        if (n != null) {
            n.put("ostatus", o.s("status"));
            acao = "<a class=\"btn " + (Svc.myTurn(r, n) ? "" : "gh") + "\" href=\"/negociacao?id=" + n.s("id") + "\">" + (Svc.myTurn(r, n) ? "Responder clínica" : "Ver minha proposta") + "</a>";
        } else acao = "<a class=\"btn\" href=\"/dentista/oportunidade?id=" + o.s("id") + "\">Ver detalhes e negociar</a>";
        return "<div class=\"card" + (o.b("urgente") ? " hot" : "") + "\"><div class=\"bar\" style=\"margin-bottom:4px\"><div><b>" + h(cl.s("nome")) + "</b> " + Svc.stars(cl.s("id")) + " " + Svc.selos(cl)
            + "<div class=\"mut\">📍 " + (m.km() != null ? Svc.kmText(m.km()) + " · " : "") + h(cl.s("cidade")) + "</div></div><div class=\"tools\">" + Svc.matchBadge(m) + (o.b("urgente") ? badge("🔥 Urgente", "b-hot") : "") + "</div></div>"
            + "<b>" + h(o.s("titulo")) + "</b> " + (Svc.catOpp(o).equals("DENTISTA") ? badge(o.s("especialidade"), "b-m") : "") + "<div class=\"mut\">📅 " + dLong(o.s("data")) + " · " + o.s("hora_ini") + "–" + o.s("hora_fim") + "</div>"
            + "<div class=\"terms mt\">" + h(Svc.termsText(t)) + "</div><div class=\"mut\">" + Svc.matText(t.mat()) + " · " + c.n() + " paciente(s) agendado(s)</div>"
            + "<div class=\"gain\"><span class=\"mut\">Seu ganho estimado<br><small>produção " + money(c.prod()) + "</small></span><b>" + money(c.rep()) + "</b></div>"
            + Svc.matchWhy(m) + "<div class=\"bar mt\" style=\"margin-bottom:0\"><span class=\"mut\">" + concorr + " profissional(is) negociando</span>" + acao + "</div></div>";
    }

    static void detalhe(Req r) throws Exception {
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=?", r.p("id"));
        if (o == null) throw new Bad("Turno não encontrado.");
        Row n = Db.one("SELECT * FROM negociacoes WHERE oportunidade_id=? AND dentista_id=?", o.s("id"), r.uid());
        if (n != null) { r.redirect("/negociacao?id=" + n.s("id")); return; }
        if (!o.s("status").equals("ABERTA") || o.s("data").compareTo(today()) < 0) {
            r.err("Este turno não está mais disponível.");
            r.redirect("/dentista/oportunidades");
            return;
        }
        if (!Svc.catOpp(o).equals(Svc.cat(r.user))) throw new Bad("Este turno é destinado a: " + Svc.label(Svc.CATEGORIAS, Svc.catOpp(o)) + ".");
        Row c = Svc.user(o.s("clinica_id"));
        Svc.T t = Svc.T.of(o, "");
        List<Row> ap = Svc.oppAppts(o);
        Svc.Calc calc = Svc.calc(t, ap);
        Svc.Match m = Svc.match(r.user, o, c);
        StringBuilder pl = new StringBuilder();
        int i = 1;
        for (Row a : ap) pl.append("<div class=\"mut\">• ").append(a.s("hora")).append(" — Paciente ").append(i++).append(" · ").append(h(a.s("procedimento"))).append(" · ")
            .append(a.s("pagador").equals("CONVENIO") ? "Convênio" : "Particular").append(" · ").append(money(a.d("valor"))).append("</div>");
        boolean liberado = !Svc.exigeVerificacao() || r.user.b("verificado");
        String acoes = !liberado ? "<div class=\"banner\">Seu cadastro está em verificação. Você poderá enviar propostas após a aprovação.</div>"
            : "<div class=\"card\" style=\"background:#f0fdf4\"><h3>✅ Aceitar a oferta da clínica</h3><p>Você topa <b>" + h(Svc.termsText(t)) + "</b> — " + h(Svc.label(Svc.MATERIAIS, t.mat()).toLowerCase())
            + ". A clínica escolhe entre os profissionais interessados.</p><form method=\"post\" action=\"/dentista/proposta\">" + r.csrf()
            + "<input type=\"hidden\" name=\"id\" value=\"" + o.s("id") + "\"><input type=\"hidden\" name=\"aceitar\" value=\"1\">"
            + input("Mensagem para a clínica (opcional)", "texto", "", "text", "maxlength=\"500\"") + "<button class=\"btn ok mt\">Aceitar " + h(Svc.termsText(t)) + "</button></form></div>"
            + (o.b("aceita_contra") ? "<div class=\"card\"><h3>💬 Fazer contraproposta</h3><form method=\"post\" action=\"/dentista/proposta\">" + r.csrf()
            + "<input type=\"hidden\" name=\"id\" value=\"" + o.s("id") + "\">" + Svc.termsFields(t)
            + input("Justificativa / mensagem", "texto", "", "text", "maxlength=\"500\" placeholder=\"Ex.: tenho especialização e kit próprio...\"")
            + "<button class=\"btn acc mt\">Enviar contraproposta</button></form></div>" : "<p class=\"mut\">Esta clínica não aceita contraproposta neste turno.</p>")
            + "<p class=\"mut\">💬 Por segurança, telefones, e-mails e links ficam ocultos no chat até o fechamento. Depois, os contatos são liberados para as duas partes.</p>";
        String body = "<a class=\"btn sm gh\" href=\"/dentista/oportunidades\">← Voltar</a><div class=\"grid g2 mt\"><div><div class=\"card\"><div class=\"bar\"><h2 style=\"margin:0\">" + h(o.s("titulo")) + "</h2>" + Svc.matchBadge(m) + "</div>"
            + Svc.matchWhy(m)
            + "<p>🏥 <b>" + h(c.s("nome")) + "</b> " + Svc.stars(c.s("id")) + " " + Svc.selos(c) + "<br><span class=\"mut\">📍 " + h(c.s("cidade")) + (m.km() != null ? " · " + Svc.kmText(m.km()) + " de você" : "")
            + " · " + Math.max(1, c.i("cadeiras")) + " cadeira(s) · endereço completo liberado após o fechamento</span></p>"
            + "<p>📅 " + dLong(o.s("data")) + "<br>⏰ " + o.s("hora_ini") + " às " + o.s("hora_fim") + " · " + h(Svc.label(Svc.CATEGORIAS, Svc.catOpp(o)))
            + (Svc.catOpp(o).equals("DENTISTA") ? " · " + h(o.s("especialidade")) : "") + "</p>"
            + "<h4>Oferta da clínica</h4><div class=\"terms\" style=\"font-size:22px\">" + h(Svc.termsText(t)) + "</div><div class=\"mut\">" + h(Svc.label(Svc.MODELOS, t.modelo())) + "</div>"
            + "<p>" + Svc.matText(t.mat()) + (o.has("obs_materiais") ? "<br><span class=\"mut\">" + h(o.s("obs_materiais")) + "</span>" : "") + "</p>"
            + (o.has("requisitos") ? "<p><b>Requisitos:</b> " + h(o.s("requisitos")) + "</p>" : "")
            + "<h4>Pacientes agendados</h4>" + (pl.length() == 0 ? "<p class=\"mut\">Ainda sem pacientes — a clínica pode agendar após a contratação.</p>" : pl)
            + "<div class=\"gain\"><span class=\"mut\">Ganho estimado com a oferta</span><b>" + money(calc.rep()) + "</b></div>"
            + "<p class=\"mut\">🔒 Nomes e prontuários são liberados somente após o fechamento.</p>"
            + "<p class=\"mut\">⚠ Política de cancelamento após fechar: >24h livre · 24h–6h registrado · <6h ou falta = strike.</p></div></div><div>" + acoes + "</div></div>";
        r.html(page(r, "Detalhes do turno", body));
    }

    static void proposta(Req r) throws Exception {
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=?", r.p("id"));
        if (o == null || !o.s("status").equals("ABERTA") || o.s("data").compareTo(today()) < 0) throw new Bad("Turno indisponível.");
        if (!Svc.catOpp(o).equals(Svc.cat(r.user))) throw new Bad("Este turno é destinado a: " + Svc.label(Svc.CATEGORIAS, Svc.catOpp(o)) + ".");
        if (Svc.exigeVerificacao() && !r.user.b("verificado")) throw new Bad("Seu cadastro está em verificação. Aguarde a aprovação para enviar propostas.");
        Row ex = Db.one("SELECT * FROM negociacoes WHERE oportunidade_id=? AND dentista_id=?", o.s("id"), r.uid());
        if (ex != null) { r.redirect("/negociacao?id=" + ex.s("id")); return; }
        boolean aceitar = r.p("aceitar").equals("1");
        Svc.T t = aceitar ? Svc.T.of(o, "") : Svc.termsFrom(r);
        if (!aceitar && !o.b("aceita_contra")) throw new Bad("Este turno não aceita contraproposta.");
        String txt = Svc.mask(r.p("texto").isEmpty() ? (aceitar ? "Aceito os termos propostos pela clínica." : "") : r.p("texto"));
        String[] nid = new String[1];
        Db.tx(() -> {
            nid[0] = Db.insert("negociacoes", Db.m("oportunidade_id", o.s("id"), "dentista_id", r.uid(), "status", "ABERTA", "atualizado", System.currentTimeMillis()));
            Db.insert("mensagens", Db.m("negociacao_id", nid[0], "autor", "DENTISTA", "tipo", "OFERTA", "modelo", t.modelo(), "pct_part", t.pp(), "pct_conv", t.pc(),
                "diaria", t.diaria(), "materiais", t.mat(), "texto", txt));
        });
        Svc.Match m = Svc.match(r.user, o, Svc.user(o.s("clinica_id")));
        Svc.notify(o.s("clinica_id"), r.user.s("nome") + " (" + m.score() + "% match) " + (aceitar ? "aceitou os termos" : "enviou contraproposta") + " em \"" + o.s("titulo") + "\": " + Svc.termsText(t) + ".", "/negociacao?id=" + nid[0]);
        Svc.audit(r, aceitar ? "PROPOSTA_ACEITE_TERMOS" : "CONTRAPROPOSTA", o.s("titulo") + " · " + Svc.termsText(t));
        r.ok((aceitar ? "Você topou o valor! " : "Contraproposta enviada! ") + "A clínica já foi notificada e verá sua proposta em Negociações. Você será avisado quando ela responder.");
        r.redirect("/negociacao?id=" + nid[0]);
    }

    // ================= AGENDA =================
    static void agenda(Req r) throws Exception {
        StringBuilder fut = new StringBuilder(), pas = new StringBuilder();
        for (Row o : Db.q("SELECT * FROM oportunidades WHERE dentista_id=? AND status='FECHADA' ORDER BY data, hora_ini", r.uid())) {
            if (o.s("data").compareTo(today()) >= 0) fut.append(dayCard(r, o));
            else pas.insert(0, dayCard(r, o));
        }
        String body = "<h4>Próximos turnos</h4>" + (fut.length() == 0 ? "<div class=\"card\">" + empty("Nenhum turno futuro.") + "<p class=\"center\"><a class=\"btn\" href=\"/dentista/oportunidades\">Ver turnos compatíveis</a></p></div>" : fut)
            + "<h4>Histórico</h4>" + (pas.length() == 0 ? "<div class=\"card\">" + empty("Sem histórico ainda.") + "</div>" : pas);
        r.html(page(r, "Minha agenda", body));
    }

    static String dayCard(Req r, Row o) {
        Row c = Svc.user(o.s("clinica_id"));
        boolean hoje = o.s("data").equals(today());
        List<Row> ap = Svc.oppAppts(o);
        Svc.Calc s = Svc.settle(o);
        double hrs = Svc.horasAte(o.s("data"), o.s("hora_ini"));
        StringBuilder b = new StringBuilder("<div class=\"card\"" + (hoje ? " style=\"border:2px solid #0d9488\"" : "") + "><div class=\"bar\"><div><h2 style=\"margin:0\">" + dLong(o.s("data"))
            + (hoje ? " " + badge("Hoje", "b-p") : "") + "</h2><div class=\"mut\">⏰ " + o.s("hora_ini") + "–" + o.s("hora_fim") + " · 🏥 <b>" + h(c.s("nome")) + "</b> · " + h(c.s("endereco"))
            + " — " + h(c.s("cidade")) + " · 📞 " + h(c.s("telefone")) + " · <a target=\"_blank\" rel=\"noopener\" href=\"https://www.google.com/maps/search/?api=1&query="
            + java.net.URLEncoder.encode(c.has("lat") ? c.s("lat") + "," + c.s("lng") : c.s("endereco") + " " + c.s("cidade"), java.nio.charset.StandardCharsets.UTF_8) + "\">Rota no mapa</a></div>"
            + "<div class=\"mut\">" + h(Svc.termsText(Svc.T.of(o, "f_"))) + " · " + Svc.matText(o.s("f_materiais")) + "</div></div><div class=\"tools\">");
        if (hoje && !o.has("checkin")) b.append(post(r, "/dentista/checkin", "📍 Fazer check-in", "sm ok", null, "id", o.s("id")));
        if (o.has("checkin")) b.append(badge("✅ Check-in " + o.s("checkin"), "b-ok"));
        b.append("<a class=\"btn sm gh\" href=\"/termo?id=").append(o.s("id")).append("\">📄 Termo</a>");
        if (hrs > -12) b.append(SharedPages.cancelForm(r, o, hrs));
        if (hrs <= 0 && hrs > -48)
            b.append("<form method=\"post\" action=\"/dentista/denunciar\" class=\"inl\" onsubmit=\"return confirm('Informar à administração que a clínica não te recebeu?')\">").append(r.csrf())
                .append("<input type=\"hidden\" name=\"id\" value=\"").append(o.s("id")).append("\"><button class=\"btn sm gh\">⚑ Clínica não me recebeu</button></form>");
        if (o.s("data").compareTo(today()) <= 0) b.append(SharedPages.reviewButton(r, o, o.s("clinica_id")));
        b.append("</div></div>");
        if (ap.isEmpty()) b.append(empty("Nenhum paciente agendado ainda."));
        for (Row a : ap) {
            Row p = Db.one("SELECT * FROM pacientes WHERE id=?", a.s("paciente_id"));
            if (p == null) continue;
            List<String> al = Svc.alertas(p);
            b.append("<div class=\"item st-").append(a.s("status")).append("\"><div class=\"tm\">").append(a.s("hora")).append("<small>").append(a.i("duracao")).append(" min</small></div><div class=\"info\"><b>")
                .append(h(p.s("nome"))).append("</b> ").append(al.isEmpty() ? "" : "<span class=\"badge b-e\" title=\"" + h(String.join(" | ", al)) + "\">⚠ Anamnese</span>")
                .append("<div class=\"mut\">").append(h(a.s("procedimento"))).append(" · ").append(a.s("pagador").equals("CONVENIO") ? h(a.s("convenio")) : "Particular").append(" · ").append(money(a.d("valor")))
                .append("</div>").append(a.has("obs") ? "<div class=\"mut\">📝 " + h(a.s("obs")) + "</div>" : "").append("</div><div class=\"tools\">")
                .append(Svc.apStatusSelect(r, a, "/dentista/agenda")).append("<a class=\"btn sm gh\" href=\"/prontuario?id=").append(p.s("id")).append("\">📋 Prontuário</a></div></div>");
        }
        long at = ap.stream().filter(a -> a.s("status").equals("ATENDIDO")).count();
        b.append("<div class=\"gain\"><span>").append(at).append("/").append(ap.size()).append(" atendidos · produção ").append(money(s.prod())).append("</span><span>Seu repasse <b>").append(money(s.rep()))
            .append("</b> ").append(Svc.settleKind(o)).append("</span></div></div>");
        return b.toString();
    }

    static void checkin(Req r) throws Exception {
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=? AND dentista_id=? AND status='FECHADA'", r.p("id"), r.uid());
        if (o == null || !o.s("data").equals(today())) throw new Bad("Check-in disponível apenas no dia do turno.");
        String agora = nowHm();
        Db.x("UPDATE oportunidades SET checkin=? WHERE id=?", agora, o.s("id"));
        Svc.notify(o.s("clinica_id"), "📍 " + r.user.s("nome") + " fez check-in às " + agora + ".", "/clinica/agenda");
        Svc.audit(r, "CHECKIN", o.s("titulo") + " " + agora);
        r.ok("Check-in realizado às " + agora + ".");
        r.redirect("/dentista/agenda");
    }

    static void denunciar(Req r) throws Exception {
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=? AND dentista_id=? AND status='FECHADA'", r.p("id"), r.uid());
        if (o == null) throw new Bad("Turno não encontrado.");
        if (Db.count("SELECT COUNT(*) FROM ocorrencias WHERE oportunidade_id=? AND tipo='DENUNCIA'", o.s("id")) > 0) throw new Bad("Você já informou este problema. A administração está analisando.");
        Svc.ocorrencia(r, o.s("clinica_id"), o.s("id"), "DENUNCIA", false, "Profissional " + r.user.s("nome") + " informou que não foi recebido(a) no turno \"" + o.s("titulo") + "\" de " + d(o.s("data")));
        Svc.notifyAdmins("⚑ Denúncia: " + r.user.s("nome") + " informou que " + Svc.nome(o.s("clinica_id")) + " não o(a) recebeu em " + d(o.s("data")) + ".", "/admin/ocorrencias?tipo=DENUNCIA");
        r.ok("Ocorrência enviada à administração para análise.");
        r.redirect("/dentista/agenda");
    }

    // ================= SALAS =================
    static void salas(Req r) throws Exception {
        int raio = r.intp("raio", 0);
        List<Object[]> l = new ArrayList<>();
        for (Row s : Db.q("SELECT s.* FROM salas s JOIN usuarios u ON u.id=s.clinica_id WHERE s.ativa=TRUE AND u.ativo=TRUE")) {
            Row c = Svc.user(s.s("clinica_id"));
            Double km = Svc.distKm(r.user, c);
            if (raio > 0 && (km == null || km > raio)) continue;
            l.add(new Object[]{s, c, km});
        }
        l.sort(Comparator.comparingDouble(a -> a[2] == null ? 1e9 : (Double) a[2]));
        boolean liberado = !Svc.exigeVerificacao() || r.user.b("verificado");
        StringBuilder b = new StringBuilder("<div class=\"card\"><form method=\"get\" action=\"/dentista/salas\" class=\"tools\">")
            .append(select("Distância", "raio", new String[][]{{"0", "Qualquer"}, {"5", "Até 5 km"}, {"10", "Até 10 km"}, {"20", "Até 20 km"}, {"50", "Até 50 km"}}, String.valueOf(raio), "onchange=\"this.form.submit()\""))
            .append("</form><p class=\"mut\">Alugue um consultório equipado para atender seus próprios pacientes. A clínica confirma e você recebe o comprovante por e-mail.</p></div><div class=\"grid g2\">");
        String[] dias = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
        for (Object[] x : l) {
            Row s = (Row) x[0], c = (Row) x[1];
            Double km = (Double) x[2];
            StringBuilder dd = new StringBuilder();
            for (String d : s.s("dias").split(",")) try { dd.append(dias[Integer.parseInt(d) - 1]).append(" "); } catch (Exception ignored) { }
            b.append("<div class=\"card\"><div class=\"bar\" style=\"margin-bottom:4px\"><div><b>").append(h(s.s("titulo"))).append("</b><div class=\"mut\">🏥 ").append(h(c.s("nome"))).append(" ")
                .append(Svc.stars(c.s("id"))).append(" · 📍 ").append(km != null ? Svc.kmText(km) + " · " : "").append(h(c.s("cidade"))).append("</div></div><div class=\"terms\">").append(money(s.d("valor")))
                .append("</div></div><div class=\"mut\">").append(h(Svc.label(Svc.MODALIDADE_SALA, s.s("modalidade")))).append(" · dias: ").append(dd).append("</div><p>").append(h(s.s("descricao")))
                .append("</p><p class=\"mut\">🧰 ").append(h(s.s("equipamentos"))).append("</p>");
            if (liberado) {
                b.append("<form method=\"post\" action=\"/dentista/reserva\">").append(r.csrf()).append("<input type=\"hidden\" name=\"sala\" value=\"").append(s.s("id")).append("\">")
                    .append("<div class=\"row\">").append(input("Data", "data", plusDays(today(), 1), "date", "required min=\"" + today() + "\""))
                    .append(input("Início", "hora_ini", "08:00", "time", "required")).append(input("Fim", "hora_fim", "12:00", "time", "required")).append("</div>")
                    .append(input("Mensagem para a clínica (opcional)", "mensagem", "", "text", "maxlength=\"500\"")).append("<button class=\"btn sm mt\">Solicitar reserva</button></form>");
            } else b.append("<p class=\"mut\">Reservas liberadas após a verificação do seu cadastro.</p>");
            b.append("</div>");
        }
        b.append("</div>");
        if (l.isEmpty()) b.append("<div class=\"card\">").append(empty("Nenhuma sala disponível com esse filtro no momento.")).append("</div>");
        List<String[]> rows = new ArrayList<>();
        for (Row x : Db.q("SELECT r.*, s.titulo FROM reservas r JOIN salas s ON s.id=r.sala_id WHERE r.profissional_id=? ORDER BY r.data DESC", r.uid())) {
            Row c = Svc.user(x.s("clinica_id"));
            boolean podeCancelar = (x.s("status").equals("PENDENTE") || x.s("status").equals("ACEITA")) && x.s("data").compareTo(today()) >= 0;
            rows.add(new String[]{dw(x.s("data")) + " " + x.s("hora_ini") + "–" + x.s("hora_fim"), h(x.s("titulo")), h(c.s("nome")) + (x.s("status").equals("ACEITA") ? "<div class=\"mut\">📍 " + h(c.s("endereco")) + " · 📞 " + h(c.s("telefone")) + "</div>" : ""),
                money(x.d("valor")), badge(x.s("status"), x.s("status").equals("ACEITA") ? "b-ok" : x.s("status").equals("PENDENTE") ? "b-w" : "b-m") + (x.has("resposta") ? "<div class=\"mut\">" + h(x.s("resposta")) + "</div>" : ""),
                podeCancelar ? post(r, "/dentista/reserva/cancelar", "Cancelar", "sm red", "Cancelar esta reserva?", "id", x.s("id")) : ""});
        }
        b.append("<div class=\"card mt\"><h2>Minhas reservas</h2>").append(table(new String[]{"Data", "Sala", "Clínica", "Valor", "Situação", ""}, rows, "Você ainda não fez reservas.")).append("</div>");
        r.html(page(r, "Alugar sala", b.toString()));
    }

    static BigDecimal valorReserva(Row s, String hi, String hf) {
        BigDecimal v = s.d("valor");
        return switch (s.s("modalidade")) {
            case "HORA" -> v.multiply(BigDecimal.valueOf(ClinicPages.toMin(hf) - ClinicPages.toMin(hi))).divide(new BigDecimal(60), 2, RoundingMode.HALF_UP);
            case "TURNO" -> v.multiply(BigDecimal.valueOf(Svc.turnos(hi, hf).size()));
            default -> v;
        };
    }

    static void reservar(Req r) throws Exception {
        if (Svc.exigeVerificacao() && !r.user.b("verificado")) throw new Bad("Reservas liberadas após a verificação do seu cadastro.");
        Row s = Db.one("SELECT * FROM salas WHERE id=? AND ativa=TRUE", r.p("sala"));
        if (s == null) throw new Bad("Sala indisponível.");
        String data = r.p("data"), hi = r.p("hora_ini"), hf = r.p("hora_fim");
        if (!Svc.isDate(data) || data.compareTo(today()) < 0) throw new Bad("Informe uma data a partir de hoje.");
        if (!Svc.isTime(hi) || !Svc.isTime(hf) || hf.compareTo(hi) <= 0) throw new Bad("O horário final deve ser depois do inicial.");
        int dow = LocalDate.parse(data).getDayOfWeek().getValue();
        if (!Arrays.asList(s.s("dias").split(",")).contains(String.valueOf(dow))) throw new Bad("A sala não está disponível nesse dia da semana.");
        if (Db.count("SELECT COUNT(*) FROM reservas WHERE sala_id=? AND status='ACEITA' AND data=? AND hora_ini<? AND hora_fim>?", s.s("id"), data, hf, hi) > 0)
            throw new Bad("Esse horário já está reservado. Escolha outro.");
        if (Db.count("SELECT COUNT(*) FROM reservas WHERE sala_id=? AND profissional_id=? AND status='PENDENTE' AND data=? AND hora_ini<? AND hora_fim>?", s.s("id"), r.uid(), data, hf, hi) > 0)
            throw new Bad("Você já tem um pedido pendente para esse horário.");
        BigDecimal valor = valorReserva(s, hi, hf);
        Db.insert("reservas", Db.m("sala_id", s.s("id"), "clinica_id", s.s("clinica_id"), "profissional_id", r.uid(), "data", data, "hora_ini", hi, "hora_fim", hf, "valor", valor,
            "mensagem", Svc.mask(r.p("mensagem")), "resposta", "", "status", "PENDENTE"));
        Svc.notify(s.s("clinica_id"), "🚪 " + r.user.s("nome") + " pediu para reservar \"" + s.s("titulo") + "\" em " + d(data) + " (" + hi + "–" + hf + ") · " + money(valor) + ".", "/clinica/salas");
        Svc.audit(r, "RESERVA_SOLICITADA", s.s("titulo") + " " + data);
        r.ok("Pedido de reserva enviado (" + money(valor) + "). Você será avisado quando a clínica responder.");
        r.redirect("/dentista/salas");
    }

    static void cancelarReserva(Req r) throws Exception {
        Row x = Db.one("SELECT r.*, s.titulo FROM reservas r JOIN salas s ON s.id=r.sala_id WHERE r.id=? AND r.profissional_id=?", r.p("id"), r.uid());
        if (x == null || !(x.s("status").equals("PENDENTE") || x.s("status").equals("ACEITA"))) throw new Bad("Reserva não pode ser cancelada.");
        Db.x("UPDATE reservas SET status='CANCELADA' WHERE id=?", x.s("id"));
        Svc.notify(x.s("clinica_id"), r.user.s("nome") + " cancelou a reserva da sala \"" + x.s("titulo") + "\" em " + d(x.s("data")) + ".", "/clinica/salas");
        r.ok("Reserva cancelada.");
        r.redirect("/dentista/salas");
    }

    // ================= GANHOS =================
    static void ganhos(Req r) throws Exception {
        BigDecimal rec = BigDecimal.ZERO, pend = BigDecimal.ZERO, prev = BigDecimal.ZERO;
        List<String[]> rows = new ArrayList<>();
        for (Row o : Db.q("SELECT * FROM oportunidades WHERE dentista_id=? AND status='FECHADA' ORDER BY data DESC", r.uid())) {
            Svc.Calc c = Svc.settle(o);
            if (o.b("repasse_pago")) rec = rec.add(c.rep());
            else if (o.s("data").compareTo(today()) <= 0) pend = pend.add(c.rep());
            else prev = prev.add(c.rep());
            List<Row> ap = Svc.oppAppts(o);
            long at = ap.stream().filter(a -> a.s("status").equals("ATENDIDO")).count();
            rows.add(new String[]{dw(o.s("data")), h(Svc.nome(o.s("clinica_id"))), h(Svc.termsText(Svc.T.of(o, "f_"))), at + "/" + ap.size(), money(c.prod()), "<b>" + money(c.rep()) + "</b>", Svc.settleKind(o)});
        }
        String body = "<div class=\"grid g3\">" + kpi("Recebido", money(rec), "✅") + kpi("A receber", money(pend), "⏳") + kpi("Previsto (próximos turnos)", money(prev), "🗓️") + "</div>"
            + "<div class=\"card mt\"><h2>Extrato por turno</h2>" + table(new String[]{"Data", "Clínica", "Termos", "Atendidos", "Produção", "Seu repasse", "Situação"}, rows, "Nenhum turno ainda.")
            + "<p class=\"mut\">💡 Percentuais incidem sobre procedimentos marcados como \"Atendido\". Diárias são devidas pelo turno realizado. "
            + "Mantenha sua chave PIX atualizada em <a href=\"/conta/perfil\">Meu perfil</a>.</p></div>";
        r.html(page(r, "Ganhos", body));
    }
}

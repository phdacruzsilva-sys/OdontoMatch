package odontomatch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static odontomatch.Ui.*;

/** Área da clínica: agenda, pacientes, turnos, match, profissionais, salas e repasses. */
final class ClinicPages {
    private ClinicPages() {}

    static void routes() {
        App.get("/clinica", ClinicPages::dashboard);
        App.get("/clinica/agenda", ClinicPages::agenda);
        App.get("/clinica/agendamento", ClinicPages::apptForm);
        App.post("/clinica/agendamento/salvar", ClinicPages::apptSave);
        App.get("/clinica/pacientes", ClinicPages::pacientes);
        App.get("/clinica/paciente", ClinicPages::pacienteForm);
        App.post("/clinica/paciente/salvar", ClinicPages::pacienteSave);
        App.get("/clinica/oportunidades", ClinicPages::oportunidades);
        App.get("/clinica/oportunidade", ClinicPages::oppForm);
        App.post("/clinica/oportunidade/salvar", ClinicPages::oppSave);
        App.post("/clinica/oportunidade/cancelar", ClinicPages::oppCancel);
        App.get("/clinica/match", ClinicPages::matchPage);
        App.post("/clinica/noshow", ClinicPages::noShow);
        App.get("/clinica/profissionais", ClinicPages::profissionais);
        App.post("/clinica/convidar", ClinicPages::convidar);
        App.get("/clinica/salas", ClinicPages::salas);
        App.get("/clinica/sala", ClinicPages::salaForm);
        App.post("/clinica/sala/salvar", ClinicPages::salaSave);
        App.post("/clinica/sala/ativar", ClinicPages::salaToggle);
        App.post("/clinica/reserva/responder", ClinicPages::reservaResponder);
        App.get("/clinica/repasses", ClinicPages::repasses);
        App.post("/clinica/repasse/pagar", ClinicPages::pagarRepasse);
    }

    // ================= INÍCIO =================
    static void dashboard(Req r) throws Exception {
        String id = r.uid(), hoje = today(), mes = month();
        List<Row> hojeAp = Db.q("SELECT * FROM agendamentos WHERE clinica_id=? AND data=? AND status<>'CANCELADO' ORDER BY hora", id, hoje);
        long abertas = Db.count("SELECT COUNT(*) FROM oportunidades WHERE clinica_id=? AND status='ABERTA' AND data>=?", id, hoje);
        List<Row> turn = new ArrayList<>();
        for (Row n : Svc.myNegs(r)) if (Svc.myTurn(r, n)) turn.add(n);
        long resPend = Db.count("SELECT COUNT(*) FROM reservas WHERE clinica_id=? AND status='PENDENTE'", id);
        BigDecimal prod = BigDecimal.ZERO;
        for (Row a : Db.q("SELECT valor FROM agendamentos WHERE clinica_id=? AND status='ATENDIDO' AND data>=? AND data<=?", id, mes + "-01", mes + "-31")) prod = prod.add(a.d("valor"));
        Map<String, List<Row>> gaps = new TreeMap<>();
        for (Row a : Db.q("SELECT * FROM agendamentos WHERE clinica_id=? AND dentista_id IS NULL AND status<>'CANCELADO' AND data>=? ORDER BY data, hora", id, hoje)) {
            long cov = Db.count("SELECT COUNT(*) FROM oportunidades WHERE clinica_id=? AND status='ABERTA' AND data=? AND hora_ini<=? AND hora_fim>?", id, a.s("data"), a.s("hora"), a.s("hora"));
            if (cov == 0) gaps.computeIfAbsent(a.s("data"), k -> new ArrayList<>()).add(a);
        }
        StringBuilder b = new StringBuilder("<div class=\"grid g4\">").append(kpi("Consultas hoje", String.valueOf(hojeAp.size()), "📅"))
            .append(kpi("Turnos abertos", String.valueOf(abertas), "📣")).append(kpi("Propostas aguardando você", String.valueOf(turn.size()), "🤝"))
            .append(kpi("Produção do mês (atendidos)", money(prod), "💰")).append("</div>");
        if (resPend > 0) b.append("<div class=\"banner mt\">🚪 Você tem <b>").append(resPend).append(" pedido(s) de reserva de sala</b> aguardando resposta. <a href=\"/clinica/salas\">Responder</a></div>");
        if (!gaps.isEmpty()) {
            b.append("<div class=\"card mt\" style=\"border-left:4px solid #f59e0b;background:#fffbeb\"><h2>⚠️ Pacientes agendados sem profissional</h2>");
            for (var e : gaps.entrySet()) {
                List<Row> l = e.getValue();
                StringBuilder hs = new StringBuilder();
                for (Row a : l) hs.append(a.s("hora")).append(" ");
                b.append("<div class=\"item\"><div class=\"info\"><b>").append(dLong(e.getKey())).append("</b><div class=\"mut\">").append(l.size()).append(" paciente(s): ").append(hs)
                    .append("</div></div><a class=\"btn sm acc\" href=\"/clinica/oportunidade?data=").append(e.getKey()).append("&hora=").append(l.get(0).s("hora"))
                    .append("\">📣 Publicar turno</a></div>");
            }
            b.append("</div>");
        }
        StringBuilder ag = new StringBuilder();
        for (Row a : hojeAp) ag.append(apptItem(r, a, "/clinica"));
        StringBuilder pr = new StringBuilder();
        for (Row n : turn) pr.append(SharedPages.negMini(r, n));
        b.append("<div class=\"grid g2 mt\"><div class=\"card\"><div class=\"bar\"><h2>Agenda de hoje</h2><a class=\"btn sm sec\" href=\"/clinica/agenda\">Abrir agenda</a></div>")
            .append(ag.length() == 0 ? empty("Nenhuma consulta hoje.") : ag).append("</div><div class=\"card\"><div class=\"bar\"><h2>Propostas aguardando resposta</h2><a class=\"btn sm sec\" href=\"/negociacoes\">Ver todas</a></div>")
            .append(pr.length() == 0 ? empty("Nenhuma proposta aguardando você.") : pr).append("</div></div>");
        r.html(page(r, "Início", b.toString()));
    }

    static String apptItem(Req r, Row a, String back) {
        Row p = Db.one("SELECT * FROM pacientes WHERE id=?", a.s("paciente_id"));
        if (p == null) return "";
        List<String> al = Svc.alertas(p);
        String wa = Svc.waLink(p, a, r.user.s("nome"));
        return "<div class=\"item st-" + a.s("status") + "\"><div class=\"tm\">" + a.s("hora") + "<small>" + a.i("duracao") + " min</small></div><div class=\"info\"><b>" + h(p.s("nome")) + "</b> "
            + (al.isEmpty() ? "" : "<span class=\"badge b-e\" title=\"" + h(String.join(" | ", al)) + "\">⚠ Anamnese</span>")
            + "<div class=\"mut\">" + h(a.s("procedimento")) + " · " + (a.s("pagador").equals("CONVENIO") ? h(a.s("convenio")) : "Particular") + " · " + money(a.d("valor")) + "</div>"
            + "<div class=\"mut\">" + (a.has("dentista_id") ? "🦷 " + h(Svc.nome(a.s("dentista_id"))) : "<span class=\"badge b-w\">Sem profissional</span>") + (a.has("obs") ? " · 📝 " + h(a.s("obs")) : "") + "</div></div>"
            + "<div class=\"tools\">" + Svc.apStatusSelect(r, a, back) + "<a class=\"btn sm gh\" href=\"/prontuario?id=" + p.s("id") + "\">📋 Prontuário</a>"
            + (wa.isEmpty() ? "" : "<a class=\"btn sm gh\" target=\"_blank\" rel=\"noopener\" href=\"" + h(wa) + "\">💬 WhatsApp</a>")
            + "<a class=\"btn sm gh\" href=\"/clinica/agendamento?id=" + a.s("id") + "\">✏️</a></div></div>";
    }

    // ================= AGENDA =================
    static void agenda(Req r) throws Exception {
        String dia = Svc.isDate(r.p("dia")) ? r.p("dia") : today();
        String id = r.uid();
        StringBuilder w = new StringBuilder("<div class=\"week\">");
        for (int i = -3; i <= 3; i++) {
            String x = plusDays(dia, i);
            long n = Db.count("SELECT COUNT(*) FROM agendamentos WHERE clinica_id=? AND data=? AND status<>'CANCELADO'", id, x);
            w.append("<a class=\"wd").append(x.equals(dia) ? " on" : "").append("\" href=\"/clinica/agenda?dia=").append(x).append("\"><small>").append(dw(x).substring(0, 3))
                .append("</small><b>").append(x.substring(8)).append("</b><span>").append(n > 0 ? n + " pac." : "").append(x.equals(today()) ? " •hoje" : "").append("</span></a>");
        }
        w.append("</div>");
        StringBuilder ops = new StringBuilder();
        for (Row o : Db.q("SELECT * FROM oportunidades WHERE clinica_id=? AND data=? AND status<>'CANCELADA' ORDER BY hora_ini", id, dia)) {
            boolean f = o.s("status").equals("FECHADA");
            ops.append("<div class=\"item\" style=\"border-left-color:").append(f ? "#16a34a" : "#6366f1").append("\"><div class=\"info\"><b>📣 ").append(h(o.s("titulo"))).append("</b> ")
                .append(Svc.oppBadge(o)).append("<div class=\"mut\">").append(o.s("hora_ini")).append("–").append(o.s("hora_fim")).append(" · ")
                .append(h(Svc.termsText(f ? Svc.T.of(o, "f_") : Svc.T.of(o, "")))).append(f ? " · 🦷 " + h(Svc.nome(o.s("dentista_id"))) : "")
                .append(o.has("checkin") ? " · ✅ check-in " + o.s("checkin") : "").append("</div></div><a class=\"btn sm gh\" href=\"")
                .append(f ? "/termo?id=" + o.s("id") : "/negociacoes?op=" + o.s("id")).append("\">").append(f ? "📄 Termo" : "🤝 Propostas").append("</a></div>");
        }
        for (Row x : Db.q("SELECT r.*, s.titulo FROM reservas r JOIN salas s ON s.id=r.sala_id WHERE r.clinica_id=? AND r.data=? AND r.status='ACEITA' ORDER BY r.hora_ini", id, dia))
            ops.append("<div class=\"item\" style=\"border-left-color:#0ea5e9\"><div class=\"info\"><b>🚪 ").append(h(x.s("titulo"))).append("</b> ").append(badge("Sala alugada", "b-p"))
                .append("<div class=\"mut\">").append(x.s("hora_ini")).append("–").append(x.s("hora_fim")).append(" · ").append(h(Svc.nome(x.s("profissional_id")))).append(" · ").append(money(x.d("valor"))).append("</div></div></div>");
        StringBuilder ap = new StringBuilder();
        for (Row a : Db.q("SELECT * FROM agendamentos WHERE clinica_id=? AND data=? ORDER BY hora", id, dia)) ap.append(apptItem(r, a, "/clinica/agenda?dia=" + dia));
        String body = "<div class=\"bar\"><form method=\"get\" action=\"/clinica/agenda\" class=\"tools\"><a class=\"btn sm gh\" href=\"/clinica/agenda?dia=" + plusDays(dia, -1) + "\">◀</a>"
            + "<input type=\"date\" name=\"dia\" value=\"" + dia + "\" onchange=\"this.form.submit()\" style=\"width:auto\"><a class=\"btn sm gh\" href=\"/clinica/agenda?dia=" + plusDays(dia, 1) + "\">▶</a>"
            + "<a class=\"btn sm sec\" href=\"/clinica/agenda\">Hoje</a></form><div class=\"tools\"><a class=\"btn\" href=\"/clinica/agendamento?dia=" + dia + "\">+ Agendamento</a>"
            + "<a class=\"btn acc\" href=\"/clinica/oportunidade?data=" + dia + "\">📣 Publicar turno</a></div></div>" + w + ops
            + "<div class=\"card\"><h2>" + dLong(dia) + "</h2>" + (ap.length() == 0 ? empty("Nenhum agendamento neste dia.") : ap) + "</div>";
        r.html(page(r, "Agenda", body));
    }

    static void apptForm(Req r) throws Exception {
        Row a = r.p("id").isEmpty() ? null : Db.one("SELECT * FROM agendamentos WHERE id=? AND clinica_id=?", r.p("id"), r.uid());
        List<Row> ps = Db.q("SELECT id, nome, convenio FROM pacientes WHERE clinica_id=? ORDER BY nome", r.uid());
        if (ps.isEmpty()) { r.err("Cadastre um paciente antes de agendar."); r.redirect("/clinica/paciente"); return; }
        String pid = a != null ? a.s("paciente_id") : r.p("paciente");
        String[][] po = new String[ps.size()][];
        for (int i = 0; i < ps.size(); i++) po[i] = new String[]{ps.get(i).s("id"), ps.get(i).s("nome") + (ps.get(i).has("convenio") ? " · " + ps.get(i).s("convenio") : "")};
        String[][] pr = new String[Svc.PROCS.length][];
        StringBuilder data = new StringBuilder("{");
        for (int i = 0; i < Svc.PROCS.length; i++) {
            pr[i] = new String[]{(String) Svc.PROCS[i][0], (String) Svc.PROCS[i][0]};
            data.append(i > 0 ? "," : "").append("\"").append(Svc.PROCS[i][0]).append("\":[").append(Svc.PROCS[i][1]).append(",").append(Svc.PROCS[i][2]).append("]");
        }
        data.append("}");
        List<String[]> dl = new ArrayList<>();
        dl.add(new String[]{"", "— Sem profissional (será coberto por turno)"});
        for (Row d : Db.q("SELECT DISTINCT u.id, u.nome FROM oportunidades o JOIN usuarios u ON u.id=o.dentista_id WHERE o.clinica_id=? AND o.status='FECHADA' ORDER BY u.nome", r.uid()))
            dl.add(new String[]{d.s("id"), d.s("nome")});
        String[][] co = new String[Svc.CONVENIOS.length + 1][];
        co[0] = new String[]{"", "—"};
        for (int i = 0; i < Svc.CONVENIOS.length; i++) co[i + 1] = new String[]{Svc.CONVENIOS[i], Svc.CONVENIOS[i]};
        String proc = a != null ? a.s("procedimento") : (String) Svc.PROCS[0][0];
        String body = "<div class=\"card\" style=\"max-width:820px\"><form method=\"post\" action=\"/clinica/agendamento/salvar\">" + r.csrf()
            + "<input type=\"hidden\" name=\"id\" value=\"" + (a == null ? "" : a.s("id")) + "\">"
            + select("Paciente", "paciente_id", po, pid, "required") + "<p class=\"mut\"><a href=\"/clinica/paciente\">+ Cadastrar novo paciente</a></p>"
            + "<div class=\"row\">" + input("Data", "data", a != null ? a.s("data") : (Svc.isDate(r.p("dia")) ? r.p("dia") : today()), "date", "required")
            + input("Horário", "hora", a != null ? a.s("hora") : "08:00", "time", "required step=\"300\"")
            + input("Duração (min)", "duracao", a != null ? a.i("duracao") : 30, "number", "min=\"10\" max=\"480\" step=\"5\" id=\"f_dur\"") + "</div>"
            + select("Procedimento", "procedimento", pr, proc, "id=\"f_proc\"")
            + "<div class=\"row\">" + select("Pagamento", "pagador", new String[][]{{"PARTICULAR", "Particular"}, {"CONVENIO", "Convênio"}}, a != null ? a.s("pagador") : "PARTICULAR", "")
            + select("Convênio", "convenio", co, a != null ? a.s("convenio") : "", "")
            + input("Valor (R$)", "valor", a != null ? inputNum(a.d("valor")) : String.valueOf(Svc.PROCS[0][1]), "number", "min=\"0\" step=\"0.01\" id=\"f_val\"") + "</div>"
            + select("Profissional", "dentista_id", dl.toArray(new String[0][]), a != null ? a.s("dentista_id") : "", "")
            + "<p class=\"mut\">Se houver um profissional contratado para esse horário, o agendamento vai automaticamente para a agenda dele.</p>"
            + area("Observações", "obs", a != null ? a.s("obs") : "", 2)
            + "<button class=\"btn mt\">Salvar agendamento</button> <a class=\"btn gh mt\" href=\"/clinica/agenda\">Voltar</a></form></div>"
            + "<script>var P=" + data + ";document.getElementById('f_proc').addEventListener('change',function(){var x=P[this.value];if(x){if(x[0]>0)document.getElementById('f_val').value=x[0];document.getElementById('f_dur').value=x[1];}});</script>";
        r.html(page(r, a == null ? "Novo agendamento" : "Editar agendamento", body));
    }

    static void apptSave(Req r) throws Exception {
        String id = r.p("id");
        Row old = id.isEmpty() ? null : Db.one("SELECT * FROM agendamentos WHERE id=? AND clinica_id=?", id, r.uid());
        if (!id.isEmpty() && old == null) throw new Bad("Agendamento não encontrado.");
        Row p = Db.one("SELECT * FROM pacientes WHERE id=? AND clinica_id=?", r.p("paciente_id"), r.uid());
        if (p == null) throw new Bad("Selecione um paciente.");
        String data = r.p("data"), hora = r.p("hora");
        if (!Svc.isDate(data)) throw new Bad("Data inválida.");
        if (!Svc.isTime(hora)) throw new Bad("Horário inválido.");
        int dur = r.intp("duracao", 30);
        if (dur < 10 || dur > 480) throw new Bad("Duração deve ficar entre 10 e 480 minutos.");
        String pag = r.p("pagador").equals("CONVENIO") ? "CONVENIO" : "PARTICULAR";
        if (pag.equals("CONVENIO") && r.p("convenio").isEmpty()) throw new Bad("Selecione o convênio.");
        BigDecimal valor = r.num("valor");
        if (valor.signum() < 0) throw new Bad("Valor inválido.");
        String den = r.p("dentista_id");
        int ini = toMin(hora), fim = ini + dur;
        int cadeiras = Math.max(1, r.user.i("cadeiras"));
        int ocup = 0;
        for (Row a : Db.q("SELECT * FROM agendamentos WHERE clinica_id=? AND data=? AND status NOT IN ('CANCELADO','FALTOU')", r.uid(), data)) {
            if (a.s("id").equals(id)) continue;
            int ai = toMin(a.s("hora")), af = ai + a.i("duracao");
            if (ai < fim && af > ini) {
                ocup++;
                if (a.s("paciente_id").equals(p.s("id"))) throw new Bad("Este paciente já tem consulta nesse horário.");
                if (!den.isEmpty() && den.equals(a.s("dentista_id"))) throw new Bad("Este profissional já tem paciente nesse horário.");
            }
        }
        if (ocup >= cadeiras) throw new Bad("Todas as " + cadeiras + " cadeira(s) estão ocupadas nesse horário. Ajuste o número de cadeiras no perfil, se necessário.");
        String oid = null;
        Row o = den.isEmpty()
            ? Db.one("SELECT * FROM oportunidades WHERE clinica_id=? AND status='FECHADA' AND data=? AND hora_ini<=? AND hora_fim>?", r.uid(), data, hora, hora)
            : Db.one("SELECT * FROM oportunidades WHERE clinica_id=? AND status='FECHADA' AND dentista_id=? AND data=? AND hora_ini<=? AND hora_fim>?", r.uid(), den, data, hora, hora);
        if (o != null) { oid = o.s("id"); den = o.s("dentista_id"); }
        Map<String, Object> m = Db.m("paciente_id", p.s("id"), "data", data, "hora", hora, "duracao", dur, "procedimento", r.req("procedimento", "Procedimento"), "valor", valor,
            "pagador", pag, "convenio", pag.equals("CONVENIO") ? r.p("convenio") : "", "dentista_id", den.isEmpty() ? null : den, "oportunidade_id", oid, "obs", r.p("obs"));
        if (old == null) {
            m.put("clinica_id", r.uid());
            m.put("status", "AGENDADO");
            Db.insert("agendamentos", m);
        } else {
            Db.x("UPDATE agendamentos SET paciente_id=?, data=?, hora=?, duracao=?, procedimento=?, valor=?, pagador=?, convenio=?, dentista_id=?, oportunidade_id=?, obs=? WHERE id=?",
                m.get("paciente_id"), data, hora, dur, m.get("procedimento"), valor, pag, m.get("convenio"), m.get("dentista_id"), oid, r.p("obs"), id);
        }
        if (!den.isEmpty() && (old == null || !den.equals(old.s("dentista_id"))))
            Svc.notify(den, "Novo paciente na sua agenda: " + d(data) + " às " + hora + " (" + r.user.s("nome") + ").", "/dentista/agenda");
        r.ok("Agendamento salvo" + (o != null ? " e vinculado ao turno de " + Svc.nome(den) : "") + ".");
        r.redirect("/clinica/agenda?dia=" + data);
    }

    static int toMin(String t) { String[] p = t.split(":"); return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]); }

    // ================= PACIENTES =================
    static void pacientes(Req r) throws Exception {
        String q = r.p("q").toLowerCase();
        List<String[]> rows = new ArrayList<>();
        for (Row p : Db.q("SELECT * FROM pacientes WHERE clinica_id=? ORDER BY nome", r.uid())) {
            if (!q.isEmpty() && !(p.s("nome") + " " + p.s("cpf") + " " + p.s("telefone")).toLowerCase().contains(q)) continue;
            Row nx = Db.one("SELECT data, hora FROM agendamentos WHERE paciente_id=? AND data>=? AND status<>'CANCELADO' ORDER BY data, hora", p.s("id"), today());
            int age = age(p.s("nascimento"));
            rows.add(new String[]{"<b>" + h(p.s("nome")) + "</b> " + (Svc.alertas(p).isEmpty() ? "" : badge("⚠", "b-e")), age < 0 ? "—" : String.valueOf(age), h(p.s("telefone")),
                p.has("convenio") ? h(p.s("convenio")) : "Particular", nx == null ? "—" : dw(nx.s("data")) + " " + nx.s("hora"),
                "<div class=\"tools\"><a class=\"btn sm sec\" href=\"/prontuario?id=" + p.s("id") + "\">Prontuário</a><a class=\"btn sm gh\" href=\"/clinica/paciente?id=" + p.s("id")
                    + "\">Editar</a><a class=\"btn sm gh\" href=\"/clinica/agendamento?paciente=" + p.s("id") + "\">Agendar</a></div>"});
        }
        String body = "<div class=\"bar\"><form method=\"get\" action=\"/clinica/pacientes\" class=\"tools\"><input type=\"search\" name=\"q\" value=\"" + h(r.p("q"))
            + "\" placeholder=\"🔎 Nome, CPF ou telefone\" style=\"width:280px\"><button class=\"btn sm\">Buscar</button></form><a class=\"btn\" href=\"/clinica/paciente\">+ Novo paciente</a></div>"
            + "<div class=\"card\">" + table(new String[]{"Paciente", "Idade", "Celular", "Convênio", "Próxima consulta", "Ações"}, rows, "Nenhum paciente cadastrado.") + "</div>";
        r.html(page(r, "Pacientes", body));
    }

    static final String[][] CONDICOES = {{"HIPERTENSO", "Hipertensão"}, {"DIABETICO", "Diabetes"}, {"GESTANTE", "Gestante"}, {"ANTICOAGULANTE", "Anticoagulante"},
        {"CARDIOPATA", "Cardiopatia"}, {"FUMANTE", "Fumante"}};

    static void pacienteForm(Req r) throws Exception {
        Row p = r.p("id").isEmpty() ? new Row() : Db.one("SELECT * FROM pacientes WHERE id=? AND clinica_id=?", r.p("id"), r.uid());
        if (p == null) throw new Bad("Paciente não encontrado.");
        String[][] co = new String[Svc.CONVENIOS.length + 1][];
        co[0] = new String[]{"", "Particular"};
        for (int i = 0; i < Svc.CONVENIOS.length; i++) co[i + 1] = new String[]{Svc.CONVENIOS[i], Svc.CONVENIOS[i]};
        StringBuilder ck = new StringBuilder();
        for (String[] c : CONDICOES) ck.append(check(c[1], "cond_" + c[0], p.s("condicoes").contains(c[0])));
        String body = "<div class=\"card\" style=\"max-width:900px\"><form method=\"post\" action=\"/clinica/paciente/salvar\">" + r.csrf()
            + "<input type=\"hidden\" name=\"id\" value=\"" + h(p.s("id")) + "\"><h4>Dados pessoais</h4>"
            + "<div class=\"row\">" + input("Nome completo", "nome", p.s("nome"), "text", "required maxlength=\"200\"") + input("CPF", "cpf", p.s("cpf"), "text", "maxlength=\"20\"") + "</div>"
            + "<div class=\"row\">" + input("Nascimento", "nascimento", p.s("nascimento"), "date", "") + input("Celular / WhatsApp", "telefone", p.s("telefone"), "tel", "required maxlength=\"40\"")
            + input("E-mail", "email", p.s("email"), "email", "maxlength=\"200\"") + "</div>"
            + "<div class=\"row\">" + select("Convênio", "convenio", co, p.s("convenio"), "") + input("Nº carteirinha", "carteira", p.s("carteira"), "text", "maxlength=\"60\"") + "</div>"
            + "<h4>Anamnese</h4><div class=\"row\">" + input("Alergias", "alergias", p.s("alergias"), "text", "placeholder=\"Ex.: penicilina, látex, dipirona\"")
            + input("Medicamentos em uso", "medicamentos", p.s("medicamentos")) + "</div>" + input("Doenças sistêmicas / cirurgias", "doencas", p.s("doencas"))
            + "<div>" + ck + "</div>" + area("Observações", "obs", p.s("obs"), 2)
            + "<p class=\"mut\">🔒 Dados de saúde são sensíveis (LGPD). O profissional contratado só visualiza pacientes da própria agenda.</p>"
            + "<button class=\"btn mt\">Salvar paciente</button> <a class=\"btn gh mt\" href=\"/clinica/pacientes\">Voltar</a></form></div>";
        r.html(page(r, p.has("id") ? "Editar paciente" : "Novo paciente", body));
    }

    static void pacienteSave(Req r) throws Exception {
        String id = r.p("id");
        if (!id.isEmpty() && Db.count("SELECT COUNT(*) FROM pacientes WHERE id=? AND clinica_id=?", id, r.uid()) == 0) throw new Bad("Paciente não encontrado.");
        String nasc = r.p("nascimento");
        if (!nasc.isEmpty() && !Svc.isDate(nasc)) throw new Bad("Data de nascimento inválida.");
        List<String> c = new ArrayList<>();
        for (String[] x : CONDICOES) if (r.chk("cond_" + x[0])) c.add(x[0]);
        Object[] v = {r.req("nome", "Nome"), r.p("cpf"), nasc, r.req("telefone", "Celular"), r.p("email"), r.p("convenio"), r.p("carteira"), r.p("alergias"),
            r.p("medicamentos"), r.p("doencas"), String.join(";", c), r.p("obs")};
        if (id.isEmpty()) {
            id = Db.insert("pacientes", Db.m("clinica_id", r.uid(), "nome", v[0], "cpf", v[1], "nascimento", v[2], "telefone", v[3], "email", v[4], "convenio", v[5],
                "carteira", v[6], "alergias", v[7], "medicamentos", v[8], "doencas", v[9], "condicoes", v[10], "obs", v[11], "odontograma", ""));
        } else {
            Db.x("UPDATE pacientes SET nome=?, cpf=?, nascimento=?, telefone=?, email=?, convenio=?, carteira=?, alergias=?, medicamentos=?, doencas=?, condicoes=?, obs=? WHERE id=?",
                v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], id);
        }
        r.ok("Paciente salvo.");
        r.redirect("/prontuario?id=" + id);
    }

    // ================= TURNOS / OPORTUNIDADES =================
    static void oportunidades(Req r) throws Exception {
        StringBuilder ab = new StringBuilder(), ou = new StringBuilder();
        String hoje = today();
        for (Row o : Db.q("SELECT * FROM oportunidades WHERE clinica_id=? ORDER BY data DESC, hora_ini", r.uid())) {
            boolean ativa = (o.s("status").equals("ABERTA") || o.s("status").equals("FECHADA")) && o.s("data").compareTo(hoje) >= 0;
            (ativa ? ab : ou).append(oppCard(r, o));
        }
        String body = "<div class=\"bar\"><p class=\"mut\" style=\"margin:0\">Publique turnos para cobrir horários. Os profissionais compatíveis são notificados e podem aceitar ou negociar.</p>"
            + "<a class=\"btn acc\" href=\"/clinica/oportunidade\">📣 Publicar turno</a></div>"
            + "<h4>Próximos</h4><div class=\"grid g2\">" + (ab.length() == 0 ? empty("Nenhum turno futuro.") : ab) + "</div>"
            + "<h4>Histórico</h4><div class=\"grid g2\">" + (ou.length() == 0 ? empty("Sem histórico.") : ou) + "</div>";
        r.html(page(r, "Turnos / oportunidades", body));
    }

    static String oppCard(Req r, Row o) {
        boolean fech = o.s("status").equals("FECHADA");
        Svc.T t = fech ? Svc.T.of(o, "f_") : Svc.T.of(o, "");
        List<Row> ap = Svc.oppAppts(o);
        Svc.Calc c = fech ? Svc.settle(o) : Svc.calc(t, ap);
        long negs = Db.count("SELECT COUNT(*) FROM negociacoes WHERE oportunidade_id=? AND status='ABERTA'", o.s("id"));
        boolean futura = o.s("data").compareTo(today()) >= 0;
        String cat = Svc.catOpp(o);
        StringBuilder b = new StringBuilder("<div class=\"card").append(o.b("urgente") && o.s("status").equals("ABERTA") ? " hot" : "").append("\"><div class=\"bar\" style=\"margin-bottom:6px\"><b>")
            .append(h(o.s("titulo"))).append("</b><div class=\"tools\">").append(o.b("urgente") && o.s("status").equals("ABERTA") ? badge("🔥 Urgente", "b-hot") : "").append(Svc.oppBadge(o)).append("</div></div>")
            .append("<div class=\"mut\">📅 ").append(dLong(o.s("data"))).append(" · ").append(o.s("hora_ini")).append("–").append(o.s("hora_fim")).append(" · ")
            .append(h(Svc.label(Svc.CATEGORIAS, cat))).append(cat.equals("DENTISTA") ? " · " + h(o.s("especialidade")) : "").append("</div>")
            .append("<div class=\"terms mt\">").append(h(Svc.termsText(t))).append("</div><div class=\"mut\">").append(Svc.matText(t.mat()))
            .append(fech && !t.same(Svc.T.of(o, "")) ? " · <i>oferta original: " + h(Svc.termsText(Svc.T.of(o, ""))) + "</i>" : "").append("</div>")
            .append("<div class=\"gain\"><span>").append(ap.size()).append(" paciente(s) · produção ").append(money(c.prod())).append("</span><span>Repasse ").append(money(c.rep()))
            .append(" · <b style=\"font-size:15px;color:").append(c.net().signum() < 0 ? "#dc2626" : "#16a34a").append("\">Clínica ").append(money(c.net())).append("</b></span></div>");
        if (o.has("motivo_cancel") && !fech) b.append("<div class=\"mut\">↩ Reaberto/cancelado: ").append(h(o.s("motivo_cancel"))).append("</div>");
        if (fech) {
            Row d = Svc.user(o.s("dentista_id"));
            b.append("<div>🦷 <b>").append(h(d.s("nome"))).append("</b> ").append(Svc.stars(d.s("id"))).append(" ").append(Svc.selos(d))
                .append(o.has("checkin") ? " " + badge("✅ Check-in " + o.s("checkin"), "b-ok") : "").append("</div>");
        }
        b.append("<div class=\"tools mt\">");
        if (o.s("status").equals("ABERTA") && futura) {
            b.append("<a class=\"btn sm\" href=\"/negociacoes?op=").append(o.s("id")).append("\">🤝 Propostas (").append(negs).append(")</a>")
                .append("<a class=\"btn sm acc\" href=\"/clinica/match?op=").append(o.s("id")).append("\">🎯 Melhores matches</a>")
                .append("<a class=\"btn sm gh\" href=\"/clinica/oportunidade?id=").append(o.s("id")).append("\">Editar</a>")
                .append(post(r, "/clinica/oportunidade/cancelar", "Cancelar", "sm red", "Cancelar este turno? As negociações serão encerradas.", "id", o.s("id")));
        }
        if (fech) {
            b.append("<a class=\"btn sm gh\" href=\"/termo?id=").append(o.s("id")).append("\">📄 Termo</a><a class=\"btn sm gh\" href=\"/clinica/agenda?dia=").append(o.s("data")).append("\">📅 Agenda</a>");
            double hrs = Svc.horasAte(o.s("data"), o.s("hora_ini"));
            if (hrs > -12) b.append(SharedPages.cancelForm(r, o, hrs));
            if (hrs <= -0.5 && !o.has("checkin") && o.s("data").compareTo(plusDays(today(), -2)) >= 0)
                b.append(post(r, "/clinica/noshow", "🚫 Profissional não compareceu", "sm red", "Confirmar o não comparecimento? O profissional receberá um strike e os pacientes voltarão para \"sem profissional\".", "id", o.s("id")));
            if (!futura || o.s("data").equals(today())) b.append(SharedPages.reviewButton(r, o, o.s("dentista_id")));
        }
        return b.append("</div></div>").toString();
    }

    static void oppForm(Req r) throws Exception {
        Row o = r.p("id").isEmpty() ? null : Db.one("SELECT * FROM oportunidades WHERE id=? AND clinica_id=? AND status='ABERTA'", r.p("id"), r.uid());
        if (!r.p("id").isEmpty() && o == null) throw new Bad("Turno não pode ser editado.");
        if (o == null && Svc.exigeVerificacao() && !r.user.b("verificado")) {
            r.err("Seu cadastro ainda está em verificação. A publicação de turnos será liberada após a aprovação.");
            r.redirect("/clinica/oportunidades");
            return;
        }
        String hora = Svc.isTime(r.p("hora")) ? r.p("hora") : "08:00";
        int hi = Integer.parseInt(hora.substring(0, 2));
        String fim = String.format("%02d:00", Math.min(23, hi + 4));
        Svc.T t = o != null ? Svc.T.of(o, "") : new Svc.T("PERCENTUAL", new BigDecimal(40), new BigDecimal(30), BigDecimal.ZERO, "CLINICA");
        String body = "<div class=\"card\" style=\"max-width:860px\"><form method=\"post\" action=\"/clinica/oportunidade/salvar\">" + r.csrf()
            + "<input type=\"hidden\" name=\"id\" value=\"" + (o == null ? "" : o.s("id")) + "\">"
            + input("Título", "titulo", o != null ? o.s("titulo") : "", "text", "maxlength=\"200\" placeholder=\"Ex.: Clínico geral – turno da manhã\"")
            + "<div class=\"row\">" + select("Profissional procurado", "categoria", Svc.CATEGORIAS, o != null ? Svc.catOpp(o) : "DENTISTA", "")
            + select("Especialidade (para dentistas)", "especialidade", opts(Svc.ESPECIALIDADES), o != null ? o.s("especialidade") : "Clínico Geral", "") + "</div>"
            + "<div class=\"row\">" + input("Data", "data", o != null ? o.s("data") : (Svc.isDate(r.p("data")) ? r.p("data") : plusDays(today(), 1)), "date", "required min=\"" + today() + "\"")
            + input("Início", "hora_ini", o != null ? o.s("hora_ini") : hora, "time", "required") + input("Fim", "hora_fim", o != null ? o.s("hora_fim") : fim, "time", "required") + "</div>"
            + "<h4>Quanto a clínica oferece</h4>" + Svc.termsFields(t)
            + input("Detalhes sobre materiais e estrutura", "obs_materiais", o != null ? o.s("obs_materiais") : "", "text", "maxlength=\"1000\" placeholder=\"Ex.: RX digital, autoclave, auxiliar disponível\"")
            + area("Requisitos / observações", "requisitos", o != null ? o.s("requisitos") : "", 2)
            + "<div>" + check("🤝 Aceita contraproposta (negociação)", "aceita_contra", o == null || o.b("aceita_contra")) + check("🔥 Urgente (destaque no feed)", "urgente", o != null && o.b("urgente")) + "</div>"
            + "<p class=\"mut\">Os pacientes agendados sem profissional nesse dia e horário entram automaticamente na agenda do profissional contratado. "
            + "Nomes e prontuários só são liberados após o fechamento.</p>"
            + "<button class=\"btn acc mt\">" + (o == null ? "Publicar turno" : "Salvar alterações") + "</button> <a class=\"btn gh mt\" href=\"/clinica/oportunidades\">Voltar</a></form></div>";
        r.html(page(r, o == null ? "Publicar turno" : "Editar turno", body));
    }

    static void oppSave(Req r) throws Exception {
        String id = r.p("id");
        Row old = id.isEmpty() ? null : Db.one("SELECT * FROM oportunidades WHERE id=? AND clinica_id=? AND status='ABERTA'", id, r.uid());
        if (!id.isEmpty() && old == null) throw new Bad("Turno não pode ser editado.");
        if (old == null && Svc.exigeVerificacao() && !r.user.b("verificado")) throw new Bad("Cadastro em verificação: publicação ainda não liberada.");
        String data = r.p("data"), hi = r.p("hora_ini"), hf = r.p("hora_fim");
        if (!Svc.isDate(data) || data.compareTo(today()) < 0) throw new Bad("Informe uma data a partir de hoje.");
        if (!Svc.isTime(hi) || !Svc.isTime(hf) || hf.compareTo(hi) <= 0) throw new Bad("O horário final deve ser depois do inicial.");
        String cat = r.p("categoria");
        if (Svc.label(Svc.CATEGORIAS, cat).equals(cat)) throw new Bad("Selecione o profissional procurado.");
        String esp = r.p("especialidade");
        if (!Arrays.asList(Svc.ESPECIALIDADES).contains(esp)) throw new Bad("Especialidade inválida.");
        Svc.T t = Svc.termsFrom(r);
        String titulo = r.p("titulo").isEmpty() ? (cat.equals("DENTISTA") ? esp : Svc.label(Svc.CATEGORIAS, cat)) + " – " + hi + " às " + hf : r.p("titulo");
        if (old == null) {
            String nid = Db.insert("oportunidades", Db.m("clinica_id", r.uid(), "titulo", titulo, "categoria", cat, "especialidade", esp, "data", data, "hora_ini", hi, "hora_fim", hf,
                "modelo", t.modelo(), "pct_part", t.pp(), "pct_conv", t.pc(), "diaria", t.diaria(), "materiais", t.mat(), "obs_materiais", r.p("obs_materiais"),
                "requisitos", r.p("requisitos"), "urgente", r.chk("urgente"), "aceita_contra", r.chk("aceita_contra"), "status", "ABERTA", "repasse_pago", false));
            Row o = Db.one("SELECT * FROM oportunidades WHERE id=?", nid);
            int n = 0;
            for (Row d : Svc.elegiveis(o)) {
                Svc.Match m = Svc.match(d, o, r.user);
                boolean espOk = !cat.equals("DENTISTA") || Arrays.asList(d.s("especialidades").split(";")).contains(esp);
                boolean longe = m.km() != null && m.km() > (d.i("raio_km") > 0 ? d.i("raio_km") : 30);
                if (!espOk || longe) continue;
                Svc.notify(d.s("id"), (r.chk("urgente") ? "🔥 " : "📣 ") + "Novo turno " + m.score() + "% compatível: \"" + titulo + "\" em " + d(data) + (m.km() != null ? " · " + Svc.kmText(m.km()) : "")
                    + " — " + Svc.termsText(t) + ".", "/dentista/oportunidade?id=" + nid);
                n++;
            }
            Svc.audit(r, "OPORTUNIDADE_CRIADA", titulo + " · " + Svc.termsText(t));
            r.ok("Turno publicado! " + n + " profissional(is) compatível(is) notificado(s). Veja os melhores matches e convide.");
            r.redirect("/clinica/match?op=" + nid);
            return;
        }
        Db.x("UPDATE oportunidades SET titulo=?, categoria=?, especialidade=?, data=?, hora_ini=?, hora_fim=?, modelo=?, pct_part=?, pct_conv=?, diaria=?, materiais=?, obs_materiais=?, requisitos=?, urgente=?, aceita_contra=? WHERE id=?",
            titulo, cat, esp, data, hi, hf, t.modelo(), t.pp(), t.pc(), t.diaria(), t.mat(), r.p("obs_materiais"), r.p("requisitos"), r.chk("urgente"), r.chk("aceita_contra"), id);
        for (Row n : Db.q("SELECT * FROM negociacoes WHERE oportunidade_id=? AND status='ABERTA'", id)) {
            Db.insert("mensagens", Db.m("negociacao_id", n.s("id"), "autor", "SISTEMA", "tipo", "SISTEMA", "texto", "A clínica alterou o turno. Oferta da clínica agora: " + Svc.termsText(t) + "."));
            Svc.notify(n.s("dentista_id"), r.user.s("nome") + " alterou o turno \"" + titulo + "\". Confira.", "/negociacao?id=" + n.s("id"));
        }
        Svc.audit(r, "OPORTUNIDADE_EDITADA", titulo);
        r.ok("Turno atualizado.");
        r.redirect("/clinica/oportunidades");
    }

    static void oppCancel(Req r) throws Exception {
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=? AND clinica_id=? AND status='ABERTA'", r.p("id"), r.uid());
        if (o == null) throw new Bad("Turno não encontrado.");
        Db.tx(() -> {
            Db.x("UPDATE oportunidades SET status='CANCELADA' WHERE id=?", o.s("id"));
            for (Row n : Db.q("SELECT * FROM negociacoes WHERE oportunidade_id=? AND status='ABERTA'", o.s("id"))) {
                Db.x("UPDATE negociacoes SET status='ENCERRADA', atualizado=? WHERE id=?", System.currentTimeMillis(), n.s("id"));
                Db.insert("mensagens", Db.m("negociacao_id", n.s("id"), "autor", "SISTEMA", "tipo", "SISTEMA", "texto", "Turno cancelado pela clínica antes da contratação."));
                Svc.notify(n.s("dentista_id"), "O turno \"" + o.s("titulo") + "\" foi cancelado pela clínica.", "/negociacao?id=" + n.s("id"));
            }
        });
        Svc.audit(r, "OPORTUNIDADE_CANCELADA", o.s("titulo"));
        r.ok("Turno cancelado (ainda não havia profissional contratado, sem impacto na reputação).");
        r.redirect("/clinica/oportunidades");
    }

    static void noShow(Req r) throws Exception {
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=? AND clinica_id=? AND status='FECHADA'", r.p("id"), r.uid());
        if (o == null) throw new Bad("Turno não encontrado.");
        if (o.has("checkin")) throw new Bad("O profissional fez check-in às " + o.s("checkin") + ".");
        if (Svc.horasAte(o.s("data"), o.s("hora_ini")) > -0.5) throw new Bad("Aguarde ao menos 30 minutos após o início do turno.");
        String pro = o.s("dentista_id");
        String det = o.s("titulo") + " em " + d(o.s("data")) + " " + o.s("hora_ini") + " · informado por " + r.user.s("nome");
        Db.tx(() -> {
            Svc.desvincular(o.s("id"), false, "Profissional não compareceu");
            Svc.ocorrencia(r, pro, o.s("id"), "NO_SHOW", true, det);
        });
        Svc.audit(r, "NO_SHOW", det + " · " + Svc.nome(pro));
        r.ok("Não comparecimento registrado. Strike aplicado e os pacientes voltaram para \"sem profissional\" — publique um novo turno se precisar.");
        r.redirect("/clinica/oportunidades");
    }

    // ================= MATCH =================
    static void matchPage(Req r) throws Exception {
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=? AND clinica_id=?", r.p("op"), r.uid());
        if (o == null) throw new Bad("Turno não encontrado.");
        List<Object[]> l = new ArrayList<>();
        for (Row d : Svc.elegiveis(o)) l.add(new Object[]{d, Svc.match(d, o, r.user)});
        l.sort((a, b) -> Integer.compare(((Svc.Match) b[1]).score(), ((Svc.Match) a[1]).score()));
        StringBuilder b = new StringBuilder("<a class=\"btn sm gh\" href=\"/clinica/oportunidades\">← Turnos</a><div class=\"card mt\"><h2>🎯 Melhores matches para \"").append(h(o.s("titulo"))).append("\"</h2>")
            .append("<p class=\"mut\">").append(dLong(o.s("data"))).append(" · ").append(o.s("hora_ini")).append("–").append(o.s("hora_fim")).append(" · ").append(h(Svc.termsText(Svc.T.of(o, ""))))
            .append("</p><p class=\"mut\">Pontuação: especialidade 30 · distância 25 · avaliações 20 · disponibilidade 15 · confiabilidade (CRO verificado e strikes) 10.</p></div><div class=\"grid g3\">");
        int i = 0;
        for (Object[] x : l) {
            if (i++ >= 30) break;
            Row d = (Row) x[0];
            Svc.Match m = (Svc.Match) x[1];
            boolean jaNeg = Db.count("SELECT COUNT(*) FROM negociacoes WHERE oportunidade_id=? AND dentista_id=?", o.s("id"), d.s("id")) > 0;
            b.append("<div class=\"card\"><div class=\"bar\" style=\"margin-bottom:4px\"><b>").append(h(d.s("nome"))).append("</b>").append(Svc.matchBadge(m)).append("</div>")
                .append("<div>").append(Svc.stars(d.s("id"))).append(" ").append(Svc.selos(d)).append("</div><div class=\"mut\">").append(h(d.s("documento"))).append(" · ").append(d.i("experiencia"))
                .append(" anos · ").append(h(d.s("cidade"))).append(Svc.cat(d).equals("ESTUDANTE") && d.has("faculdade") ? " · 🎓 " + h(d.s("faculdade") + " " + d.s("semestre")) : "").append("</div>")
                .append(Svc.matchWhy(m)).append("<p class=\"mut\">").append(h(d.s("bio"))).append("</p>");
            if (jaNeg) b.append(badge("Já está negociando", "b-p"));
            else if (o.s("status").equals("ABERTA")) b.append(post(r, "/clinica/convidar", "📨 Convidar para este turno", "sm", null, "op", o.s("id"), "dentista", d.s("id")));
            b.append("</div>");
        }
        b.append("</div>");
        if (l.isEmpty()) b.append("<div class=\"card\">").append(empty("Ainda não há profissionais verificados dessa categoria. Os novos cadastros aparecerão aqui.")).append("</div>");
        r.html(page(r, "Melhores matches", b.toString()));
    }

    // ================= PROFISSIONAIS =================
    static void profissionais(Req r) throws Exception {
        String esp = r.p("esp"), cat = r.p("cat").isEmpty() ? "DENTISTA" : r.p("cat"), ordem = r.p("ordem");
        int raio = r.intp("raio", 0);
        List<Row> abertas = Db.q("SELECT * FROM oportunidades WHERE clinica_id=? AND status='ABERTA' AND data>=? ORDER BY data", r.uid(), today());
        List<Object[]> l = new ArrayList<>();
        for (Row d : Db.q("SELECT * FROM usuarios WHERE perfil='DENTISTA' AND ativo=TRUE ORDER BY nome")) {
            if (Svc.exigeVerificacao() && !d.b("verificado")) continue;
            if (!Svc.cat(d).equals(cat)) continue;
            if (!esp.isEmpty() && !Arrays.asList(d.s("especialidades").split(";")).contains(esp)) continue;
            Double km = Svc.distKm(d, r.user);
            if (raio > 0 && (km == null || km > raio)) continue;
            l.add(new Object[]{d, km, Svc.avgRating(d.s("id"))});
        }
        if (ordem.equals("avaliacao")) l.sort((a, b) -> Double.compare((Double) b[2], (Double) a[2]));
        else l.sort(Comparator.comparingDouble(a -> a[1] == null ? 1e9 : (Double) a[1]));
        StringBuilder b = new StringBuilder("<div class=\"card\"><form method=\"get\" action=\"/clinica/profissionais\" class=\"row\" style=\"align-items:flex-end\">")
            .append(select("Categoria", "cat", Svc.CATEGORIAS, cat, "")).append(select("Especialidade", "esp", withAll("Todas", opts(Svc.ESPECIALIDADES)), esp, ""))
            .append(select("Distância", "raio", new String[][]{{"0", "Qualquer"}, {"5", "Até 5 km"}, {"10", "Até 10 km"}, {"20", "Até 20 km"}, {"50", "Até 50 km"}}, String.valueOf(raio), ""))
            .append(select("Ordenar", "ordem", new String[][]{{"distancia", "Mais próximos"}, {"avaliacao", "Melhor avaliados"}}, ordem, ""))
            .append("<div style=\"flex:0\"><button class=\"btn\">Buscar</button></div></form>")
            .append(r.user.s("lat").isEmpty() ? "<p class=\"mut\">📍 Informe a localização da clínica no <a href=\"/conta/perfil\">perfil</a> para ver as distâncias.</p>" : "")
            .append("</div><p class=\"mut\">").append(l.size()).append(" profissional(is) encontrado(s). Para ranking completo por turno, use \"🎯 Melhores matches\" em Turnos.</p><div class=\"grid g3\">");
        for (Object[] x : l) {
            Row d = (Row) x[0];
            Double km = (Double) x[1];
            long at = Db.count("SELECT COUNT(*) FROM oportunidades WHERE clinica_id=? AND dentista_id=? AND status='FECHADA'", r.uid(), d.s("id"));
            StringBuilder tags = new StringBuilder();
            for (String s : d.s("especialidades").split(";")) if (!s.isBlank()) tags.append(badge(s, "b-m")).append(" ");
            b.append("<div class=\"card\"><div class=\"bar\" style=\"margin-bottom:4px\"><b>").append(h(d.s("nome"))).append("</b>").append(Svc.stars(d.s("id"))).append("</div>")
                .append("<div>").append(Svc.selos(d)).append("</div><div class=\"mut\">").append(h(d.s("documento"))).append(" · ").append(d.i("experiencia")).append(" anos · 📍 ")
                .append(km != null ? Svc.kmText(km) : h(d.s("cidade"))).append("</div><div class=\"mt\">").append(tags).append("</div>")
                .append("<p class=\"mut\">").append(h(d.s("bio"))).append("</p><p class=\"mut\">").append(d.b("materiais_proprios") ? "🎒 Possui materiais próprios" : "🧰 Usa materiais da clínica")
                .append(" · ").append(at).append(" turno(s) na sua clínica · 🗓 ").append(d.has("disponibilidade") ? d.s("disponibilidade").split(",").length + " turnos/semana" : "disponibilidade não informada").append("</p>");
            if (!abertas.isEmpty()) {
                b.append("<form method=\"post\" action=\"/clinica/convidar\" class=\"tools\">").append(r.csrf()).append("<input type=\"hidden\" name=\"dentista\" value=\"").append(d.s("id"))
                    .append("\"><select name=\"op\" style=\"flex:1\">");
                for (Row o : abertas) b.append("<option value=\"").append(o.s("id")).append("\">").append(h(d(o.s("data")) + " · " + o.s("titulo"))).append("</option>");
                b.append("</select><button class=\"btn sm\">📨 Convidar</button></form>");
            }
            b.append("</div>");
        }
        b.append("</div>");
        if (l.isEmpty()) b.append(empty("Nenhum profissional encontrado com esses filtros."));
        r.html(page(r, "Encontrar profissionais", b.toString()));
    }

    static void convidar(Req r) throws Exception {
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=? AND clinica_id=? AND status='ABERTA'", r.p("op"), r.uid());
        Row d = Svc.user(r.p("dentista"));
        if (o == null || d == null || !d.s("perfil").equals("DENTISTA")) throw new Bad("Convite inválido.");
        Svc.Match m = Svc.match(d, o, r.user);
        Svc.notifyMail(d.s("id"), "📨 " + r.user.s("nome") + " convidou você para \"" + o.s("titulo") + "\" em " + d(o.s("data")) + " — " + Svc.termsText(Svc.T.of(o, "")) + " (" + m.score() + "% match).",
            "/dentista/oportunidade?id=" + o.s("id"), "Convite para turno — " + r.user.s("nome"),
            r.user.s("nome") + " convidou você para o turno \"" + o.s("titulo") + "\" em " + dLong(o.s("data")) + ", das " + o.s("hora_ini") + " às " + o.s("hora_fim")
                + ".\nOferta: " + Svc.termsText(Svc.T.of(o, "")) + "\n\nEntre no sistema para aceitar ou fazer uma contraproposta.", "CONVITE:" + o.s("id"));
        r.ok("Convite enviado para " + d.s("nome") + ".");
        r.redirect(r.back("/clinica/profissionais"));
    }

    // ================= SALAS =================
    static void salas(Req r) throws Exception {
        StringBuilder b = new StringBuilder("<div class=\"bar\"><p class=\"mut\" style=\"margin:0\">Alugue salas ou consultórios ociosos para profissionais qualificados, por hora, turno ou dia.</p>"
            + "<a class=\"btn acc\" href=\"/clinica/sala\">+ Anunciar sala</a></div>");
        List<String[]> pend = new ArrayList<>();
        for (Row x : Db.q("SELECT r.*, s.titulo FROM reservas r JOIN salas s ON s.id=r.sala_id WHERE r.clinica_id=? AND r.status='PENDENTE' ORDER BY r.data, r.hora_ini", r.uid())) {
            Row p = Svc.user(x.s("profissional_id"));
            pend.add(new String[]{dw(x.s("data")) + "<div class=\"mut\">" + x.s("hora_ini") + "–" + x.s("hora_fim") + "</div>", h(x.s("titulo")),
                "<b>" + h(p.s("nome")) + "</b><div>" + Svc.stars(p.s("id")) + " " + Svc.selos(p) + "</div>", money(x.d("valor")), h(Svc.mask(x.s("mensagem"))),
                "<form method=\"post\" action=\"/clinica/reserva/responder\" class=\"tools\">" + r.csrf() + "<input type=\"hidden\" name=\"id\" value=\"" + x.s("id") + "\">"
                    + "<input name=\"resposta\" placeholder=\"Mensagem (opcional)\" style=\"width:150px\"><button class=\"btn sm ok\" name=\"acao\" value=\"ACEITAR\">Aceitar</button>"
                    + "<button class=\"btn sm red\" name=\"acao\" value=\"RECUSAR\">Recusar</button></form>"});
        }
        b.append("<div class=\"card\"><h2>Pedidos de reserva pendentes</h2>").append(table(new String[]{"Data", "Sala", "Profissional", "Valor", "Mensagem", ""}, pend, "Nenhum pedido pendente.")).append("</div>");
        b.append("<h4>Minhas salas</h4><div class=\"grid g3\">");
        List<Row> ss = Db.q("SELECT * FROM salas WHERE clinica_id=? ORDER BY titulo", r.uid());
        for (Row s : ss) {
            long res = Db.count("SELECT COUNT(*) FROM reservas WHERE sala_id=? AND status='ACEITA' AND data>=?", s.s("id"), today());
            b.append("<div class=\"card\"><div class=\"bar\" style=\"margin-bottom:4px\"><b>").append(h(s.s("titulo"))).append("</b>").append(s.b("ativa") ? badge("Anunciada", "b-ok") : badge("Pausada", "b-m")).append("</div>")
                .append("<div class=\"terms\">").append(money(s.d("valor"))).append(" <span class=\"mut\">").append(h(Svc.label(Svc.MODALIDADE_SALA, s.s("modalidade")).toLowerCase())).append("</span></div>")
                .append("<p class=\"mut\">").append(h(s.s("descricao"))).append("</p><p class=\"mut\">🧰 ").append(h(s.s("equipamentos"))).append("</p><p class=\"mut\">").append(res).append(" reserva(s) futura(s)</p>")
                .append("<div class=\"tools\"><a class=\"btn sm gh\" href=\"/clinica/sala?id=").append(s.s("id")).append("\">Editar</a>")
                .append(post(r, "/clinica/sala/ativar", s.b("ativa") ? "Pausar anúncio" : "Reativar anúncio", "sm gh", null, "id", s.s("id"))).append("</div></div>");
        }
        b.append("</div>");
        if (ss.isEmpty()) b.append("<div class=\"card\">").append(empty("Nenhuma sala anunciada ainda.")).append("</div>");
        List<String[]> prox = new ArrayList<>();
        for (Row x : Db.q("SELECT r.*, s.titulo FROM reservas r JOIN salas s ON s.id=r.sala_id WHERE r.clinica_id=? AND r.status IN ('ACEITA','RECUSADA','CANCELADA') ORDER BY r.data DESC", r.uid())) {
            if (prox.size() >= 50) break;
            Row p = Svc.user(x.s("profissional_id"));
            prox.add(new String[]{dw(x.s("data")) + " " + x.s("hora_ini") + "–" + x.s("hora_fim"), h(x.s("titulo")), h(p.s("nome")) + (x.s("status").equals("ACEITA") ? "<div class=\"mut\">📞 " + h(p.s("telefone")) + "</div>" : ""),
                money(x.d("valor")), badge(x.s("status"), x.s("status").equals("ACEITA") ? "b-ok" : "b-m")});
        }
        b.append("<div class=\"card mt\"><h2>Histórico de reservas</h2>").append(table(new String[]{"Data", "Sala", "Profissional", "Valor", "Situação"}, prox, "Sem reservas ainda.")).append("</div>");
        r.html(page(r, "Aluguel de salas", b.toString()));
    }

    static final String[] DIAS = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};

    static void salaForm(Req r) throws Exception {
        Row s = r.p("id").isEmpty() ? new Row() : Db.one("SELECT * FROM salas WHERE id=? AND clinica_id=?", r.p("id"), r.uid());
        if (s == null) throw new Bad("Sala não encontrada.");
        List<String> dias = Arrays.asList(s.s("dias").split(","));
        StringBuilder ck = new StringBuilder();
        for (int i = 1; i <= 7; i++) ck.append(check(DIAS[i - 1], "dia_" + i, s.has("id") ? dias.contains(String.valueOf(i)) : i <= 5));
        String body = "<div class=\"card\" style=\"max-width:760px\"><form method=\"post\" action=\"/clinica/sala/salvar\">" + r.csrf() + "<input type=\"hidden\" name=\"id\" value=\"" + h(s.s("id")) + "\">"
            + input("Título do anúncio", "titulo", s.s("titulo"), "text", "required maxlength=\"200\" placeholder=\"Ex.: Consultório completo com RX digital\"")
            + area("Descrição", "descricao", s.s("descricao"), 3) + input("Equipamentos disponíveis", "equipamentos", s.s("equipamentos"), "text", "placeholder=\"Cadeira, autoclave, RX, fotopolimerizador...\"")
            + "<div class=\"row\">" + select("Cobrança", "modalidade", Svc.MODALIDADE_SALA, s.has("modalidade") ? s.s("modalidade") : "TURNO", "")
            + input("Valor (R$)", "valor", s.has("valor") ? inputNum(s.d("valor")) : "", "number", "required min=\"1\" step=\"0.01\"") + "</div>"
            + "<label>Dias disponíveis</label><div>" + ck + "</div>" + check("Anúncio ativo", "ativa", !s.has("id") || s.b("ativa"))
            + "<br><button class=\"btn mt\">Salvar sala</button> <a class=\"btn gh mt\" href=\"/clinica/salas\">Voltar</a></form></div>";
        r.html(page(r, s.has("id") ? "Editar sala" : "Anunciar sala", body));
    }

    static void salaSave(Req r) throws Exception {
        String id = r.p("id");
        if (!id.isEmpty() && Db.count("SELECT COUNT(*) FROM salas WHERE id=? AND clinica_id=?", id, r.uid()) == 0) throw new Bad("Sala não encontrada.");
        String mod = r.p("modalidade");
        if (Svc.label(Svc.MODALIDADE_SALA, mod).equals(mod)) throw new Bad("Modalidade inválida.");
        BigDecimal valor = r.num("valor");
        if (valor.signum() <= 0) throw new Bad("Informe o valor.");
        List<String> dias = new ArrayList<>();
        for (int i = 1; i <= 7; i++) if (r.chk("dia_" + i)) dias.add(String.valueOf(i));
        if (dias.isEmpty()) throw new Bad("Selecione ao menos um dia disponível.");
        if (id.isEmpty()) {
            Db.insert("salas", Db.m("clinica_id", r.uid(), "titulo", r.req("titulo", "Título"), "descricao", r.p("descricao"), "equipamentos", r.p("equipamentos"), "modalidade", mod,
                "valor", valor, "dias", String.join(",", dias), "ativa", r.chk("ativa")));
            Svc.audit(r, "SALA_ANUNCIADA", r.p("titulo"));
        } else {
            Db.x("UPDATE salas SET titulo=?, descricao=?, equipamentos=?, modalidade=?, valor=?, dias=?, ativa=? WHERE id=?", r.req("titulo", "Título"), r.p("descricao"), r.p("equipamentos"), mod, valor,
                String.join(",", dias), r.chk("ativa"), id);
        }
        r.ok("Sala salva.");
        r.redirect("/clinica/salas");
    }

    static void salaToggle(Req r) throws Exception {
        Row s = Db.one("SELECT * FROM salas WHERE id=? AND clinica_id=?", r.p("id"), r.uid());
        if (s == null) throw new Bad("Sala não encontrada.");
        Db.x("UPDATE salas SET ativa=? WHERE id=?", !s.b("ativa"), s.s("id"));
        r.ok(s.b("ativa") ? "Anúncio pausado." : "Anúncio reativado.");
        r.redirect("/clinica/salas");
    }

    static void reservaResponder(Req r) throws Exception {
        Row x = Db.one("SELECT r.*, s.titulo FROM reservas r JOIN salas s ON s.id=r.sala_id WHERE r.id=? AND r.clinica_id=? AND r.status='PENDENTE'", r.p("id"), r.uid());
        if (x == null) throw new Bad("Pedido não encontrado.");
        boolean aceitar = r.p("acao").equals("ACEITAR");
        if (aceitar && Db.count("SELECT COUNT(*) FROM reservas WHERE sala_id=? AND status='ACEITA' AND data=? AND hora_ini<? AND hora_fim>?", x.s("sala_id"), x.s("data"), x.s("hora_fim"), x.s("hora_ini")) > 0)
            throw new Bad("Já existe uma reserva aceita nesse horário para esta sala.");
        Db.x("UPDATE reservas SET status=?, resposta=? WHERE id=?", aceitar ? "ACEITA" : "RECUSADA", Svc.mask(r.p("resposta")), x.s("id"));
        Row c = r.user;
        String quando = dLong(x.s("data")) + ", das " + x.s("hora_ini") + " às " + x.s("hora_fim");
        if (aceitar) {
            for (Row o : Db.q("SELECT * FROM reservas WHERE sala_id=? AND status='PENDENTE' AND data=? AND hora_ini<? AND hora_fim>? AND id<>?", x.s("sala_id"), x.s("data"), x.s("hora_fim"), x.s("hora_ini"), x.s("id"))) {
                Db.x("UPDATE reservas SET status='RECUSADA', resposta='Horário reservado por outro profissional.' WHERE id=?", o.s("id"));
                Svc.notify(o.s("profissional_id"), "A sala \"" + x.s("titulo") + "\" foi reservada por outro profissional nesse horário.", "/dentista/salas");
            }
            String resumo = "Sala: " + x.s("titulo") + "\nClínica: " + c.s("nome") + " — " + c.s("endereco") + " " + c.s("cidade") + " · Tel.: " + c.s("telefone")
                + "\nQuando: " + quando + "\nValor: " + money(x.d("valor")) + (r.p("resposta").isEmpty() ? "" : "\nMensagem da clínica: " + r.p("resposta"));
            Svc.notifyMail(x.s("profissional_id"), "✅ Reserva confirmada: \"" + x.s("titulo") + "\" em " + d(x.s("data")) + ".", "/dentista/salas", "Reserva de sala confirmada — " + d(x.s("data")),
                "Sua reserva foi confirmada. Este e-mail serve como comprovante.\n\n" + resumo, "RESERVA:" + x.s("id"));
            Mail.queue(c.s("email"), c.s("nome"), "Reserva de sala confirmada — " + d(x.s("data")), "Você confirmou a reserva abaixo.\n\n" + resumo + "\nProfissional: " + Svc.nome(x.s("profissional_id"))
                + " · Tel.: " + Svc.user(x.s("profissional_id")).s("telefone"), "RESERVA:" + x.s("id"));
        } else Svc.notify(x.s("profissional_id"), "Seu pedido de reserva da sala \"" + x.s("titulo") + "\" em " + d(x.s("data")) + " foi recusado." + (r.p("resposta").isEmpty() ? "" : " Mensagem: " + Svc.mask(r.p("resposta"))), "/dentista/salas");
        Svc.audit(r, aceitar ? "RESERVA_ACEITA" : "RESERVA_RECUSADA", x.s("titulo") + " " + x.s("data"));
        r.ok(aceitar ? "Reserva confirmada. O profissional recebeu a confirmação por e-mail." : "Pedido recusado.");
        r.redirect("/clinica/salas");
    }

    // ================= REPASSES =================
    static void repasses(Req r) throws Exception {
        List<String[]> rows = new ArrayList<>();
        BigDecimal pago = BigDecimal.ZERO, pend = BigDecimal.ZERO, prev = BigDecimal.ZERO, prod = BigDecimal.ZERO;
        String hoje = today();
        for (Row o : Db.q("SELECT * FROM oportunidades WHERE clinica_id=? AND status='FECHADA' ORDER BY data DESC", r.uid())) {
            Svc.Calc c = Svc.settle(o);
            if (o.b("repasse_pago")) pago = pago.add(c.rep());
            else if (o.s("data").compareTo(hoje) <= 0) pend = pend.add(c.rep());
            else prev = prev.add(c.rep());
            if (o.s("data").compareTo(hoje) <= 0) prod = prod.add(c.prod());
            List<Row> ap = Svc.oppAppts(o);
            long at = ap.stream().filter(a -> a.s("status").equals("ATENDIDO")).count();
            Row du = Svc.user(o.s("dentista_id"));
            String pix = du == null ? "" : du.s("pix");
            rows.add(new String[]{dw(o.s("data")), "<b>" + h(Svc.nome(o.s("dentista_id"))) + "</b>" + (pix.isEmpty() ? "" : "<div class=\"mut\">PIX: " + h(pix) + "</div>"),
                h(Svc.termsText(Svc.T.of(o, "f_"))), at + "/" + ap.size(), money(c.prod()), "<b>" + money(c.rep()) + "</b>", money(c.net()), Svc.settleKind(o),
                !o.b("repasse_pago") && o.s("data").compareTo(hoje) <= 0 ? post(r, "/clinica/repasse/pagar", "Confirmar pagamento", "sm ok",
                    "Confirmar o pagamento de " + money(c.rep()) + " para " + Svc.nome(o.s("dentista_id")) + "?", "id", o.s("id")) : ""});
        }
        String body = "<div class=\"grid g4\">" + kpi("Produção realizada", money(prod), "📈") + kpi("Repasses pagos", money(pago), "✅") + kpi("Repasses a pagar", money(pend), "⏳")
            + kpi("Repasses previstos", money(prev), "🗓️") + "</div><div class=\"card mt\"><h2>Repasses por turno</h2>"
            + table(new String[]{"Data", "Profissional", "Termos", "Atendidos", "Produção", "Repasse", "Clínica", "Situação", ""}, rows, "Nenhum turno contratado ainda.")
            + "<p class=\"mut\">Turnos passados consideram apenas atendimentos marcados como \"Atendido\". Diárias são devidas pelo turno realizado.</p></div>";
        r.html(page(r, "Repasses aos profissionais", body));
    }

    static void pagarRepasse(Req r) throws Exception {
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=? AND clinica_id=? AND status='FECHADA'", r.p("id"), r.uid());
        if (o == null || o.b("repasse_pago")) throw new Bad("Repasse não disponível.");
        if (o.s("data").compareTo(today()) > 0) throw new Bad("O turno ainda não aconteceu.");
        Svc.Calc c = Svc.settle(o);
        Db.tx(() -> {
            Db.x("UPDATE oportunidades SET repasse_pago=TRUE WHERE id=?", o.s("id"));
            BigDecimal pct = Svc.cfgNum("comissao_pct", "0");
            if (pct.signum() > 0 && c.rep().signum() > 0) {
                BigDecimal com = c.rep().multiply(pct).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
                Db.insert("lancamentos", Db.m("tipo", "RECEITA", "categoria", "Comissões", "descricao", "Comissão " + Ui.num(pct) + "% — " + o.s("titulo") + " (" + d(o.s("data")) + ")",
                    "valor", com, "data", today(), "pago", false, "usuario_id", r.uid(), "referencia", o.s("id"), "automatico", true));
            }
        });
        Svc.notify(o.s("dentista_id"), "💸 " + r.user.s("nome") + " confirmou o pagamento de " + money(c.rep()) + " referente a " + d(o.s("data")) + ".", "/dentista/ganhos");
        Svc.audit(r, "REPASSE_PAGO", o.s("titulo") + " " + money(c.rep()));
        r.ok("Pagamento do repasse registrado.");
        r.redirect("/clinica/repasses");
    }
}

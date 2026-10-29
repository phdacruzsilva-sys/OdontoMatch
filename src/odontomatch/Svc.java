package odontomatch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static odontomatch.Ui.h;

/** Regras de negócio compartilhadas. */
public final class Svc {
    private Svc() {}

    // ---------------- catálogos ----------------
    public static final String[] ESPECIALIDADES = {"Clínico Geral", "Endodontia", "Ortodontia", "Implantodontia", "Periodontia", "Prótese",
        "Odontopediatria", "Cirurgia", "Estética/Dentística", "Harmonização Orofacial", "Radiologia"};
    public static final String[][] CATEGORIAS = {{"DENTISTA", "Cirurgião(ã)-dentista"}, {"AUXILIAR", "Auxiliar / Técnico(a) em Saúde Bucal"}, {"ESTUDANTE", "Estudante de Odontologia"}};
    public static final String[] CONVENIOS = {"Amil Dental", "OdontoPrev", "Bradesco Dental", "SulAmérica Odonto", "Unimed Odonto", "MetLife", "GEAP", "Outro"};
    public static final Object[][] PROCS = {{"Consulta/Avaliação", 150, 30}, {"Limpeza (profilaxia)", 200, 40}, {"Restauração em resina", 250, 45},
        {"Tratamento de canal", 900, 90}, {"Extração simples", 300, 45}, {"Manutenção ortodôntica", 180, 30}, {"Clareamento", 800, 60},
        {"Raspagem periodontal", 350, 60}, {"Implante (etapa cirúrgica)", 2500, 120}, {"Coroa/Prótese", 1500, 60}, {"Urgência/Dor", 200, 30}, {"Outro", 0, 30}};
    public static final String[][] MODELOS = {{"PERCENTUAL", "Porcentagem sobre a produção"}, {"DIARIA", "Diária / valor fixo pelo turno"}, {"MISTO", "Diária mínima + porcentagem"}};
    public static final String[][] MATERIAIS = {{"CLINICA", "Clínica fornece os materiais"}, {"DENTISTA", "Profissional traz os materiais"},
        {"COMPARTILHADO", "Compartilhado (básico da clínica, específico do profissional)"}};
    public static final String[][] ST_AGENDA = {{"AGENDADO", "Agendado"}, {"CONFIRMADO", "Confirmado"}, {"NA_CLINICA", "Na clínica"},
        {"ATENDIDO", "Atendido"}, {"FALTOU", "Faltou"}, {"CANCELADO", "Cancelado"}};
    public static final String[][] ST_ASSINATURA = {{"TESTE", "Em teste"}, {"ATIVA", "Ativa"}, {"INADIMPLENTE", "Inadimplente"}, {"SUSPENSA", "Suspensa"}, {"CANCELADA", "Cancelada"}};
    public static final String[][] DENTE = {{"", "—"}, {"C", "Cárie"}, {"R", "Restaurado"}, {"E", "Canal"}, {"X", "Extrair"}, {"A", "Ausente"}, {"P", "Prótese"}, {"I", "Implante"}};
    public static final String[] CATEG_RECEITA = {"Mensalidades", "Comissões", "Serviços extras", "Outras receitas"};
    public static final String[] CATEG_DESPESA = {"Infraestrutura/Hospedagem", "Taxas de pagamento", "Impostos", "Marketing", "Salários/Pró-labore",
        "Software/Licenças", "Contabilidade/Jurídico", "Suporte", "Estornos", "Outras despesas"};
    public static final String[][] MODALIDADE_SALA = {{"HORA", "Por hora"}, {"TURNO", "Por turno (manhã/tarde/noite)"}, {"DIARIA", "Por dia"}};
    public static final String[][] TIPOS_OCORRENCIA = {{"CANCEL_LIVRE", "Cancelamento com antecedência"}, {"CANCEL_TARDIO", "Cancelamento tardio (6h–24h)"},
        {"STRIKE_CANCEL", "Cancelamento em cima da hora (<6h)"}, {"NO_SHOW", "Não comparecimento (no-show)"}, {"DENUNCIA", "Denúncia em análise"}, {"STRIKE_MANUAL", "Strike aplicado pelo administrador"}};
    public static final String[] CAT_SUPORTE = {"Conta e cadastro", "Pagamento e assinatura", "Turnos e candidaturas", "Aluguel de salas", "Problema técnico", "Sugestão", "Outro"};

    public static String label(String[][] list, String key) {
        for (String[] o : list) if (o[0].equals(key)) return o[1];
        return key;
    }

    /** Categoria do profissional (bancos antigos não têm o campo → dentista). */
    public static String cat(Row u) { String c = u.s("categoria"); return c.isEmpty() ? "DENTISTA" : c; }

    public static String catOpp(Row o) { String c = o.s("categoria"); return c.isEmpty() ? "DENTISTA" : c; }

    public static String docLabel(String cat) {
        return switch (cat) { case "ESTUDANTE" -> "Matrícula / instituição"; case "AUXILIAR" -> "CRO (ASB/TSB)"; case "CLINICA" -> "CNPJ"; default -> "CRO (ex.: CRO-DF 12345)"; };
    }

    // ---------------- configurações ----------------
    private static final Map<String, String> CFG = new ConcurrentHashMap<>();
    private static volatile boolean cfgLoaded;

    private static void loadCfg() {
        if (cfgLoaded) return;
        for (Row r : Db.q("SELECT k, v FROM configuracoes")) CFG.put(r.s("k"), r.s("v"));
        cfgLoaded = true;
    }

    public static String cfg(String k, String def) { loadCfg(); String v = CFG.get(k); return v == null || v.isEmpty() ? def : v; }

    public static BigDecimal cfgNum(String k, String def) { try { return new BigDecimal(cfg(k, def)); } catch (Exception e) { return new BigDecimal(def); } }

    public static void setCfg(String k, String v) {
        loadCfg();
        if (Db.count("SELECT COUNT(*) FROM configuracoes WHERE k=?", k) > 0) Db.x("UPDATE configuracoes SET v=? WHERE k=?", v, k);
        else Db.x("INSERT INTO configuracoes (k, v) VALUES (?, ?)", k, v);
        CFG.put(k, v);
    }

    public static boolean setupDone() { return "true".equals(cfg("setup_ok", "false")) && Db.count("SELECT COUNT(*) FROM usuarios WHERE perfil='ADMIN'") > 0; }

    public static String appName() { return cfg("app_nome", "OdontoMatch"); }

    public static boolean exigeVerificacao() { return "true".equals(cfg("exigir_verificacao", "true")); }

    public static int strikesBloqueio() { try { return Math.max(1, Integer.parseInt(cfg("strikes_bloqueio", "3"))); } catch (Exception e) { return 3; } }

    // ---------------- auditoria e notificações ----------------
    public static void audit(Req r, String acao, String detalhe) {
        Db.insert("auditoria", Db.m("usuario_id", r == null ? null : r.uid(), "email", r == null || r.user == null ? "sistema" : r.user.s("email"),
            "acao", acao, "detalhe", detalhe == null ? "" : (detalhe.length() > 990 ? detalhe.substring(0, 990) : detalhe), "ip", r == null ? "" : r.ip()));
    }

    public static void auditAs(Req r, String email, String acao, String detalhe) {
        Db.insert("auditoria", Db.m("usuario_id", null, "email", email, "acao", acao, "detalhe", detalhe, "ip", r == null ? "" : r.ip()));
    }

    public static void notify(String userId, String texto, String link) {
        if (userId == null) return;
        Db.insert("notificacoes", Db.m("usuario_id", userId, "texto", texto.length() > 490 ? texto.substring(0, 490) : texto, "link", link, "lida", false));
    }

    public static void notifyAdmins(String texto, String link) {
        for (Row a : Db.q("SELECT id FROM usuarios WHERE perfil='ADMIN' AND ativo=TRUE")) notify(a.s("id"), texto, link);
    }

    /** Notificação + e-mail. */
    public static void notifyMail(String userId, String texto, String link, String assunto, String corpo, String ref) {
        notify(userId, texto, link);
        Row u = user(userId);
        if (u != null) Mail.queue(u.s("email"), u.s("nome"), assunto, corpo, ref);
    }

    public static Row user(String id) { return id == null ? null : Db.one("SELECT * FROM usuarios WHERE id=?", id); }

    public static String nome(String id) { Row u = user(id); return u == null ? "—" : u.s("nome"); }

    // ---------------- assinaturas ----------------
    public static void criarAssinatura(String userId, String perfil) {
        boolean clin = perfil.equals("CLINICA");
        BigDecimal valor = cfgNum(clin ? "preco_clinica" : "preco_dentista", clin ? "149.90" : "0");
        int dias = Integer.parseInt(cfg("dias_teste", "30"));
        String status = dias > 0 && valor.signum() > 0 ? "TESTE" : "ATIVA";
        Db.insert("assinaturas", Db.m("usuario_id", userId, "plano", clin ? "Clínica Pro" : "Profissional", "valor", valor, "status", status,
            "teste_ate", Ui.plusDays(Ui.today(), dias), "atualizado", System.currentTimeMillis()));
    }

    public static Row assinatura(String userId) { return Db.one("SELECT * FROM assinaturas WHERE usuario_id=?", userId); }

    public static String stBadge(String st) {
        String cls = switch (st) { case "ATIVA" -> "b-ok"; case "TESTE" -> "b-p"; case "INADIMPLENTE" -> "b-w"; case "SUSPENSA", "CANCELADA" -> "b-e"; default -> "b-m"; };
        return Ui.badge(label(ST_ASSINATURA, st), cls);
    }

    public static int gerarFaturas(Req r, String competencia) {
        YearMonth ym = YearMonth.parse(competencia);
        int dia = Math.max(1, Math.min(28, Integer.parseInt(cfg("dia_vencimento", "10"))));
        String venc = ym.atDay(dia).toString();
        String hoje = Ui.today();
        int n = 0;
        for (Row s : Db.q("SELECT a.*, u.nome, u.ativo FROM assinaturas a JOIN usuarios u ON u.id=a.usuario_id")) {
            String st = s.s("status");
            boolean cobravel = st.equals("ATIVA") || st.equals("INADIMPLENTE") || (st.equals("TESTE") && s.s("teste_ate").compareTo(hoje) < 0);
            if (!cobravel || !s.b("ativo") || s.d("valor").signum() <= 0) continue;
            if (Db.count("SELECT COUNT(*) FROM faturas WHERE assinatura_id=? AND competencia=? AND status<>'CANCELADA'", s.s("id"), competencia) > 0) continue;
            Db.insert("faturas", Db.m("usuario_id", s.s("usuario_id"), "assinatura_id", s.s("id"), "competencia", competencia,
                "descricao", s.s("plano") + " — " + Ui.monthName(competencia), "valor", s.d("valor"), "vencimento", venc, "status", "ABERTA"));
            if (st.equals("TESTE")) Db.x("UPDATE assinaturas SET status='ATIVA', atualizado=? WHERE id=?", System.currentTimeMillis(), s.s("id"));
            notify(s.s("usuario_id"), "Nova fatura disponível: " + s.s("plano") + " — " + Ui.money(s.d("valor")) + ", vencimento " + Ui.d(venc) + ".", "/conta/plano");
            n++;
        }
        if (r != null) audit(r, "FATURAS_GERADAS", competencia + ": " + n + " fatura(s)");
        return n;
    }

    public static int atualizarInadimplencia() {
        String hoje = Ui.today();
        int n = 0;
        for (Row s : Db.q("SELECT * FROM assinaturas WHERE status IN ('ATIVA','INADIMPLENTE')")) {
            long venc = Db.count("SELECT COUNT(*) FROM faturas WHERE assinatura_id=? AND status='ABERTA' AND vencimento<?", s.s("id"), hoje);
            String novo = venc > 0 ? "INADIMPLENTE" : "ATIVA";
            if (!novo.equals(s.s("status"))) {
                Db.x("UPDATE assinaturas SET status=?, atualizado=? WHERE id=?", novo, System.currentTimeMillis(), s.s("id"));
                if (novo.equals("INADIMPLENTE")) notify(s.s("usuario_id"), "Há fatura vencida no seu plano. Regularize para manter o acesso.", "/conta/plano");
                n++;
            }
        }
        return n;
    }

    public static void pagarFatura(Req r, String faturaId, String metodo, String data) {
        Db.tx(() -> {
            Row f = Db.one("SELECT * FROM faturas WHERE id=?", faturaId);
            if (f == null) throw new Bad("Fatura não encontrada.");
            if (f.s("status").equals("PAGA")) throw new Bad("Fatura já está paga.");
            Db.x("UPDATE faturas SET status='PAGA', pago_em=?, metodo=? WHERE id=?", data, metodo, faturaId);
            Db.insert("lancamentos", Db.m("tipo", "RECEITA", "categoria", "Mensalidades", "descricao", f.s("descricao") + " — " + nome(f.s("usuario_id")),
                "valor", f.d("valor"), "data", data, "pago", true, "usuario_id", f.s("usuario_id"), "fatura_id", faturaId, "referencia", metodo, "automatico", true));
            Row s = Db.one("SELECT * FROM assinaturas WHERE id=?", f.s("assinatura_id"));
            if (s != null && (s.s("status").equals("INADIMPLENTE") || s.s("status").equals("TESTE"))
                && Db.count("SELECT COUNT(*) FROM faturas WHERE assinatura_id=? AND status='ABERTA' AND vencimento<?", s.s("id"), Ui.today()) == 0)
                Db.x("UPDATE assinaturas SET status='ATIVA', atualizado=? WHERE id=?", System.currentTimeMillis(), s.s("id"));
            notify(f.s("usuario_id"), "Pagamento confirmado: " + f.s("descricao") + " (" + Ui.money(f.d("valor")) + "). Obrigado!", "/conta/plano");
            audit(r, "FATURA_PAGA", f.s("descricao") + " " + Ui.money(f.d("valor")) + " via " + metodo);
        });
    }

    public static String banner(Req r) {
        if (r.user == null || r.role().equals("ADMIN")) return "";
        StringBuilder b = new StringBuilder();
        if (!r.user.b("verificado") && exigeVerificacao())
            b.append("<div class=\"banner\">⏳ Seu cadastro está <b>em análise</b> (verificação de ").append(r.role().equals("CLINICA") ? "CNPJ" : "registro/CRO")
                .append("). Você já pode explorar o sistema; ").append(r.role().equals("CLINICA") ? "a publicação de turnos" : "o envio de propostas").append(" será liberado após a aprovação.</div>");
        if (r.role().equals("DENTISTA") && (r.user.s("lat").isEmpty() || r.user.s("disponibilidade").isEmpty()))
            b.append("<div class=\"banner\" style=\"background:#eef2ff;color:#3730a3;border-color:#c7d2fe\">🎯 Complete sua <b>localização e disponibilidade</b> para receber os melhores matches. <a href=\"/conta/perfil\">Completar perfil</a></div>");
        if (r.role().equals("CLINICA") && r.user.s("lat").isEmpty())
            b.append("<div class=\"banner\" style=\"background:#eef2ff;color:#3730a3;border-color:#c7d2fe\">📍 Informe a <b>localização da clínica</b> para mostrar a distância dos profissionais. <a href=\"/conta/perfil\">Completar perfil</a></div>");
        Row s = assinatura(r.uid());
        if (s != null) {
            String st = s.s("status");
            if (st.equals("TESTE") && s.s("teste_ate").compareTo(Ui.today()) >= 0)
                b.append("<div class=\"banner\" style=\"background:#ecfeff;color:#155e75;border-color:#a5f3fc\">🎁 Período de teste gratuito até <b>")
                    .append(Ui.d(s.s("teste_ate"))).append("</b>. <a href=\"/conta/plano\">Ver plano</a></div>");
            else if (st.equals("INADIMPLENTE") || st.equals("TESTE"))
                b.append("<div class=\"banner\">💳 Existe pendência no seu plano. <a href=\"/conta/plano\">Regularizar agora</a></div>");
        }
        return b.toString();
    }

    // ---------------- reputação ----------------
    public static double avgRating(String userId) {
        Row r = Db.one("SELECT COUNT(*) AS n, AVG(nota * 1.0) AS m FROM avaliacoes WHERE para_id=?", userId);
        return r == null || r.l("n") == 0 ? -1 : r.d("m").doubleValue();
    }

    public static String stars(String userId) {
        Row r = Db.one("SELECT COUNT(*) AS n, AVG(nota * 1.0) AS m FROM avaliacoes WHERE para_id=?", userId);
        long n = r == null ? 0 : r.l("n");
        if (n == 0) return Ui.badge("Novo", "b-m");
        BigDecimal m = r.d("m").setScale(1, RoundingMode.HALF_UP);
        return "<span class=\"stars\">★ " + Ui.num(m) + "</span> <span class=\"mut\">(" + n + ")</span>";
    }

    /** Selos de confiança: registro verificado, strikes e cancelamentos tardios. */
    public static String selos(Row u) {
        if (u == null) return "";
        StringBuilder b = new StringBuilder();
        if (u.b("verificado")) {
            String p = u.s("perfil");
            b.append(Ui.badge(p.equals("CLINICA") ? "✔ CNPJ verificado" : cat(u).equals("ESTUDANTE") ? "✔ Matrícula verificada" : "✔ CRO verificado", "b-ver")).append(' ');
        }
        if (u.i("strikes") > 0) b.append(Ui.badge("⚠ " + u.i("strikes") + " strike(s)", "b-e")).append(' ');
        if (u.i("cancel_tardios") > 0) b.append(Ui.badge(u.i("cancel_tardios") + " cancelamento(s) tardio(s)", "b-w")).append(' ');
        return b.toString();
    }

    // ---------------- localização, disponibilidade e match ----------------
    public static Double distKm(Row a, Row b) {
        try {
            if (a == null || b == null || a.s("lat").isEmpty() || b.s("lat").isEmpty()) return null;
            double la1 = Math.toRadians(Double.parseDouble(a.s("lat"))), lo1 = Math.toRadians(Double.parseDouble(a.s("lng")));
            double la2 = Math.toRadians(Double.parseDouble(b.s("lat"))), lo2 = Math.toRadians(Double.parseDouble(b.s("lng")));
            double h = Math.pow(Math.sin((la2 - la1) / 2), 2) + Math.cos(la1) * Math.cos(la2) * Math.pow(Math.sin((lo2 - lo1) / 2), 2);
            return 6371 * 2 * Math.asin(Math.sqrt(h));
        } catch (Exception e) { return null; }
    }

    public static String kmText(Double d) {
        if (d == null) return "";
        return d < 1 ? "< 1 km" : String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", d);
    }

    public static boolean validCoord(String lat, String lng) {
        try {
            double a = Double.parseDouble(lat), b = Double.parseDouble(lng);
            return a >= -90 && a <= 90 && b >= -180 && b <= 180;
        } catch (Exception e) { return false; }
    }

    /** Turnos (M/T/N) cobertos por um intervalo de horário. */
    public static Set<String> turnos(String hi, String hf) {
        Set<String> s = new LinkedHashSet<>();
        if (hi.compareTo("12:00") < 0) s.add("M");
        if (hi.compareTo("18:00") < 0 && hf.compareTo("12:00") > 0) s.add("T");
        if (hf.compareTo("18:00") > 0) s.add("N");
        return s;
    }

    /** null = profissional não informou disponibilidade. */
    public static Boolean disponivel(Row pro, String data, String hi, String hf) {
        String disp = pro.s("disponibilidade");
        if (disp.isEmpty()) return null;
        Set<String> on = new HashSet<>(Arrays.asList(disp.split(",")));
        int dow = LocalDate.parse(data).getDayOfWeek().getValue();
        for (String t : turnos(hi, hf)) if (on.contains(dow + t)) return true;
        return false;
    }

    public record Match(int score, List<String> motivos, Double km) {}

    /**
     * Pontuação de compatibilidade (0–100) entre profissional e turno:
     * especialidade/categoria 30 · distância 25 · avaliação 20 · disponibilidade 15 · confiabilidade 10.
     */
    public static Match match(Row pro, Row opp, Row clinica) {
        List<String> why = new ArrayList<>();
        int s = 0;
        String cOpp = catOpp(opp);
        if (cOpp.equals("DENTISTA")) {
            List<String> sp = Arrays.asList(pro.s("especialidades").split(";"));
            if (sp.contains(opp.s("especialidade"))) { s += 30; why.add("✔ " + opp.s("especialidade")); }
            else if (sp.contains("Clínico Geral")) { s += 10; why.add("≈ Clínico geral"); }
            else why.add("✖ especialidade diferente");
        } else { s += 30; why.add("✔ " + label(CATEGORIAS, cOpp)); }
        Double km = distKm(pro, clinica);
        int raio = pro.i("raio_km") > 0 ? pro.i("raio_km") : 30;
        if (km != null) {
            int p = km <= 5 ? 25 : km <= 10 ? 22 : km <= 20 ? 18 : km <= raio ? 14 : km <= 50 ? 6 : 0;
            s += p;
            why.add((km <= raio ? "📍 " : "⚠ fora do raio · ") + kmText(km));
        } else if (!pro.s("cidade").isEmpty() && pro.s("cidade").equalsIgnoreCase(clinica.s("cidade"))) { s += 15; why.add("📍 mesma cidade"); }
        else { s += 8; why.add("📍 distância não informada"); }
        double av = avgRating(pro.s("id"));
        if (av < 0) { s += 12; why.add("★ novo"); }
        else { s += (int) Math.round(av / 5.0 * 20); why.add("★ " + String.format(Locale.forLanguageTag("pt-BR"), "%.1f", av)); }
        Boolean disp = disponivel(pro, opp.s("data"), opp.s("hora_ini"), opp.s("hora_fim"));
        if (disp == null) { s += 8; why.add("🗓 disponibilidade não informada"); }
        else if (disp) { s += 15; why.add("🗓 disponível nesse turno"); }
        else why.add("🗓 fora da disponibilidade");
        int conf = (pro.b("verificado") ? 5 : 0) + Math.max(0, 5 - pro.i("strikes") * 3 - Math.min(2, pro.i("cancel_tardios")));
        s += conf;
        if (pro.i("strikes") > 0) why.add("⚠ " + pro.i("strikes") + " strike(s)");
        return new Match(Math.max(0, Math.min(100, s)), why, km);
    }

    public static String matchBadge(Match m) {
        return "<span class=\"match\">🎯 " + m.score() + "% match</span>";
    }

    public static String matchWhy(Match m) {
        StringBuilder b = new StringBuilder("<div class=\"meter\"><span style=\"width:" + m.score() + "%\"></span></div><div class=\"why\">");
        for (String w : m.motivos()) b.append("<span>").append(h(w)).append("</span>");
        return b.append("</div>").toString();
    }

    /** Profissionais elegíveis para o turno (categoria correta, ativos, verificados quando exigido). */
    public static List<Row> elegiveis(Row opp) {
        String c = catOpp(opp);
        List<Row> out = new ArrayList<>();
        for (Row d : Db.q("SELECT * FROM usuarios WHERE perfil='DENTISTA' AND ativo=TRUE")) {
            if (!cat(d).equals(c)) continue;
            if (exigeVerificacao() && !d.b("verificado")) continue;
            out.add(d);
        }
        return out;
    }

    // ---------------- chat seguro ----------------
    private static final java.util.regex.Pattern P_EMAIL = java.util.regex.Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final java.util.regex.Pattern P_FONE = java.util.regex.Pattern.compile("(\\+?\\d[\\d\\s().-]{7,}\\d)");
    private static final java.util.regex.Pattern P_LINK = java.util.regex.Pattern.compile("(?i)(https?://\\S+|wa\\.me/\\S+|www\\.\\S+|instagram\\.com/\\S+|(?<!\\w)@[a-z0-9_.]{3,})");

    /** Oculta telefones, e-mails, links e @perfis enquanto o negócio não é fechado. */
    public static String mask(String t) {
        if (t == null) return "";
        String s = P_EMAIL.matcher(t).replaceAll("[contato oculto]");
        s = P_LINK.matcher(s).replaceAll("[contato oculto]");
        s = P_FONE.matcher(s).replaceAll("[contato oculto]");
        return s;
    }

    // ---------------- termos e cálculos ----------------
    public record T(String modelo, BigDecimal pp, BigDecimal pc, BigDecimal diaria, String mat) {
        static T of(Row r, String px) {
            return new T(r.s(px + "modelo"), r.d(px + "pct_part"), r.d(px + "pct_conv"), r.d(px + "diaria"), r.s(px + "materiais"));
        }
        boolean same(T o) {
            return modelo.equals(o.modelo) && pp.compareTo(o.pp) == 0 && pc.compareTo(o.pc) == 0 && diaria.compareTo(o.diaria) == 0 && mat.equals(o.mat);
        }
    }

    public static T termsFrom(Req r) {
        String m = r.p("modelo");
        if (!m.equals("PERCENTUAL") && !m.equals("DIARIA") && !m.equals("MISTO")) throw new Bad("Selecione o modelo de remuneração.");
        String mat = r.p("materiais");
        if (label(MATERIAIS, mat).equals(mat)) throw new Bad("Selecione quem fornece os materiais.");
        BigDecimal pp = m.equals("DIARIA") ? BigDecimal.ZERO : r.num("pct_part");
        BigDecimal pc = m.equals("DIARIA") ? BigDecimal.ZERO : r.num("pct_conv");
        BigDecimal d = m.equals("PERCENTUAL") ? BigDecimal.ZERO : r.num("diaria");
        if (pp.signum() < 0 || pc.signum() < 0 || d.signum() < 0) throw new Bad("Valores não podem ser negativos.");
        if (pp.compareTo(new BigDecimal(100)) > 0 || pc.compareTo(new BigDecimal(100)) > 0) throw new Bad("Porcentagem máxima é 100%.");
        if (!m.equals("DIARIA") && pp.signum() == 0 && pc.signum() == 0) throw new Bad("Informe ao menos uma porcentagem (particular ou convênio).");
        if (!m.equals("PERCENTUAL") && d.signum() == 0) throw new Bad("Informe o valor da diária.");
        return new T(m, pp, pc, d, mat);
    }

    public static String termsText(T t) {
        if (t.modelo.equals("DIARIA")) return "Diária " + Ui.money(t.diaria);
        List<String> p = new ArrayList<>();
        if (t.pp.signum() > 0) p.add(Ui.num(t.pp) + "% particular");
        if (t.pc.signum() > 0) p.add(Ui.num(t.pc) + "% convênio");
        String pct = p.isEmpty() ? "0%" : String.join(" · ", p);
        return (t.modelo.equals("MISTO") ? "Diária " + Ui.money(t.diaria) + " + " : "") + pct;
    }

    public static String matText(String m) {
        return switch (m) { case "CLINICA" -> "🧰 Materiais da clínica"; case "DENTISTA" -> "🎒 Profissional traz materiais"; case "COMPARTILHADO" -> "🔁 Materiais compartilhados"; default -> ""; };
    }

    public static String termsFields(T t) {
        return "<div class=\"row\">" + Ui.select("Modelo de remuneração", "modelo", MODELOS, t.modelo, "") +
            Ui.input("Diária (R$)", "diaria", Ui.inputNum(t.diaria), "number", "min=\"0\" step=\"0.01\"") + "</div>" +
            "<div class=\"row\">" + Ui.input("% sobre particular", "pct_part", Ui.inputNum(t.pp), "number", "min=\"0\" max=\"100\" step=\"0.01\"") +
            Ui.input("% sobre convênio", "pct_conv", Ui.inputNum(t.pc), "number", "min=\"0\" max=\"100\" step=\"0.01\"") + "</div>" +
            Ui.select("Materiais", "materiais", MATERIAIS, t.mat, "") +
            "<p class=\"mut\">Porcentagem: o profissional recebe o percentual sobre os procedimentos realizados. Diária: valor fixo pelo turno. Misto: diária mínima + percentual.</p>";
    }

    public record Calc(BigDecimal prod, BigDecimal part, BigDecimal conv, BigDecimal rep, int n) {
        BigDecimal net() { return prod.subtract(rep); }
    }

    public static Calc calc(T t, List<Row> ap) {
        BigDecimal part = BigDecimal.ZERO, conv = BigDecimal.ZERO;
        for (Row a : ap) {
            if (a.s("pagador").equals("CONVENIO")) conv = conv.add(a.d("valor"));
            else part = part.add(a.d("valor"));
        }
        BigDecimal rep = BigDecimal.ZERO;
        if (t != null) {
            if (!t.modelo.equals("DIARIA"))
                rep = rep.add(part.multiply(t.pp).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP)).add(conv.multiply(t.pc).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP));
            if (!t.modelo.equals("PERCENTUAL")) rep = rep.add(t.diaria);
        }
        return new Calc(part.add(conv), part, conv, rep, ap.size());
    }

    public static List<Row> oppAppts(Row o) {
        if (o.s("status").equals("FECHADA"))
            return Db.q("SELECT * FROM agendamentos WHERE oportunidade_id=? AND status<>'CANCELADO' ORDER BY hora", o.s("id"));
        return Db.q("SELECT * FROM agendamentos WHERE clinica_id=? AND data=? AND dentista_id IS NULL AND status<>'CANCELADO' AND hora>=? AND hora<? ORDER BY hora",
            o.s("clinica_id"), o.s("data"), o.s("hora_ini"), o.s("hora_fim"));
    }

    public static Calc settle(Row o) {
        List<Row> ap = oppAppts(o);
        List<Row> use = new ArrayList<>();
        boolean past = o.s("data").compareTo(Ui.today()) < 0;
        for (Row a : ap) {
            String st = a.s("status");
            if (past ? st.equals("ATENDIDO") : !st.equals("FALTOU")) use.add(a);
        }
        return calc(T.of(o, "f_"), use);
    }

    public static String settleKind(Row o) {
        if (o.b("repasse_pago")) return Ui.badge("Pago", "b-ok");
        int c = o.s("data").compareTo(Ui.today());
        if (c < 0) return Ui.badge("A pagar", "b-w");
        if (c == 0) return Ui.badge("Hoje", "b-p");
        return Ui.badge("Previsto", "b-m");
    }

    public static String oppBadge(Row o) {
        return switch (o.s("status")) {
            case "FECHADA" -> Ui.badge("Contratada", "b-ok");
            case "CANCELADA" -> Ui.badge("Cancelada", "b-m");
            default -> o.s("data").compareTo(Ui.today()) < 0 ? Ui.badge("Expirada", "b-m") : Ui.badge("Aberta", "b-p");
        };
    }

    /** Horas que faltam para o início do turno (negativo = já começou). */
    public static double horasAte(String data, String hora) {
        LocalDateTime ini = LocalDateTime.of(LocalDate.parse(data), LocalTime.parse(hora));
        return Duration.between(LocalDateTime.now(Ui.zone()), ini).toMinutes() / 60.0;
    }

    // ---------------- negociações ----------------
    public static Row lastOffer(String negId) {
        List<Row> l = Db.q("SELECT * FROM mensagens WHERE negociacao_id=? AND tipo='OFERTA' ORDER BY criado DESC", negId);
        return l.isEmpty() ? null : l.get(0);
    }

    public static List<Row> myNegs(Req r) {
        if (r.role().equals("CLINICA"))
            return Db.q("SELECT n.*, o.titulo, o.data, o.hora_ini, o.hora_fim, o.status AS ostatus, o.clinica_id, o.aceita_contra FROM negociacoes n JOIN oportunidades o ON o.id=n.oportunidade_id WHERE o.clinica_id=? ORDER BY n.atualizado DESC", r.uid());
        return Db.q("SELECT n.*, o.titulo, o.data, o.hora_ini, o.hora_fim, o.status AS ostatus, o.clinica_id, o.aceita_contra FROM negociacoes n JOIN oportunidades o ON o.id=n.oportunidade_id WHERE n.dentista_id=? ORDER BY n.atualizado DESC", r.uid());
    }

    public static boolean myTurn(Req r, Row n) {
        if (!n.s("status").equals("ABERTA") || !n.s("ostatus").equals("ABERTA")) return false;
        Row lo = lastOffer(n.s("id"));
        return lo != null && !lo.s("autor").equals(r.role());
    }

    public static long myTurnCount(Req r) {
        long c = 0;
        for (Row n : myNegs(r)) if (myTurn(r, n)) c++;
        c += r.role().equals("CLINICA") ? Db.count("SELECT COUNT(*) FROM reservas WHERE clinica_id=? AND status='PENDENTE'", r.uid()) : 0;
        return c;
    }

    /** Aceita a última oferta: fecha o turno, vincula agendamentos, encerra concorrentes e envia e-mails de confirmação. */
    public static void aceitar(Req r, String negId) {
        String[] info = new String[6];
        Db.tx(() -> {
            Row n = Db.one("SELECT n.*, o.status AS ostatus, o.clinica_id, o.titulo, o.data, o.hora_ini, o.hora_fim FROM negociacoes n JOIN oportunidades o ON o.id=n.oportunidade_id WHERE n.id=?", negId);
            if (n == null) throw new Bad("Negociação não encontrada.");
            boolean dono = r.role().equals("CLINICA") ? n.s("clinica_id").equals(r.uid()) : n.s("dentista_id").equals(r.uid());
            if (!dono) throw new Bad("Sem permissão.");
            if (!myTurn(r, n)) throw new Bad("Não é possível aceitar agora: aguarde a resposta da outra parte.");
            Row conflito = Db.one("SELECT titulo FROM oportunidades WHERE dentista_id=? AND status='FECHADA' AND data=? AND hora_ini<? AND hora_fim>?",
                n.s("dentista_id"), n.s("data"), n.s("hora_fim"), n.s("hora_ini"));
            if (conflito != null) throw new Bad("O profissional já está contratado em outro turno nesse horário (" + conflito.s("titulo") + ").");
            Row lo = lastOffer(negId);
            String oid = n.s("oportunidade_id");
            Db.x("UPDATE oportunidades SET status='FECHADA', dentista_id=?, f_modelo=?, f_pct_part=?, f_pct_conv=?, f_diaria=?, f_materiais=?, fechado_em=?, repasse_pago=FALSE, checkin=NULL WHERE id=?",
                n.s("dentista_id"), lo.s("modelo"), lo.d("pct_part"), lo.d("pct_conv"), lo.d("diaria"), lo.s("materiais"), System.currentTimeMillis(), oid);
            int vinc = Db.x("UPDATE agendamentos SET dentista_id=?, oportunidade_id=? WHERE clinica_id=? AND data=? AND dentista_id IS NULL AND status<>'CANCELADO' AND hora>=? AND hora<?",
                n.s("dentista_id"), oid, n.s("clinica_id"), n.s("data"), n.s("hora_ini"), n.s("hora_fim"));
            Db.x("UPDATE negociacoes SET status='FECHADA', atualizado=? WHERE id=?", System.currentTimeMillis(), negId);
            Db.insert("mensagens", Db.m("negociacao_id", negId, "autor", r.role(), "tipo", "ACEITE", "texto", "Proposta aceita."));
            for (Row o : Db.q("SELECT * FROM negociacoes WHERE oportunidade_id=? AND id<>? AND status='ABERTA'", oid, negId)) {
                Db.x("UPDATE negociacoes SET status='ENCERRADA', atualizado=? WHERE id=?", System.currentTimeMillis(), o.s("id"));
                Db.insert("mensagens", Db.m("negociacao_id", o.s("id"), "autor", "SISTEMA", "tipo", "SISTEMA", "texto", "Vaga preenchida por outro profissional."));
                notify(o.s("dentista_id"), "A oportunidade \"" + n.s("titulo") + "\" foi preenchida por outro profissional.", "/negociacao?id=" + o.s("id"));
            }
            info[0] = n.s("dentista_id"); info[1] = n.s("clinica_id"); info[2] = n.s("titulo"); info[3] = oid; info[4] = String.valueOf(vinc); info[5] = termsText(T.of(lo, ""));
            audit(r, "NEGOCIO_FECHADO", n.s("titulo") + " · " + info[5]);
        });
        confirmacaoTurno(info[3], Integer.parseInt(info[4]));
    }

    /** E-mail automático de confirmação do turno para clínica e profissional (comprovante). */
    static void confirmacaoTurno(String oid, int vinc) {
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=?", oid);
        Row c = user(o.s("clinica_id")), d = user(o.s("dentista_id"));
        String resumo = "Turno: " + o.s("titulo") + "\nData: " + Ui.dLong(o.s("data")) + ", das " + o.s("hora_ini") + " às " + o.s("hora_fim")
            + "\nClínica: " + c.s("nome") + " — " + c.s("endereco") + " " + c.s("cidade") + " · Tel.: " + c.s("telefone")
            + "\nProfissional: " + d.s("nome") + " (" + d.s("documento") + ") · Tel.: " + d.s("telefone")
            + "\nRemuneração combinada: " + termsText(T.of(o, "f_")) + "\nMateriais: " + label(MATERIAIS, o.s("f_materiais"))
            + "\n\nPolítica de cancelamento: mais de 24h antes é livre; entre 24h e 6h fica registrado na reputação; com menos de 6h ou não comparecimento gera strike.";
        notifyMail(d.s("id"), "🎉 Turno confirmado: \"" + o.s("titulo") + "\" em " + Ui.d(o.s("data")) + ". " + vinc + " paciente(s) na sua agenda.", "/dentista/agenda",
            "Turno confirmado — " + Ui.d(o.s("data")) + " · " + c.s("nome"), "Seu turno foi confirmado. Este e-mail serve como comprovante.\n\n" + resumo, "OPORTUNIDADE:" + oid);
        notifyMail(c.s("id"), "🎉 " + d.s("nome") + " confirmado(a) para \"" + o.s("titulo") + "\" em " + Ui.d(o.s("data")) + ".", "/clinica/oportunidades",
            "Profissional confirmado — " + Ui.d(o.s("data")) + " · " + d.s("nome"), "A contratação foi confirmada. Este e-mail serve como comprovante.\n\n" + resumo, "OPORTUNIDADE:" + oid);
    }

    // ---------------- cancelamentos e strikes ----------------
    /** Registra ocorrência; se for strike, incrementa e bloqueia automaticamente ao atingir o limite. */
    public static void ocorrencia(Req r, String userId, String oppId, String tipo, boolean strike, String detalhe) {
        Db.insert("ocorrencias", Db.m("usuario_id", userId, "oportunidade_id", oppId, "tipo", tipo, "strike", strike, "anulada", false, "detalhe", detalhe));
        Row u = user(userId);
        if (tipo.equals("CANCEL_TARDIO")) Db.x("UPDATE usuarios SET cancel_tardios=? WHERE id=?", u.i("cancel_tardios") + 1, userId);
        if (!strike) return;
        int s = u.i("strikes") + 1;
        Db.x("UPDATE usuarios SET strikes=? WHERE id=?", s, userId);
        int lim = strikesBloqueio();
        String txt = "⚠ Você recebeu um strike (" + s + " de " + lim + "): " + label(TIPOS_OCORRENCIA, tipo) + ". Ao atingir " + lim + " strikes a conta é bloqueada.";
        notifyMail(userId, txt, "/conta/perfil", "Strike registrado na sua conta", txt + "\n\nDetalhe: " + detalhe + "\nSe discordar, responda pelo formulário de suporte.", "STRIKE:" + userId);
        if (s >= lim) {
            Db.x("UPDATE usuarios SET ativo=FALSE WHERE id=?", userId);
            Sec.logoutUser(userId);
            notifyAdmins("🚫 " + u.s("nome") + " foi bloqueado(a) automaticamente ao atingir " + s + " strikes.", "/admin/usuario?id=" + userId);
            audit(r, "BLOQUEIO_AUTOMATICO_STRIKES", u.s("email") + " · " + s + " strikes");
        }
    }

    /** Desfaz o vínculo do turno contratado. reabrir=true volta a oportunidade para o marketplace. */
    static void desvincular(String oid, boolean reabrir, String motivo) {
        Db.x("UPDATE agendamentos SET dentista_id=NULL, oportunidade_id=NULL WHERE oportunidade_id=?", oid);
        Db.x("UPDATE negociacoes SET status='CANCELADA', atualizado=? WHERE oportunidade_id=? AND status='FECHADA'", System.currentTimeMillis(), oid);
        Db.x("UPDATE oportunidades SET status=?, dentista_id=NULL, f_modelo=NULL, f_pct_part=NULL, f_pct_conv=NULL, f_diaria=NULL, f_materiais=NULL, checkin=NULL, fechado_em=NULL, motivo_cancel=? WHERE id=?",
            reabrir ? "ABERTA" : "CANCELADA", motivo, oid);
    }

    /** Cancelamento de turno contratado por clínica ou profissional, aplicando a política 24h/6h. */
    public static String cancelarTurno(Req r, String oid, String motivo) {
        Row o = Db.one("SELECT * FROM oportunidades WHERE id=? AND status='FECHADA'", oid);
        if (o == null) throw new Bad("Turno não encontrado ou não está contratado.");
        boolean clin = r.role().equals("CLINICA") && o.s("clinica_id").equals(r.uid());
        boolean pro = r.role().equals("DENTISTA") && o.s("dentista_id").equals(r.uid());
        if (!clin && !pro) throw new Bad("Sem permissão.");
        if (motivo.isBlank()) throw new Bad("Informe o motivo do cancelamento.");
        double h = horasAte(o.s("data"), o.s("hora_ini"));
        if (h < -12) throw new Bad("Este turno já aconteceu e não pode mais ser cancelado.");
        String tipo = h >= 24 ? "CANCEL_LIVRE" : h >= 6 ? "CANCEL_TARDIO" : "STRIKE_CANCEL";
        String outro = clin ? o.s("dentista_id") : o.s("clinica_id");
        String det = o.s("titulo") + " em " + Ui.d(o.s("data")) + " " + o.s("hora_ini") + " · cancelado por " + r.user.s("nome") + " com " + String.format(Locale.US, "%.1f", h) + "h de antecedência · motivo: " + motivo;
        boolean reabrir = pro && o.s("data").compareTo(Ui.today()) >= 0;
        Db.tx(() -> {
            desvincular(oid, reabrir, motivo);
            ocorrencia(r, r.uid(), oid, tipo, tipo.equals("STRIKE_CANCEL"), det);
        });
        String quem = clin ? "A clínica " + r.user.s("nome") : r.user.s("nome");
        notifyMail(outro, "❌ " + quem + " cancelou o turno \"" + o.s("titulo") + "\" de " + Ui.d(o.s("data")) + ". Motivo: " + motivo, clin ? "/dentista/agenda" : "/clinica/oportunidades",
            "Turno cancelado — " + Ui.d(o.s("data")), quem + " cancelou o turno \"" + o.s("titulo") + "\" de " + Ui.dLong(o.s("data")) + " (" + o.s("hora_ini") + "–" + o.s("hora_fim") + ").\nMotivo: " + motivo
                + (reabrir ? "\n\nA vaga foi reaberta automaticamente no marketplace." : ""), "OPORTUNIDADE:" + oid);
        if (reabrir) for (Row d : elegiveis(o)) if (!d.s("id").equals(r.uid())) notify(d.s("id"), "🔥 Vaga reaberta: \"" + o.s("titulo") + "\" em " + Ui.d(o.s("data")) + ".", "/dentista/oportunidade?id=" + oid);
        audit(r, "TURNO_CANCELADO", det + " · " + tipo);
        return tipo;
    }

    // ---------------- pacientes ----------------
    public static boolean canSeePatient(Req r, Row p) {
        if (p == null) return false;
        if (r.role().equals("CLINICA")) return p.s("clinica_id").equals(r.uid());
        if (r.role().equals("DENTISTA")) return Db.count("SELECT COUNT(*) FROM agendamentos WHERE paciente_id=? AND dentista_id=?", p.s("id"), r.uid()) > 0;
        return false;
    }

    public static List<String> alertas(Row p) {
        List<String> l = new ArrayList<>();
        if (p.has("alergias")) l.add("Alergia: " + p.s("alergias"));
        String c = p.s("condicoes");
        if (c.contains("HIPERTENSO")) l.add("Hipertensão");
        if (c.contains("DIABETICO")) l.add("Diabetes");
        if (c.contains("GESTANTE")) l.add("Gestante");
        if (c.contains("ANTICOAGULANTE")) l.add("Usa anticoagulante");
        if (c.contains("CARDIOPATA")) l.add("Cardiopatia");
        if (c.contains("FUMANTE")) l.add("Fumante");
        if (p.has("doencas")) l.add("Doenças: " + p.s("doencas"));
        return l;
    }

    public static String apStatusSelect(Req r, Row a, String back) {
        StringBuilder b = new StringBuilder("<form method=\"post\" action=\"/agendamento/status\" class=\"inl\">").append(r.csrf())
            .append("<input type=\"hidden\" name=\"id\" value=\"").append(h(a.s("id"))).append("\"><input type=\"hidden\" name=\"voltar\" value=\"").append(h(back)).append("\">")
            .append("<select name=\"status\" onchange=\"this.form.submit()\" style=\"width:auto;display:inline-block\">");
        for (String[] s : ST_AGENDA) b.append("<option value=\"").append(s[0]).append("\"").append(s[0].equals(a.s("status")) ? " selected" : "").append(">").append(s[1]).append("</option>");
        return b.append("</select><noscript><button class=\"btn sm\">OK</button></noscript></form>").toString();
    }

    public static String waLink(Row p, Row a, String clinica) {
        String fone = p.s("telefone").replaceAll("\\D", "");
        if (fone.isEmpty()) return "";
        if (!fone.startsWith("55")) fone = "55" + fone;
        String msg = "Olá, " + p.s("nome").split(" ")[0] + "! Confirmando sua consulta na " + clinica + " em " + Ui.d(a.s("data")) + " às " + a.s("hora")
            + " (" + a.s("procedimento") + "). Responda SIM para confirmar.";
        return "https://wa.me/" + fone + "?text=" + java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static boolean isDate(String s) { try { LocalDate.parse(s); return true; } catch (Exception e) { return false; } }
    public static boolean isTime(String s) { return s.matches("([01]\\d|2[0-3]):[0-5]\\d"); }
}

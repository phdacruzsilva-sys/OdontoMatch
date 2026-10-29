package odontomatch;

import java.math.BigDecimal;
import java.util.Map;

/** Dados de demonstração opcionais criados na configuração inicial. Senha de todos: demo123456 */
final class Demo {
    private Demo() {}

    static final String SENHA = "demo123456";

    static String user(String perfil, String nome, String email, String hash, Map<String, Object> extra) {
        Map<String, Object> m = Db.m("perfil", perfil, "nome", nome, "email", email, "senha_hash", hash, "ativo", true, "verificado", true, "trocar_senha", false,
            "tentativas", 0, "bloqueado_ate", 0L, "ultimo_login", 0L, "cadeiras", 1, "materiais_proprios", false, "experiencia", 0, "especialidades", "",
            "raio_km", 30, "strikes", 0, "cancel_tardios", 0, "disponibilidade", "", "categoria", perfil.equals("DENTISTA") ? "DENTISTA" : "");
        m.putAll(extra);
        if (Db.count("SELECT COUNT(*) FROM usuarios WHERE email=?", email) > 0) return null;
        String id = Db.insert("usuarios", m);
        Svc.criarAssinatura(id, perfil);
        return id;
    }

    static final String SEMANA = "1M,1T,2M,2T,3M,3T,4M,4T,5M,5T,6M";

    static void seed() {
        String hash = Sec.hash(SENHA);
        String hoje = Ui.today();
        Db.tx(() -> {
            String c = user("CLINICA", "Clínica Sorriso Pleno (demo)", "clinica@demo.com", hash, Db.m("documento", "12.345.678/0001-90", "telefone", "61999990001",
                "cidade", "Brasília/DF", "endereco", "SCS Qd. 2, Bl. C, Sala 210 - Asa Sul", "cadeiras", 3, "lat", "-15.7998", "lng", "-47.8919"));
            String ana = user("DENTISTA", "Dra. Ana Lima (demo)", "ana@demo.com", hash, Db.m("documento", "CRO-DF 12345", "telefone", "61988880001", "cidade", "Brasília/DF",
                "especialidades", "Clínico Geral;Endodontia", "experiencia", 10, "bio", "Endodontia mecanizada e clínica geral.", "pix", "ana.demo@pix.com", "materiais_proprios", true,
                "lat", "-15.7633", "lng", "-47.8822", "raio_km", 20, "disponibilidade", SEMANA, "formacao", "Especialização em Endodontia — UnB"));
            String bruno = user("DENTISTA", "Dr. Bruno Costa (demo)", "bruno@demo.com", hash, Db.m("documento", "CRO-DF 23456", "telefone", "61988880002", "cidade", "Águas Claras/DF",
                "especialidades", "Clínico Geral;Ortodontia", "experiencia", 6, "bio", "Clínico geral e ortodontista.", "pix", "bruno.demo@pix.com",
                "lat", "-15.8400", "lng", "-48.0270", "raio_km", 30, "disponibilidade", "1T,2T,3T,4T,5T,6M"));
            String julia = user("DENTISTA", "Júlia Rocha — ASB (demo)", "julia@demo.com", hash, Db.m("categoria", "AUXILIAR", "documento", "CRO-DF ASB 3344", "telefone", "61988880003",
                "cidade", "Guará/DF", "experiencia", 4, "bio", "Auxiliar em saúde bucal com experiência em instrumentação e esterilização.",
                "lat", "-15.8240", "lng", "-47.9790", "raio_km", 15, "disponibilidade", SEMANA));
            String marcos = user("DENTISTA", "Marcos Alves — estudante (demo)", "marcos@demo.com", hash, Db.m("categoria", "ESTUDANTE", "documento", "Matrícula 2021001",
                "telefone", "61988880004", "cidade", "Brasília/DF", "faculdade", "Universidade de Brasília", "semestre", "8º", "bio", "Estudante do último ano buscando experiência clínica.",
                "lat", "-15.7631", "lng", "-47.8708", "raio_km", 10, "disponibilidade", "6M,6T"));
            if (c == null || ana == null || bruno == null) return;
            String[][] pts = {{"Maria Souza", "1968-04-12", "61991110001", "Amil Dental", "Penicilina", "HIPERTENSO", "36:E;46:R;17:C"},
                {"João Pereira", "1990-09-02", "61991110002", "", "", "", "26:C;27:C"}, {"Fernanda Alves", "1995-01-20", "61991110003", "OdontoPrev", "", "GESTANTE", ""},
                {"Lucas Martins", "2001-07-15", "61991110004", "", "", "FUMANTE", "11:R"}, {"Patrícia Gomes", "1975-11-30", "61991110005", "GEAP", "", "DIABETICO", "48:A;38:A"},
                {"Rafael Lima", "1988-03-08", "61991110006", "", "Látex", "", "46:X"}};
            String[] pid = new String[pts.length];
            for (int i = 0; i < pts.length; i++) {
                String[] p = pts[i];
                pid[i] = Db.insert("pacientes", Db.m("clinica_id", c, "nome", p[0], "cpf", "", "nascimento", p[1], "telefone", p[2], "email", "", "convenio", p[3],
                    "carteira", p[3].isEmpty() ? "" : "0000" + (i + 1), "alergias", p[4], "medicamentos", "", "doencas", "", "condicoes", p[5], "obs", "", "odontograma", p[6]));
            }
            String passado = Ui.plusDays(hoje, -3);
            String o1 = Db.insert("oportunidades", Db.m("clinica_id", c, "titulo", "Clínico geral – manhã", "categoria", "DENTISTA", "especialidade", "Clínico Geral", "data", passado, "hora_ini", "08:00",
                "hora_fim", "12:00", "modelo", "PERCENTUAL", "pct_part", new BigDecimal(40), "pct_conv", new BigDecimal(30), "diaria", BigDecimal.ZERO, "materiais", "CLINICA",
                "obs_materiais", "", "requisitos", "", "urgente", false, "aceita_contra", true, "status", "FECHADA", "dentista_id", ana, "f_modelo", "PERCENTUAL",
                "f_pct_part", new BigDecimal(45), "f_pct_conv", new BigDecimal(30), "f_diaria", BigDecimal.ZERO, "f_materiais", "CLINICA", "repasse_pago", false,
                "fechado_em", System.currentTimeMillis() - 5 * 86400000L, "checkin", "07:55"));
            String n1 = Db.insert("negociacoes", Db.m("oportunidade_id", o1, "dentista_id", ana, "status", "FECHADA", "atualizado", System.currentTimeMillis()));
            Db.insert("mensagens", Db.m("negociacao_id", n1, "autor", "DENTISTA", "tipo", "OFERTA", "modelo", "PERCENTUAL", "pct_part", new BigDecimal(45), "pct_conv", new BigDecimal(30),
                "diaria", BigDecimal.ZERO, "materiais", "CLINICA", "texto", "Posso cobrir a manhã com 45% no particular.", "criado", System.currentTimeMillis() - 6 * 86400000L));
            Db.insert("mensagens", Db.m("negociacao_id", n1, "autor", "CLINICA", "tipo", "ACEITE", "texto", "Proposta aceita.", "criado", System.currentTimeMillis() - 5 * 86400000L));
            ap(c, pid[0], ana, o1, passado, "08:00", "Tratamento de canal", "900", "PARTICULAR", "", "ATENDIDO");
            ap(c, pid[4], ana, o1, passado, "09:30", "Restauração em resina", "180", "CONVENIO", "GEAP", "ATENDIDO");
            ap(c, pid[1], ana, o1, passado, "10:30", "Consulta/Avaliação", "150", "PARTICULAR", "", "FALTOU");
            Db.insert("evolucoes", Db.m("paciente_id", pid[0], "autor_id", ana, "autor_nome", "Dra. Ana Lima (demo)", "texto", "Abertura coronária do 36 e odontometria. Retorno para obturação."));
            ap(c, pid[3], null, null, hoje, "14:00", "Limpeza (profilaxia)", "200", "PARTICULAR", "", "CONFIRMADO");
            String futuro = Ui.plusDays(hoje, 2);
            ap(c, pid[2], null, null, futuro, "08:00", "Limpeza (profilaxia)", "120", "CONVENIO", "OdontoPrev", "AGENDADO");
            ap(c, pid[5], null, null, futuro, "09:00", "Extração simples", "300", "PARTICULAR", "", "AGENDADO");
            ap(c, pid[1], null, null, futuro, "10:00", "Restauração em resina", "250", "PARTICULAR", "", "AGENDADO");
            String o2 = Db.insert("oportunidades", Db.m("clinica_id", c, "titulo", "Clínico geral – cobertura de férias", "categoria", "DENTISTA", "especialidade", "Clínico Geral", "data", futuro,
                "hora_ini", "08:00", "hora_fim", "12:00", "modelo", "PERCENTUAL", "pct_part", new BigDecimal(40), "pct_conv", new BigDecimal(30), "diaria", BigDecimal.ZERO,
                "materiais", "CLINICA", "obs_materiais", "Clínica fornece material de consumo, EPIs e autoclave.", "requisitos", "Experiência mínima de 2 anos.",
                "urgente", true, "aceita_contra", true, "status", "ABERTA", "repasse_pago", false));
            String n2 = Db.insert("negociacoes", Db.m("oportunidade_id", o2, "dentista_id", bruno, "status", "ABERTA", "atualizado", System.currentTimeMillis()));
            Db.insert("mensagens", Db.m("negociacao_id", n2, "autor", "DENTISTA", "tipo", "OFERTA", "modelo", "MISTO", "pct_part", new BigDecimal(40), "pct_conv", new BigDecimal(30),
                "diaria", new BigDecimal(100), "materiais", "CLINICA", "texto", "Tenho disponibilidade. Proponho diária mínima de R$ 100 + os mesmos percentuais."));
            Db.insert("oportunidades", Db.m("clinica_id", c, "titulo", "Auxiliar para turno de cirurgias", "categoria", "AUXILIAR", "especialidade", "Cirurgia", "data", Ui.plusDays(hoje, 3),
                "hora_ini", "13:00", "hora_fim", "18:00", "modelo", "DIARIA", "pct_part", BigDecimal.ZERO, "pct_conv", BigDecimal.ZERO, "diaria", new BigDecimal(120),
                "materiais", "CLINICA", "obs_materiais", "EPIs fornecidos pela clínica.", "requisitos", "Experiência com instrumentação cirúrgica.",
                "urgente", false, "aceita_contra", true, "status", "ABERTA", "repasse_pago", false));
            Db.insert("salas", Db.m("clinica_id", c, "titulo", "Consultório 3 completo com RX digital", "descricao", "Sala climatizada, recepção compartilhada e esterilização inclusa.",
                "equipamentos", "Cadeira Gnatus, RX digital, autoclave, fotopolimerizador, ultrassom", "modalidade", "TURNO", "valor", new BigDecimal(180), "dias", "1,2,3,4,5,6", "ativa", true));
            Svc.notify(c, "Dr. Bruno Costa (demo) enviou contraproposta em \"Clínico geral – cobertura de férias\".", "/negociacao?id=" + n2);
            Svc.notify(ana, "📣 Novo turno compatível: \"Clínico geral – cobertura de férias\".", "/dentista/oportunidade?id=" + o2);
            Db.insert("avaliacoes", Db.m("de_id", c, "para_id", ana, "oportunidade_id", o1, "nota", 5, "texto", "Excelente profissional, pontual e cuidadosa."));
            Db.insert("avaliacoes", Db.m("de_id", ana, "para_id", c, "oportunidade_id", o1, "nota", 5, "texto", "Estrutura ótima e equipe organizada."));
            Db.insert("lancamentos", Db.m("tipo", "DESPESA", "categoria", "Infraestrutura/Hospedagem", "descricao", "Servidor de hospedagem (exemplo)", "valor", new BigDecimal("89.90"),
                "data", hoje, "pago", true, "referencia", "", "automatico", false));
        });
    }

    static void ap(String c, String p, String d, String o, String data, String hora, String proc, String valor, String pag, String conv, String st) {
        int dur = 30;
        for (Object[] x : Svc.PROCS) if (x[0].equals(proc)) dur = (Integer) x[2];
        Db.insert("agendamentos", Db.m("clinica_id", c, "paciente_id", p, "dentista_id", d, "oportunidade_id", o, "data", data, "hora", hora, "duracao", dur, "procedimento", proc,
            "valor", new BigDecimal(valor), "pagador", pag, "convenio", conv, "status", st, "obs", ""));
    }
}

package odontomatch;

/** Criação automática das tabelas e atualização de bancos de versões anteriores. */
final class Schema {
    private Schema() {}

    private static final String ID = "id VARCHAR(36) NOT NULL PRIMARY KEY, ";

    private static final String[][] TABLES = {
        {"configuracoes", "CREATE TABLE configuracoes (k VARCHAR(100) NOT NULL PRIMARY KEY, v VARCHAR(4000))"},
        {"usuarios", "CREATE TABLE usuarios (" + ID + "perfil VARCHAR(20) NOT NULL, nome VARCHAR(200) NOT NULL, email VARCHAR(200) NOT NULL, "
            + "senha_hash VARCHAR(300) NOT NULL, ativo BOOLEAN NOT NULL, verificado BOOLEAN NOT NULL, trocar_senha BOOLEAN NOT NULL, "
            + "documento VARCHAR(60), telefone VARCHAR(40), cidade VARCHAR(120), endereco VARCHAR(300), especialidades VARCHAR(1000), "
            + "bio VARCHAR(2000), cadeiras INTEGER, pix VARCHAR(200), materiais_proprios BOOLEAN, experiencia INTEGER, notas_admin VARCHAR(2000), "
            + "tentativas INTEGER, bloqueado_ate BIGINT, ultimo_login BIGINT, criado BIGINT NOT NULL, CONSTRAINT uq_usuarios_email UNIQUE (email))"},
        {"assinaturas", "CREATE TABLE assinaturas (" + ID + "usuario_id VARCHAR(36) NOT NULL, plano VARCHAR(100), valor DECIMAL(12,2), "
            + "status VARCHAR(20), teste_ate VARCHAR(10), criado BIGINT, atualizado BIGINT)"},
        {"faturas", "CREATE TABLE faturas (" + ID + "usuario_id VARCHAR(36) NOT NULL, assinatura_id VARCHAR(36), competencia VARCHAR(7), "
            + "descricao VARCHAR(200), valor DECIMAL(12,2), vencimento VARCHAR(10), status VARCHAR(20), pago_em VARCHAR(10), metodo VARCHAR(40), "
            + "informado_em BIGINT, criado BIGINT)"},
        {"lancamentos", "CREATE TABLE lancamentos (" + ID + "tipo VARCHAR(10), categoria VARCHAR(60), descricao VARCHAR(300), valor DECIMAL(12,2), "
            + "data VARCHAR(10), pago BOOLEAN, usuario_id VARCHAR(36), fatura_id VARCHAR(36), referencia VARCHAR(100), automatico BOOLEAN, criado BIGINT)"},
        {"pacientes", "CREATE TABLE pacientes (" + ID + "clinica_id VARCHAR(36) NOT NULL, nome VARCHAR(200), cpf VARCHAR(20), nascimento VARCHAR(10), "
            + "telefone VARCHAR(40), email VARCHAR(200), convenio VARCHAR(100), carteira VARCHAR(60), alergias VARCHAR(500), medicamentos VARCHAR(500), "
            + "doencas VARCHAR(500), condicoes VARCHAR(300), obs VARCHAR(2000), odontograma VARCHAR(1000), criado BIGINT)"},
        {"oportunidades", "CREATE TABLE oportunidades (" + ID + "clinica_id VARCHAR(36) NOT NULL, titulo VARCHAR(200), especialidade VARCHAR(100), "
            + "data VARCHAR(10), hora_ini VARCHAR(5), hora_fim VARCHAR(5), modelo VARCHAR(20), pct_part DECIMAL(6,2), pct_conv DECIMAL(6,2), "
            + "diaria DECIMAL(12,2), materiais VARCHAR(20), obs_materiais VARCHAR(1000), requisitos VARCHAR(1000), urgente BOOLEAN, aceita_contra BOOLEAN, "
            + "status VARCHAR(20), dentista_id VARCHAR(36), f_modelo VARCHAR(20), f_pct_part DECIMAL(6,2), f_pct_conv DECIMAL(6,2), f_diaria DECIMAL(12,2), "
            + "f_materiais VARCHAR(20), checkin VARCHAR(5), repasse_pago BOOLEAN, fechado_em BIGINT, criado BIGINT)"},
        {"negociacoes", "CREATE TABLE negociacoes (" + ID + "oportunidade_id VARCHAR(36) NOT NULL, dentista_id VARCHAR(36) NOT NULL, "
            + "status VARCHAR(20), criado BIGINT, atualizado BIGINT)"},
        {"mensagens", "CREATE TABLE mensagens (" + ID + "negociacao_id VARCHAR(36) NOT NULL, autor VARCHAR(20), tipo VARCHAR(20), modelo VARCHAR(20), "
            + "pct_part DECIMAL(6,2), pct_conv DECIMAL(6,2), diaria DECIMAL(12,2), materiais VARCHAR(20), texto VARCHAR(2000), criado BIGINT)"},
        {"agendamentos", "CREATE TABLE agendamentos (" + ID + "clinica_id VARCHAR(36) NOT NULL, paciente_id VARCHAR(36) NOT NULL, dentista_id VARCHAR(36), "
            + "oportunidade_id VARCHAR(36), data VARCHAR(10), hora VARCHAR(5), duracao INTEGER, procedimento VARCHAR(150), valor DECIMAL(12,2), "
            + "pagador VARCHAR(20), convenio VARCHAR(100), status VARCHAR(20), obs VARCHAR(1000), criado BIGINT)"},
        {"evolucoes", "CREATE TABLE evolucoes (" + ID + "paciente_id VARCHAR(36) NOT NULL, autor_id VARCHAR(36), autor_nome VARCHAR(200), "
            + "texto VARCHAR(4000), criado BIGINT)"},
        {"avaliacoes", "CREATE TABLE avaliacoes (" + ID + "de_id VARCHAR(36), para_id VARCHAR(36), oportunidade_id VARCHAR(36), nota INTEGER, "
            + "texto VARCHAR(1000), criado BIGINT)"},
        {"notificacoes", "CREATE TABLE notificacoes (" + ID + "usuario_id VARCHAR(36), texto VARCHAR(500), link VARCHAR(200), lida BOOLEAN, criado BIGINT)"},
        {"auditoria", "CREATE TABLE auditoria (" + ID + "usuario_id VARCHAR(36), email VARCHAR(200), acao VARCHAR(80), detalhe VARCHAR(1000), "
            + "ip VARCHAR(60), criado BIGINT)"},
        // ---- novos na v5 ----
        {"salas", "CREATE TABLE salas (" + ID + "clinica_id VARCHAR(36) NOT NULL, titulo VARCHAR(200), descricao VARCHAR(2000), equipamentos VARCHAR(1000), "
            + "modalidade VARCHAR(20), valor DECIMAL(12,2), dias VARCHAR(100), ativa BOOLEAN, criado BIGINT)"},
        {"reservas", "CREATE TABLE reservas (" + ID + "sala_id VARCHAR(36) NOT NULL, clinica_id VARCHAR(36), profissional_id VARCHAR(36) NOT NULL, data VARCHAR(10), "
            + "hora_ini VARCHAR(5), hora_fim VARCHAR(5), valor DECIMAL(12,2), mensagem VARCHAR(1000), resposta VARCHAR(1000), status VARCHAR(20), criado BIGINT)"},
        {"ocorrencias", "CREATE TABLE ocorrencias (" + ID + "usuario_id VARCHAR(36) NOT NULL, oportunidade_id VARCHAR(36), tipo VARCHAR(30), strike BOOLEAN, "
            + "anulada BOOLEAN, detalhe VARCHAR(1000), criado BIGINT)"},
        {"emails", "CREATE TABLE emails (" + ID + "para VARCHAR(200), nome VARCHAR(200), assunto VARCHAR(300), corpo VARCHAR(8000), status VARCHAR(20), "
            + "erro VARCHAR(500), referencia VARCHAR(100), enviado_em BIGINT, criado BIGINT)"},
        {"tickets", "CREATE TABLE tickets (" + ID + "usuario_id VARCHAR(36), nome VARCHAR(200), email VARCHAR(200), categoria VARCHAR(60), assunto VARCHAR(200), "
            + "mensagem VARCHAR(2000), resposta VARCHAR(2000), status VARCHAR(20), respondido_em BIGINT, criado BIGINT)"},
    };

    /** Colunas adicionadas em versões novas (o sistema atualiza bancos antigos sozinho). */
    private static final String[][] NEW_COLS = {
        {"usuarios", "categoria", "VARCHAR(20)"}, {"usuarios", "faculdade", "VARCHAR(200)"}, {"usuarios", "semestre", "VARCHAR(20)"},
        {"usuarios", "formacao", "VARCHAR(2000)"}, {"usuarios", "lat", "VARCHAR(20)"}, {"usuarios", "lng", "VARCHAR(20)"},
        {"usuarios", "raio_km", "INTEGER"}, {"usuarios", "disponibilidade", "VARCHAR(200)"}, {"usuarios", "strikes", "INTEGER"},
        {"usuarios", "cancel_tardios", "INTEGER"},
        {"oportunidades", "categoria", "VARCHAR(20)"}, {"oportunidades", "motivo_cancel", "VARCHAR(500)"},
    };

    static void create() throws Exception {
        for (String[] t : TABLES) if (!Db.tableExists(t[0])) Db.ddl(t[1]);
        for (String[] c : NEW_COLS) if (!Db.columnExists(c[0], c[1])) Db.ddl("ALTER TABLE " + c[0] + " ADD COLUMN " + c[1] + " " + c[2]);
    }
}

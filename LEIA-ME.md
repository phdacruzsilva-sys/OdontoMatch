# OdontoMatch v5 — marketplace odontológico (Java 17)

Conecta **clínicas** a **dentistas, auxiliares/TSB e estudantes** para cobertura de turnos, com negociação de valores no estilo InDrive, agenda, prontuário e um painel completo de administração.

## Novidades da v5 (inspiradas no DentMatch)
- **Página inicial pública** com "Como funciona", "Para quem é" e recursos.
- **Novos perfis:** auxiliar/técnico em saúde bucal e estudante (com faculdade e semestre). Cada turno indica qual profissional procura.
- **Selo "CRO verificado":** o administrador verifica o registro, e o selo aparece em todas as telas.
- **Localização e raio de atuação:** latitude e longitude (com botão do GPS) e distância calculada em km.
- **Disponibilidade semanal:** grade de manhã, tarde e noite para cada dia da semana.
- **Match inteligente (0–100%)**, com o porquê de cada nota:
  - especialidade: 30 pontos;
  - distância: 25;
  - avaliações: 20;
  - disponibilidade: 15;
  - confiabilidade (registro verificado e strikes): 10.
- **Telas de match:**
  - a clínica vê o ranking em "🎯 Melhores matches" e convida com um clique;
  - o profissional vê os turnos ordenados pelo melhor match.
- **Chat seguro:** telefones, e-mails, links e @perfis ficam ocultos até o negócio ser fechado. Depois, os contatos são liberados.
- **Confirmação automática por e-mail** para clínica e profissional (comprovante). Também há e-mails de aprovação de cadastro, convite, cancelamento, reserva de sala e respostas do suporte.
- **Política de cancelamento e strikes:**
  - mais de 24h antes: livre;
  - entre 24h e 6h: registrado como cancelamento tardio;
  - menos de 6h ou falta (no-show): strike.
  
  O bloqueio é automático ao atingir o limite (padrão 3, configurável).
- **Aluguel de salas e consultórios** por hora, turno ou dia: a clínica anuncia, o profissional solicita, e a clínica aceita ou recusa, com e-mail de confirmação.
- **Contato e suporte:** formulário público e interno, além do painel do administrador para responder.
- **Administrador:** novas telas de Cancelamentos e strikes (anular ou aplicar), Suporte, E-mails (caixa de saída) e configuração de SMTP com botão de teste.
- **Atualização automática:** quem usa a v4 não perde dados; as tabelas e colunas novas são criadas sozinhas.

## Como rodar (Windows)
1. Descompacte em uma pasta fixa, por exemplo `C:\OdontoMatch`. Para manter os dados da v4, copie a pasta `data` antiga para dentro dela.
2. Dê dois cliques em **`iniciar.bat`** e acesse `http://localhost:8080`.
3. Na primeira execução aparece a Configuração inicial.

Requisito único: **Java 17 ou superior**.

## Contas de demonstração (senha `demo123456`)
| Perfil | E-mail |
|---|---|
| Clínica | clinica@demo.com |
| Dentista (endodontia) | ana@demo.com |
| Dentista (ortodontia) | bruno@demo.com |
| Auxiliar (ASB) | julia@demo.com |
| Estudante | marcos@demo.com |

Para testar dois usuários ao mesmo tempo, use uma **janela anônima (Ctrl+Shift+N)** ou outro navegador.

## E-mails automáticos
Configure em **Admin › Configurações › SMTP** e use **Testar SMTP**.
- **Gmail:** smtp.gmail.com, porta 587, STARTTLS, "senha de app".
- **Outlook/Microsoft 365:** smtp.office365.com, porta 587, STARTTLS.

Sem SMTP, os e-mails ficam registrados em **Admin › E-mails**, mas não são enviados.

## Localização
O botão "📍 Usar minha localização" funciona em `localhost` ou em HTTPS. Pelo IP da rede (http://192.168…), o navegador bloqueia o GPS; nesse caso, preencha as coordenadas manualmente, copiando do Google Maps.

## Banco de dados
HSQLDB embarcado na pasta `data`.
- **Backup:** feche o sistema e copie a pasta.
- **Recomeçar do zero:** apague a pasta.

## Alterar o código
O código fica em `src/odontomatch`. Depois de editar, rode `compilar.bat`.

| Arquivo | Responsabilidade |
|---|---|
| App.java | Servidor, rotas, permissões e tempo real |
| Pages.java | Página inicial, instalação, login, cadastro, perfil, plano e suporte |
| AdminPages.java | Administração (usuários, assinaturas, financeiro, strikes, suporte, e-mails) |
| ClinicPages.java | Agenda, pacientes, turnos, match, salas, repasses |
| DentistPages.java | Turnos compatíveis, propostas, agenda, salas, ganhos |
| SharedPages.java | Negociação (chat seguro), cancelamento, prontuário, termo, avaliações |
| Svc.java | Regras de negócio: match, distância, strikes, cálculos |
| Mail.java | Envio de e-mails (SMTP) |
| Ui.java | Layout, estilo e scripts |
| Db.java / Schema.java | Banco de dados e atualização automática |
| Sec.java | Senhas (PBKDF2), sessões e CSRF |

---
Banco embarcado: HSQLDB 1.8 (licença BSD; consulte LICENCA-HSQLDB.txt).

package odontomatch;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * E-mails transacionais (confirmação de turno, reserva de sala, cancelamentos, suporte).
 * Todo e-mail fica registrado na caixa de saída (tabela emails). Se o SMTP estiver configurado
 * em Admin › Configurações, é enviado em segundo plano; senão fica como "não configurado".
 */
public final class Mail {
    private Mail() {}

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "mail"); t.setDaemon(true); return t; });

    public static boolean configured() { return !Svc.cfg("smtp_host", "").isEmpty() && !Svc.cfg("smtp_from", "").isEmpty(); }

    /** Registra e envia (assíncrono). */
    public static void queue(String para, String nome, String assunto, String corpo, String ref) {
        if (para == null || para.isBlank()) return;
        String full = "Olá, " + nome + "!\n\n" + corpo + "\n\n—\n" + Svc.appName() + " · " + Svc.cfg("suporte_email", "") + "\nEste é um e-mail automático.";
        String id = Db.insert("emails", Db.m("para", para, "nome", nome, "assunto", assunto, "corpo", full.length() > 7900 ? full.substring(0, 7900) : full,
            "status", configured() ? "PENDENTE" : "NAO_CONFIGURADO", "erro", "", "referencia", ref == null ? "" : ref, "enviado_em", 0L));
        if (configured()) POOL.submit(() -> deliver(id));
    }

    public static void deliver(String id) {
        Row e = Db.one("SELECT * FROM emails WHERE id=?", id);
        if (e == null) return;
        try {
            smtp(e.s("para"), e.s("assunto"), e.s("corpo"));
            Db.x("UPDATE emails SET status='ENVIADO', erro='', enviado_em=? WHERE id=?", System.currentTimeMillis(), id);
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            Db.x("UPDATE emails SET status='ERRO', erro=? WHERE id=?", msg.length() > 490 ? msg.substring(0, 490) : msg, id);
        }
    }

    public static void resend(String id) {
        Db.x("UPDATE emails SET status='PENDENTE' WHERE id=?", id);
        POOL.submit(() -> deliver(id));
    }

    /** Envio síncrono — usado pelo botão "Testar SMTP". */
    public static void testNow(String para) throws Exception { smtp(para, "Teste de e-mail — " + Svc.appName(), "Configuração de SMTP funcionando corretamente.\n\n" + Svc.appName()); }

    // ---------------- cliente SMTP mínimo (SSL, STARTTLS ou sem criptografia) ----------------
    private static BufferedReader in;
    private static Writer out;

    private static synchronized void smtp(String to, String subject, String body) throws Exception {
        String host = Svc.cfg("smtp_host", ""), sec = Svc.cfg("smtp_seg", "STARTTLS"), user = Svc.cfg("smtp_user", ""), pass = Svc.cfg("smtp_pass", ""), from = Svc.cfg("smtp_from", "");
        int port = Integer.parseInt(Svc.cfg("smtp_port", sec.equals("SSL") ? "465" : "587"));
        if (host.isEmpty() || from.isEmpty()) throw new IllegalStateException("SMTP não configurado.");
        Socket s = sec.equals("SSL") ? SSLSocketFactory.getDefault().createSocket(host, port) : new Socket(host, port);
        s.setSoTimeout(20000);
        try {
            io(s);
            expect(220);
            cmd("EHLO odontomatch.local", 250);
            if (sec.equals("STARTTLS")) {
                cmd("STARTTLS", 220);
                SSLSocket ss = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(s, host, port, true);
                ss.startHandshake();
                s = ss;
                io(s);
                cmd("EHLO odontomatch.local", 250);
            }
            if (!user.isEmpty()) {
                cmd("AUTH LOGIN", 334);
                cmd(b64(user), 334);
                cmd(b64(pass), 235);
            }
            cmd("MAIL FROM:<" + addr(from) + ">", 250);
            cmd("RCPT TO:<" + addr(to) + ">", 250);
            cmd("DATA", 354);
            StringBuilder m = new StringBuilder();
            m.append("From: ").append(enc(Svc.appName())).append(" <").append(addr(from)).append(">\r\n");
            m.append("To: <").append(addr(to)).append(">\r\n");
            m.append("Subject: ").append(enc(subject)).append("\r\n");
            m.append("Date: ").append(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US))).append("\r\n");
            m.append("MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n");
            String b = b64(body);
            for (int i = 0; i < b.length(); i += 76) m.append(b, i, Math.min(b.length(), i + 76)).append("\r\n");
            out.write(m.toString());
            cmd(".", 250);
            try { cmd("QUIT", 221); } catch (Exception ignored) { }
        } finally {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    private static String addr(String a) { return a.replaceAll("[\\r\\n<>]", "").trim(); }

    private static String b64(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }

    private static String enc(String s) { return "=?UTF-8?B?" + b64(s) + "?="; }

    private static void io(Socket s) throws IOException {
        in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
    }

    private static void cmd(String c, int code) throws IOException {
        out.write(c + "\r\n");
        out.flush();
        expect(code);
    }

    private static void expect(int code) throws IOException {
        String line, last = "";
        do {
            line = in.readLine();
            if (line == null) throw new IOException("Conexão SMTP encerrada pelo servidor.");
            last = line;
        } while (line.length() > 3 && line.charAt(3) == '-');
        int got;
        try { got = Integer.parseInt(last.substring(0, 3)); } catch (Exception e) { throw new IOException("Resposta SMTP inválida: " + last); }
        if (got != code && !(code == 250 && got == 251)) throw new IOException("SMTP esperava " + code + ", recebeu: " + last);
    }
}

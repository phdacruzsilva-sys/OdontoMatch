package odontomatch;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Segurança: hash de senha (PBKDF2-HMAC-SHA256, 600.000 iterações, salt individual),
 * sessões e tokens CSRF. A senha original nunca é armazenada nem pode ser recuperada.
 */
public final class Sec {
    private Sec() {}

    private static final SecureRandom RND = new SecureRandom();
    static final int ITER = 600_000;
    static final long IDLE_MS = 60L * 60 * 1000;

    public static String hash(String pw) {
        byte[] salt = new byte[16];
        RND.nextBytes(salt);
        byte[] h = pbkdf2(pw, salt, ITER);
        return "pbkdf2_sha256$" + ITER + "$" + b64(salt) + "$" + b64(h);
    }

    public static boolean verify(String pw, String stored) {
        try {
            String[] p = stored.split("\\$");
            int it = Integer.parseInt(p[1]);
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] exp = Base64.getDecoder().decode(p[3]);
            return MessageDigest.isEqual(exp, pbkdf2(pw, salt, it));
        } catch (Exception e) { return false; }
    }

    private static byte[] pbkdf2(String pw, byte[] salt, int it) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, it, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }

    public static String token() {
        byte[] b = new byte[32];
        RND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String tempPassword() {
        String up = "ABCDEFGHJKLMNPQRSTUVWXYZ", lo = "abcdefghijkmnpqrstuvwxyz", dg = "23456789";
        String all = up + lo + dg;
        StringBuilder s = new StringBuilder();
        s.append(up.charAt(RND.nextInt(up.length()))).append(lo.charAt(RND.nextInt(lo.length()))).append(dg.charAt(RND.nextInt(dg.length())));
        while (s.length() < 12) s.append(all.charAt(RND.nextInt(all.length())));
        return s + "!";
    }

    public static String checkPolicy(String pw) {
        if (pw == null || pw.length() < 10) return "A senha deve ter pelo menos 10 caracteres.";
        if (pw.length() > 128) return "A senha deve ter no máximo 128 caracteres.";
        if (!pw.matches(".*[A-Za-z].*") || !pw.matches(".*[0-9].*")) return "A senha deve conter letras e números.";
        return null;
    }

    public static final class Session {
        final String id;
        volatile String userId;
        final String csrf;
        volatile long last = System.currentTimeMillis();
        volatile String flash, flashType;
        Session(String id) { this.id = id; this.csrf = token(); }
    }

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    public static Session get(String id) {
        if (id == null) return null;
        Session s = SESSIONS.get(id);
        if (s == null) return null;
        if (System.currentTimeMillis() - s.last > IDLE_MS) { SESSIONS.remove(id); return null; }
        s.last = System.currentTimeMillis();
        return s;
    }

    public static Session create() {
        if (SESSIONS.size() > 20000) SESSIONS.values().removeIf(x -> System.currentTimeMillis() - x.last > IDLE_MS);
        Session s = new Session(token());
        SESSIONS.put(s.id, s);
        return s;
    }

    public static Session rotate(Session old, String userId) {
        if (old != null) SESSIONS.remove(old.id);
        Session s = create();
        s.userId = userId;
        return s;
    }

    public static void destroy(Session s) { if (s != null) SESSIONS.remove(s.id); }

    public static void logoutUser(String userId) { SESSIONS.values().removeIf(x -> userId.equals(x.userId)); }
}

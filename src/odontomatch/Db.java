package odontomatch;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Acesso ao banco HSQLDB embarcado (arquivo local em ./data). */
public final class Db {
    private static Connection c;

    private Db() {}

    public static synchronized void open(String dir) throws Exception {
        Class.forName("org.hsqldb.jdbcDriver");
        c = DriverManager.getConnection("jdbc:hsqldb:file:" + dir + "/odontomatch;shutdown=true", "sa", "");
        c.setAutoCommit(true);
        try (Statement s = c.createStatement()) { s.execute("SET WRITE_DELAY FALSE"); }
        Schema.create();
    }

    public static synchronized void close() {
        try {
            if (c != null && !c.isClosed()) {
                try (Statement s = c.createStatement()) { s.execute("SHUTDOWN"); }
            }
        } catch (Exception ignored) { }
    }

    static synchronized boolean tableExists(String t) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(null, null, t.toUpperCase(), null)) { return rs.next(); }
    }

    static synchronized boolean columnExists(String t, String col) throws SQLException {
        try (ResultSet rs = c.getMetaData().getColumns(null, null, t.toUpperCase(), col.toUpperCase())) { return rs.next(); }
    }

    static synchronized void ddl(String sql) throws SQLException {
        try (Statement s = c.createStatement()) { s.execute(sql); }
    }

    private static void bind(PreparedStatement ps, Object[] p) throws SQLException {
        for (int i = 0; i < p.length; i++) {
            Object o = p[i];
            int n = i + 1;
            if (o == null) ps.setNull(n, Types.VARCHAR);
            else if (o instanceof Boolean b) ps.setBoolean(n, b);
            else if (o instanceof Integer v) ps.setInt(n, v);
            else if (o instanceof Long v) ps.setLong(n, v);
            else if (o instanceof BigDecimal v) ps.setBigDecimal(n, v);
            else if (o instanceof Double v) ps.setBigDecimal(n, BigDecimal.valueOf(v));
            else ps.setString(n, o.toString());
        }
    }

    public static synchronized List<Row> q(String sql, Object... p) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Row> out = new ArrayList<>();
                while (rs.next()) {
                    Row r = new Row();
                    for (int i = 1; i <= md.getColumnCount(); i++) r.put(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                    out.add(r);
                }
                return out;
            }
        } catch (SQLException e) { throw new RuntimeException("Erro SQL: " + e.getMessage() + " :: " + sql, e); }
    }

    public static Row one(String sql, Object... p) {
        List<Row> l = q(sql, p);
        return l.isEmpty() ? null : l.get(0);
    }

    public static Object val(String sql, Object... p) {
        Row r = one(sql, p);
        return r == null || r.isEmpty() ? null : r.values().iterator().next();
    }

    public static long count(String sql, Object... p) {
        Object o = val(sql, p);
        return o == null ? 0 : ((Number) o).longValue();
    }

    public static synchronized int x(String sql, Object... p) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            return ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("Erro SQL: " + e.getMessage() + " :: " + sql, e); }
    }

    /** Insere um registro a partir de um mapa coluna->valor. Gera id se ausente. */
    public static synchronized String insert(String table, Map<String, Object> cols) {
        Map<String, Object> m = new LinkedHashMap<>(cols);
        m.putIfAbsent("id", UUID.randomUUID().toString());
        m.putIfAbsent("criado", System.currentTimeMillis());
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (String k : m.keySet()) {
            if (a.length() > 0) { a.append(','); b.append(','); }
            a.append(k); b.append('?');
        }
        x("INSERT INTO " + table + " (" + a + ") VALUES (" + b + ")", m.values().toArray());
        return (String) m.get("id");
    }

    public interface Work { void run() throws Exception; }

    /** Executa várias operações em uma única transação. */
    public static synchronized void tx(Work w) {
        try {
            c.setAutoCommit(false);
            try {
                w.run();
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException(e);
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public static Map<String, Object> m(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}

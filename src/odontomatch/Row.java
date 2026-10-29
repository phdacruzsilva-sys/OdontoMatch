package odontomatch;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

/** Linha de resultado com acessores tipados. */
public class Row extends LinkedHashMap<String, Object> {
    public String s(String k) { Object o = get(k); return o == null ? "" : o.toString(); }
    public BigDecimal d(String k) {
        Object o = get(k);
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
    public boolean b(String k) { Object o = get(k); return o instanceof Boolean bb ? bb : "true".equalsIgnoreCase(String.valueOf(o)); }
    public int i(String k) { Object o = get(k); return o instanceof Number n ? n.intValue() : 0; }
    public long l(String k) { Object o = get(k); return o instanceof Number n ? n.longValue() : 0L; }
    public boolean has(String k) { return get(k) != null && !s(k).isEmpty(); }
}

package com.procel.ingestion.service.rooms;

import java.math.BigDecimal;

public final class TextNorm {
    private TextNorm() {}

    /** trim + collapse whitespace. Não muda caixa, não remove acentos. */
    public static String norm(String s) {
        if (s == null) return null;
        // NBSP -> space, trim, collapse whitespace
        String t = s.replace('\u00A0', ' ').trim();
        t = t.replaceAll("\\s+", " ");
        return t;
    }

    public static BigDecimal toBigDecimalOrNull(String s) {
        String t = norm(s);
        if (t == null) return null;
        t = t.replace(",", ".");
        return new BigDecimal(t);
    }
}
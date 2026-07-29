package br.org.gam.api.shared.activitylog;

public final class ActivityReasonNormalizer {
    private static final int MAX_REASON_CODE_POINTS = 2_000;

    private ActivityReasonNormalizer() {
    }

    public static String normalizeRequired(String reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Activity reason is required.");
        }
        return normalizeSupplied(reason);
    }

    public static int normalizedCodePointCount(String reason) {
        if (reason == null) {
            return 0;
        }
        String normalized = stripUnicodeWhitespace(reason);
        return normalized.codePointCount(0, normalized.length());
    }

    static String normalizeSupplied(String reason) {
        String normalized = stripUnicodeWhitespace(reason);
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints == 0 || codePoints > MAX_REASON_CODE_POINTS) {
            throw new IllegalArgumentException("Activity reason must contain from 1 through 2,000 code points.");
        }
        return normalized;
    }

    private static String stripUnicodeWhitespace(String value) {
        int start = 0;
        int end = value.length();

        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }

        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }

        return value.substring(start, end);
    }

    private static boolean isUnicodeWhitespace(int codePoint) {
        return (codePoint >= 0x0009 && codePoint <= 0x000D)
                || codePoint == 0x0020
                || codePoint == 0x0085
                || codePoint == 0x00A0
                || codePoint == 0x1680
                || (codePoint >= 0x2000 && codePoint <= 0x200A)
                || codePoint == 0x2028
                || codePoint == 0x2029
                || codePoint == 0x202F
                || codePoint == 0x205F
                || codePoint == 0x3000;
    }
}

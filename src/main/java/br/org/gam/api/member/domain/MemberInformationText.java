package br.org.gam.api.member.domain;

import java.text.Normalizer;

public final class MemberInformationText {
    private MemberInformationText() {}

    public static String trimmed(String value) {
        if (value == null) return null;
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isWhitespace(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isWhitespace(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return Normalizer.normalize(value.substring(start, end), Normalizer.Form.NFC);
    }

    public static String collapsed(String value) {
        String trimmed = trimmed(value);
        if (trimmed == null) return null;
        StringBuilder result = new StringBuilder(trimmed.length());
        boolean whitespace = false;
        for (int offset = 0; offset < trimmed.length();) {
            int codePoint = trimmed.codePointAt(offset);
            if (isWhitespace(codePoint)) {
                whitespace = true;
            } else {
                if (whitespace && !result.isEmpty()) result.append(' ');
                result.appendCodePoint(codePoint);
                whitespace = false;
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static boolean isWhitespace(int codePoint) {
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

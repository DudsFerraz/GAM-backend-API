package br.org.gam.api.shared.specification;

final class UnicodeWhitespace {

    private UnicodeWhitespace() {
    }

    static boolean isBlank(String value) {
        return value.isEmpty() || value.codePoints().allMatch(UnicodeWhitespace::isWhiteSpace);
    }

    static String trim(String value) {
        int start = 0;
        int end = value.length();

        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isWhiteSpace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isWhiteSpace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }

        return value.substring(start, end);
    }

    static String collapse(String value) {
        String trimmed = trim(value);
        StringBuilder collapsed = new StringBuilder(trimmed.length());
        boolean pendingSpace = false;

        for (int offset = 0; offset < trimmed.length();) {
            int codePoint = trimmed.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isWhiteSpace(codePoint)) {
                pendingSpace = true;
            } else {
                if (pendingSpace && !collapsed.isEmpty()) {
                    collapsed.append(' ');
                }
                collapsed.appendCodePoint(codePoint);
                pendingSpace = false;
            }
        }
        return collapsed.toString();
    }

    private static boolean isWhiteSpace(int codePoint) {
        return codePoint >= 0x0009 && codePoint <= 0x000D
                || codePoint == 0x0020
                || codePoint == 0x0085
                || codePoint == 0x00A0
                || codePoint == 0x1680
                || codePoint >= 0x2000 && codePoint <= 0x200A
                || codePoint == 0x2028
                || codePoint == 0x2029
                || codePoint == 0x202F
                || codePoint == 0x205F
                || codePoint == 0x3000;
    }
}

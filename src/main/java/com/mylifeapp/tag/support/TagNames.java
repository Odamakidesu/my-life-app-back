package com.mylifeapp.tag.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * メモに付けるタグ名の正規化。
 *
 * <p>API はタグを「カンマ区切りの名前」で受け渡す（フロントエンドとの既存の契約）。
 * 前後の空白を除き、空の要素を捨て、大文字小文字だけが違う重複を1つにまとめる。
 * 照合は DB 側の照合順序（utf8mb4_0900_ai_ci）と同じく大文字小文字を区別しない。
 */
public final class TagNames {

    /** tags.name の桁数 */
    public static final int MAX_LENGTH = 50;

    private TagNames() {
    }

    public static List<String> parse(String csv) {
        List<String> names = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return names;
        }
        Set<String> seen = new HashSet<>();
        for (String raw : csv.split(",")) {
            String name = raw.trim();
            if (!name.isEmpty() && seen.add(name.toLowerCase(Locale.ROOT))) {
                names.add(name);
            }
        }
        return names;
    }

    public static String join(List<String> names) {
        return String.join(",", names);
    }
}

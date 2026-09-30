package com.demandhub.platform.shared.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class Texts {

    private static final Set<String> STOPWORDS = Set.of(
            "a", "o", "as", "os", "de", "da", "do", "das", "dos", "e", "em", "no", "na", "nos", "nas",
            "um", "uma", "para", "por", "com", "que", "se", "ao", "aos", "the", "and", "of", "to",
            "mais", "sem", "sobre", "entre", "como", "ser", "ter", "seu", "sua", "processo");

    private Texts() {}

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    /** Tokens significativos (sem acentos/stopwords, ≥ 3 caracteres). */
    public static Set<String> tokens(String s) {
        return Arrays.stream(normalize(s).split(" "))
                .filter(t -> t.length() >= 3 && !STOPWORDS.contains(t))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> inter = new LinkedHashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new LinkedHashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    public static String firstSentence(String s, int max) {
        if (isBlank(s)) {
            return "";
        }
        String trimmed = s.trim();
        int idx = trimmed.indexOf(". ");
        String sentence = idx > 0 ? trimmed.substring(0, idx + 1) : trimmed;
        return truncate(sentence, max);
    }
}

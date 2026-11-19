package ee.tuleva.onboarding.savings.fund;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.text.Normalizer.Form.NFC;
import static java.text.Normalizer.Form.NFKD;
import static java.util.stream.Collectors.joining;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class NameMatcher {

  private static final String FIE = "FIE";
  private static final String OSAUHING = "OSAUHING";
  private static final String OSAUHING_ABBREVIATION = "OU";
  private static final double SISEKORD_13_P_6_3_2_SAME_PAYER_SIMILARITY = 0.90;
  private static final int MOST_WORDS_COMPARED_IN_EVERY_ORDER = 5;
  private static final Map<String, String> GERMAN_AND_ENGLISH_TRANSLITERATIONS =
      Map.of("Ä", "AE", "Ö", "OE", "Ü", "UE", "Š", "SH", "Ž", "ZH");

  public boolean isSameName(@Nullable String name1, @Nullable String name2) {
    if (name1 == null || name2 == null) return false;
    var normalized1 = normalize(name1);
    var normalized2 = normalize(name2);
    if (isMissingDistinguishingName(normalized1) || isMissingDistinguishingName(normalized2)) {
      return false;
    }
    return normalized1.equals(normalized2);
  }

  public boolean isSimilarName(@Nullable String name1, @Nullable String name2) {
    return similarity(name1, name2) >= SISEKORD_13_P_6_3_2_SAME_PAYER_SIMILARITY;
  }

  double similarity(@Nullable String name1, @Nullable String name2) {
    if (name1 == null || name2 == null) return 0;
    return spellings(name1)
        .flatMapToDouble(
            words1 -> spellings(name2).mapToDouble(words2 -> similarity(words1, words2)))
        .max()
        .orElse(0);
  }

  public boolean looksLikeACompany(@Nullable String name) {
    return name != null && normalizedTokens(name).anyMatch(OSAUHING_ABBREVIATION::equals);
  }

  private Stream<List<String>> spellings(String name) {
    return Stream.<UnaryOperator<String>>of(UnaryOperator.identity(), NameMatcher::transliterated)
        .map(spelling -> normalizedTokens(name, spelling).toList())
        .distinct();
  }

  private static String transliterated(String upperCaseWord) {
    var word = upperCaseWord;
    for (var letter : GERMAN_AND_ENGLISH_TRANSLITERATIONS.entrySet()) {
      word = word.replace(letter.getKey(), letter.getValue());
    }
    return word;
  }

  private double similarity(List<String> words1, List<String> words2) {
    var name2 = String.join(" ", words2);
    if (isMissingDistinguishingName(String.join(" ", words1))
        || isMissingDistinguishingName(name2)) {
      return 0;
    }
    return orders(words1)
        .mapToDouble(order -> editSimilarity(String.join(" ", order), name2))
        .max()
        .orElse(0);
  }

  private static Stream<List<String>> orders(List<String> words) {
    if (words.size() > MOST_WORDS_COMPARED_IN_EVERY_ORDER) {
      return Stream.of(words);
    }
    return permutations(words);
  }

  private static Stream<List<String>> permutations(List<String> words) {
    if (words.size() <= 1) {
      return Stream.of(words);
    }
    return IntStream.range(0, words.size())
        .boxed()
        .flatMap(
            first -> {
              var rest = new ArrayList<>(words);
              var word = rest.remove((int) first);
              return permutations(rest)
                  .map(tail -> Stream.concat(Stream.of(word), tail.stream()).toList());
            });
  }

  private static double editSimilarity(String name1, String name2) {
    return 1 - (double) levenshteinDistance(name1, name2) / max(name1.length(), name2.length());
  }

  private static int levenshteinDistance(String name1, String name2) {
    var previous = IntStream.rangeClosed(0, name2.length()).toArray();
    for (var i = 1; i <= name1.length(); i++) {
      var current = new int[name2.length() + 1];
      current[0] = i;
      for (var j = 1; j <= name2.length(); j++) {
        var substitution = previous[j - 1] + (name1.charAt(i - 1) == name2.charAt(j - 1) ? 0 : 1);
        current[j] = min(substitution, min(previous[j], current[j - 1]) + 1);
      }
      previous = current;
    }
    return previous[name2.length()];
  }

  private boolean isMissingDistinguishingName(String normalizedName) {
    return normalizedName.isEmpty() || normalizedName.equals(OSAUHING_ABBREVIATION);
  }

  private String normalize(String name) {
    return normalizedTokens(name).sorted().collect(joining(" "));
  }

  private Stream<String> normalizedTokens(String name) {
    return normalizedTokens(name, UnaryOperator.identity());
  }

  private Stream<String> normalizedTokens(String name, UnaryOperator<String> spelling) {
    return Arrays.stream(splitIntoWords(name))
        .map(String::strip)
        .filter(token -> !token.isEmpty())
        .map(token -> Normalizer.normalize(token.toUpperCase(), NFC))
        .map(token -> isLegalForm(withoutDiacritics(token)) ? token : spelling.apply(token))
        .map(NameMatcher::withoutDiacritics)
        .filter(token -> !token.equals(FIE))
        .map(token -> token.equals(OSAUHING) ? OSAUHING_ABBREVIATION : token);
  }

  private static boolean isLegalForm(String token) {
    return Set.of(FIE, OSAUHING, OSAUHING_ABBREVIATION).contains(token);
  }

  private static String withoutDiacritics(String token) {
    return Normalizer.normalize(token, NFKD).replaceAll("\\p{M}", "");
  }

  private String[] splitIntoWords(String name) {
    return name.replaceAll("\\p{Cf}", "").replaceAll("[^\\p{L}\\p{N}\\p{M}]", " ").split("\\s+");
  }
}

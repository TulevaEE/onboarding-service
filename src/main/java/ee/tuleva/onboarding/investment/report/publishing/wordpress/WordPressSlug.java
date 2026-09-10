package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.text.Normalizer;
import java.util.Locale;

final class WordPressSlug {

  private static final String DEFAULT_EXTENSION = "pdf";
  private static final int MAX_BASE_SLUG_LENGTH = 100;

  private WordPressSlug() {}

  static String of(String filename) {
    var dotIndex = filename.lastIndexOf('.');
    var base = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    var extension = dotIndex > 0 ? filename.substring(dotIndex + 1) : DEFAULT_EXTENSION;
    var baseSlug = toHyphenatedWords(base);
    if (baseSlug.isEmpty()) {
      throw new IllegalArgumentException(
          "Filename sanitises to an empty slug: filename=" + filename);
    }
    var extensionSlug = toSingleWord(extension);
    return baseSlug + "." + (extensionSlug.isEmpty() ? DEFAULT_EXTENSION : extensionSlug);
  }

  private static String toHyphenatedWords(String value) {
    var hyphenated = asciiSlug(value);
    return hyphenated
        .substring(0, Math.min(hyphenated.length(), MAX_BASE_SLUG_LENGTH))
        .replaceAll("(^-+)|(-+$)", "");
  }

  private static String toSingleWord(String value) {
    return asciiSlug(value).replace("-", "");
  }

  private static String asciiSlug(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-");
  }
}

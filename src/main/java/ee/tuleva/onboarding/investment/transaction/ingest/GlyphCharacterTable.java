package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntPredicate;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.TrueTypeFont;

final class GlyphCharacterTable {

  private static final String COLUMN_SEPARATOR = "\t";

  private final Map<String, String> characterByFingerprint;

  GlyphCharacterTable(Map<String, String> characterByFingerprint) {
    this.characterByFingerprint = Map.copyOf(characterByFingerprint);
  }

  Optional<String> character(String fingerprint) {
    return Optional.ofNullable(characterByFingerprint.get(fingerprint));
  }

  int size() {
    return characterByFingerprint.size();
  }

  static GlyphCharacterTable fromFonts(List<TrueTypeFont> fonts, IntPredicate codePoints)
      throws IOException {
    Map<String, Integer> codePointByFingerprint = new HashMap<>();
    for (TrueTypeFont font : fonts) {
      collectLowestPrintableCodePoints(font, codePoints, codePointByFingerprint);
    }
    Map<String, String> characters = new HashMap<>();
    codePointByFingerprint.forEach(
        (fingerprint, codePoint) -> characters.put(fingerprint, Character.toString(codePoint)));
    return new GlyphCharacterTable(characters);
  }

  static GlyphCharacterTable fromClasspath(String resource) {
    try (InputStream stream = GlyphCharacterTable.class.getResourceAsStream("/" + resource)) {
      if (stream == null) {
        throw new IllegalStateException("Glyph table resource missing: resource=" + resource);
      }
      return fromTsv(new String(stream.readAllBytes(), UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Glyph table resource unreadable: resource=" + resource, e);
    }
  }

  static GlyphCharacterTable fromTsv(String tsv) {
    Map<String, String> characters = new HashMap<>();
    tsv.lines()
        .filter(line -> !line.isBlank())
        .map(line -> line.split(COLUMN_SEPARATOR))
        .forEach(
            columns ->
                characters.put(columns[0], Character.toString(Integer.parseInt(columns[1], 16))));
    return new GlyphCharacterTable(characters);
  }

  String toTsv() {
    return characterByFingerprint.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            entry ->
                entry.getKey()
                    + COLUMN_SEPARATOR
                    + "%04X".formatted(entry.getValue().codePointAt(0)))
        .collect(joining("\n", "", "\n"));
  }

  private static void collectLowestPrintableCodePoints(
      TrueTypeFont font, IntPredicate codePoints, Map<String, Integer> codePointByFingerprint)
      throws IOException {
    CmapLookup cmap = font.getUnicodeCmapLookup();
    for (int glyphId = 0; glyphId < font.getNumberOfGlyphs(); glyphId++) {
      Optional<Integer> codePoint =
          lowestPrintableCodePoint(cmap.getCharCodes(glyphId), codePoints);
      if (codePoint.isPresent()) {
        codePointByFingerprint.merge(
            GlyphFingerprint.of(font, glyphId), codePoint.get(), Math::min);
      }
    }
  }

  private static Optional<Integer> lowestPrintableCodePoint(
      List<Integer> mapped, IntPredicate codePoints) {
    if (mapped == null) {
      return Optional.empty();
    }
    return mapped.stream()
        .filter(codePoint -> !Character.isISOControl(codePoint))
        .filter(codePoints::test)
        .min(Integer::compare);
  }
}

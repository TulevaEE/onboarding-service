package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.util.Comparator.comparingDouble;
import static java.util.Comparator.comparingInt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

class GlyphTextExtractor extends PDFTextStripper {

  private static final float WORD_GAP_IN_EMS = 0.15f;
  private static final float COLUMN_GAP_IN_EMS = 2f;
  private static final float SAME_LINE_TOLERANCE = 1f;

  private final GlyphCharacterTable glyphTable;
  private final Map<COSBase, Map<Integer, String>> characterCache = new HashMap<>();
  private final List<PlacedCharacter> characters = new ArrayList<>();

  private GlyphTextExtractor(GlyphCharacterTable glyphTable) {
    this.glyphTable = glyphTable;
  }

  static List<String> lines(PDDocument document, GlyphCharacterTable glyphTable)
      throws IOException {
    GlyphTextExtractor extractor = new GlyphTextExtractor(glyphTable);
    extractor.getText(document);
    return extractor.lines();
  }

  @Override
  protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code, Vector displacement)
      throws IOException {
    float fontSize = textRenderingMatrix.getScalingFactorX();
    characters.add(
        new PlacedCharacter(
            getCurrentPageNo(),
            textRenderingMatrix.getTranslateX(),
            textRenderingMatrix.getTranslateY(),
            displacement.getX() * fontSize,
            fontSize,
            character(font, code)));
  }

  private String character(PDFont font, int code) throws IOException {
    Map<Integer, String> fontCache =
        characterCache.computeIfAbsent(font.getCOSObject(), ignored -> new HashMap<>());
    String cached = fontCache.get(code);
    if (cached != null) {
      return cached;
    }
    String character = decode(font, code);
    fontCache.put(code, character);
    return character;
  }

  private String decode(PDFont font, int code) throws IOException {
    if (font instanceof PDType0Font type0
        && type0.getDescendantFont() instanceof PDCIDFontType2 trueTypeFont) {
      int glyphId = trueTypeFont.codeToGID(code);
      return glyphTable
          .character(GlyphFingerprint.of(trueTypeFont.getTrueTypeFont(), glyphId))
          .orElseThrow(
              () ->
                  new SebFundConfirmationPdfParseException(
                      "Glyph not in table: font=" + font.getName() + ", glyphId=" + glyphId));
    }
    String unicode = font.toUnicode(code);
    if (unicode == null) {
      throw new SebFundConfirmationPdfParseException(
          "Character code has no text: font=" + font.getName() + ", code=" + code);
    }
    return unicode;
  }

  private List<String> lines() {
    List<List<PlacedCharacter>> rows = new ArrayList<>();
    characters.stream()
        .sorted(
            comparingInt(PlacedCharacter::page)
                .thenComparing(comparingDouble(PlacedCharacter::y).reversed()))
        .forEach(character -> rowFor(rows, character).add(character));
    return rows.stream().map(GlyphTextExtractor::text).toList();
  }

  private static List<PlacedCharacter> rowFor(
      List<List<PlacedCharacter>> rows, PlacedCharacter character) {
    if (!rows.isEmpty()) {
      List<PlacedCharacter> last = rows.getLast();
      PlacedCharacter first = last.getFirst();
      if (first.page() == character.page()
          && Math.abs(first.y() - character.y()) <= SAME_LINE_TOLERANCE) {
        return last;
      }
    }
    List<PlacedCharacter> row = new ArrayList<>();
    rows.add(row);
    return row;
  }

  private static String text(List<PlacedCharacter> row) {
    List<PlacedCharacter> ordered =
        row.stream().sorted(comparingDouble(PlacedCharacter::x)).toList();
    StringBuilder text = new StringBuilder();
    PlacedCharacter previous = null;
    for (PlacedCharacter character : ordered) {
      if (previous != null) {
        text.append(separator(previous, character));
      }
      text.append(character.text());
      previous = character;
    }
    return text.toString();
  }

  private static String separator(PlacedCharacter previous, PlacedCharacter next) {
    float gap = next.x() - (previous.x() + previous.width());
    if (gap > COLUMN_GAP_IN_EMS * previous.fontSize()) {
      return "\t";
    }
    if (gap > WORD_GAP_IN_EMS * previous.fontSize()) {
      return " ";
    }
    return "";
  }

  private record PlacedCharacter(
      int page, float x, float y, float width, float fontSize, String text) {}
}

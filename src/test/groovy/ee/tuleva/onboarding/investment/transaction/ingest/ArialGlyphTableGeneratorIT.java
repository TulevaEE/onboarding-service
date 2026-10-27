package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationPdfParser.ARIAL_GLYPH_TABLE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ARIAL_FONT_DIR", matches = ".+")
class ArialGlyphTableGeneratorIT {

  private static final List<String> ARIAL_FILES = List.of("Arial.ttf", "Arial Bold.ttf");
  private static final int LAST_LATIN_OR_SPACING_MODIFIER = 0x02FF;
  private static final int FIRST_PUNCTUATION = 0x2000;
  private static final int LAST_CURRENCY_SYMBOL = 0x20CF;

  @Test
  void writesTheArialGlyphTableThatSebFundConfirmationsAreDecodedWith() throws IOException {
    List<TrueTypeFont> fonts = new ArrayList<>();
    for (String file : ARIAL_FILES) {
      fonts.add(
          new TTFParser()
              .parse(
                  new RandomAccessReadBufferedFile(
                      new File(System.getenv("ARIAL_FONT_DIR"), file))));
    }

    GlyphCharacterTable table =
        GlyphCharacterTable.fromFonts(
            fonts, ArialGlyphTableGeneratorIT::isLatinPunctuationOrCurrency);
    Path target = Path.of("src/main/resources", ARIAL_GLYPH_TABLE);
    Files.createDirectories(target.getParent());
    Files.writeString(target, table.toTsv(), UTF_8);

    assertThat(table.size()).isPositive();
  }

  private static boolean isLatinPunctuationOrCurrency(int codePoint) {
    return codePoint <= LAST_LATIN_OR_SPACING_MODIFIER
        || (codePoint >= FIRST_PUNCTUATION && codePoint <= LAST_CURRENCY_SYMBOL);
  }
}

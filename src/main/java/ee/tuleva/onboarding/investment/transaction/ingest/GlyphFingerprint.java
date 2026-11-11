package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.GlyphDescription;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.jspecify.annotations.Nullable;

final class GlyphFingerprint {

  private static final int ON_CURVE = 0x01;

  private GlyphFingerprint() {}

  static String of(TrueTypeFont font, int glyphId) throws IOException {
    StringBuilder outline = new StringBuilder("w").append(font.getAdvanceWidth(glyphId));
    appendOutline(outline, font.getGlyph().getGlyph(glyphId));
    return sha256(outline.toString());
  }

  private static void appendOutline(StringBuilder outline, @Nullable GlyphData glyph) {
    if (glyph == null) {
      return;
    }
    GlyphDescription description = glyph.getDescription();
    outline.append(";e");
    for (int contour = 0; contour < description.getContourCount(); contour++) {
      outline.append(',').append(description.getEndPtOfContours(contour));
    }
    outline.append(";p");
    for (int point = 0; point < description.getPointCount(); point++) {
      outline
          .append(',')
          .append(description.getXCoordinate(point))
          .append(' ')
          .append(description.getYCoordinate(point))
          .append(' ')
          .append(description.getFlags(point) & ON_CURVE);
    }
  }

  private static String sha256(String value) {
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}

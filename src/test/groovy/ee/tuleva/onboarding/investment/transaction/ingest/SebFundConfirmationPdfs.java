package ee.tuleva.onboarding.investment.transaction.ingest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

final class SebFundConfirmationPdfs {

  private static final String FONT_RESOURCE =
      "/insurance-transfer-forms/LiberationSans-Regular.ttf";
  private static final float LABEL_X = 59.5f;
  private static final float FIRST_VALUE_X = 229.6f;
  private static final float SECOND_VALUE_X = 314.6f;
  private static final float FIRST_ROW_Y = 692f;
  private static final float ROW_HEIGHT = 15f;
  private static final float FONT_SIZE = 10f;
  private static final float FOOTER_Y = 100f;

  private SebFundConfirmationPdfs() {}

  static GlyphCharacterTable liberationSansTable() {
    try (InputStream stream = fontStream()) {
      TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(stream));
      return GlyphCharacterTable.fromFonts(List.of(font), codePoint -> true);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Map<String, List<String>> buyFields() {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put("Kliendi nimi", List.of("TULEVA MAAILMA AKTSIATE PENSIONIFOND"));
    fields.put("Tehingu nr", List.of("DLA1234567"));
    fields.put("Korralduse saamise aeg", List.of("05/01/26", "11:48:17"));
    fields.put("Hinna arvutamise kuupäev", List.of("06/01/26"));
    fields.put("Tegelik väärtuspäev", List.of("09/01/26"));
    fields.put(
        "Väärtpaberi nimetus", List.of("iShares Emerging Market Screened Equity Index Fund"));
    fields.put("ISIN kood", List.of("IE00BKPTWY98"));
    fields.put("Tehingu liik", List.of("SUBS Ost (Osakute väljalaskmine)"));
    fields.put("Makseviis", List.of("ülekanne"));
    fields.put("Kogus", List.of("1,000.00"));
    fields.put("Hind", List.of("12.345678901", "EUR"));
    fields.put("Kokkulepitud tehingusumma", List.of("12,345.68", "EUR"));
    fields.put("Tehingutasu", List.of("0.00", "EUR"));
    fields.put("Kogumaksumus", List.of("12,345.68", "EUR"));
    return fields;
  }

  static byte[] confirmation(Map<String, List<String>> fields) {
    return confirmation(fields, List.of());
  }

  static byte[] confirmation(Map<String, List<String>> fields, List<String> laterPageFirstRow) {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      PDType0Font font = PDType0Font.load(document, fontStream(), true);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        write(content, font, LABEL_X, FIRST_ROW_Y + 2 * ROW_HEIGHT, "Tehingukinnitus");
        float y = FIRST_ROW_Y;
        for (Map.Entry<String, List<String>> field : fields.entrySet()) {
          write(content, font, LABEL_X, y, field.getKey());
          List<String> values = field.getValue();
          write(content, font, FIRST_VALUE_X, y, values.get(0));
          if (values.size() > 1) {
            write(content, font, SECOND_VALUE_X, y, values.get(1));
          }
          y -= ROW_HEIGHT;
        }
        write(
            content,
            new PDType1Font(Standard14Fonts.FontName.HELVETICA),
            LABEL_X,
            FOOTER_Y,
            "AS SEB Pank, Tornimäe 2");
      }
      if (!laterPageFirstRow.isEmpty()) {
        PDPage laterPage = new PDPage(PDRectangle.A4);
        document.addPage(laterPage);
        try (PDPageContentStream content = new PDPageContentStream(document, laterPage)) {
          write(content, font, LABEL_X, FIRST_ROW_Y, laterPageFirstRow.get(0));
          write(content, font, FIRST_VALUE_X, FIRST_ROW_Y, laterPageFirstRow.get(1));
        }
      }
      return withoutToUnicodeMaps(save(document));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void write(PDPageContentStream content, PDFont font, float x, float y, String text)
      throws IOException {
    content.beginText();
    content.setFont(font, FONT_SIZE);
    content.newLineAtOffset(x, y);
    content.showText(text);
    content.endText();
  }

  private static byte[] withoutToUnicodeMaps(byte[] pdf) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      for (PDPage page : document.getPages()) {
        for (COSName name : page.getResources().getFontNames()) {
          page.getResources().getFont(name).getCOSObject().removeItem(COSName.TO_UNICODE);
        }
      }
      return save(document);
    }
  }

  private static byte[] save(PDDocument document) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    document.save(out);
    return out.toByteArray();
  }

  private static InputStream fontStream() {
    InputStream stream = SebFundConfirmationPdfs.class.getResourceAsStream(FONT_RESOURCE);
    if (stream == null) {
      throw new IllegalStateException("Test font missing: resource=" + FONT_RESOURCE);
    }
    return stream;
  }
}

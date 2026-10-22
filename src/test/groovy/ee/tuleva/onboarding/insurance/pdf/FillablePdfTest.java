package ee.tuleva.onboarding.insurance.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class FillablePdfTest {

  private static final String COMB_FORM = "/insurance-transfer-forms/ergo-benefit-payment.pdf";
  private static final String PAYOUT_FORM = "/insurance-transfer-forms/artea-pension-payout.pdf";
  private static final String QUESTIONNAIRE =
      "/insurance-transfer-forms/artea-know-your-customer.pdf";

  @Test
  void estonianLettersRenderAfterFlattening() throws IOException {
    var values = new FormValues().text("Aeesnimi", "Õie õäöüšž").text("Aperenimi", "Šõžäöü ÕÄÖÜŠŽ");

    String text = textOf(FillablePdf.fillAndFlatten(List.of(new FormDocument(COMB_FORM, values))));

    assertThat(text).contains("Õie õäöüšž", "Šõžäöü ÕÄÖÜŠŽ");
  }

  @Test
  void embedsTheFontItWritesFieldsIn() throws IOException {
    byte[] filled =
        FillablePdf.fillAndFlatten(
            List.of(new FormDocument(COMB_FORM, new FormValues().text("Aeesnimi", "Žanna"))));

    try (PDDocument pdf = Loader.loadPDF(filled)) {
      assertThat(fontsOf(pdf.getPage(0).getResources()))
          .filteredOn(font -> font.getName().contains("LiberationSans"))
          .isNotEmpty()
          .allMatch(PDFont::isEmbedded);
    }
  }

  @Test
  void fillsCombFieldsWhoseWidgetsInheritTheirMaximumLength() throws IOException {
    var values = new FormValues().text("no xx", "12").text("no xxxxxx", "345678");

    try (PDDocument pdf = FillablePdf.fill(new FormDocument(COMB_FORM, values))) {
      var form = pdf.getDocumentCatalog().getAcroForm();
      assertThat(form.getField("no xx").getValueAsString()).isEqualTo("12");
      assertThat(form.getField("no xxxxxx").getValueAsString()).isEqualTo("345678");
    }
    String text = textOf(FillablePdf.fillAndFlatten(List.of(new FormDocument(COMB_FORM, values))));
    assertThat(text.replace(" ", "")).contains("345678");
  }

  @Test
  void ticksTheOnlyOptionOfACheckboxAndTheOptionChosenByItsPrefix() throws IOException {
    var values = new FormValues().check("market").choose("opt", "ennet").choose("APEP", "ei");

    try (PDDocument pdf = FillablePdf.fill(new FormDocument(COMB_FORM, values))) {
      var form = pdf.getDocumentCatalog().getAcroForm();
      assertThat(form.getField("market").getValueAsString()).isEqualTo("yes");
      assertThat(form.getField("opt").getValueAsString()).startsWith("ennet");
      assertThat(form.getField("APEP").getValueAsString()).isEqualTo("ei");
    }
  }

  @Test
  void keepsTheTemplateFontSizeForALineThatFitsAndShrinksOnlyALineThatDoesNot() throws IOException {
    var values =
        new FormValues()
            .text("Aeesnimi", "Mari")
            .text("teenusepakkuja", "Tuleva III Samba Pensionifond, pensionikonto 993432432");

    try (PDDocument pdf = FillablePdf.fill(new FormDocument(COMB_FORM, values))) {
      var form = pdf.getDocumentCatalog().getAcroForm();
      assertThat(fontSizeDrawn(form.getField("Aeesnimi"))).isEqualTo(8);
      assertThat(fontSizeDrawn(form.getField("teenusepakkuja"))).isLessThan(8);
    }
  }

  @Test
  void addsAFieldTheTemplateLacksAtTheGivenPlaceOnTheGivenPage() throws IOException {
    var added = new AddedField("Muu riik", 0, 75, 458, 156, 471);
    var document =
        new FormDocument(QUESTIONNAIRE, new FormValues().text("Muu riik", "Soome"), List.of(added));

    try (PDDocument pdf = FillablePdf.fill(document)) {
      var field = pdf.getDocumentCatalog().getAcroForm().getField("Muu riik");
      var widget = field.getWidgets().getFirst();
      assertThat(field.getValueAsString()).isEqualTo("Soome");
      assertThat(widget.getRectangle().toString())
          .isEqualTo(new PDRectangle(75, 458, 81, 13).toString());
      assertThat(pdf.getPage(0).getAnnotations()).contains(widget);
    }
    try (PDDocument pdf = Loader.loadPDF(FillablePdf.fillAndFlatten(List.of(document)))) {
      assertThat(textOf(pdf, 1)).contains("Soome");
    }
  }

  @Test
  void refusesAFieldTheTemplateDoesNotHave() {
    var values = new FormValues().text("No such field", "value");

    assertThatThrownBy(() -> FillablePdf.fill(new FormDocument(COMB_FORM, values)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesAnOptionTheButtonDoesNotOffer() {
    var values = new FormValues().choose("APEP", "maybe");

    assertThatThrownBy(() -> FillablePdf.fill(new FormDocument(COMB_FORM, values)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void leavesNoEditableFieldsAfterFlattening() throws IOException {
    byte[] filled =
        FillablePdf.fillAndFlatten(
            List.of(new FormDocument(COMB_FORM, new FormValues().text("Aeesnimi", "Mari"))));

    try (PDDocument pdf = Loader.loadPDF(filled)) {
      assertThat(pdf.getDocumentCatalog().getAcroForm()).isNull();
    }
  }

  @Test
  void mergesSeveralFormsIntoOneDocumentInOrder() throws IOException {
    byte[] filled =
        FillablePdf.fillAndFlatten(
            List.of(
                new FormDocument(PAYOUT_FORM, new FormValues().text("Text Field1", "Payout")),
                new FormDocument(
                    QUESTIONNAIRE, new FormValues().text("Text Field0", "Questionnaire"))));

    try (PDDocument pdf = Loader.loadPDF(filled)) {
      assertThat(pdf.getNumberOfPages()).isEqualTo(4);
      assertThat(textOf(pdf, 1)).contains("Payout");
      assertThat(textOf(pdf, 2)).contains("Questionnaire");
    }
  }

  private static float fontSizeDrawn(PDField field) throws IOException {
    List<Object> tokens =
        new PDFStreamParser(field.getWidgets().getFirst().getNormalAppearanceStream()).parse();
    int fontSetting = tokens.indexOf(Operator.getOperator("Tf"));
    return ((COSNumber) tokens.get(fontSetting - 1)).floatValue();
  }

  private static List<PDFont> fontsOf(PDResources resources) throws IOException {
    List<PDFont> fonts = new ArrayList<>();
    for (COSName name : resources.getFontNames()) {
      fonts.add(resources.getFont(name));
    }
    for (COSName name : resources.getXObjectNames()) {
      if (resources.getXObject(name) instanceof PDFormXObject form && form.getResources() != null) {
        fonts.addAll(fontsOf(form.getResources()));
      }
    }
    return fonts;
  }

  private static String textOf(byte[] pdf) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(document);
    }
  }

  private static String textOf(PDDocument pdf, int page) throws IOException {
    var stripper = new PDFTextStripper();
    stripper.setStartPage(page);
    stripper.setEndPage(page);
    return stripper.getText(pdf);
  }
}

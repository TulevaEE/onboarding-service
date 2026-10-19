package ee.tuleva.onboarding.insurance;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.insurance.pdf.FieldValue;
import ee.tuleva.onboarding.insurance.pdf.FillablePdf;
import ee.tuleva.onboarding.insurance.pdf.FormDocument;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.text.PDFTextStripper;

final class FilledForms {

  private FilledForms() {}

  static Map<String, String> fieldValues(FormDocument document) throws IOException {
    try (PDDocument pdf = FillablePdf.fill(document)) {
      var form = pdf.getDocumentCatalog().getAcroForm();
      return document.values().asMap().keySet().stream()
          .collect(
              toMap(
                  name -> name,
                  name -> valueOf(form.getField(name)),
                  (first, second) -> first,
                  TreeMap::new));
    }
  }

  private static String valueOf(PDField field) {
    if (field instanceof PDButton button) {
      return button.getWidgets().stream()
          .map(PDAnnotationWidget::getAppearanceState)
          .filter(state -> state != null && !COSName.Off.equals(state))
          .map(COSName::getName)
          .collect(joining(","));
    }
    return field.getValueAsString();
  }

  static List<String> valuesDrawnOutsideTheirBox(FormDocument document) throws IOException {
    try (PDDocument pdf = FillablePdf.fill(document)) {
      PDAcroForm form = pdf.getDocumentCatalog().getAcroForm();
      List<String> outside = new ArrayList<>();
      for (var entry : document.values().asMap().entrySet()) {
        if (!(entry.getValue() instanceof FieldValue.Check
            || entry.getValue() instanceof FieldValue.Choice)) {
          for (PDAnnotationWidget widget : form.getField(entry.getKey()).getWidgets()) {
            if (!drawnInsideItsBox(widget, form.getDefaultResources())) {
              outside.add(entry.getKey());
            }
          }
        }
      }
      return outside;
    }
  }

  private static boolean drawnInsideItsBox(PDAnnotationWidget widget, PDResources formResources)
      throws IOException {
    PDAppearanceStream appearance = widget.getNormalAppearanceStream();
    List<Object> tokens = new PDFStreamParser(appearance).parse();
    List<COSBase> operands = new ArrayList<>();
    float[] rectangle = null;
    float[] clip = null;
    PDFont font = null;
    float fontSize = 0;
    float x = 0;
    float y = 0;
    for (Object token : tokens) {
      if (!(token instanceof Operator operator)) {
        operands.add((COSBase) token);
        continue;
      }
      switch (operator.getName()) {
        case "re" -> rectangle = numbers(operands);
        case "W" -> clip = rectangle;
        case "Tf" -> {
          COSName fontName = (COSName) operands.get(0);
          PDResources resources =
              appearance.getResources() != null ? appearance.getResources() : formResources;
          font = resources.getFont(fontName);
          if (font == null) {
            font = formResources.getFont(fontName);
          }
          fontSize = numbers(operands)[1];
        }
        case "Td" -> {
          float[] offset = numbers(operands);
          x += offset[0];
          y += offset[1];
        }
        case "Tj" -> {
          float width = width(font, (COSString) operands.getFirst()) * fontSize;
          float capHeight = font.getFontDescriptor().getCapHeight() / 1000 * fontSize;
          if (x < clip[0]
              || x + width > clip[0] + clip[2]
              || y < clip[1]
              || y + capHeight > clip[1] + clip[3]) {
            return false;
          }
        }
        default -> {}
      }
      operands.clear();
    }
    return true;
  }

  static boolean paintsItsBoxWhiteBeforeTheText(FormDocument document, String fieldName)
      throws IOException {
    try (PDDocument pdf = FillablePdf.fill(document)) {
      PDAnnotationWidget widget =
          pdf.getDocumentCatalog().getAcroForm().getField(fieldName).getWidgets().getFirst();
      PDAppearanceStream appearance = widget.getNormalAppearanceStream();
      float[] box = {
        appearance.getBBox().getLowerLeftX(),
        appearance.getBBox().getLowerLeftY(),
        appearance.getBBox().getWidth(),
        appearance.getBBox().getHeight()
      };
      List<COSBase> operands = new ArrayList<>();
      float[] fillColor = {0};
      float[] rectangle = null;
      for (Object token : new PDFStreamParser(appearance).parse()) {
        if (!(token instanceof Operator operator)) {
          operands.add((COSBase) token);
          continue;
        }
        switch (operator.getName()) {
          case "g", "rg", "sc", "scn" -> fillColor = numbers(operands);
          case "re" -> rectangle = numbers(operands);
          case "f" -> {
            if (covers(rectangle, box) && isWhite(fillColor)) {
              return true;
            }
          }
          case "BT" -> {
            return false;
          }
          default -> {}
        }
        operands.clear();
      }
      return false;
    }
  }

  private static boolean covers(float[] rectangle, float[] box) {
    for (int i = 0; i < box.length; i++) {
      if (Math.abs(rectangle[i] - box[i]) > 0.01) {
        return false;
      }
    }
    return true;
  }

  private static boolean isWhite(float[] color) {
    for (float component : color) {
      if (component != 1) {
        return false;
      }
    }
    return true;
  }

  private static float width(PDFont font, COSString text) throws IOException {
    InputStream codes = new ByteArrayInputStream(text.getBytes());
    float width = 0;
    while (codes.available() > 0) {
      width += font.getWidth(font.readCode(codes));
    }
    return width / 1000;
  }

  private static float[] numbers(List<COSBase> operands) {
    float[] numbers = new float[operands.size()];
    for (int i = 0; i < operands.size(); i++) {
      numbers[i] = operands.get(i) instanceof COSNumber number ? number.floatValue() : 0;
    }
    return numbers;
  }

  static String text(List<FormDocument> documents) throws IOException {
    try (PDDocument pdf = Loader.loadPDF(FillablePdf.fillAndFlatten(documents))) {
      return new PDFTextStripper().getText(pdf);
    }
  }

  static boolean hasEditableFields(List<FormDocument> documents) throws IOException {
    try (PDDocument pdf = Loader.loadPDF(FillablePdf.fillAndFlatten(documents))) {
      return pdf.getDocumentCatalog().getAcroForm() != null;
    }
  }

  static int pageCount(List<FormDocument> documents) throws IOException {
    try (PDDocument pdf = Loader.loadPDF(FillablePdf.fillAndFlatten(documents))) {
      return pdf.getNumberOfPages();
    }
  }
}

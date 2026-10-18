package ee.tuleva.onboarding.insurance;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.insurance.pdf.FillablePdf;
import ee.tuleva.onboarding.insurance.pdf.FormDocument;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
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

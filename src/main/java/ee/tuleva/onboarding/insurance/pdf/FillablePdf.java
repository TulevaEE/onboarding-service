package ee.tuleva.onboarding.insurance.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.jspecify.annotations.Nullable;

public final class FillablePdf {

  private FillablePdf() {}

  public static byte[] fillAndFlatten(List<FormDocument> documents) {
    return merge(documents.stream().map(FillablePdf::fillAndFlatten).toList());
  }

  public static PDDocument fill(FormDocument document) {
    PDDocument pdf = load(document.template());
    try {
      PDAcroForm form =
          Objects.requireNonNull(
              pdf.getDocumentCatalog().getAcroForm(),
              "Form template has no fields: template=" + document.template());
      FieldFont.embedForTextFields(pdf, form);
      document
          .values()
          .asMap()
          .forEach((name, value) -> FieldWriter.write(field(form, name), value));
      return pdf;
    } catch (IOException | RuntimeException e) {
      closeQuietly(pdf, e);
      throw new IllegalStateException(
          "Could not fill form template: template=" + document.template(), e);
    }
  }

  private static byte[] fillAndFlatten(FormDocument document) {
    try (PDDocument pdf = fill(document)) {
      PDAcroForm form = Objects.requireNonNull(pdf.getDocumentCatalog().getAcroForm());
      form.flatten();
      pdf.getDocumentCatalog().setAcroForm(null);
      return bytes(pdf);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Could not flatten form template: template=" + document.template(), e);
    }
  }

  private static byte[] merge(List<byte[]> pdfs) {
    if (pdfs.size() == 1) {
      return pdfs.getFirst();
    }
    List<PDDocument> parts = new ArrayList<>();
    try (PDDocument merged = new PDDocument()) {
      PDFMergerUtility merger = new PDFMergerUtility();
      for (byte[] pdf : pdfs) {
        PDDocument part = load(pdf);
        parts.add(part);
        merger.appendDocument(merged, part);
      }
      return bytes(merged);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not merge filled forms: count=" + pdfs.size(), e);
    } finally {
      parts.forEach(part -> closeQuietly(part, null));
    }
  }

  private static PDDocument load(String template) {
    try {
      return Loader.loadPDF(resource(template));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read form template: template=" + template, e);
    }
  }

  private static PDDocument load(byte[] pdf) {
    try {
      return Loader.loadPDF(pdf);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read filled form", e);
    }
  }

  private static void closeQuietly(PDDocument pdf, @Nullable Exception failure) {
    try {
      pdf.close();
    } catch (IOException e) {
      if (failure != null) {
        failure.addSuppressed(e);
      }
    }
  }

  private static PDField field(PDAcroForm form, String name) {
    return Objects.requireNonNull(
        form.getField(name), () -> "Form template has no such field: field=" + name);
  }

  private static byte[] bytes(PDDocument pdf) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    pdf.save(out);
    return out.toByteArray();
  }

  private static byte[] resource(String path) throws IOException {
    try (InputStream in = resourceStream(path)) {
      return in.readAllBytes();
    }
  }

  static InputStream resourceStream(String path) {
    return Objects.requireNonNull(
        FillablePdf.class.getResourceAsStream(path), () -> "Resource missing: path=" + path);
  }
}

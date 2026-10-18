package ee.tuleva.onboarding.insurance.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.jspecify.annotations.Nullable;

final class FieldFont {

  private static final Pattern FONT_SETTING = Pattern.compile("/(\\S+)\\s+([\\d.]+)\\s+Tf");

  private FieldFont() {}

  static void embedForTextFields(PDDocument pdf, PDAcroForm form) throws IOException {
    final String FONT = "/insurance-transfer-forms/LiberationSans-Regular.ttf";
    PDFont font;
    try (InputStream fontFile = FillablePdf.resourceStream(FONT)) {
      font = PDType0Font.load(pdf, fontFile, false);
    }
    PDResources defaultResources =
        Objects.requireNonNullElseGet(form.getDefaultResources(), PDResources::new);
    form.setDefaultResources(defaultResources);
    textFields(form).forEach(field -> useFontFor(field, font, defaultResources));
  }

  static void useAutomaticSize(PDTextField field) {
    field.setDefaultAppearance(automaticSize(field.getDefaultAppearance()));
    field.getWidgets().stream()
        .filter(widget -> widget.getCOSObject().getString(COSName.DA) != null)
        .forEach(
            widget ->
                widget
                    .getCOSObject()
                    .setString(
                        COSName.DA, automaticSize(widget.getCOSObject().getString(COSName.DA))));
  }

  private static String automaticSize(@Nullable String appearance) {
    Matcher setting =
        FONT_SETTING.matcher(
            Objects.requireNonNull(appearance, "Form field has no appearance settings"));
    if (!setting.find()) {
      throw new IllegalStateException("Form field sets no font: appearance=" + appearance);
    }
    return setting.replaceFirst("/$1 0 Tf");
  }

  private static void useFontFor(PDTextField field, PDFont font, PDResources defaultResources) {
    fontNames(field).forEach(name -> defaultResources.put(COSName.getPDFName(name), font));
  }

  private static Stream<PDTextField> textFields(PDAcroForm form) {
    return StreamSupport.stream(form.getFieldTree().spliterator(), false)
        .filter(PDTextField.class::isInstance)
        .map(PDTextField.class::cast);
  }

  private static Stream<String> fontNames(PDTextField field) {
    return Stream.concat(
            Stream.of(field.getDefaultAppearance()),
            field.getWidgets().stream().map(widget -> widget.getCOSObject().getString(COSName.DA)))
        .filter(Objects::nonNull)
        .map(FONT_SETTING::matcher)
        .filter(Matcher::find)
        .map(setting -> setting.group(1))
        .distinct();
  }
}

package ee.tuleva.onboarding.insurance.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.form.PDButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

final class FieldWriter {

  private FieldWriter() {}

  static void write(PDField field, FieldValue value) {
    try {
      switch (value) {
        case FieldValue.Text text -> writeLine((PDTextField) field, text.text());
        case FieldValue.Paragraph paragraph ->
            writeParagraph((PDTextField) field, paragraph.text());
        case FieldValue.Check() -> checkOnlyOption((PDButton) field);
        case FieldValue.Choice choice ->
            choose((PDButton) field, onState -> onState.startsWith(choice.onValuePrefix()));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Could not write form field: field=" + field.getFullyQualifiedName(), e);
    }
  }

  private static void writeLine(PDTextField field, String text) throws IOException {
    field.setMultiline(false);
    redrawWidgets(field);
    field.setValue(text);
  }

  private static void writeParagraph(PDTextField field, String text) throws IOException {
    field.setMultiline(true);
    FieldFont.useAutomaticSize(field);
    redrawWidgets(field);
    field.setValue(text);
  }

  private static void redrawWidgets(PDTextField field) {
    field.getWidgets().forEach(widget -> widget.setAppearance(null));
  }

  private static void checkOnlyOption(PDButton button) throws IOException {
    Set<String> onValues = button.getOnValues();
    if (onValues.size() != 1) {
      throw new IllegalStateException(
          "Form button is not a single checkbox: field="
              + button.getFullyQualifiedName()
              + ", onValues="
              + onValues);
    }
    button.setValue(onValues.iterator().next());
  }

  private static void choose(PDButton button, Predicate<String> wanted) throws IOException {
    List<PDAnnotationWidget> widgets = button.getWidgets();
    List<Integer> matching =
        IntStream.range(0, widgets.size())
            .filter(index -> onStates(widgets.get(index)).anyMatch(wanted))
            .boxed()
            .toList();
    if (matching.size() != 1) {
      throw new IllegalStateException(
          "Form button has no single matching option: field="
              + button.getFullyQualifiedName()
              + ", options="
              + widgets.stream().flatMap(FieldWriter::onStates).toList());
    }
    int index = matching.getFirst();
    if (button.getExportValues().isEmpty()) {
      button.setValue(onStates(widgets.get(index)).filter(wanted).findFirst().orElseThrow());
    } else {
      button.setValue(index);
    }
  }

  private static Stream<String> onStates(PDAnnotationWidget widget) {
    PDAppearanceDictionary appearance = widget.getAppearance();
    if (appearance == null || !appearance.getNormalAppearance().isSubDictionary()) {
      return Stream.empty();
    }
    return appearance.getNormalAppearance().getSubDictionary().keySet().stream()
        .filter(state -> !COSName.Off.equals(state))
        .map(COSName::getName);
  }
}

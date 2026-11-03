package ee.tuleva.onboarding.insurance.pdf;

public sealed interface FieldValue {

  record Text(String text) implements FieldValue {}

  record Paragraph(String text) implements FieldValue {}

  record ParagraphOverWritingLines(String text) implements FieldValue {}

  record Check() implements FieldValue {}

  record Choice(String onValuePrefix) implements FieldValue {}
}

package ee.tuleva.onboarding.insurance.pdf;

import java.util.List;

public record FormDocument(String template, FormValues values, List<AddedField> addedFields) {

  public FormDocument(String template, FormValues values) {
    this(template, values, List.of());
  }
}

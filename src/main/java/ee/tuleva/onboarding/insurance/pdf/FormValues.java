package ee.tuleva.onboarding.insurance.pdf;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class FormValues {

  private final Map<String, FieldValue> values = new LinkedHashMap<>();

  public FormValues text(String field, @Nullable String text) {
    if (text != null) {
      values.put(field, new FieldValue.Text(text));
    }
    return this;
  }

  public FormValues paragraph(String field, @Nullable String text) {
    if (text != null) {
      values.put(field, new FieldValue.Paragraph(text));
    }
    return this;
  }

  public FormValues check(String field) {
    values.put(field, new FieldValue.Check());
    return this;
  }

  public FormValues choose(String field, String onValuePrefix) {
    values.put(field, new FieldValue.Choice(onValuePrefix));
    return this;
  }

  public Map<String, FieldValue> asMap() {
    return Collections.unmodifiableMap(values);
  }
}

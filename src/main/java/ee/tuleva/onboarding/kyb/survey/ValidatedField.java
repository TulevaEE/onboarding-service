package ee.tuleva.onboarding.kyb.survey;

import java.util.List;

record ValidatedField<T>(T value, List<String> errors) {

  static <T> ValidatedField<T> valid(T value) {
    return new ValidatedField<>(value, List.of());
  }

  static <T> ValidatedField<T> withErrors(T value, List<String> errors) {
    return new ValidatedField<>(value, errors);
  }
}

package ee.tuleva.onboarding.kyb.survey;

import java.util.List;
import org.jspecify.annotations.Nullable;

record ValidatedField<T>(@Nullable T value, List<ValidationError> errors) {

  static <T> ValidatedField<T> valid(@Nullable T value) {
    return new ValidatedField<>(value, List.of());
  }

  static <T> ValidatedField<T> withErrors(@Nullable T value, List<ValidationError> errors) {
    return new ValidatedField<>(value, errors);
  }
}

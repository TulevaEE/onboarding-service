package ee.tuleva.onboarding.accounting.directo;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
class DirectoRequestException extends RuntimeException {

  record Failure(String resource, @Nullable Integer status, @Nullable String errorType) {}

  private final Failure failure;

  DirectoRequestException(String resource, int status) {
    this(new Failure(resource, status, null));
  }

  DirectoRequestException(String resource, Throwable error) {
    this(new Failure(resource, null, error.getClass().getSimpleName()));
  }

  private DirectoRequestException(Failure failure) {
    super(
        "Directo request failed: resource="
            + failure.resource()
            + (failure.status() == null ? "" : ", status=" + failure.status())
            + (failure.errorType() == null ? "" : ", errorType=" + failure.errorType()));
    this.failure = failure;
  }
}

package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.web.client.HttpClientErrorException;

public abstract sealed class UploadFailed extends RuntimeException
    permits UploadFailed.NothingStored, UploadFailed.MayBeStored {

  private final String failedBecause;

  private UploadFailed(String outcome, String filename, RuntimeException failure) {
    super("Report upload failed: outcome=" + outcome + ", filename=" + filename, failure);
    this.failedBecause = failure.getClass().getSimpleName();
  }

  static UploadFailed after(String filename, RuntimeException failure) {
    return neverReachedWordPress(failure) || failure instanceof HttpClientErrorException
        ? new NothingStored(filename, failure)
        : new MayBeStored(filename, failure);
  }

  private static boolean neverReachedWordPress(Throwable failure) {
    return Stream.iterate(failure, Objects::nonNull, Throwable::getCause)
        .anyMatch(
            cause ->
                WordPressConfiguration.NEVER_REACHED_WORDPRESS.stream()
                    .anyMatch(type -> type.isInstance(cause)));
  }

  public String failedBecause() {
    return failedBecause;
  }

  public static final class NothingStored extends UploadFailed {

    public NothingStored(String filename, RuntimeException failure) {
      super("nothing stored", filename, failure);
    }
  }

  public static final class MayBeStored extends UploadFailed {

    public MayBeStored(String filename, RuntimeException failure) {
      super("may be stored", filename, failure);
    }
  }
}

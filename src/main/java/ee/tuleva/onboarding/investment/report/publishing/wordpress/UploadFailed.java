package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.util.Objects;
import java.util.stream.Stream;

public abstract sealed class UploadFailed extends RuntimeException
    permits UploadFailed.NothingStored, UploadFailed.MayBeStored, UploadFailed.Stored {

  private final String failedBecause;

  private UploadFailed(String outcome, String filename, RuntimeException failure) {
    super("Report upload failed: outcome=" + outcome + ", filename=" + filename, failure);
    this.failedBecause = InnermostCause.nameOf(failure);
  }

  static UploadFailed after(String filename, RuntimeException failure) {
    if (failure instanceof UploadFailed decided) {
      return decided;
    }
    return neverLeftTheClient(failure)
        ? new NothingStored(filename, failure)
        : new MayBeStored(filename, failure);
  }

  private static boolean neverLeftTheClient(Throwable failure) {
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

  public static final class Stored extends UploadFailed {

    private final int attachmentId;

    public Stored(String filename, int attachmentId, RuntimeException failure) {
      super("stored as attachment " + attachmentId, filename, failure);
      this.attachmentId = attachmentId;
    }

    public int attachmentId() {
      return attachmentId;
    }
  }
}

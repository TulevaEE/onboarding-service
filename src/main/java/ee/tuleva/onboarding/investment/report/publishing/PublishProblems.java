package ee.tuleva.onboarding.investment.report.publishing;

import java.util.ArrayList;
import java.util.List;

final class PublishProblems {

  private final List<String> lines = new ArrayList<>();
  private final List<String> uploadsLeftUnlinked = new ArrayList<>();

  void add(String line) {
    lines.add(line);
  }

  void addAll(List<String> more) {
    lines.addAll(more);
  }

  void addLeavingUploadUnlinked(String line, String upload) {
    lines.add(line);
    uploadsLeftUnlinked.add(upload);
  }

  boolean any() {
    return !lines.isEmpty();
  }

  List<String> lines() {
    return List.copyOf(lines);
  }

  List<String> uploadsLeftUnlinked() {
    return List.copyOf(uploadsLeftUnlinked);
  }
}

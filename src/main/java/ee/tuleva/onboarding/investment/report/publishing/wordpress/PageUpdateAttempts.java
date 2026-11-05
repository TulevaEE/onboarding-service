package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

final class PageUpdateAttempts {

  private final List<RuntimeException> answered = new ArrayList<>();
  private final List<RuntimeException> unanswered = new ArrayList<>();

  <T> T send(Supplier<T> post) {
    try {
      return post.get();
    } catch (ResourceAccessException noResponse) {
      unanswered.add(noResponse);
      throw noResponse;
    } catch (RestClientException response) {
      answered.add(response);
      throw response;
    }
  }

  boolean noneSent() {
    return answered.isEmpty() && unanswered.isEmpty();
  }

  boolean anyUnanswered() {
    return !unanswered.isEmpty();
  }

  PublishLockLost lockLost(String pageSlug) {
    var lockLost = new PublishLockLost(pageSlug);
    answered.forEach(lockLost::addSuppressed);
    unanswered.forEach(lockLost::addSuppressed);
    return lockLost;
  }
}

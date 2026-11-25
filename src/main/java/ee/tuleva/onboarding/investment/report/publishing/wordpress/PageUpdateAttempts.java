package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

final class PageUpdateAttempts {

  private final List<RuntimeException> answered = new ArrayList<>();
  private final List<RuntimeException> uncertain = new ArrayList<>();

  <T> T send(Supplier<T> post) {
    try {
      return post.get();
    } catch (HttpClientErrorException refused) {
      answered.add(refused);
      throw refused;
    } catch (RestClientResponseException serverErrorThatMayStillBeSaving) {
      uncertain.add(serverErrorThatMayStillBeSaving);
      throw serverErrorThatMayStillBeSaving;
    } catch (ResourceAccessException noResponse) {
      uncertain.add(noResponse);
      throw noResponse;
    } catch (RestClientException unreadableSuccessfulResponse) {
      answered.add(unreadableSuccessfulResponse);
      throw unreadableSuccessfulResponse;
    }
  }

  boolean noneSent() {
    return answered.isEmpty() && uncertain.isEmpty();
  }

  boolean anyUncertain() {
    return !uncertain.isEmpty();
  }

  PublishLockLost lockLost(String pageSlug) {
    var lockLost = new PublishLockLost(pageSlug);
    answered.forEach(lockLost::addSuppressed);
    uncertain.forEach(lockLost::addSuppressed);
    return lockLost;
  }
}

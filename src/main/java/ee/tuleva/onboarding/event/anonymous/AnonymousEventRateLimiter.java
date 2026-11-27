package ee.tuleva.onboarding.event.anonymous;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

// Per client address and app instance: enough for a person clicking around, not for a flood.
@Component
class AnonymousEventRateLimiter {

  static final int EVENTS_PER_MINUTE = 60;

  private final Cache<String, AtomicInteger> counts =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(100_000).build();

  boolean tryAcquire(String clientAddress) {
    return counts.get(clientAddress, address -> new AtomicInteger()).incrementAndGet()
        <= EVENTS_PER_MINUTE;
  }
}

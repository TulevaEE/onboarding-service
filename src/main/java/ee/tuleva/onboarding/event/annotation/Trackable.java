package ee.tuleva.onboarding.event.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import ee.tuleva.onboarding.event.TrackableEventType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(METHOD)
@Retention(RUNTIME)
public @interface Trackable {
  TrackableEventType value();
}

package ee.tuleva.onboarding.log;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogConfigurationException;
import org.apache.commons.logging.LogFactory;

public class LogFactoryImpl extends LogFactory {

  @Override
  public Object getAttribute(String name) {
    return null;
  }

  @Override
  public String[] getAttributeNames() {
    return new String[0];
  }

  @Override
  public Log getInstance(Class clazz) throws LogConfigurationException {
    return null;
  }

  @Override
  public Log getInstance(String name) throws LogConfigurationException {
    return null;
  }

  @Override
  public void release() {}

  @Override
  public void removeAttribute(String name) {}

  @Override
  public void setAttribute(String name, Object value) {}
}

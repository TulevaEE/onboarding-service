package ee.tuleva.onboarding.auth.idcard.exception;

import ee.tuleva.onboarding.auth.idcard.IdDocumentType;

public class UnsupportedDocumentTypeException extends RuntimeException {
  public UnsupportedDocumentTypeException(IdDocumentType documentType) {
    super("ID-card document type is not allowed to log in: documentType=" + documentType);
  }
}

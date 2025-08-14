package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.signature.SignatureFile;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class SignatureFileArchiver {

  public void writeSignatureFilesToZipOutputStream(
      List<SignatureFile> files, OutputStream outputStream) {
    try {
      ZipOutputStream out = new ZipOutputStream(outputStream);
      files.forEach(file -> writeZipEntry(file, out));
      out.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void writeZipEntry(SignatureFile file, ZipOutputStream out) {
    try {
      ZipEntry entry = new ZipEntry(file.getName());
      out.putNextEntry(entry);
      out.write(file.getContent(), 0, file.getContent().length);
      out.closeEntry();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

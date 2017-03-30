package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.SignatureFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SignatureFileArchiver {

    public void writeSignatureFilesToZipOutputStream(List<SignatureFile> files, OutputStream outputStream) {

        ZipOutputStream out = new ZipOutputStream(outputStream);

        files.forEach( file -> {
            writeZipEntry(file, out);
        });

        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeZipEntry(SignatureFile file, ZipOutputStream out) {
        ZipEntry e = new ZipEntry(file.name);
        try {
            out.putNextEntry(e);
            out.write(file.content, 0, file.content.length);
            out.closeEntry();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

}

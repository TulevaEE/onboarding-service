package ee.tuleva.onboarding.log;

import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;

/**
 * This class logs all bytes written to it as output stream
 */

@Slf4j
public class LogInfoOutputStream extends OutputStream {
    /** The internal memory for the written bytes. */
    private String mem;

    public LogInfoOutputStream () {
        mem = "";
    }

    /**
     * Writes a byte to the output stream. This method flushes automatically at the end of a line.
     *
     * @param b DOCUMENT ME!
     */
    public void write (int b) {
        byte[] bytes = new byte[1];
        bytes[0] = (byte) (b & 0xff);
        mem = mem + new String(bytes);

        if (mem.endsWith ("\n")) {
            mem = mem.substring (0, mem.length () - 1);
            flush ();
        }
    }

    /**
     * Flushes the output stream.
     */
    public void flush () {
        log.info (mem);
        mem = "";
    }
}
package edu.columbia.irt.netserv.core.backbone;

import java.io.*;
import java.util.*;
import java.util.logging.*;

import java.nio.ByteBuffer;

/**
 * This class collects various utility methods needed by the other classes.
 */
public class Util {

    public static final String ASCII = "US-ASCII";
    public static final String UTF8 = "UTF-8";

    /**
     * Execute an external process.
     * @param out Console output from the process (both stdout and stderr).
     * @param cmdLine Full command line.
     * @return Exit code.
     */
    public static int executeProcess(StringBuilder out, String cmdLine)
            throws IOException {
        // split the comLine at whitespaces
        String cmdArray[] = cmdLine.trim().split("\\s+", 0);
        Process child = new ProcessBuilder(cmdArray).redirectErrorStream(true).start();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(child.getInputStream()));
        String line;
        while ((line = in.readLine()) != null) {
            out.append(line);
        }
        for (;;) {
            try {
                return child.waitFor();
            } catch (InterruptedException ex) {
            }
        }
    }

    /**
     * Add an item to a TXT record.
     * @return A new TXT record with the item added.
     */
    public static byte[] appendToTXTRecord(byte[] oldTXT, byte[] item) {
        if (oldTXT == null || oldTXT.length <= 1) {
            byte[] newTXT = new byte[item.length + 1];
            newTXT[0] = (byte) item.length;
            System.arraycopy(item, 0, newTXT, 1, item.length);
            return newTXT;
        }

        int newLen = oldTXT.length + 1 + item.length;
        int newPos = oldTXT.length;
        byte[] newTXT = new byte[newLen];
        System.arraycopy(oldTXT, 0, newTXT, 0, oldTXT.length);
        newTXT[newPos] = (byte) item.length;
        System.arraycopy(item, 0, newTXT, newPos + 1, item.length);
        return newTXT;
    }

    /**
     * Add a key-value pair to a TXT record.  
     * @param key Must be a plain old ASCII string.
     * @param value Gets encoded in UTF-8 in the TXT record.
     * @return A new TXT record with the item added.
     */
    public static byte[] appendToTXTRecord(byte[] oldTXT,
            String key, String value) {
        try {
            key += "=";
            byte[] k = key.getBytes(ASCII);
            byte[] v = value.getBytes(UTF8);
            byte[] pair = new byte[k.length + v.length];
            System.arraycopy(k, 0, pair, 0, k.length);
            System.arraycopy(v, 0, pair, k.length, v.length);
            return appendToTXTRecord(oldTXT, pair);
        } catch (UnsupportedEncodingException x) {
            // if US-ASCII and UTF-8 are not supported
            x.printStackTrace();
            System.exit(-1);
        }
        return null; // not reached - just to make compiler happy.
    }

    /**
     * Formats the TXT record.
     * @param s The StringBuilder object into which the TXT record
     * gets formatted.
     * @param txt The TXT record to format.
     * @param prefix  Each item is prepended by this.
     * @param printLen If true, "(num)" indicating the length of the
     * item is inserted between prefix and the item.
     * @param postfix Each item is appended by this.
     */
    public static void formatTXTRecord(StringBuilder s, byte[] txt,
            String prefix, boolean printLen, String postfix) {
        if (txt == null || txt.length <= 1) {
            return;
        }
        try {
            int p = 0;
            while (p < txt.length) {
                int len = txt[p++];
                s.append(prefix);
                if (printLen) {
                    s.append("(").append(len).append(")");
                }
                s.append(new String(txt, p, len, UTF8));
                s.append(postfix);
                p += len;
            }
        } catch (UnsupportedEncodingException x) {
            // if UTF-8 is not supported, we really can't run.
            x.printStackTrace();
            System.exit(-1);
        }
    }

    public static void reportLoggingLevel(Logger logger) {
        // report logging level (if it's above INFO)
        Logger lgr = logger;
        Level level;
        do {
            level = lgr.getLevel();
            logger.log(Level.INFO, "logging level for [{0}]: {1}", 
                    new Object[]{lgr.getName(), level});
            lgr = lgr.getParent();
        } while (level == null);
    }

    /**
     * Set up the given logger so that it logs to the standard error 
     * at the given level.
     */
    public static void setupConsoleLogging(Logger logger, Level level) {
        // set the desired logging level on the logger.
        logger.setLevel(level);

        // remove all existing handlers for this logger
        Handler[] handlers = logger.getHandlers();
        for (Handler h : handlers) {
            logger.removeHandler(h);
        }

        // add a console handler (after setting its level as well)
        // and turn off using parent handlers.
        Handler h = new ConsoleHandler();
        h.setLevel(level);
        logger.addHandler(h);
        logger.setUseParentHandlers(false);
    }

    /**
     * Writes the UTF-8 encoding of the given string, preceded by
     * a 2-byte unsigned length in network order.
     *
     * Note that this method writes a true UTF-8, as opposed to
     * java.io.DataOutput.writeUTF() which writes a modified version
     * of UTF-8.
     */
    public static void writeRealUTF(DataOutputStream o, String s)
            throws IOException {
        try {
            byte[] b = s.getBytes(UTF8);
            writeByteArray(o, b);
        } catch (UnsupportedEncodingException x) {
            // if UTF-8 is not supported, we really can't run.
            x.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * Writes a byte array into the output stream, preceded by a
     * 2-byte unsigned length in network order.
     * If b.length is 0, only the 2-byte length is written.
     *
     * @throws NullPointerException if b is null
     */
    public static void writeByteArray(DataOutputStream o, byte[] b)
            throws IOException {
        int len = b.length;
        o.writeShort(len);
        o.write(b);
    }

    /**
     * Read back a String that was written out by {@link #writeRealUTF}.
     */
    public static String readRealUTF(DataInputStream in)
            throws IOException {
        try {
            return new String(readByteArray(in), UTF8);
        } catch (UnsupportedEncodingException x) {
            // if UTF-8 is not supported, we really can't run.
            System.exit(-1);
        }
        return null; // unreachable dummy statement to shut up compiler.
    }

    /**
     * Read back a byte array written by {@link #writeByteArray}.
     * If the 2-byte length read first is 0, new byte[0] is returned.
     */
    public static byte[] readByteArray(DataInputStream in)
            throws IOException {
        int len = in.readUnsignedShort();
        byte[] b = new byte[len];
        in.readFully(b);
        return b;
    }

    /**
     * Only take letters and digits from the inout, 
     * convert it to lowercase for English,
     * and return the result.
     */
    public static String reduce(String input) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                s.append(c);
            }
        }
        return s.toString().toLowerCase(Locale.ENGLISH);
    }

    /**
     * Return position of the first occurence of byte b in the byte array ba.
     * Starts searching from offset for length number of chars.
     * If b is not found in ba, -1 is returned.
     */
    public static int findFirstByte(byte b, byte[] ba, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            if (ba[i] == b) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Return position of the first occurence of byte b in the ByteBuffer ba.
     * Starts searching from offset for length number of chars.
     * If b is not found in ba, -1 is returned.
     */
    public static int findFirstByte(byte b, ByteBuffer ba, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            if (ba.get(i) == b) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Same as above, but this is specifically used to find a byte representing
     * a single char such that it ignores case sensitivity.
     * Starts searching from offset for length number of chars.
     * If b is not found in ba, -1 is returned.
     */
    public static int findFirstCaseByte(byte b, byte[] ba, int offset, int length) {
        int otherChar = 0;
        if (b >= 65 && b <= 90) {
            otherChar = 32;
        } else if (b >= 97 && b <= 122) {
            otherChar = -32;
        }
        for (int i = offset; i < offset + length; i++) {
            if (ba[i] == b || ba[i] == b + otherChar) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Compare byte array a to byte array b starting from positions a_off and
     * b_off and comparing for up to length bytes.  Returns 0 if they are
     * lexicographically identical, a negative value if a is lexicographically
     * less than b, and a positive value if a is lexicographically greater than
     * b.  Note that if a or b are of a size smaller than length, the shortest
     * of the three values will be used instead.
     */
    public static int compareBytes(byte[] a, int a_off, byte[] b, int b_off, int length) {
        int minLength = length;
        if (a.length < minLength) {
            minLength = a.length;
        }
        if (b.length < minLength) {
            minLength = b.length;
        }
        int i = a_off;
        int j = b_off;
        for (int n = 0; n < minLength; n++) {
            if (a[i] != b[j]) {
                return a[i] - b[j];
            }
            i++;
            j++;
        }
        return 0;
    }

    /**
     * Same as above, but this is specifically used to find a byte representing
     * a single char such that it ignores case sensitivity.
     * Compare byte array a to byte array b starting from positions a_off and
     * b_off and comparing for up to length bytes.  Returns 0 if they are
     * lexicographically identical, a negative value if a is lexicographically
     * less than b, and a positive value if a is lexicographically greater than
     * b.  Note that if a or b are of a size smaller than length, the shortest
     * of the three values will be used instead.
     */
    public static int compareCaseBytes(byte[] a, int a_off, byte[] b, int b_off, int length) {
        int minLength = length;
        int aCase = 0;
        int bCase = 0;
        if (a.length < minLength) {
            minLength = a.length;
        }
        if (b.length < minLength) {
            minLength = b.length;
        }
        int i = a_off;
        int j = b_off;
        for (int n = 0; n < minLength; n++) {
            if (a[i] >= 65 && a[i] <= 90) {
                aCase = 32;
            } else if (a[i] >= 97 && a[i] <= 122) {
                aCase = -32;
            }
            if (b[j] >= 65 && b[j] <= 90) {
                bCase = 32;
            } else if (b[j] >= 97 && b[j] <= 122) {
                bCase = -32;
            }
            if ((a[i] != b[j]) && (a[i] + aCase != b[j]) && (a[i] != b[j] + bCase)) {
                return a[i] - b[j];
            }
            i++;
            j++;
        }
        return 0;
    }

    /**
     * Same as above, but this is specifically used to find a byte representing
     * a single char such that it ignores case sensitivity.  This one also ignores
     * white spaces such as ' ', '\n', 'r\', and 't\'.
     * Compare byte array a to byte array b starting from positions a_off and
     * b_off and comparing for up to length bytes.  Returns 0 if they are
     * lexicographically identical, a negative value if a is lexicographically
     * less than b, and a positive value if a is lexicographically greater than
     * b.  Note that if a or b are of a size smaller than length, the shortest
     * of the three values will be used instead.
     */
    public static int compareSpaceCaseBytes(byte[] a, int a_off, byte[] b, int b_off, int length) {
        int minLength = length;
        int aCase = 0;
        int bCase = 0;
        if (a.length < minLength) {
            minLength = a.length;
        }
        if (b.length < minLength) {
            minLength = b.length;
        }
        int i = a_off;
        int j = b_off;
        for (int n = 0; n < minLength; n++) {
            if (a[i] >= 65 && a[i] <= 90) {
                aCase = 32;
            } else if (a[i] >= 97 && a[i] <= 122) {
                aCase = -32;
            }
            if (b[j] >= 65 && b[j] <= 90) {
                bCase = 32;
            } else if (b[j] >= 97 && b[j] <= 122) {
                bCase = -32;
            }
            if (a[i] == ' ' || a[i] == '\t') {
                i++;
                n--;
                continue;
            }
            if (b[j] == ' ' || b[j] == '\t') {
                j++;
                n--;
                continue;
            }
            if (a[i] == '\n' || a[i] == '\r') {
                if (i + 1 < a.length) {
                    if (a[i + 1] == ' ' || a[i + 1] == '\t') {
                        i += 2;
                        n--;
                        continue;
                    }
                } else {
                    return -1;
                }
            }
            if (b[j] == '\n' || b[j] == '\r') {
                if (j + 1 < b.length) {
                    if (b[j + 1] == ' ' || b[j + 1] == '\t') {
                        j += 2;
                        n--;
                        continue;
                    }
                } else {
                    return 1;
                }
            }
            if ((a[i] != b[j]) && (a[i] + aCase != b[j]) && (a[i] != b[j] + bCase)) {
                return a[i] - b[j];
            }
            i++;
            j++;
        }
        return 0;
    }

    /**
     * Return position of the end of a line by checking for "\r\n".
     * This checks for all cases of end of line that could include any
     * combination of '\r' and '\n'.  If neither terminating character is
     * present in the payload, returns -1.
     */
    public static int findEndOfLine(byte[] ba, int offset, int length) {
        int firstSlashN = findFirstByte((byte) '\n', ba, offset, length);
        int firstSlashR = findFirstByte((byte) '\r', ba, offset, length);
        if (firstSlashN < 0 && firstSlashR < 0) {
            return -1;
        } else if (firstSlashN < 0) {
            return firstSlashR;
        } else if (firstSlashR < 0) {
            return firstSlashN;
        } else if (firstSlashN < firstSlashR) {
            return firstSlashN;
        } else {
            return firstSlashR;
        }
    }

    /**
     * Return position of the end of a SIP line by checking for "\r\n".
     * This checks for all cases of end of line that could include any
     * combination of '\r' and '\n'.  If neither terminating character is
     * present in the payload, returns -1.
     */
    public static int findEndOfLine(ByteBuffer ba, int offset, int length) {
        int firstSlashN = findFirstByte((byte) '\n', ba, offset, length);
        int firstSlashR = findFirstByte((byte) '\r', ba, offset, length);
        if (firstSlashN < 0 && firstSlashR < 0) {
            return -1;
        } else if (firstSlashN < 0) {
            return firstSlashR;
        } else if (firstSlashR < 0) {
            return firstSlashN;
        } else if (firstSlashN < firstSlashR) {
            return firstSlashN;
        } else {
            return firstSlashR;
        }
    }
}

package org.mozilla.mozstumbler.service.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Zipper {
    public static byte[] zipData(byte[] data) throws IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        GZIPOutputStream gstream = new GZIPOutputStream(os);
        if (gstream == null) {
            return null;
        }
        byte[] output;
        try {
            gstream.write(data);
            gstream.finish();
            output = os.toByteArray();
        } finally {
            gstream.close();
            os.close();
        }
        return output;
    }

    public static String unzipData(byte[] data) throws IOException {
        String result = "";
        final ByteArrayInputStream bs = new ByteArrayInputStream(data);
        GZIPInputStream gstream = new GZIPInputStream(bs);
        if (gstream == null) {
            return result;
        }

        try {
            InputStreamReader reader = new InputStreamReader(gstream);
            BufferedReader in = new BufferedReader(reader);
            String read;
            while ((read = in.readLine()) != null) {
                result += read;
            }
        } finally {
            gstream.close();
            bs.close();
        }
        return result;
    }
}

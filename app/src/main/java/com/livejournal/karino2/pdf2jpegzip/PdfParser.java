package com.livejournal.karino2.pdf2jpegzip;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by karino on 2/4/15.
 */
public class PdfParser {
    final byte[] header = new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0};
    final byte[] endMarker = new byte[] {(byte)0xFF, (byte)0xD9};

    final int READ_SIZE = 1024;
    byte[] buffer = new byte[READ_SIZE+3];
    int bufReadLen = 0;

    int fileCount = 0;
    File targetFolder;
    File pdfFile;

    public PdfParser(File targetFolder) {
        this.targetFolder = targetFolder;

    }

    int findByte(byte[] buf, int len, int from, byte target) {
        for(int i = from; i < len; i++) {
            if(buf[i] == target)
                return i;
        }
        return -1;
    }

    private boolean lookAt(byte[] buf, int len, int from, byte[] pat) {
        if(pat.length > len-from)
            return false;
        for(int i = 0; i < pat.length; i++) {
            if(pat[i] != buf[from+i]) {
                return false;
            }
        }
        return true;
    }



    private int findMatch(byte[] buf, int len, byte[] pat) {
        return findMatchFrom(buf, len, 0, pat);
    }

    private int findMatchFrom(byte[] buf, int len, int from, byte[] pat) {
        while(true) {
            int pos = findByte(buf, len, from, pat[0]);
            if (pos == -1)
                return -1;
            if(lookAt(buf, len, pos, pat))
                return pos;
            from = pos+1;
        }
    }


    public void doOnePdf(File pdfFile) throws IOException {
        this.pdfFile = pdfFile;

        fileCount = 0;
        int offset = 0;

        int debPos = 0;

        InputStream stream = new BufferedInputStream(new FileInputStream(pdfFile));

        try {
            int bytesRead = stream.read(buffer, offset, READ_SIZE);
            while(bytesRead > 0) {
                bufReadLen = offset+bytesRead;
                debPos += bytesRead;

                int pos = findMatch(buffer, bufReadLen, header);
                if(pos != -1) {
                    do {
                        pos = writeJpeg(stream, buffer, bufReadLen, pos);
                        if (pos == -1) // invalid file, what should I do?
                            return;
                        pos = findMatchFrom(buffer, bufReadLen, pos, header);
                    }while(pos != -1);
                }

                if(bufReadLen < 3) {
                    // EOF.
                    return;
                }
                offset = 3;
                buffer[0] = buffer[bufReadLen -3];
                buffer[1] = buffer[bufReadLen -2];
                buffer[2] = buffer[bufReadLen -1];

                bytesRead = stream.read(buffer, offset, READ_SIZE);
            }
        }finally {
            stream.close();
        }
    }


    private File createJPEGFile(int fileIndex) {
        return new File(targetFolder,
                String.format("%s_%02d.jpg", getBaseName(pdfFile), fileIndex));
    }



    public static String getBaseName(File file) {
        String fileName = file.getName();
        return fileName.substring(0, fileName.lastIndexOf("."));
    }


    private int writeJpeg(InputStream stream, byte[] buf, int iniLen, int pos) throws IOException {
        File jpegFile = createJPEGFile(fileCount++);

        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(jpegFile));
        try {
            outputStream.write(buf, pos, iniLen - pos);

            int offset = 0;
            int bytesRead = stream.read(buffer, 0, READ_SIZE);
            while (bytesRead > 0) {
                bufReadLen = bytesRead + offset;
                int endMarkerPos = findMatch(buffer, bufReadLen, endMarker);
                if (endMarkerPos == -1) {
                    outputStream.write(buffer, offset, bytesRead);
                    offset = 1;
                    buffer[0] = buffer[bufReadLen - 1];
                } else {
                    outputStream.write(buffer, offset, endMarkerPos + endMarker.length - offset);
                    return endMarkerPos + endMarker.length;
                }
                bytesRead = stream.read(buffer, offset, READ_SIZE);
            }
        }finally {
            outputStream.close();
        }
        return -1; // finish before endmarker.
    }
}

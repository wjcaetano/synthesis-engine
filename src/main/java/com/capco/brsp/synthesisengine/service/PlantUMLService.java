package com.capco.brsp.synthesisengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.DiagramDescription;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;

@Slf4j
@Service(value = "plantUMLService")
@RequiredArgsConstructor
public class PlantUMLService {
    public String writeToBase64(String plantUmlScript) throws IOException {
        return Base64.getEncoder().encodeToString(writeToBytes(plantUmlScript));
    }

    public byte[] writeToBytes(String plantUmlScript) throws IOException {
        SourceStringReader reader = new SourceStringReader(plantUmlScript);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            DiagramDescription desc = reader.outputImage(os);

            if (desc == null || desc.getDescription().toLowerCase().contains("error")) {
                throw new RuntimeException("PlantUML generation failed: " + (desc != null ? desc.getDescription() : "Unknown error"));
            }
            if (desc.getDescription().toLowerCase().contains("cannot find graphviz")) {
                throw new RuntimeException("PlantUML generation failed: " + desc.getDescription());
            }
            return os.toByteArray();
        }
    }

    public void writeToImg(String filePath, String plantUmlScript) throws IOException {
        byte[] imageBytes = writeToBytes(plantUmlScript);

        File outputFile = new File(filePath);

        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            outputStream.write(imageBytes);
        }
    }

//    public static String encode6bit(int b) {
//        if (b < 10) {
//            return String.valueOf((char) (48 + b));  // Characters '0' to '9'
//        }
//        b -= 10;
//        if (b < 26) {
//            return String.valueOf((char) (65 + b));  // Characters 'A' to 'Z'
//        }
//        b -= 26;
//        if (b < 26) {
//            return String.valueOf((char) (97 + b));  // Characters 'a' to 'z'
//        }
//        b -= 26;
//        if (b == 0) {
//            return "-";  // Special character '-'
//        }
//        if (b == 1) {
//            return "_";  // Special character '_'
//        }
//        return "?";
//    }
//
//    public static String append3bytes(int b1, int b2, int b3) {
//        int c1 = b1 >> 2;
//        int c2 = ((b1 & 0x3) << 4) | (b2 >> 4);
//        int c3 = ((b2 & 0xF) << 2) | (b3 >> 6);
//        int c4 = b3 & 0x3F;
//
//        return encode6bit(c1 & 0x3F) + encode6bit(c2 & 0x3F) +
//                encode6bit(c3 & 0x3F) + encode6bit(c4 & 0x3F);
//    }
//
//    public static String encode(String data) {
//        StringBuilder r = new StringBuilder();
//        for (int i = 0; i < data.length(); i += 3) {
//            if (i + 2 == data.length()) {
//                r.append(append3bytes(data.charAt(i), data.charAt(i + 1), 0));
//            } else if (i + 1 == data.length()) {
//                r.append(append3bytes(data.charAt(i), 0, 0));
//            } else {
//                r.append(append3bytes(data.charAt(i), data.charAt(i + 1), data.charAt(i + 2)));
//            }
//        }
//        return r.toString();
//    }
//
//    public static String deflateRaw(String data) throws IOException {
//        byte[] input = data.getBytes(StandardCharsets.UTF_8);
//
//        // Create a byte array output stream to hold the result of the compression
//        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//
//        // Create a Deflater with the maximum compression level (9)
//        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);  // true for raw compression
//
//        // Set the input data for compression
//        deflater.setInput(input);
//        deflater.finish();
//
//        // Compress the data and write to the output stream
//        byte[] buffer = new byte[1024];
//        while (!deflater.finished()) {
//            int bytesCompressed = deflater.deflate(buffer);
//            byteArrayOutputStream.write(buffer, 0, bytesCompressed);
//        }
//
//        // Get the compressed data as a byte array
//
//        // Return the compressed data as a string (binary format)
//        return byteArrayOutputStream.toString(StandardCharsets.ISO_8859_1);  // using ISO-8859-1 to handle binary data
//    }
}

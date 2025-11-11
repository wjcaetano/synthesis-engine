package com.capco.brsp.synthesisengine.utils;

import com.capco.brsp.synthesisengine.dto.FileDto;
import com.capco.brsp.synthesisengine.exception.IllegalFilesMapException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
public class FileUtils {
    public static final Path USER_TEMP_PROJECTS_FOLDER_PATH = FileUtils.absolutePathJoin(System.getProperty("user.dir"), "temp", "projects");
    @Getter
    private static final FileUtils INSTANCE = new FileUtils();

    private FileUtils() {
    }

    public static void recreateEmptyIfExist(Path path) {
        try {
            FileUtils.deleteIfExists(path);
            FileUtils.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void deleteIfExists(String strPath) throws IOException {
        Path path = Paths.get(strPath);
        deleteIfExists(path);
    }

    public static void deleteIfExists(Path path) throws IOException {
        if (isFileExists(path)) {
            org.apache.commons.io.FileUtils.forceDelete(path.toFile());
        }
    }

    public static boolean isFileExists(String stringPath) {
        Path path = Paths.get(stringPath);
        return isFileExists(path);
    }

    public static boolean isFileExists(Path path) {
        return path.toFile().exists();
    }

    public static void createDirectories(String strPath) throws IOException {
        Path path = Paths.get(strPath);
        Files.createDirectories(path);
    }

    public static void createDirectories(Path path) throws IOException {
        Files.createDirectories(path);
    }

    public static Map<String, String> zipToMapOfStrings(File zipFile, boolean asBase64) throws IOException {
        return zipToMapOfStrings(zipFile, asBase64, null);
    }

    public static Map<String, String> zipToMapOfStrings(File zipFile, boolean asBase64, String password) throws IOException {
        if (password != null && !password.isEmpty()) {
            return zipToMapOfStringsWithPassword(zipFile, asBase64, password);
        }
        try (FileInputStream fileInputStream = new FileInputStream(zipFile)) {
            return zipToMapOfStrings(fileInputStream.readAllBytes(), asBase64);
        }
    }

    public static Map<String, String> zipToMapOfStrings(String zipBase64, boolean asBase64) throws IOException {
        return zipToMapOfStrings(zipBase64, asBase64, null);
    }

    public static Map<String, String> zipToMapOfStrings(String zipBase64, boolean asBase64, String password) throws IOException {
        try {
            var zipPath = Path.of(zipBase64);
            if (Files.exists(zipPath)) {
                return zipToMapOfStrings(zipPath.toFile(), asBase64, password);
            }
        } catch (InvalidPathException ignored) {}

        String base64Content = zipBase64.trim();
        if (zipBase64.matches("^data:.*?;base64,[\\s\\S]+$")) {
            base64Content = zipBase64.substring(zipBase64.indexOf("base64,") + 7);
        }

        base64Content = base64Content.replaceAll("\\s+", "");
        byte[] zipBytes = Base64.getDecoder().decode(base64Content);
        
        if (password != null && !password.isEmpty()) {
            Path tmp = Files.createTempFile("zip", ".zip");
            Files.write(tmp, zipBytes);
            try {
                return zipToMapOfStrings(tmp.toFile(), asBase64, password);
            } finally {
                Files.deleteIfExists(tmp);
            }
        }

        return zipToMapOfStrings(zipBytes, asBase64);
    }

    public static Map<String, String> zipToMapOfStrings(byte[] zipBytes, boolean asBase64) throws IOException {
        Map<String, String> filesMap = new ConcurrentLinkedHashMap<>();

        try (var byteInputStream = new ByteArrayInputStream(zipBytes);
             var zis = new ZipInputStream(byteInputStream)) {

            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                var zipEntryName = zipEntry.getName();
                var zipEntryNameNormalized = zipEntryName.replace("\\", "/");

                if (zipEntry.isDirectory()) {
                    filesMap.put(zipEntryNameNormalized, null);
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                if (asBase64) {
                    var bytes = baos.toByteArray();
                    var mimeType = getMimeType(bytes);
                    var base64 = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
                    filesMap.put(zipEntryNameNormalized, base64);
                } else {
                    filesMap.put(zipEntryNameNormalized, baos.toString(StandardCharsets.UTF_8));
                }
                zis.closeEntry();
            }
        }

        return filesMap;
    }

    private static Map<String, String> zipToMapOfStringsWithPassword(File zipFile, boolean asBase64, String password) throws IOException {
        Map<String, String> filesMap = new LinkedHashMap<>();

        try (ZipFile zip = new ZipFile(zipFile, password.toCharArray())) {
            for (FileHeader header : zip.getFileHeaders()) {
                String name = header.getFileName().replace("\\", "/");
                if (header.isDirectory()) {
                    filesMap.put(name, null);
                    continue;
                }

                try (InputStream is = zip.getInputStream(header)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    is.transferTo(baos);

                    if (asBase64) {
                        var bytes = baos.toByteArray();
                        var mimeType = getMimeType(bytes);
                        var base64 = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
                        filesMap.put(name, base64);
                    } else {
                        filesMap.put(name, baos.toString(StandardCharsets.UTF_8));
                    }
                }
            }
        }

        return filesMap;
    }

    public static void unzipFile(File fileZip, Path targetPath) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {

                File newFile = newUnzipedFile(targetPath.toFile(), zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // write file content
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }

            zis.closeEntry();
        }
    }

    public static File newUnzipedFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        String raw = zipEntry.getName();
        if (raw.isBlank() || raw.indexOf('\0') >= 0) {
            throw new IOException("Invalid name");
        }
        raw = raw.replace('\\', '/');
        while (raw.startsWith("/")) {
            raw = raw.substring(1);
        }
        if (raw.equals(".") || raw.equals("..")) {
            throw new IOException("Invalid path");
        }
        Path base = destinationDir.toPath().toAbsolutePath();
        Path target = base.resolve(raw).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("Entry escapes target dir: " + zipEntry.getName());
        }
        Path parent = target.getParent();
        if (parent == null || !parent.startsWith(base)) {
            throw new IOException("Invalid parent for: " + zipEntry.getName());
        }
        return target.toFile();
    }

    public static Path pathJoin(Object path, Object... paths) {
        return Paths.get(path.toString(), Arrays.stream(paths).map(it -> it.toString().replaceAll("[\r\n]", "")).toList().toArray(new String[]{}));
    }

    public static Path absolutePathJoin(Object path, Object... paths) {
        return pathJoin(path, paths).toAbsolutePath();
    }

    public static void zipFile(String sourceFolder, String zipFile) {
        try {
            FileOutputStream fos = new FileOutputStream(zipFile);
            ZipOutputStream zos = new ZipOutputStream(fos);

            byte[] buffer = new byte[1024];

            zipFolderContents(sourceFolder, sourceFolder, zos, buffer);

            zos.close();
        } catch (IOException e) {
            log.error("Failed to zip content of the folder '{}' to a file named '{}'", sourceFolder, zipFile, e);
        }
    }

    public static void zipFolderContents(String sourceFolder, String basePath, ZipOutputStream zos, byte[] buffer) throws IOException {
        // Normalization of the path to avoid further issues
        sourceFolder = Path.of(sourceFolder).toString();
        basePath = Path.of(basePath).toString();

        File directory = new File(sourceFolder);
        File[] files = directory.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile()) {
                String entryName = file.getAbsolutePath().substring(basePath.length() + 1);
                ZipEntry ze = new ZipEntry(entryName);
                zos.putNextEntry(ze);

                try (FileInputStream fis = new FileInputStream(file)) {
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                }

                zos.closeEntry();

            } else if (file.isDirectory()) {
                zipFolderContents(file.getAbsolutePath(), basePath, zos, buffer);
            }
        }
    }

    public static void writeMapOfFiles(String projectFolder, Map<String, Object> filesMap) {
        writeMapOfFiles(Path.of(projectFolder), filesMap);
    }

    public static void writeMapOfFiles(Path projectFolder, Map<String, Object> filesMap) {
        if (filesMap == null || filesMap.isEmpty()) {
            throw new IllegalArgumentException("resources must not be null or empty");
        }

        var filesContent = Utils.normalizeMap(filesMap, File.separator);

        for (var filePathAndContent : filesContent.entrySet()) {
            var pathName = FileUtils.absolutePathJoin(projectFolder, filePathAndContent.getKey());

            writeFile(pathName, (String) filePathAndContent.getValue(), false);
        }
    }

    public static void writeFile(Path path, String content, boolean append) {
        try {
            Files.createDirectories(path.getParent());
            try (var fileWriter = Files.newBufferedWriter(path, StandardOpenOption.CREATE, append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
                fileWriter.write(String.valueOf(content));
            }
        } catch (IOException e) {
            throw new IllegalFilesMapException(e);
        }
    }

    public static List<FileDto> crawlDirectory(String basePath) throws IOException {
        List<FileDto> fileDtos = new ConcurrentLinkedList<>();
        Path baseDir = Paths.get(basePath);

        try {
            Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
                    String relativePath = baseDir.relativize(filePath).toString();
                    if (!relativePath.contains("meta")) {
                        LocalDateTime creationTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(attrs.creationTime().toMillis()), ZoneId.systemDefault());
                        LocalDateTime updateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()), ZoneId.systemDefault());

                        String fileExtension = FilenameUtils.getExtension(filePath.toString());
                        Set<String> imageExtensions = Set.of(
                                "jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp", "svg", "heif", "raw", "ico"
                        );
                        String mimeType = getMimeType(filePath);
                        String content;
                        if (imageExtensions.contains(fileExtension)) {
                            content = getFullBase64(filePath);
                        } else {
                            byte[] fileBytes = Files.readAllBytes(filePath);
                            content = new String(fileBytes, StandardCharsets.UTF_8);
                        }

                        FileDto fileDto = FileDto.builder()
                                .path(relativePath)
                                .mimeType(mimeType)
                                .createdAt(creationTime)
                                .updatedAt(updateTime)
                                .content(content)
                                .build();

                        fileDtos.add(fileDto);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ex) {
            log.error("Failed to do a walkFileTree at {}", baseDir);
        }

        return fileDtos;
    }

    public static List<FileDto> crawlFilterDirectory(String basePath, String pattern) {
        Pattern filePattern = pattern != null ? Pattern.compile(pattern) : null;

        List<FileDto> fileDtos = new ConcurrentLinkedList<>();
        Path baseDir = Paths.get(basePath);

        try {
            Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(Path filePath, @NotNull BasicFileAttributes attrs) throws IOException {
                    String relativePath = baseDir.relativize(filePath).toString();

                    if (filePattern == null || filePattern.matcher(relativePath).find()) {
                        LocalDateTime creationTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(attrs.creationTime().toMillis()), ZoneId.systemDefault());
                        LocalDateTime updateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()), ZoneId.systemDefault());

                        String fileExtension = FilenameUtils.getExtension(filePath.toString());
                        Set<String> imageExtensions = Set.of(
                                "jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp", "svg", "heif", "raw", "ico"
                        );
                        String mimeType = getMimeType(filePath);
                        String content;
                        if (imageExtensions.contains(fileExtension)) {
                            content = getFullBase64(filePath);
                        } else {
                            byte[] fileBytes = Files.readAllBytes(filePath);
                            content = new String(fileBytes, StandardCharsets.UTF_8);
                        }

                        FileDto fileDto = FileDto.builder()
                                .path(relativePath)
                                .fullPath(filePath.toString())
                                .mimeType(mimeType)
                                .createdAt(creationTime)
                                .updatedAt(updateTime)
                                .content(content)
                                .build();

                        fileDtos.add(fileDto);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ex) {
            log.error("Failed to do a walkFileTree at {}", baseDir, ex);
        }

        return fileDtos;
    }

    public static String getMimeType(byte[] bytes) {
        try {
            Tika tika = new Tika();
            return tika.detect(bytes);
        } catch (Exception ex) {
            return "application/octet-stream";
        }
    }

    public static String getMimeType(Path filePath) {
        try {
            Tika tika = new Tika();
            return tika.detect(filePath.toString());
        } catch (Exception ex) {
            log.error("Not able to identify the right mimetype for the file: {}\nException Message: {}", ex.getMessage());
            return "application/octet-stream";
        }
    }

    public static String getFullBase64(String filePath) throws IOException {
        return getFullBase64(Paths.get(filePath));
    }

    public static String getFullBase64(Path filePath) throws IOException {
        try {
            byte[] fileBytes = Files.readAllBytes(filePath);

            String mimeType = getMimeType(filePath);

            String base64String = Base64.getEncoder().encodeToString(fileBytes);

            return "data:" + mimeType + ";base64," + base64String;
        } catch (NoSuchFileException ex) {
            log.error("File '{}' not found to extract base64 content from it!", filePath);
            return null;
        }
    }

    public static boolean isAbsolutePath(String path) {
        if (path.startsWith("/")) {
            return true;
        }

        return path.matches("[A-Za-z]:\\\\.*") || path.matches("^\\\\\\\\.*");
    }

    public static String removeFileExtension(String file) {

        String filename;

        // Remove the path upto the filename.
        int lastSeparatorIndex = file.lastIndexOf(File.separator);
        if (lastSeparatorIndex == -1) {
            filename = file;
        } else {
            filename = file.substring(lastSeparatorIndex + 1);
        }

        // Remove the extension.
        int extensionIndex = filename.lastIndexOf(".");
        if (extensionIndex == -1)
            return filename;

        return filename.substring(0, extensionIndex);
    }

    public static MultipartFile createMultipartFileFromBytes(String fileName, String contentType, byte[] fileBytes){
        return new MultipartFile() {
            @Override
            public String getName() {
                return fileName;
            }

            @Override
            public String getOriginalFilename() {
                return fileName;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public boolean isEmpty() {
                return fileBytes.length == 0;
            }

            @Override
            public long getSize() {
                return fileBytes.length;
            }

            @Override
            public byte[] getBytes() throws IOException {
                return fileBytes;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(fileBytes);
            }

            @Override
            public void transferTo(File dest) throws IOException, IllegalStateException {
                try (FileOutputStream fos = new FileOutputStream(dest)) {
                    fos.write(fileBytes);
                }
            }
        };
    }

    public static boolean equalsPaths(Object path1, Object path2) {
        String path11 = path1 instanceof String path1String ? path1String : path1.toString();
        String path22 = path2 instanceof String path2String ? path2String : path2.toString();

        path11 = path11.replace("\\", "/");
        path22 = path22.replace("\\", "/");

        return path11.equals(path22);
    }

    private static final Map<Character, String> FORWARD_MAP = Map.of(
            '<', "$1$",
            '>', "$2$",
            ':', "$3$",
            '?', "$4$",
            '*', "$5$"
    );

    private static final Map<String, Character> REVERSE_MAP = Map.of(
            "$1$", '<',
            "$2$", '>',
            "$3$", ':',
            "$4$", '?',
            "$5$", '*'
    );

    public static String sanitize(String input) {
        String result = input;
        for (var e : FORWARD_MAP.entrySet()) {
            result = result.replace(String.valueOf(e.getKey()), e.getValue());
        }
        return result;
    }

    public static String restore(String sanitized) {
        String result = sanitized;
        for (var e : REVERSE_MAP.entrySet()) {
            result = result.replace(e.getKey(), String.valueOf(e.getValue()));
        }
        return result;
    }
}

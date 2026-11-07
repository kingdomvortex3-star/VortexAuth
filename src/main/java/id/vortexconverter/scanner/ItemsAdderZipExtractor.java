
package id.vortexconverter.scanner;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ItemsAdderZipExtractor {

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_ENTRIES = 100000;
    private static final long MAX_UNCOMPRESSED_SIZE =
            1024L * 1024L * 1024L;

    public Path extract(File zipFile, Path destination)
            throws IOException {

        if (zipFile == null || !zipFile.isFile()) {
            throw new IOException(
                    "ItemsAdder generated.zip tidak ditemukan."
            );
        }

        if (destination == null) {
            throw new IOException(
                    "Folder ekstraksi tidak ditentukan."
            );
        }

        Path target = destination.toAbsolutePath().normalize();

        Path parent = target.getParent();

        if (parent == null) {
            throw new IOException(
                    "Folder tujuan ekstraksi tidak valid."
            );
        }

        Files.createDirectories(parent);

        deleteDirectory(target);

        Files.createDirectories(target);

        Path realTarget = target.toRealPath();

        int entryCount = 0;
        long totalBytes = 0L;

        try (ZipFile archive = new ZipFile(zipFile)) {

            Enumeration<? extends ZipEntry> entries =
                    archive.entries();

            while (entries.hasMoreElements()) {

                ZipEntry entry = entries.nextElement();

                entryCount++;

                if (entryCount > MAX_ENTRIES) {
                    throw new IOException(
                            "ZIP memiliki terlalu banyak entri. "
                                    + "Batas: " + MAX_ENTRIES
                    );
                }

                Path output = resolveEntryPath(
                        realTarget,
                        entry.getName()
                );

                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }

                Path outputParent = output.getParent();

                if (outputParent == null) {
                    throw new IOException(
                            "Path file ZIP tidak valid: "
                                    + entry.getName()
                    );
                }

                Files.createDirectories(outputParent);

                long entrySize = entry.getSize();

                if (entrySize > MAX_UNCOMPRESSED_SIZE) {
                    throw new IOException(
                            "File ZIP terlalu besar: "
                                    + entry.getName()
                    );
                }

                try (
                        InputStream input = new BufferedInputStream(
                                archive.getInputStream(entry)
                        );

                        BufferedOutputStream outputStream =
                                new BufferedOutputStream(
                                        Files.newOutputStream(output)
                                )
                ) {

                    byte[] buffer = new byte[BUFFER_SIZE];

                    int read;

                    while ((read = input.read(buffer)) != -1) {

                        totalBytes += read;

                        if (totalBytes > MAX_UNCOMPRESSED_SIZE) {
                            throw new IOException(
                                    "Total hasil ekstraksi ZIP "
                                            + "melebihi batas 1 GB."
                            );
                        }

                        outputStream.write(buffer, 0, read);
                    }
                }

                if (entrySize >= 0
                        && Files.size(output) != entrySize) {
                    throw new IOException(
                            "Ukuran hasil ekstraksi tidak cocok: "
                                    + entry.getName()
                    );
                }
            }

        } catch (IOException exception) {

            try {
                deleteDirectory(target);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw new IOException(
                    "Gagal mengekstrak ItemsAdder generated.zip: "
                            + exception.getMessage(),
                    exception
            );
        }

        if (entryCount == 0) {
            deleteDirectory(target);

            throw new IOException(
                    "ItemsAdder generated.zip kosong."
            );
        }

        return target;
    }

    private Path resolveEntryPath(
            Path root,
            String entryName
    ) throws IOException {

        if (entryName == null || entryName.isBlank()) {
            throw new IOException(
                    "ZIP memiliki nama entri kosong."
            );
        }

        String normalizedName = entryName.replace('\\', '/');

        if (normalizedName.startsWith("/")
                || normalizedName.matches("^[a-zA-Z]:.*")) {
            throw new IOException(
                    "Path absolut tidak diizinkan dalam ZIP: "
                            + entryName
            );
        }

        Path output = root.resolve(normalizedName)
                .normalize();

        if (!output.startsWith(root)) {
            throw new IOException(
                    "ZIP Slip terdeteksi: " + entryName
            );
        }

        return output;
    }

    public void deleteDirectory(Path directory)
            throws IOException {

        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {

            for (Path path : stream
                    .sorted(Comparator.reverseOrder())
                    .toList()) {

                Files.deleteIfExists(path);
            }
        }
    }
}

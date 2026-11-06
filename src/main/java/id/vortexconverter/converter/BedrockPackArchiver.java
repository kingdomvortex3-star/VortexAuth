
package id.vortexconverter.converter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BedrockPackArchiver {

    private static final int BUFFER_SIZE = 8192;

    public Path createMcpack(
            Path packDirectory,
            Path outputDirectory,
            String packName
    ) throws IOException {

        if (packDirectory == null
                || !Files.isDirectory(packDirectory)) {
            throw new IOException(
                    "Resource pack directory does not exist."
            );
        }

        if (outputDirectory == null) {
            throw new IOException(
                    "Output directory cannot be null."
            );
        }

        if (!Files.exists(packDirectory.resolve("manifest.json"))) {
            throw new IOException(
                    "manifest.json is missing from resource pack."
            );
        }

        Files.createDirectories(outputDirectory);

        String safeName = sanitize(packName);

        Path outputFile = outputDirectory.resolve(
                safeName + ".mcpack"
        );

        Path temporaryFile = outputDirectory.resolve(
                safeName + ".mcpack.tmp"
        );

        try {
            writeArchive(packDirectory, temporaryFile);

            try {
                Files.move(
                        temporaryFile,
                        outputFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (IOException exception) {
                Files.move(
                        temporaryFile,
                        outputFile,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            return outputFile;

        } catch (IOException exception) {
            Files.deleteIfExists(temporaryFile);
            throw exception;
        }
    }

    private void writeArchive(
            Path packDirectory,
            Path outputFile
    ) throws IOException {

        Path root = packDirectory.toAbsolutePath().normalize();

        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(
                        Files.newOutputStream(outputFile)
                )
        )) {

            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .sorted()
                        .toList()) {

                    Path normalized = path.toAbsolutePath().normalize();

                    if (!normalized.startsWith(root)) {
                        throw new IOException(
                                "Invalid resource pack file path."
                        );
                    }

                    String entryName = root.relativize(normalized)
                            .toString()
                            .replace('\\', '/');

                    addFile(zip, normalized, entryName);
                }
            }
        }
    }

    private void addFile(
            ZipOutputStream zip,
            Path file,
            String entryName
    ) throws IOException {

        ZipEntry entry = new ZipEntry(entryName);

        zip.putNextEntry(entry);

        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(file)
        )) {
            byte[] buffer = new byte[BUFFER_SIZE];

            int length;

            while ((length = input.read(buffer)) != -1) {
                zip.write(buffer, 0, length);
            }
        }

        zip.closeEntry();
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "VortexPack";
        }

        String safe = name.replaceAll(
                "[^a-zA-Z0-9_-]",
                "_"
        );

        return safe.isBlank() ? "VortexPack" : safe;
    }
}

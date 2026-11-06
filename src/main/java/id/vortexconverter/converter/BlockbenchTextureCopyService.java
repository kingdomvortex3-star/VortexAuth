
package id.vortexconverter.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BlockbenchTextureCopyService {

    public CopyResult copyTextures(
            File modelFile,
            File sourceDirectory,
            File packDirectory
    ) throws IOException {

        if (modelFile == null || !modelFile.isFile()) {
            throw new IOException("BBModel file does not exist.");
        }

        if (packDirectory == null) {
            throw new IOException("Pack directory cannot be null.");
        }

        Path textureOutput = packDirectory.toPath()
                .resolve("textures")
                .resolve("entity");

        Files.createDirectories(textureOutput);

        JsonObject root;

        try (FileReader reader = new FileReader(modelFile)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException(
                    "Invalid BBModel texture data.",
                    exception
            );
        }

        JsonArray textures = getArray(root, "textures");

        List<String> copied = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();

        for (JsonElement element : textures) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject texture = element.getAsJsonObject();

            String name = getString(texture, "name", "texture.png");
            String source = getString(texture, "source", "");
            String relativePath = getString(texture, "path", "");

            String outputName = uniqueName(
                    sanitizeFileName(name),
                    usedNames
            );

            Path destination = textureOutput.resolve(outputName);

            try {
                if (source.startsWith("data:image/")) {
                    byte[] imageBytes = decodeBase64(source);

                    Files.write(destination, imageBytes);
                    copied.add(outputName);
                    continue;
                }

                Path sourceFile = findTexture(
                        sourceDirectory,
                        modelFile,
                        relativePath,
                        source,
                        name
                );

                if (sourceFile == null) {
                    missing.add(name);
                    continue;
                }

                Files.copy(
                        sourceFile,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING
                );

                copied.add(outputName);

            } catch (IOException exception) {
                missing.add(name + " (" + exception.getMessage() + ")");
            }
        }

        return new CopyResult(
                copied,
                missing,
                copied.size(),
                missing.size()
        );
    }

    private Path findTexture(
            File sourceDirectory,
            File modelFile,
            String relativePath,
            String source,
            String name
    ) {
        List<Path> candidates = new ArrayList<>();

        Path modelDirectory = modelFile.toPath()
                .toAbsolutePath()
                .getParent();

        if (modelDirectory != null) {
            if (!relativePath.isBlank()) {
                candidates.add(modelDirectory.resolve(relativePath));
            }

            if (!source.isBlank() && !source.startsWith("data:")) {
                candidates.add(modelDirectory.resolve(source));
            }

            if (!name.isBlank()) {
                candidates.add(modelDirectory.resolve(name));
            }
        }

        if (sourceDirectory != null && sourceDirectory.isDirectory()) {
            Path base = sourceDirectory.toPath().toAbsolutePath();

            if (!relativePath.isBlank()) {
                candidates.add(base.resolve(relativePath));
            }

            if (!source.isBlank() && !source.startsWith("data:")) {
                candidates.add(base.resolve(source));
            }

            if (!name.isBlank()) {
                candidates.add(base.resolve(name));
            }
        }

        for (Path candidate : candidates) {
            Path normalized = candidate.normalize();

            if (Files.isRegularFile(normalized)
                    && normalized.getFileName().toString()
                    .toLowerCase(Locale.ROOT).endsWith(".png")) {
                return normalized;
            }
        }

        return null;
    }

    private byte[] decodeBase64(String source) throws IOException {
        int comma = source.indexOf(',');

        if (comma < 0 || comma == source.length() - 1) {
            throw new IOException("Invalid embedded image data.");
        }

        try {
            return Base64.getDecoder().decode(
                    source.substring(comma + 1)
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException(
                    "Unable to decode embedded texture.",
                    exception
            );
        }
    }

    private JsonArray getArray(JsonObject object, String key) {
        if (object == null
                || !object.has(key)
                || !object.get(key).isJsonArray()) {
            return new JsonArray();
        }

        return object.getAsJsonArray(key);
    }

    private String getString(
            JsonObject object,
            String key,
            String fallback
    ) {
        if (object == null
                || !object.has(key)
                || object.get(key).isJsonNull()) {
            return fallback;
        }

        return object.get(key).getAsString();
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "texture.png";
        }

        String safe = name.replaceAll(
                "[^a-zA-Z0-9_.-]",
                "_"
        );

        if (!safe.toLowerCase(Locale.ROOT).endsWith(".png")) {
            safe += ".png";
        }

        return safe;
    }

    private String uniqueName(
            String name,
            Set<String> usedNames
    ) {
        String candidate = name;
        int index = 1;

        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
            int dot = name.lastIndexOf('.');

            if (dot > 0) {
                candidate = name.substring(0, dot)
                        + "_" + index
                        + name.substring(dot);
            } else {
                candidate = name + "_" + index;
            }

            index++;
        }

        return candidate;
    }

    public record CopyResult(
            List<String> copiedFiles,
            List<String> missingFiles,
            int copiedCount,
            int missingCount
    ) {}
}

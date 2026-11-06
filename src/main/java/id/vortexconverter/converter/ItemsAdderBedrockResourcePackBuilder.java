
package id.vortexconverter.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import id.vortexconverter.mappings.ItemsAdderCustomMappingGenerator;
import id.vortexconverter.scanner.ItemsAdderConfigParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ItemsAdderBedrockResourcePackBuilder {

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    public PackResult build(
            File itemsAdderContents,
            File geyserDataDirectory,
            ItemsAdderConfigParser.ParseResult parseResult,
            ItemsAdderCustomMappingGenerator.MappingResult mappingResult
    ) throws IOException {

        if (itemsAdderContents == null
                || !itemsAdderContents.isDirectory()) {
            throw new IOException("ItemsAdder contents tidak ditemukan.");
        }

        if (geyserDataDirectory == null) {
            throw new IOException("Folder data Geyser tidak ditemukan.");
        }

        if (parseResult == null || mappingResult == null
                || mappingResult.mapping() == null) {
            throw new IOException("Data parser atau mapping tidak tersedia.");
        }

        File packsDirectory = new File(geyserDataDirectory, "packs");

        if (!packsDirectory.exists() && !packsDirectory.mkdirs()) {
            throw new IOException("Tidak dapat membuat folder Geyser packs.");
        }

        File workingDirectory = new File(
                geyserDataDirectory,
                ".vortexconverter-bedrock-work"
        );

        if (workingDirectory.exists()) {
            deleteDirectory(workingDirectory.toPath());
        }

        File texturesDirectory = new File(
                workingDirectory,
                "textures/items"
        );

        if (!texturesDirectory.mkdirs()
                && !texturesDirectory.isDirectory()) {
            throw new IOException("Tidak dapat membuat folder tekstur.");
        }

        List<String> warnings = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        TextureIndex textureIndex = indexTextures(itemsAdderContents);

        JsonObject textureData = new JsonObject();

        JsonObject items = mappingResult.mapping()
                .getAsJsonObject("items");

        if (items == null) {
            throw new IOException("Mapping tidak memiliki objek items.");
        }

        int discoveredItems = 0;
        int copiedTextures = 0;

        Map<String, String> generatedAtlasKeys = new LinkedHashMap<>();

        for (ItemsAdderConfigParser.AssetInfo asset
                : parseResult.getAssets()) {

            if (asset.getType()
                    != ItemsAdderConfigParser.AssetType.ITEM) {
                continue;
            }

            discoveredItems++;

            String javaIdentifier = asset.getJavaIdentifier();

            if (javaIdentifier == null || !items.has(javaIdentifier)) {
                skipped.add(asset.getNamespacedId()
                        + ": mapping Java tidak ditemukan.");
                continue;
            }

            JsonArray definitions;

            try {
                definitions = items.getAsJsonArray(javaIdentifier);
            } catch (Exception exception) {
                skipped.add(asset.getNamespacedId()
                        + ": format definisi mapping tidak valid.");
                continue;
            }

            if (definitions == null || definitions.isEmpty()) {
                skipped.add(asset.getNamespacedId()
                        + ": definisi mapping kosong.");
                continue;
            }

            JsonObject definition =
                    definitions.get(0).getAsJsonObject();

            if (!definition.has("bedrock_identifier")) {
                skipped.add(asset.getNamespacedId()
                        + ": Bedrock identifier tidak ditemukan.");
                continue;
            }

            String bedrockIdentifier = definition
                    .get("bedrock_identifier")
                    .getAsString();

            String atlasKey = toAtlasKey(bedrockIdentifier);

            if (generatedAtlasKeys.containsKey(bedrockIdentifier)) {
                continue;
            }

            List<String> textureReferences =
                    findTextureReferences(asset);

            if (textureReferences.isEmpty()) {
                skipped.add(asset.getNamespacedId()
                        + ": referensi tekstur tidak ditemukan.");
                continue;
            }

            File sourceTexture = null;

            for (String reference : textureReferences) {
                sourceTexture = resolveTexture(
                        reference,
                        asset.getNamespace(),
                        textureIndex
                );

                if (sourceTexture != null) {
                    break;
                }
            }

            if (sourceTexture == null) {
                skipped.add(asset.getNamespacedId()
                        + ": file tekstur tidak ditemukan. Referensi: "
                        + String.join(", ", textureReferences));
                continue;
            }

            File destination = new File(
                    texturesDirectory,
                    atlasKey + ".png"
            );

            Files.copy(
                    sourceTexture.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            JsonObject textureEntry = new JsonObject();
            textureEntry.addProperty(
                    "textures",
                    "textures/items/" + atlasKey
            );

            textureData.add(bedrockIdentifier, textureEntry);

            generatedAtlasKeys.put(
                    bedrockIdentifier,
                    sourceTexture.getAbsolutePath()
            );

            copiedTextures++;
        }

        if (copiedTextures == 0) {
            throw new IOException(
                    "Tidak ada tekstur yang berhasil dikonversi. "
                            + "Periksa hasil scan YAML, mapping, dan referensi tekstur."
            );
        }

        JsonObject textureAtlas = new JsonObject();
        textureAtlas.addProperty(
                "resource_pack_name",
                "VortexConverter"
        );
        textureAtlas.addProperty(
                "texture_name",
                "atlas.items"
        );
        textureAtlas.add(
                "texture_data",
                textureData
        );

        writeJson(
                new File(workingDirectory, "textures/item_texture.json"),
                textureAtlas
        );

        writeManifest(workingDirectory);

        File packFile = new File(
                packsDirectory,
                "VortexConverter-Bedrock.mcpack"
        );

        File temporaryPack = new File(
                packsDirectory,
                "VortexConverter-Bedrock.tmp"
        );

        if (temporaryPack.exists() && !temporaryPack.delete()) {
            throw new IOException("Tidak dapat menghapus pack sementara.");
        }

        createArchive(workingDirectory, temporaryPack);

        try {
            Files.move(
                    temporaryPack.toPath(),
                    packFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException exception) {
            Files.move(
                    temporaryPack.toPath(),
                    packFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

        warnings.add(
                "Builder ini mengemas tekstur item yang memiliki mapping. "
                        + "Model 3D, block, furniture, entity, animasi, "
                        + "dan suara belum diterjemahkan menjadi format Bedrock."
        );

        warnings.add(
                "Mapping dan resource pack harus menggunakan identifier "
                        + "yang sama agar tekstur dapat dipasangkan."
        );

        return new PackResult(
                true,
                discoveredItems,
                copiedTextures,
                skipped.size(),
                skipped,
                warnings,
                workingDirectory,
                packFile
        );
    }

    private List<String> findTextureReferences(
            ItemsAdderConfigParser.AssetInfo asset
    ) {
        List<String> references = new ArrayList<>();

        Map<String, String> properties = asset.getProperties();

        String[] preferredKeys = {
                "resource.textures",
                "resource.texture",
                "textures",
                "texture",
                "resource.texture_path",
                "texture_path"
        };

        for (String preferred : preferredKeys) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!entry.getKey().equalsIgnoreCase(preferred)) {
                    continue;
                }

                addTextureValues(references, entry.getValue());
            }
        }

        if (references.isEmpty()) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);

                if (key.equals("resource.material")
                        || key.endsWith(".material")
                        || key.contains("material")
                        || key.contains("model")
                        || key.contains("geometry")) {
                    continue;
                }

                if (key.equals("texture")
                        || key.endsWith(".texture")
                        || key.equals("textures")
                        || key.endsWith(".textures")
                        || key.contains("texture_path")) {
                    addTextureValues(references, entry.getValue());
                }
            }
        }

        return references.stream()
                .distinct()
                .toList();
    }

    private void addTextureValues(
            List<String> references,
            String value
    ) {
        if (value == null || value.isBlank()) {
            return;
        }

        String cleaned = value.trim();

        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        for (String part : cleaned.split(",")) {
            String reference = part.trim()
                    .replace("\"", "")
                    .replace("'", "");

            if (reference.isBlank()) {
                continue;
            }

            if (reference.contains("{")
                    || reference.contains("}")) {
                continue;
            }

            references.add(reference);
        }
    }

    private TextureIndex indexTextures(File root)
            throws IOException {

        TextureIndex index = new TextureIndex();

        Path rootPath = root.toPath().toRealPath();

        try (var stream = Files.walk(rootPath)) {
            for (Path path : stream
                    .filter(Files::isRegularFile)
                    .filter(candidate -> candidate.getFileName()
                            .toString()
                            .toLowerCase(Locale.ROOT)
                            .endsWith(".png"))
                    .toList()) {

                Path realPath = path.toRealPath();

                if (!realPath.startsWith(rootPath)) {
                    continue;
                }

                String relative = rootPath.relativize(realPath)
                        .toString()
                        .replace('\\', '/');

                index.add(relative, realPath.toFile());
            }
        }

        return index;
    }

    private File resolveTexture(
            String texturePath,
            String namespace,
            TextureIndex index
    ) {
        if (texturePath == null || texturePath.isBlank()) {
            return null;
        }

        String normalized = normalizePath(texturePath);

        if (normalized.isBlank()) {
            return null;
        }

        String namespacePrefix =
                namespace.toLowerCase(Locale.ROOT) + "/";

        if (normalized.startsWith(namespacePrefix)) {
            normalized = normalized.substring(namespacePrefix.length());
        }

        List<String> candidates = new ArrayList<>();

        candidates.add(normalized);

        if (!normalized.startsWith("textures/")) {
            candidates.add("textures/" + normalized);
        }

        if (!normalized.startsWith("resource/")) {
            candidates.add("resource/" + normalized);
        }

        for (String candidate : candidates) {
            String withExtension = candidate.toLowerCase(Locale.ROOT);

            if (!withExtension.endsWith(".png")) {
                withExtension += ".png";
            }

            File exact = index.findExact(withExtension);

            if (exact != null) {
                return exact;
            }

            File suffix = index.findSuffix(withExtension);

            if (suffix != null) {
                return suffix;
            }
        }

        String basename = normalized.substring(
                normalized.lastIndexOf('/') + 1
        );

        if (!basename.toLowerCase(Locale.ROOT).endsWith(".png")) {
            basename += ".png";
        }

        File namespaceMatch = index.findNamespaceBasename(
                namespace,
                basename
        );

        if (namespaceMatch != null) {
            return namespaceMatch;
        }

        return index.findUniqueBasename(basename);
    }

    private String normalizePath(String value) {
        String normalized = value
                .trim()
                .replace('\\', '/');

        int colon = normalized.indexOf(':');

        if (colon >= 0) {
            normalized = normalized.substring(colon + 1);
        }

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return normalized;
    }

    private String toAtlasKey(String identifier) {
        return identifier
                .replace(':', '.')
                .replace('/', '_')
                .replace('\\', '_')
                .replaceAll("[^a-zA-Z0-9_.-]", "_")
                .toLowerCase(Locale.ROOT);
    }

    private void writeManifest(File directory)
            throws IOException {

        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version", 2);

        JsonObject header = new JsonObject();
        header.addProperty(
                "name",
                "VortexConverter Bedrock Resource Pack"
        );
        header.addProperty(
                "description",
                "Generated by VortexConverter"
        );
        header.addProperty(
                "uuid",
                UUID.nameUUIDFromBytes(
                        "VortexConverter-Bedrock-Header"
                                .getBytes(StandardCharsets.UTF_8)
                ).toString()
        );
        header.add("version", versionArray(1, 0, 0));
        header.add("min_engine_version", versionArray(1, 20, 0));

        JsonObject module = new JsonObject();
        module.addProperty("type", "resources");
        module.addProperty(
                "uuid",
                UUID.nameUUIDFromBytes(
                        "VortexConverter-Bedrock-Module"
                                .getBytes(StandardCharsets.UTF_8)
                ).toString()
        );
        module.add("version", versionArray(1, 0, 0));

        JsonArray modules = new JsonArray();
        modules.add(module);

        manifest.add("header", header);
        manifest.add("modules", modules);

        writeJson(
                new File(directory, "manifest.json"),
                manifest
        );
    }

    private JsonArray versionArray(int major, int minor, int patch) {
        JsonArray array = new JsonArray();
        array.add(major);
        array.add(minor);
        array.add(patch);
        return array;
    }

    private void writeJson(File file, JsonObject json)
            throws IOException {

        File parent = file.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException(
                    "Tidak dapat membuat folder: " + parent
            );
        }

        Files.writeString(
                file.toPath(),
                GSON.toJson(json),
                StandardCharsets.UTF_8
        );
    }

    private void createArchive(File source, File destination)
            throws IOException {

        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(destination.toPath()))) {

            try (var stream = Files.walk(source.toPath())) {
                for (Path path : stream
                        .filter(Files::isRegularFile)
                        .toList()) {

                    String entryName = source.toPath()
                            .relativize(path)
                            .toString()
                            .replace('\\', '/');

                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private void deleteDirectory(Path directory)
            throws IOException {

        if (!Files.exists(directory)) {
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

    private static final class TextureIndex {

        private final Map<String, File> exact = new HashMap<>();
        private final Map<String, List<File>> basenames = new HashMap<>();

        void add(String relativePath, File file) {
            String normalized = relativePath
                    .replace('\\', '/')
                    .toLowerCase(Locale.ROOT);

            exact.put(normalized, file);

            String basename = file.getName()
                    .toLowerCase(Locale.ROOT);

            basenames.computeIfAbsent(
                    basename,
                    ignored -> new ArrayList<>()
            ).add(file);
        }

        File findExact(String path) {
            return exact.get(
                    path.replace('\\', '/')
                            .toLowerCase(Locale.ROOT)
            );
        }

        File findSuffix(String suffix) {
            String normalized = suffix
                    .replace('\\', '/')
                    .toLowerCase(Locale.ROOT);

            File match = null;

            for (Map.Entry<String, File> entry : exact.entrySet()) {
                if (!entry.getKey().endsWith("/" + normalized)) {
                    continue;
                }

                if (match != null) {
                    return null;
                }

                match = entry.getValue();
            }

            return match;
        }

        File findNamespaceBasename(
                String namespace,
                String basename
        ) {
            List<File> matches = basenames.get(
                    basename.toLowerCase(Locale.ROOT)
            );

            if (matches == null) {
                return null;
            }

            File match = null;
            String prefix = namespace.toLowerCase(Locale.ROOT) + "/";

            for (File file : matches) {
                String path = file.getPath()
                        .replace('\\', '/')
                        .toLowerCase(Locale.ROOT);

                if (!path.contains(prefix)) {
                    continue;
                }

                if (match != null) {
                    return null;
                }

                match = file;
            }

            return match;
        }

        File findUniqueBasename(String basename) {
            List<File> matches = basenames.get(
                    basename.toLowerCase(Locale.ROOT)
            );

            if (matches == null || matches.size() != 1) {
                return null;
            }

            return matches.get(0);
        }
    }

    public record PackResult(
            boolean successful,
            int discoveredItems,
            int copiedTextures,
            int skippedItems,
            List<String> skippedDetails,
            List<String> warnings,
            File workingDirectory,
            File packFile
    ) {
        public boolean isSuccessful() {
            return successful
                    && packFile != null
                    && packFile.isFile();
        }
    }
}

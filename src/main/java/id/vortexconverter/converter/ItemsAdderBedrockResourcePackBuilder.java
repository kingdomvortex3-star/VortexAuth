
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
import java.util.List;
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

        Map<String, List<File>> textureIndex =
                indexTextures(itemsAdderContents);

        JsonObject textureData = new JsonObject();

        JsonObject items = mappingResult.mapping()
                .getAsJsonObject("items");

        int discoveredItems = 0;
        int copiedTextures = 0;

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
                        + ": mapping tidak ditemukan.");
                continue;
            }

            JsonArray definitions = items.getAsJsonArray(javaIdentifier);

            if (definitions == null || definitions.isEmpty()) {
                skipped.add(asset.getNamespacedId()
                        + ": definisi mapping kosong.");
                continue;
            }

            JsonObject definition = definitions.get(0).getAsJsonObject();

            String bedrockIdentifier =
                    definition.get("bedrock_identifier").getAsString();

            String atlasKey = toAtlasKey(bedrockIdentifier);
            String texturePath = asset.getTexturePath();

            if (texturePath == null || texturePath.isBlank()) {
                skipped.add(asset.getNamespacedId()
                        + ": texture path tidak ditemukan.");
                continue;
            }

            File sourceTexture = resolveTexture(
                    texturePath,
                    textureIndex
            );

            if (sourceTexture == null) {
                skipped.add(asset.getNamespacedId()
                        + ": tekstur tidak ditemukan atau ambigu: "
                        + texturePath);
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

            // Geyser/Bedrock item_texture.json menggunakan string path.
            textureEntry.addProperty(
                    "textures",
                    "textures/items/" + atlasKey
            );

            textureData.add(bedrockIdentifier, textureEntry);
            copiedTextures++;
        }

        if (copiedTextures == 0) {
            throw new IOException(
                    "Tidak ada tekstur item yang berhasil dikonversi. "
                            + "Periksa konfigurasi texture ItemsAdder."
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

        createArchive(workingDirectory, packFile);

        warnings.add(
                "Pack saat ini berisi tekstur item dan metadata dasar. "
                        + "Model 3D, block, furniture, dan animasi "
                        + "belum dikonversi oleh builder ini."
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

    private Map<String, List<File>> indexTextures(File root)
            throws IOException {

        Map<String, List<File>> index = new HashMap<>();

        try (var stream = Files.walk(root.toPath())) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString()
                            .toLowerCase()
                            .endsWith(".png"))
                    .forEach(path -> {
                        String filename = path.getFileName()
                                .toString()
                                .toLowerCase();

                        String key = filename.substring(
                                0,
                                filename.length() - 4
                        );

                        index.computeIfAbsent(
                                key,
                                ignored -> new ArrayList<>()
                        ).add(path.toFile());
                    });
        }

        return index;
    }

    private File resolveTexture(
            String texturePath,
            Map<String, List<File>> textureIndex
    ) {
        String normalized = texturePath
                .replace('\\', '/')
                .trim();

        int colon = normalized.indexOf(':');

        if (colon >= 0) {
            normalized = normalized.substring(colon + 1);
        }

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (normalized.toLowerCase().endsWith(".png")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - 4
            );
        }

        String filename = normalized.substring(
                normalized.lastIndexOf('/') + 1
        ).toLowerCase();

        List<File> matches = textureIndex.get(filename);

        if (matches == null || matches.size() != 1) {
            return null;
        }

        return matches.get(0);
    }

    private String toAtlasKey(String identifier) {
        return identifier
                .replace(':', '.')
                .replace('/', '_')
                .toLowerCase();
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


package id.vortexconverter.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import id.vortexconverter.mappings.ItemsAdderCustomMappingGenerator;
import id.vortexconverter.scanner.ItemsAdderAssetResolver;
import id.vortexconverter.scanner.ItemsAdderConfigParser;
import id.vortexconverter.scanner.ItemsAdderZipExtractor;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ItemsAdderBedrockResourcePackBuilder {

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private static final int MAX_PARENT_DEPTH = 32;
    private static final String PARENT_KEY_PREFIX = "models:parent:";

    private final ItemsAdderAssetResolver assetResolver =
            new ItemsAdderAssetResolver();

    private final ItemsAdderZipExtractor zipExtractor =
            new ItemsAdderZipExtractor();

    private final ItemsAdderModelParser modelParser =
            new ItemsAdderModelParser();

    private final ItemsAdderBedrockGeometryConverter geometryConverter =
            new ItemsAdderBedrockGeometryConverter();

    private final ItemsAdderBedrockRenderControllerBuilder renderControllerBuilder =
            new ItemsAdderBedrockRenderControllerBuilder();

    public PackResult build(
            File itemsAdderContents,
            File geyserDataDirectory,
            ItemsAdderConfigParser.ParseResult parseResult,
            ItemsAdderCustomMappingGenerator.MappingResult mappingResult
    ) throws IOException {
        return buildFromSource(
                itemsAdderContents,
                null,
                geyserDataDirectory,
                parseResult,
                mappingResult
        );
    }

    public PackResult build(
            File itemsAdderContents,
            File generatedZip,
            File geyserDataDirectory,
            ItemsAdderConfigParser.ParseResult parseResult,
            ItemsAdderCustomMappingGenerator.MappingResult mappingResult
    ) throws IOException {

        if (generatedZip == null || !generatedZip.isFile()) {
            throw new IOException(
                    "ItemsAdder output/generated.zip tidak ditemukan."
            );
        }

        return buildFromSource(
                itemsAdderContents,
                generatedZip,
                geyserDataDirectory,
                parseResult,
                mappingResult
        );
    }

    private PackResult buildFromSource(
            File itemsAdderContents,
            File generatedZip,
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
            throw new IOException(
                    "Data parser atau mapping tidak tersedia."
            );
        }

        File packsDirectory = new File(geyserDataDirectory, "packs");
        Files.createDirectories(packsDirectory.toPath());

        if (!packsDirectory.isDirectory()) {
            throw new IOException("Folder Geyser packs tidak tersedia.");
        }

        File workingDirectory = new File(
                geyserDataDirectory,
                ".vortexconverter-bedrock-work"
        );

        Path workingPath = workingDirectory.toPath();

        if (Files.exists(workingPath)) {
            deleteDirectory(workingPath);
        }

        Files.createDirectories(workingPath);

        boolean zipMode = generatedZip != null;
        File sourceDirectory;

        if (zipMode) {
            sourceDirectory = zipExtractor.extract(
                    generatedZip,
                    workingPath.resolve("source")
            ).toFile();
        } else {
            sourceDirectory = itemsAdderContents;
        }

        File packRoot = new File(workingDirectory, "bedrock_pack");
        File texturesDirectory = new File(packRoot, "textures/items");
        File geometryDirectory = new File(packRoot, "models/entity");
        File attachablesDirectory = new File(packRoot, "attachables");

        Files.createDirectories(texturesDirectory.toPath());
        Files.createDirectories(geometryDirectory.toPath());
        Files.createDirectories(attachablesDirectory.toPath());

        ItemsAdderAssetResolver.ResolutionResult resolution =
                assetResolver.resolve(parseResult, sourceDirectory);

        List<String> warnings = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        JsonObject items =
                mappingResult.mapping().getAsJsonObject("items");

        if (items == null) {
            throw new IOException("Mapping tidak memiliki objek items.");
        }

        JsonObject textureData = new JsonObject();
        JsonObject filteredItems = new JsonObject();

        Map<String, String> generatedIdentifiers =
                new LinkedHashMap<>();

        int discoveredItems = 0;
        int copiedTextures = 0;
        int uvIconsGenerated = 0;
        int inheritedModelsUsed = 0;
        int geometriesGenerated = 0;
        int attachablesGenerated = 0;

        for (ItemsAdderConfigParser.AssetInfo asset
                : parseResult.getAssets()) {

            if (asset.getType()
                    != ItemsAdderConfigParser.AssetType.ITEM) {
                continue;
            }

            discoveredItems++;

            String javaIdentifier = asset.getJavaIdentifier();

            if (javaIdentifier == null || !items.has(javaIdentifier)) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": mapping Java tidak ditemukan."
                );
                continue;
            }

            JsonArray definitions;

            try {
                definitions = items.getAsJsonArray(javaIdentifier);
            } catch (Exception exception) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": format mapping tidak valid."
                );
                continue;
            }

            if (definitions == null || definitions.isEmpty()) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": definisi mapping kosong."
                );
                continue;
            }

            String assetCmdValue = asset.getCustomModelDataValue();

            if (assetCmdValue == null) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": Custom Model Data tidak ditemukan."
                );
                continue;
            }

            JsonObject definition = findMatchingDefinition(
                    definitions,
                    assetCmdValue
            );

            if (definition == null) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": mapping dengan CMD "
                                + assetCmdValue
                                + " tidak ditemukan."
                );
                continue;
            }

            if (!definition.has("bedrock_identifier")
                    || definition.get("bedrock_identifier").isJsonNull()) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": Bedrock identifier tidak ditemukan."
                );
                continue;
            }

            String bedrockIdentifier = definition
                    .get("bedrock_identifier")
                    .getAsString();

            if (generatedIdentifiers.containsKey(bedrockIdentifier)) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": Bedrock identifier duplikat dengan "
                                + generatedIdentifiers.get(bedrockIdentifier)
                );
                continue;
            }

            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset =
                    findResolvedAsset(resolution, asset);

            if (resolvedAsset == null) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": aset tidak ditemukan dalam resolver."
                );
                continue;
            }

            File sourceTexture = findResolvedTexture(resolvedAsset);

            if (sourceTexture == null) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": tekstur tidak ditemukan."
                );
                continue;
            }

            String atlasKey = toAtlasKey(bedrockIdentifier);

            if (textureData.has(atlasKey)) {
                skipped.add(
                        asset.getNamespacedId()
                                + ": key atlas duplikat: " + atlasKey
                );
                continue;
            }

            File destination = new File(
                    texturesDirectory,
                    atlasKey + ".png"
            );

            ModelBuildData modelData = null;
            boolean uvGenerated = false;

            try {
                modelData = loadMergedModel(
                        resolvedAsset,
                        warnings,
                        asset.getNamespacedId()
                );

                if (modelData != null && modelData.model().hasElements()) {
                    uvGenerated = createUvIcon(
                            resolvedAsset,
                            modelData.model(),
                            destination,
                            warnings,
                            asset.getNamespacedId()
                    );

                    if (modelData.inherited()) {
                        inheritedModelsUsed++;
                    }
                }
            } catch (Exception exception) {
                warnings.add(
                        asset.getNamespacedId()
                                + ": pembacaan model gagal: "
                                + exception.getMessage()
                );
            }

            if (!uvGenerated) {
                Files.copy(
                        sourceTexture.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            } else {
                uvIconsGenerated++;
            }

            JsonObject textureEntry = new JsonObject();
            JsonArray texturePaths = new JsonArray();

            texturePaths.add("textures/items/" + atlasKey);
            textureEntry.add("textures", texturePaths);
            textureData.add(atlasKey, textureEntry);

            if (modelData != null && modelData.model().hasElements()) {
                try {
                    BufferedImage textureImage =
                            ImageIO.read(sourceTexture);

                    if (textureImage != null) {
                        String geometryIdentifier =
                                "geometry.vortexconverter."
                                        + atlasKey.replace('.', '_');

                        ItemsAdderBedrockGeometryConverter.ConversionResult
                                geometryResult = geometryConverter.convert(
                                        modelData.model(),
                                        geometryIdentifier,
                                        textureImage.getWidth(),
                                        textureImage.getHeight()
                                );

                        if (geometryResult.successful()) {
                            File geometryFile = new File(
                                    geometryDirectory,
                                    atlasKey + ".geo.json"
                            );

                            geometryConverter.write(
                                    geometryResult,
                                    geometryFile
                            );

                            File attachableFile = new File(
                                    attachablesDirectory,
                                    atlasKey + ".json"
                            );

                            JsonObject attachable = createAttachable(
                                    bedrockIdentifier,
                                    geometryIdentifier,
                                    "textures/items/" + atlasKey
                            );

                            writeJson(attachableFile, attachable);

                            geometriesGenerated++;
                            attachablesGenerated++;

                            warnings.addAll(
                                    prefixWarnings(
                                            asset.getNamespacedId(),
                                            geometryResult.warnings()
                                    )
                            );
                        } else {
                            warnings.add(
                                    asset.getNamespacedId()
                                            + ": geometry gagal: "
                                            + geometryResult.error()
                            );
                        }
                    }
                } catch (Exception exception) {
                    warnings.add(
                            asset.getNamespacedId()
                                    + ": pembuatan geometry gagal: "
                                    + exception.getMessage()
                    );
                }
            }

            generatedIdentifiers.put(
                    bedrockIdentifier,
                    asset.getNamespacedId()
            );

            JsonArray retainedDefinitions;

            if (filteredItems.has(javaIdentifier)) {
                retainedDefinitions =
                        filteredItems.getAsJsonArray(javaIdentifier);
            } else {
                retainedDefinitions = new JsonArray();
                filteredItems.add(
                        javaIdentifier,
                        retainedDefinitions
                );
            }

            retainedDefinitions.add(definition.deepCopy());
            copiedTextures++;
        }

        if (copiedTextures == 0) {
            throw new IOException(
                    "Tidak ada tekstur item yang berhasil dikonversi. "
                            + "Resolver memindai "
                            + resolution.getScannedFiles()
                            + " file."
            );
        }

        mappingResult.mapping().add("items", filteredItems);

        JsonObject textureAtlas = new JsonObject();

        textureAtlas.addProperty(
                "resource_pack_name",
                "VortexConverter"
        );

        textureAtlas.addProperty(
                "texture_name",
                "atlas.items"
        );

        textureAtlas.add("texture_data", textureData);

        writeJson(
                new File(packRoot, "textures/item_texture.json"),
                textureAtlas
        );

        if (attachablesGenerated > 0) {
            File renderControllerFile =
                    renderControllerBuilder.write(packRoot);

            warnings.add(
                    "Render controller berhasil dibuat: "
                            + renderControllerFile.getName()
            );
        }

        writeManifest(packRoot);

        File packFile = new File(
                packsDirectory,
                "VortexConverter-Bedrock.mcpack"
        );

        File temporaryPack = new File(
                packsDirectory,
                "VortexConverter-Bedrock.tmp"
        );

        Files.deleteIfExists(temporaryPack.toPath());
        createArchive(packRoot, temporaryPack);

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
                "Resolver memindai " + resolution.getScannedFiles()
                        + " file."
        );

        warnings.add(
                "Referensi ditemukan: " + resolution.getResolvedFiles()
                        + ", tidak ditemukan: "
                        + resolution.getMissingReferences() + "."
        );

        warnings.add(
                "Icon: " + copiedTextures
                        + ", ekstraksi UV: " + uvIconsGenerated
                        + ", fallback: "
                        + (copiedTextures - uvIconsGenerated) + "."
        );

        warnings.add(
                "Model dengan parent inheritance: "
                        + inheritedModelsUsed + "."
        );

        warnings.add(
                "Geometry JSON dibuat: " + geometriesGenerated
                        + ", attachable dibuat: "
                        + attachablesGenerated + "."
        );

        warnings.add(
                "Geometry dan attachable dibuat sebagai struktur awal. "
                        + "Kecocokan render dan pemanggilan attachable oleh "
                        + "Geyser harus diuji di client Bedrock."
        );

        warnings.add(
                "Block, furniture, entity, animasi, dan suara "
                        + "belum dikonversi secara penuh."
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

    private JsonObject createAttachable(
            String bedrockIdentifier,
            String geometryIdentifier,
            String texturePath
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");

        JsonObject attachable = new JsonObject();
        JsonObject description = new JsonObject();

        description.addProperty(
                "identifier",
                bedrockIdentifier
        );

        JsonObject materials = new JsonObject();
        materials.addProperty(
                "default",
                "entity_alphatest"
        );

        JsonObject textures = new JsonObject();
        textures.addProperty("default", texturePath);

        JsonObject geometries = new JsonObject();
        geometries.addProperty("default", geometryIdentifier);

        JsonArray renderControllers = new JsonArray();
        renderControllers.add("controller.render.default");

        description.add("materials", materials);
        description.add("textures", textures);
        description.add("geometry", geometries);
        description.add("render_controllers", renderControllers);

        attachable.add("description", description);
        root.add("minecraft:attachable", attachable);

        return root;
    }

    private List<String> prefixWarnings(
            String assetName,
            List<String> sourceWarnings
    ) {
        List<String> result = new ArrayList<>();

        if (sourceWarnings == null) {
            return result;
        }

        for (String warning : sourceWarnings) {
            result.add(assetName + ": " + warning);
        }

        return result;
    }

    private ModelBuildData loadMergedModel(
            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset,
            List<String> warnings,
            String assetName
    ) throws IOException {

        File modelFile = findResolvedModel(resolvedAsset);

        if (modelFile == null) {
            return null;
        }

        String rootModelReference = findRootModelReference(
                resolvedAsset,
                modelFile
        );

        JsonObject mergedRoot = mergeResolvedModelHierarchy(
                resolvedAsset,
                modelFile,
                rootModelReference,
                new LinkedHashSet<>(),
                0,
                warnings,
                assetName
        );

        if (mergedRoot == null) {
            return null;
        }

        boolean inherited = mergedRoot.has(
                "vortexconverter_parent_inherited"
        ) && mergedRoot.get(
                "vortexconverter_parent_inherited"
        ).getAsBoolean();

        mergedRoot.remove("vortexconverter_parent_inherited");

        File temporaryModel = new File(
                modelFile.getParentFile(),
                ".vortex-model-" + UUID.randomUUID() + ".json"
        );

        try {
            Files.writeString(
                    temporaryModel.toPath(),
                    GSON.toJson(mergedRoot),
                    StandardCharsets.UTF_8
            );

            ItemsAdderModelParser.ModelData model =
                    modelParser.parse(temporaryModel);

            return new ModelBuildData(model, inherited);

        } finally {
            Files.deleteIfExists(temporaryModel.toPath());
        }
    }

    private JsonObject mergeResolvedModelHierarchy(
            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset,
            File modelFile,
            String rootModelReference,
            Set<String> visited,
            int depth,
            List<String> warnings,
            String assetName
    ) throws IOException {

        if (modelFile == null || !modelFile.isFile()) {
            return null;
        }

        if (depth >= MAX_PARENT_DEPTH) {
            warnings.add(
                    assetName + ": batas kedalaman parent model tercapai."
            );
            return null;
        }

        String identity = normalizeFileIdentity(modelFile);

        if (!visited.add(identity)) {
            warnings.add(
                    assetName + ": circular parent model terdeteksi."
            );
            return null;
        }

        try {
            JsonElement element = JsonParser.parseString(
                    Files.readString(modelFile.toPath())
            );

            if (!element.isJsonObject()) {
                throw new IOException(
                        "Model JSON bukan object: " + modelFile.getName()
                );
            }

            JsonObject child = element.getAsJsonObject().deepCopy();
            JsonObject merged = new JsonObject();

            boolean inherited = false;
            String parentReference = getJsonString(child, "parent");

            if (parentReference != null
                    && !parentReference.isBlank()
                    && !parentReference.startsWith("minecraft:")) {

                String parentKey = PARENT_KEY_PREFIX
                        + rootModelReference
                        + ":"
                        + parentReference;

                List<File> parentFiles = resolvedAsset
                        .getResolvedFiles()
                        .get(parentKey);

                if (parentFiles != null && !parentFiles.isEmpty()) {
                    File parentFile = firstJson(parentFiles);

                    if (parentFile != null) {
                        JsonObject parent = mergeResolvedModelHierarchy(
                                resolvedAsset,
                                parentFile,
                                rootModelReference,
                                visited,
                                depth + 1,
                                warnings,
                                assetName
                        );

                        if (parent != null) {
                            merged = parent;
                            inherited = true;
                        }
                    }
                } else {
                    warnings.add(
                            assetName
                                    + ": parent tidak ditemukan di Resolver: "
                                    + parentReference
                    );
                }
            }

            JsonObject mergedTextures = new JsonObject();

            if (merged.has("textures")
                    && merged.get("textures").isJsonObject()) {
                mergedTextures = merged.getAsJsonObject("textures")
                        .deepCopy();
            }

            if (child.has("textures")
                    && child.get("textures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry
                        : child.getAsJsonObject("textures").entrySet()) {
                    mergedTextures.add(
                            entry.getKey(),
                            entry.getValue().deepCopy()
                    );
                }
            }

            if (!mergedTextures.isEmpty()) {
                merged.add("textures", mergedTextures);
            }

            if (child.has("elements")
                    && child.get("elements").isJsonArray()) {
                merged.add(
                        "elements",
                        child.getAsJsonArray("elements").deepCopy()
                );
            }

            for (Map.Entry<String, JsonElement> entry
                    : child.entrySet()) {

                String key = entry.getKey();

                if (key.equals("parent")
                        || key.equals("textures")
                        || key.equals("elements")) {
                    continue;
                }

                merged.add(key, entry.getValue().deepCopy());
            }

            merged.remove("parent");

            merged.addProperty(
                    "vortexconverter_parent_inherited",
                    inherited || getBoolean(
                            merged,
                            "vortexconverter_parent_inherited"
                    )
            );

            return merged;

        } finally {
            visited.remove(identity);
        }
    }

    private boolean createUvIcon(
            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset,
            ItemsAdderModelParser.ModelData model,
            File destination,
            List<String> warnings,
            String assetName
    ) throws IOException {

        if (model == null || !model.hasElements()) {
            return false;
        }

        ItemsAdderModelParser.FaceData selectedFace = null;
        double largestArea = -1.0;

        for (ItemsAdderModelParser.FaceData face : model.faces()) {
            if (!face.hasExplicitUv()) {
                continue;
            }

            double width = Math.abs(face.getU2() - face.getU1());
            double height = Math.abs(face.getV2() - face.getV1());
            double area = width * height;

            if (area > largestArea) {
                largestArea = area;
                selectedFace = face;
            }
        }

        if (selectedFace == null || largestArea <= 0) {
            warnings.add(
                    assetName + ": tidak memiliki UV eksplisit."
            );
            return false;
        }

        File textureFile = findFaceTexture(
                resolvedAsset,
                model,
                selectedFace
        );

        if (textureFile == null) {
            warnings.add(
                    assetName + ": texture face UV tidak ditemukan."
            );
            return false;
        }

        BufferedImage source = ImageIO.read(textureFile);

        if (source == null) {
            return false;
        }

        BufferedImage cropped = cropUv(source, selectedFace);

        if (cropped == null) {
            return false;
        }

        BufferedImage rotated = rotateTexture(
                cropped,
                selectedFace.rotation()
        );

        Files.createDirectories(destination.getParentFile().toPath());

        if (!ImageIO.write(rotated, "png", destination)) {
            throw new IOException("Tidak ada PNG image writer.");
        }

        return true;
    }

    private String findRootModelReference(
            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset,
            File modelFile
    ) {
        for (Map.Entry<String, List<File>> entry
                : resolvedAsset.getResolvedFiles().entrySet()) {

            String key = entry.getKey();

            if (!isModelProperty(getPropertyName(key))) {
                continue;
            }

            for (File file : entry.getValue()) {
                if (file != null
                        && normalizeFileIdentity(file).equals(
                                normalizeFileIdentity(modelFile)
                        )) {
                    int separator = key.indexOf(':');

                    if (separator >= 0 && separator + 1 < key.length()) {
                        return key.substring(separator + 1);
                    }
                }
            }
        }

        return modelFile.getName().replaceFirst(
                "(?i)\\.json$",
                ""
        );
    }

    private File findResolvedModel(
            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset
    ) {
        for (Map.Entry<String, List<File>> entry
                : resolvedAsset.getResolvedFiles().entrySet()) {

            String key = entry.getKey().toLowerCase(Locale.ROOT);

            if (key.startsWith("textures:model_texture:")
                    || key.startsWith(PARENT_KEY_PREFIX)) {
                continue;
            }

            if (!isModelProperty(getPropertyName(key))) {
                continue;
            }

            File model = firstJson(entry.getValue());

            if (model != null) {
                return model;
            }
        }

        return null;
    }

    private boolean isModelProperty(String property) {
        return property.equals("model")
                || property.endsWith(".model")
                || property.contains("model_path")
                || property.contains("model_file")
                || property.contains("item_model");
    }

    private File firstJson(List<File> files) {
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file != null
                    && file.isFile()
                    && file.getName().toLowerCase(Locale.ROOT)
                    .endsWith(".json")) {
                return file;
            }
        }

        return null;
    }

    private File findFaceTexture(
            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset,
            ItemsAdderModelParser.ModelData model,
            ItemsAdderModelParser.FaceData face
    ) {
        String resolvedReference = modelParser.resolveTexture(
                face.texture(),
                model.textures()
        );

        String textureKey = face.texture();

        if (textureKey.startsWith("#")) {
            textureKey = textureKey.substring(1);
        }

        Map<String, List<File>> files =
                resolvedAsset.getResolvedFiles();

        for (Map.Entry<String, List<File>> entry : files.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);

            if (!key.startsWith("textures:model_texture:")) {
                continue;
            }

            boolean keyMatches = key.contains(
                    ":" + textureKey.toLowerCase(Locale.ROOT) + ":"
            );

            boolean referenceMatches = resolvedReference != null
                    && key.endsWith(
                            ":" + resolvedReference.toLowerCase(Locale.ROOT)
                    );

            if (!keyMatches && !referenceMatches) {
                continue;
            }

            File png = firstPng(entry.getValue());

            if (png != null) {
                return png;
            }
        }

        if (resolvedReference != null) {
            String normalized =
                    resolvedReference.toLowerCase(Locale.ROOT);

            for (Map.Entry<String, List<File>> entry : files.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);

                if (key.startsWith("textures:model_texture:")
                        && key.endsWith(":" + normalized)) {
                    File png = firstPng(entry.getValue());

                    if (png != null) {
                        return png;
                    }
                }
            }
        }

        return null;
    }

    private File findResolvedTexture(
            ItemsAdderAssetResolver.ResolvedAsset resolvedAsset
    ) {
        Map<String, List<File>> files =
                resolvedAsset.getResolvedFiles();

        for (Map.Entry<String, List<File>> entry : files.entrySet()) {
            String key = entry.getKey();

            if (key.startsWith("textures:model_texture:")) {
                continue;
            }

            if (!isTextureProperty(getPropertyName(key))) {
                continue;
            }

            File png = firstPng(entry.getValue());

            if (png != null) {
                return png;
            }
        }

        File layer0 = findModelTexture(files, ":layer0:");

        if (layer0 != null) {
            return layer0;
        }

        File particle = findModelTexture(files, ":particle:");

        if (particle != null) {
            return particle;
        }

        for (Map.Entry<String, List<File>> entry : files.entrySet()) {
            if (entry.getKey().startsWith("textures:model_texture:")) {
                File png = firstPng(entry.getValue());

                if (png != null) {
                    return png;
                }
            }
        }

        return null;
    }

    private File findModelTexture(
            Map<String, List<File>> files,
            String textureKey
    ) {
        for (Map.Entry<String, List<File>> entry : files.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);

            if (key.startsWith("textures:model_texture:")
                    && key.contains(textureKey)) {
                File png = firstPng(entry.getValue());

                if (png != null) {
                    return png;
                }
            }
        }

        return null;
    }

    private File firstPng(List<File> files) {
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file != null
                    && file.isFile()
                    && file.getName().toLowerCase(Locale.ROOT)
                    .endsWith(".png")) {
                return file;
            }
        }

        return null;
    }

    private BufferedImage cropUv(
            BufferedImage source,
            ItemsAdderModelParser.FaceData face
    ) {
        double minU = Math.min(face.getU1(), face.getU2());
        double maxU = Math.max(face.getU1(), face.getU2());
        double minV = Math.min(face.getV1(), face.getV2());
        double maxV = Math.max(face.getV1(), face.getV2());

        int x1 = (int) Math.floor(
                minU / 16.0 * source.getWidth()
        );

        int y1 = (int) Math.floor(
                minV / 16.0 * source.getHeight()
        );

        int x2 = (int) Math.ceil(
                maxU / 16.0 * source.getWidth()
        );

        int y2 = (int) Math.ceil(
                maxV / 16.0 * source.getHeight()
        );

        x1 = Math.max(0, Math.min(source.getWidth() - 1, x1));
        y1 = Math.max(0, Math.min(source.getHeight() - 1, y1));
        x2 = Math.max(x1 + 1, Math.min(source.getWidth(), x2));
        y2 = Math.max(y1 + 1, Math.min(source.getHeight(), y2));

        if (x2 <= x1 || y2 <= y1) {
            return null;
        }

        return source.getSubimage(
                x1,
                y1,
                x2 - x1,
                y2 - y1
        );
    }

    private BufferedImage rotateTexture(
            BufferedImage source,
            int rotation
    ) {
        int normalized = Math.floorMod(rotation, 360);

        if (normalized == 0
                || (normalized != 90
                && normalized != 180
                && normalized != 270)) {
            return copyImage(source);
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        int targetWidth = normalized == 180
                ? sourceWidth
                : sourceHeight;

        int targetHeight = normalized == 180
                ? sourceHeight
                : sourceWidth;

        BufferedImage target = new BufferedImage(
                targetWidth,
                targetHeight,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = target.createGraphics();

        try {
            if (normalized == 90) {
                graphics.translate(targetWidth, 0);
                graphics.rotate(Math.toRadians(90));
            } else if (normalized == 180) {
                graphics.translate(targetWidth, targetHeight);
                graphics.rotate(Math.toRadians(180));
            } else {
                graphics.translate(0, targetHeight);
                graphics.rotate(Math.toRadians(270));
            }

            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        return target;
    }

    private BufferedImage copyImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = copy.createGraphics();

        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        return copy;
    }

    private JsonObject findMatchingDefinition(
            JsonArray definitions,
            Object assetCmdValue
    ) {
        BigDecimal assetCmd = toBigDecimal(assetCmdValue);

        if (assetCmd == null) {
            return null;
        }

        for (JsonElement element : definitions) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject definition = element.getAsJsonObject();

            if (!definition.has("custom_model_data")
                    || definition.get("custom_model_data").isJsonNull()) {
                continue;
            }

            if (definition.has("type")
                    && !definition.get("type").getAsString()
                    .equalsIgnoreCase("legacy")) {
                continue;
            }

            BigDecimal mappingCmd = toBigDecimal(
                    definition.get("custom_model_data")
            );

            if (mappingCmd != null
                    && mappingCmd.compareTo(assetCmd) == 0) {
                return definition;
            }
        }

        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(JsonElement value) {
        if (value == null || value.isJsonNull()
                || !value.isJsonPrimitive()) {
            return null;
        }

        try {
            return new BigDecimal(value.getAsString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ItemsAdderAssetResolver.ResolvedAsset findResolvedAsset(
            ItemsAdderAssetResolver.ResolutionResult resolution,
            ItemsAdderConfigParser.AssetInfo asset
    ) {
        for (ItemsAdderAssetResolver.ResolvedAsset candidate
                : resolution.getAssets()) {

            ItemsAdderConfigParser.AssetInfo parsed =
                    candidate.getAsset();

            if (parsed == asset) {
                return candidate;
            }

            if (parsed.getNamespace().equalsIgnoreCase(asset.getNamespace())
                    && parsed.getId().equalsIgnoreCase(asset.getId())
                    && parsed.getType() == asset.getType()) {
                return candidate;
            }
        }

        return null;
    }

    private String getPropertyName(String key) {
        int separator = key.indexOf(':');

        return (separator >= 0
                ? key.substring(0, separator)
                : key).toLowerCase(Locale.ROOT);
    }

    private boolean isTextureProperty(String property) {
        return property.equals("texture")
                || property.equals("textures")
                || property.equals("icon")
                || property.endsWith(".texture")
                || property.endsWith(".textures")
                || property.endsWith(".icon")
                || property.contains("texture_path")
                || property.contains("texture_file");
    }

    private String getJsonString(JsonObject object, String key) {
        if (object == null || !object.has(key)) {
            return null;
        }

        JsonElement value = object.get(key);

        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            return null;
        }

        return value.getAsString().trim();
    }

    private boolean getBoolean(JsonObject object, String key) {
        if (object == null || !object.has(key)) {
            return false;
        }

        try {
            return object.get(key).getAsBoolean();
        } catch (Exception exception) {
            return false;
        }
    }

    private String normalizeFileIdentity(File file) {
        try {
            return file.toPath().toRealPath()
                    .toString().toLowerCase(Locale.ROOT);
        } catch (IOException exception) {
            return file.getAbsolutePath()
                    .toLowerCase(Locale.ROOT);
        }
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

        writeJson(new File(directory, "manifest.json"), manifest);
    }

    private JsonArray versionArray(
            int major,
            int minor,
            int patch
    ) {
        JsonArray array = new JsonArray();
        array.add(major);
        array.add(minor);
        array.add(patch);
        return array;
    }

    private void writeJson(
            File file,
            JsonObject json
    ) throws IOException {

        File parent = file.getParentFile();

        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }

        Files.writeString(
                file.toPath(),
                GSON.toJson(json),
                StandardCharsets.UTF_8
        );
    }

    private void createArchive(
            File source,
            File destination
    ) throws IOException {

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

    private record ModelBuildData(
            ItemsAdderModelParser.ModelData model,
            boolean inherited
    ) {
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

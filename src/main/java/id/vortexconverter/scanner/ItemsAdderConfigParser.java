
package id.vortexconverter.scanner;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemsAdderConfigParser {

    public enum AssetType {
        ITEM,
        BLOCK,
        FURNITURE,
        ENTITY,
        OTHER
    }

    public static final class AssetInfo {

        private final String namespace;
        private final String id;
        private final AssetType type;
        private final String sourceFile;
        private final Map<String, String> properties;

        public AssetInfo(
                String namespace,
                String id,
                AssetType type,
                String sourceFile,
                Map<String, String> properties
        ) {
            this.namespace = namespace;
            this.id = id;
            this.type = type;
            this.sourceFile = sourceFile;
            this.properties = new LinkedHashMap<>(properties);
        }

        public String getNamespace() {
            return namespace;
        }

        public String getId() {
            return id;
        }

        public AssetType getType() {
            return type;
        }

        public String getSourceFile() {
            return sourceFile;
        }

        public Map<String, String> getProperties() {
            return Collections.unmodifiableMap(properties);
        }

        public String getProperty(String key) {
            if (key == null) {
                return null;
            }

            String direct = properties.get(key);

            if (direct != null) {
                return direct;
            }

            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }

            return null;
        }

        public String getMaterial() {
            return firstProperty(
                    "resource.material",
                    "material",
                    "item.material",
                    "specific_properties.material"
            );
        }

        public String getDisplayName() {
            return firstProperty(
                    "display_name",
                    "name",
                    "item.display_name"
            );
        }

        public String getPermission() {
            return firstProperty(
                    "permission",
                    "permissions",
                    "behaviours.permission"
            );
        }

        public String getModelPath() {
            return firstProperty(
                    "resource.model_path",
                    "resource.model",
                    "model_path",
                    "model",
                    "geometry"
            );
        }

        public String getTexturePath() {
            return firstProperty(
                    "resource.texture",
                    "resource.textures",
                    "textures",
                    "texture"
            );
        }

        public String getCustomModelDataValue() {
            return firstProperty(
                    "custom_model_data",
                    "resource.custom_model_data",
                    "model_id",
                    "resource.model_id",
                    "custom_model_data.value"
            );
        }

        public Integer getCustomModelData() {
            String value = getCustomModelDataValue();

            if (value == null || value.isBlank()) {
                return null;
            }

            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        public boolean hasCustomModelData() {
            return getCustomModelData() != null;
        }

        public String getJavaIdentifier() {
            String material = getMaterial();

            if (material == null || material.isBlank()) {
                return null;
            }

            String normalized = material
                    .trim()
                    .toLowerCase(Locale.ROOT);

            if (normalized.contains(":")) {
                return normalized;
            }

            return "minecraft:" + normalized;
        }

        public String getExplicitBedrockIdentifier() {
            return firstProperty(
                    "bedrock_identifier",
                    "bedrock.identifier",
                    "geyser.identifier",
                    "geyser.bedrock_identifier"
            );
        }

        public String getNamespacedId() {
            return namespace + ":" + id;
        }

        private String firstProperty(String... keys) {
            for (String key : keys) {
                String value = getProperty(key);

                if (value != null && !value.isBlank()) {
                    return value;
                }
            }

            return null;
        }
    }

    public static final class ParseResult {

        private final List<AssetInfo> assets = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private int filesRead;

        public List<AssetInfo> getAssets() {
            return Collections.unmodifiableList(assets);
        }

        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        public int getFilesRead() {
            return filesRead;
        }

        public int getTotalAssets() {
            return assets.size();
        }

        public int count(AssetType type) {
            return (int) assets.stream()
                    .filter(asset -> asset.getType() == type)
                    .count();
        }

        public int countCustomModelData() {
            return (int) assets.stream()
                    .filter(AssetInfo::hasCustomModelData)
                    .count();
        }

        public int countWithMaterial() {
            return (int) assets.stream()
                    .filter(asset -> asset.getMaterial() != null)
                    .count();
        }

        public int countWithTexture() {
            return (int) assets.stream()
                    .filter(asset -> asset.getTexturePath() != null)
                    .count();
        }

        public int countWithModel() {
            return (int) assets.stream()
                    .filter(asset -> asset.getModelPath() != null)
                    .count();
        }

        public int countWithBedrockIdentifier() {
            return (int) assets.stream()
                    .filter(asset ->
                            asset.getExplicitBedrockIdentifier() != null
                    )
                    .count();
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    public ParseResult parse(File contentsFolder) {
        ParseResult result = new ParseResult();

        if (contentsFolder == null
                || !contentsFolder.exists()
                || !contentsFolder.isDirectory()) {
            result.errors.add(
                    "Invalid ItemsAdder contents directory."
            );
            return result;
        }

        File[] namespaces = contentsFolder.listFiles(File::isDirectory);

        if (namespaces == null) {
            result.errors.add(
                    "Cannot read ItemsAdder namespaces."
            );
            return result;
        }

        for (File namespaceFolder : namespaces) {
            File configFolder = new File(namespaceFolder, "configs");

            if (!configFolder.isDirectory()) {
                continue;
            }

            scanConfigDirectory(
                    configFolder,
                    namespaceFolder.getName(),
                    result
            );
        }

        return result;
    }

    private void scanConfigDirectory(
            File folder,
            String namespace,
            ParseResult result
    ) {
        File[] files = folder.listFiles();

        if (files == null) {
            result.errors.add(
                    "Cannot access directory: " + folder.getPath()
            );
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanConfigDirectory(file, namespace, result);
                continue;
            }

            String name = file.getName().toLowerCase(Locale.ROOT);

            if (!name.endsWith(".yml")
                    && !name.endsWith(".yaml")) {
                continue;
            }

            parseFile(file, namespace, result);
        }
    }

    private void parseFile(
            File file,
            String namespace,
            ParseResult result
    ) {
        YamlConfiguration yaml = new YamlConfiguration();

        try {
            yaml.load(file);
            result.filesRead++;

            ConfigurationSection root = yaml;

            for (String key : root.getKeys(false)) {
                ConfigurationSection section =
                        root.getConfigurationSection(key);

                if (section == null) {
                    continue;
                }

                AssetType type = identifyType(key);

                if (type == AssetType.OTHER) {
                    continue;
                }

                for (String id : section.getKeys(false)) {
                    ConfigurationSection assetSection =
                            section.getConfigurationSection(id);

                    if (assetSection == null) {
                        continue;
                    }

                    Map<String, String> properties =
                            new LinkedHashMap<>();

                    collectProperties(
                            assetSection,
                            "",
                            properties
                    );

                    result.assets.add(
                            new AssetInfo(
                                    namespace,
                                    id,
                                    type,
                                    file.getPath(),
                                    properties
                            )
                    );
                }
            }

        } catch (Exception exception) {
            result.errors.add(
                    file.getPath() + ": " + exception.getMessage()
            );
        }
    }

    private AssetType identifyType(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "items", "custom_items" -> AssetType.ITEM;

            case "blocks", "custom_blocks" -> AssetType.BLOCK;

            case "furniture" -> AssetType.FURNITURE;

            case "entities", "custom_entities" -> AssetType.ENTITY;

            default -> AssetType.OTHER;
        };
    }

    private void collectProperties(
            ConfigurationSection section,
            String prefix,
            Map<String, String> properties
    ) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);

            String path = prefix.isEmpty()
                    ? key
                    : prefix + "." + key;

            if (value instanceof ConfigurationSection child) {
                collectProperties(child, path, properties);
                continue;
            }

            if (value == null) {
                continue;
            }

            if (value instanceof List<?> list) {
                List<String> values = new ArrayList<>();

                for (Object element : list) {
                    if (element == null) {
                        continue;
                    }

                    if (element instanceof Map<?, ?> map) {
                        values.add(map.toString());
                    } else {
                        values.add(String.valueOf(element));
                    }
                }

                properties.put(
                        path,
                        String.join(", ", values)
                );

            } else if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() == null
                            || entry.getValue() == null) {
                        continue;
                    }

                    String nestedPath = path + "."
                            + entry.getKey();

                    properties.put(
                            nestedPath,
                            String.valueOf(entry.getValue())
                    );
                }

            } else {
                properties.put(
                        path,
                        String.valueOf(value)
                );
            }
        }
    }
}


package id.vortexconverter.mappings;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import id.vortexconverter.scanner.ItemsAdderConfigParser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ItemsAdderCustomMappingGenerator {

    public MappingResult generate(
            ItemsAdderConfigParser.ParseResult parseResult
    ) {
        JsonObject mapping = new JsonObject();
        JsonObject items = new JsonObject();

        mapping.addProperty("format_version", 2);
        mapping.add("items", items);

        List<String> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Set<String> registeredMappings = new HashSet<>();
        Set<String> registeredBedrockIdentifiers = new HashSet<>();

        int discoveredItems = 0;
        int generatedMappings = 0;

        if (parseResult == null) {
            warnings.add("ParseResult tidak tersedia.");

            return new MappingResult(
                    mapping,
                    0,
                    0,
                    skipped,
                    warnings,
                    null
            );
        }

        for (ItemsAdderConfigParser.AssetInfo asset
                : parseResult.getAssets()) {

            if (asset.getType()
                    != ItemsAdderConfigParser.AssetType.ITEM) {
                continue;
            }

            discoveredItems++;

            String javaIdentifier = normalizeJavaIdentifier(
                    asset.getJavaIdentifier()
            );

            if (javaIdentifier == null) {
                skipped.add(asset.getNamespacedId()
                        + ": Java item identifier tidak valid atau tidak ditemukan.");
                continue;
            }

            String customModelDataText =
                    asset.getCustomModelDataValue();

            if (customModelDataText == null
                    || customModelDataText.isBlank()) {
                skipped.add(asset.getNamespacedId()
                        + ": Custom Model Data tidak ditemukan.");
                continue;
            }

            float customModelData;

            try {
                customModelData = Float.parseFloat(
                        customModelDataText.trim()
                );
            } catch (NumberFormatException exception) {
                skipped.add(asset.getNamespacedId()
                        + ": Custom Model Data tidak valid: "
                        + customModelDataText);
                continue;
            }

            if (!Float.isFinite(customModelData)
                    || customModelData < 0) {
                skipped.add(asset.getNamespacedId()
                        + ": Custom Model Data harus berupa angka "
                        + "positif atau nol.");
                continue;
            }

            String bedrockIdentifier =
                    asset.getExplicitBedrockIdentifier();

            if (bedrockIdentifier == null
                    || bedrockIdentifier.isBlank()) {
                bedrockIdentifier =
                        "vortexconverter:"
                                + normalizeIdentifierPart(asset.getNamespace())
                                + "_"
                                + normalizeIdentifierPart(asset.getId());
            }

            bedrockIdentifier = normalizeBedrockIdentifier(
                    bedrockIdentifier
            );

            if (!isValidBedrockIdentifier(bedrockIdentifier)) {
                skipped.add(asset.getNamespacedId()
                        + ": Bedrock identifier tidak valid: "
                        + bedrockIdentifier);
                continue;
            }

            if (bedrockIdentifier.startsWith("minecraft:")) {
                skipped.add(asset.getNamespacedId()
                        + ": Namespace minecraft tidak diperbolehkan "
                        + "untuk custom Bedrock identifier.");
                continue;
            }

            String mappingKey = javaIdentifier
                    + "#"
                    + Float.toString(customModelData);

            if (registeredMappings.contains(mappingKey)) {
                skipped.add(asset.getNamespacedId()
                        + ": Java item dan Custom Model Data duplikat: "
                        + mappingKey);
                continue;
            }

            if (registeredBedrockIdentifiers.contains(
                    bedrockIdentifier)) {
                skipped.add(asset.getNamespacedId()
                        + ": Bedrock identifier sudah digunakan: "
                        + bedrockIdentifier);
                continue;
            }

            JsonObject definition = new JsonObject();

            definition.addProperty("type", "legacy");
            definition.addProperty(
                    "custom_model_data",
                    customModelData
            );
            definition.addProperty(
                    "bedrock_identifier",
                    bedrockIdentifier
            );

            String displayName = asset.getDisplayName();

            if (displayName != null && !displayName.isBlank()) {
                definition.addProperty(
                        "display_name",
                        displayName
                );
            }

            JsonArray definitions;

            if (items.has(javaIdentifier)) {
                definitions = items.getAsJsonArray(javaIdentifier);
            } else {
                definitions = new JsonArray();
                items.add(javaIdentifier, definitions);
            }

            definitions.add(definition);

            registeredMappings.add(mappingKey);
            registeredBedrockIdentifiers.add(bedrockIdentifier);

            generatedMappings++;
        }

        if (generatedMappings == 0) {
            warnings.add(
                    "Tidak ada mapping yang berhasil dibuat. "
                            + "Periksa material dan Custom Model Data "
                            + "pada konfigurasi ItemsAdder."
            );
        }

        warnings.add(
                "Generator menggunakan Geyser Custom Items format v2 "
                        + "dengan legacy Custom Model Data."
        );

        warnings.add(
                "Setiap pasangan Java item dan Custom Model Data "
                        + "hanya didaftarkan satu kali."
        );

        return new MappingResult(
                mapping,
                discoveredItems,
                generatedMappings,
                skipped,
                warnings,
                null
        );
    }

    private String normalizeJavaIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }

        String normalized = identifier
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }

        String[] parts = normalized.split(":", -1);

        if (parts.length != 2
                || !isValidIdentifierPart(parts[0])
                || !isValidIdentifierPart(parts[1])) {
            return null;
        }

        return normalized;
    }

    private String normalizeBedrockIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }

        String normalized = identifier
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!normalized.contains(":")) {
            normalized = "vortexconverter:" + normalized;
        }

        return normalized;
    }

    private String normalizeIdentifierPart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
    }

    private boolean isValidBedrockIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }

        String[] parts = identifier.split(":", -1);

        return parts.length == 2
                && isValidIdentifierPart(parts[0])
                && isValidIdentifierPart(parts[1]);
    }

    private boolean isValidIdentifierPart(String value) {
        return value != null
                && !value.isBlank()
                && value.matches("[a-z0-9_.-]+");
    }

    public record MappingResult(
            JsonObject mapping,
            int discoveredItems,
            int generatedMappings,
            List<String> skipped,
            List<String> warnings,
            File outputFile
    ) {

        public MappingResult(
                JsonObject mapping,
                int discoveredItems,
                int generatedMappings,
                List<String> skipped,
                List<String> warnings
        ) {
            this(
                    mapping,
                    discoveredItems,
                    generatedMappings,
                    skipped,
                    warnings,
                    null
            );
        }

        public int skippedCount() {
            return skipped == null ? 0 : skipped.size();
        }

        public int warningCount() {
            return warnings == null ? 0 : warnings.size();
        }

        public boolean hasMappings() {
            return generatedMappings > 0
                    && mapping != null
                    && mapping.has("items")
                    && mapping.get("items").isJsonObject()
                    && mapping.getAsJsonObject("items").size() > 0;
        }

        public boolean isSuccessful() {
            return hasMappings();
        }

        public MappingResult withOutputFile(File file) {
            return new MappingResult(
                    mapping,
                    discoveredItems,
                    generatedMappings,
                    skipped,
                    warnings,
                    file
            );
        }
    }
}

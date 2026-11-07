
package id.vortexconverter.mappings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ItemsAdderMappingValidator {

    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private static final Pattern ICON_PATTERN =
            Pattern.compile("[a-z0-9_.-]+");

    public ValidationResult validate(
            ItemsAdderCustomMappingGenerator.MappingResult result
    ) {
        if (result == null) {
            return new ValidationResult(
                    false,
                    0,
                    List.of("MappingResult tidak tersedia."),
                    new ArrayList<>()
            );
        }

        return validate(result.mapping());
    }

    public ValidationResult validate(JsonObject mapping) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (mapping == null) {
            errors.add("Mapping JSON tidak tersedia.");

            return new ValidationResult(
                    false,
                    0,
                    errors,
                    warnings
            );
        }

        JsonElement formatVersion = mapping.get("format_version");

        if (formatVersion == null
                || !formatVersion.isJsonPrimitive()
                || !formatVersion.getAsJsonPrimitive().isNumber()) {

            errors.add("format_version harus berupa angka.");

        } else {
            try {
                BigDecimal version = new BigDecimal(
                        formatVersion.getAsString()
                );

                if (version.compareTo(BigDecimal.valueOf(2)) != 0) {
                    errors.add("format_version harus bernilai 2.");
                }

            } catch (NumberFormatException exception) {
                errors.add("format_version tidak valid.");
            }
        }

        JsonElement itemsElement = mapping.get("items");

        if (itemsElement == null || !itemsElement.isJsonObject()) {
            errors.add("Bagian items harus berupa JSON object.");

            return new ValidationResult(
                    false,
                    0,
                    errors,
                    warnings
            );
        }

        JsonObject items = itemsElement.getAsJsonObject();

        if (items.size() == 0) {
            errors.add("Tidak ada item mapping yang ditemukan.");

            return new ValidationResult(
                    false,
                    0,
                    errors,
                    warnings
            );
        }

        int validDefinitions = 0;

        Set<String> registeredMappings = new HashSet<>();
        Set<String> registeredBedrockIdentifiers = new HashSet<>();

        for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
            String javaIdentifier = entry.getKey();
            JsonElement definitionsElement = entry.getValue();

            if (!isValidIdentifier(javaIdentifier)) {
                errors.add(
                        "Java item identifier tidak valid: "
                                + javaIdentifier
                );
                continue;
            }

            if (definitionsElement == null
                    || !definitionsElement.isJsonArray()) {
                errors.add(
                        javaIdentifier
                                + ": definitions harus berupa JSON array."
                );
                continue;
            }

            JsonArray definitions = definitionsElement.getAsJsonArray();

            if (definitions.isEmpty()) {
                errors.add(
                        javaIdentifier
                                + ": array definitions kosong."
                );
                continue;
            }

            for (int i = 0; i < definitions.size(); i++) {
                JsonElement definitionElement = definitions.get(i);

                if (definitionElement == null
                        || !definitionElement.isJsonObject()) {
                    errors.add(
                            javaIdentifier
                                    + ": definition indeks "
                                    + i
                                    + " bukan JSON object."
                    );
                    continue;
                }

                JsonObject definition = definitionElement.getAsJsonObject();

                if (validateLegacyDefinition(
                        javaIdentifier,
                        i,
                        definition,
                        registeredMappings,
                        registeredBedrockIdentifiers,
                        errors,
                        warnings
                )) {
                    validDefinitions++;
                }
            }
        }

        boolean successful = errors.isEmpty()
                && validDefinitions > 0;

        return new ValidationResult(
                successful,
                validDefinitions,
                errors,
                warnings
        );
    }

    private boolean validateLegacyDefinition(
            String javaIdentifier,
            int index,
            JsonObject definition,
            Set<String> registeredMappings,
            Set<String> registeredBedrockIdentifiers,
            List<String> errors,
            List<String> warnings
    ) {
        String prefix = javaIdentifier + " [" + index + "]";

        JsonElement typeElement = definition.get("type");

        if (typeElement == null
                || !typeElement.isJsonPrimitive()
                || !typeElement.getAsJsonPrimitive().isString()) {
            errors.add(prefix + ": type wajib berupa string.");
            return false;
        }

        String type = typeElement.getAsString();

        if (!"legacy".equals(type)) {
            errors.add(
                    prefix
                            + ": hanya type legacy yang didukung generator ini."
            );
            return false;
        }

        JsonElement customModelDataElement =
                definition.get("custom_model_data");

        if (customModelDataElement == null
                || !customModelDataElement.isJsonPrimitive()
                || !customModelDataElement.getAsJsonPrimitive().isNumber()) {
            errors.add(
                    prefix
                            + ": custom_model_data wajib berupa angka."
            );
            return false;
        }

        BigDecimal customModelData;

        try {
            customModelData = new BigDecimal(
                    customModelDataElement.getAsString()
            );
        } catch (NumberFormatException exception) {
            errors.add(prefix + ": custom_model_data tidak valid.");
            return false;
        }

        if (!Double.isFinite(customModelData.doubleValue())
                || customModelData.signum() < 0) {
            errors.add(
                    prefix
                            + ": custom_model_data harus angka positif atau nol."
            );
            return false;
        }

        float normalizedCmd = customModelData.floatValue();

        if (!Float.isFinite(normalizedCmd) || normalizedCmd < 0) {
            errors.add(
                    prefix
                            + ": custom_model_data berada di luar batas float."
            );
            return false;
        }

        String mappingKey = javaIdentifier
                + "#"
                + Float.toString(normalizedCmd);

        if (registeredMappings.contains(mappingKey)) {
            errors.add(
                    prefix
                            + ": pasangan Java item dan CMD duplikat: "
                            + mappingKey
            );
            return false;
        }

        JsonElement bedrockIdentifierElement =
                definition.get("bedrock_identifier");

        if (bedrockIdentifierElement == null
                || !bedrockIdentifierElement.isJsonPrimitive()
                || !bedrockIdentifierElement.getAsJsonPrimitive().isString()) {
            errors.add(
                    prefix
                            + ": bedrock_identifier wajib berupa string."
            );
            return false;
        }

        String bedrockIdentifier =
                bedrockIdentifierElement.getAsString();

        if (!isValidIdentifier(bedrockIdentifier)) {
            errors.add(
                    prefix
                            + ": bedrock_identifier tidak valid: "
                            + bedrockIdentifier
            );
            return false;
        }

        if (bedrockIdentifier.startsWith("minecraft:")) {
            errors.add(
                    prefix
                            + ": bedrock_identifier tidak boleh memakai "
                            + "namespace minecraft."
            );
            return false;
        }

        if (registeredBedrockIdentifiers.contains(bedrockIdentifier)) {
            errors.add(
                    prefix
                            + ": bedrock_identifier duplikat: "
                            + bedrockIdentifier
            );
            return false;
        }

        JsonElement bedrockOptionsElement =
                definition.get("bedrock_options");

        if (bedrockOptionsElement == null
                || !bedrockOptionsElement.isJsonObject()) {
            errors.add(
                    prefix
                            + ": bedrock_options wajib berupa JSON object."
            );
            return false;
        }

        JsonObject bedrockOptions =
                bedrockOptionsElement.getAsJsonObject();

        JsonElement iconElement = bedrockOptions.get("icon");

        if (iconElement == null
                || !iconElement.isJsonPrimitive()
                || !iconElement.getAsJsonPrimitive().isString()) {
            errors.add(
                    prefix
                            + ": bedrock_options.icon wajib berupa string."
            );
            return false;
        }

        String icon = iconElement.getAsString();

        if (!ICON_PATTERN.matcher(icon).matches()) {
            errors.add(
                    prefix
                            + ": format bedrock_options.icon tidak valid: "
                            + icon
            );
            return false;
        }

        String expectedIcon = toIconKey(bedrockIdentifier);

        if (!icon.equals(expectedIcon)) {
            errors.add(
                    prefix
                            + ": icon tidak cocok. Seharusnya "
                            + expectedIcon
                            + ", tetapi ditemukan "
                            + icon + "."
            );
            return false;
        }

        JsonElement displayName = definition.get("display_name");

        if (displayName != null
                && (!displayName.isJsonPrimitive()
                || !displayName.getAsJsonPrimitive().isString())) {
            errors.add(
                    prefix
                            + ": display_name harus berupa string."
            );
            return false;
        }

        // Registrasi dilakukan setelah seluruh validasi berhasil.
        registeredMappings.add(mappingKey);
        registeredBedrockIdentifiers.add(bedrockIdentifier);

        warnings.add(
                prefix
                        + ": pastikan texture atlas menyediakan key "
                        + expectedIcon + "."
        );

        return true;
    }

    private String toIconKey(String identifier) {
        return identifier
                .replace(':', '.')
                .replace('/', '_')
                .replace('\\', '_')
                .replaceAll("[^a-zA-Z0-9_.-]", "_")
                .toLowerCase(Locale.ROOT);
    }

    private boolean isValidIdentifier(String identifier) {
        return identifier != null
                && IDENTIFIER_PATTERN.matcher(identifier).matches();
    }

    public record ValidationResult(
            boolean successful,
            int validDefinitions,
            List<String> errors,
            List<String> warnings
    ) {
        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }

        public boolean isSuccessful() {
            return successful && !hasErrors();
        }
    }
}

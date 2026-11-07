
package id.vortexconverter.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ItemsAdderModelParser {

    private static final List<String> FACE_DIRECTIONS = List.of(
            "north",
            "south",
            "east",
            "west",
            "up",
            "down"
    );

    public ModelData parse(File modelFile) throws IOException {
        if (modelFile == null || !modelFile.isFile()) {
            throw new IOException("Model JSON tidak ditemukan.");
        }

        JsonElement rootElement;

        try {
            rootElement = JsonParser.parseString(
                    Files.readString(modelFile.toPath())
            );
        } catch (Exception exception) {
            throw new IOException(
                    "Gagal membaca model JSON: " + modelFile.getName(),
                    exception
            );
        }

        if (!rootElement.isJsonObject()) {
            throw new IOException(
                    "Root model harus berupa JSON object."
            );
        }

        JsonObject root = rootElement.getAsJsonObject();

        String parent = getString(root, "parent");

        Map<String, String> textures = parseTextures(root);
        List<ElementData> elements = parseElements(root);

        List<FaceData> faces = new ArrayList<>();

        for (ElementData element : elements) {
            faces.addAll(element.faces());
        }

        return new ModelData(
                modelFile,
                parent,
                textures,
                elements,
                faces,
                root.deepCopy()
        );
    }

    private Map<String, String> parseTextures(JsonObject root) {
        Map<String, String> textures = new LinkedHashMap<>();

        if (!root.has("textures")
                || !root.get("textures").isJsonObject()) {
            return textures;
        }

        JsonObject textureObject = root.getAsJsonObject("textures");

        for (Map.Entry<String, JsonElement> entry
                : textureObject.entrySet()) {

            JsonElement value = entry.getValue();

            if (!value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isString()) {
                continue;
            }

            String reference = value.getAsString().trim();

            if (!reference.isBlank()) {
                textures.put(entry.getKey(), reference);
            }
        }

        return textures;
    }

    private List<ElementData> parseElements(JsonObject root) {
        List<ElementData> elements = new ArrayList<>();

        if (!root.has("elements")
                || !root.get("elements").isJsonArray()) {
            return elements;
        }

        JsonArray array = root.getAsJsonArray("elements");

        for (int index = 0; index < array.size(); index++) {
            JsonElement element = array.get(index);

            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();

            List<Double> from = readVector(object.get("from"));
            List<Double> to = readVector(object.get("to"));

            if (from.size() != 3 || to.size() != 3) {
                continue;
            }

            List<FaceData> faces = parseFaces(
                    object,
                    index
            );

            ElementRotation rotation = parseElementRotation(object);

            elements.add(
                    new ElementData(
                            index,
                            from,
                            to,
                            rotation,
                            getBoolean(object, "shade", true),
                            faces
                    )
            );
        }

        return elements;
    }

    private List<FaceData> parseFaces(
            JsonObject element,
            int elementIndex
    ) {
        List<FaceData> faces = new ArrayList<>();

        if (!element.has("faces")
                || !element.get("faces").isJsonObject()) {
            return faces;
        }

        JsonObject faceObject = element.getAsJsonObject("faces");

        for (String direction : FACE_DIRECTIONS) {
            if (!faceObject.has(direction)
                    || !faceObject.get(direction).isJsonObject()) {
                continue;
            }

            JsonObject face = faceObject.getAsJsonObject(direction);

            String texture = getString(face, "texture");

            if (texture == null || texture.isBlank()) {
                continue;
            }

            List<Double> uv = readVector(face.get("uv"));

            boolean hasExplicitUv = uv.size() == 4;

            if (!hasExplicitUv) {
                uv = Collections.emptyList();
            }

            int rotation = getInteger(face, "rotation", 0);

            if (rotation != 0
                    && rotation != 90
                    && rotation != 180
                    && rotation != 270) {
                rotation = 0;
            }

            String cullface = getString(face, "cullface");
            int tintIndex = getInteger(face, "tintindex", -1);

            faces.add(
                    new FaceData(
                            elementIndex,
                            direction,
                            texture,
                            uv,
                            hasExplicitUv,
                            rotation,
                            cullface,
                            tintIndex
                    )
            );
        }

        return faces;
    }

    private ElementRotation parseElementRotation(JsonObject element) {
        if (!element.has("rotation")
                || !element.get("rotation").isJsonObject()) {
            return null;
        }

        JsonObject rotation = element.getAsJsonObject("rotation");

        List<Double> origin = readVector(rotation.get("origin"));

        if (origin.size() != 3) {
            return null;
        }

        String axis = getString(rotation, "axis");

        if (axis == null
                || !(axis.equals("x")
                || axis.equals("y")
                || axis.equals("z"))) {
            return null;
        }

        double angle = getNumber(rotation, "angle", 0.0);
        boolean rescale = getBoolean(rotation, "rescale", false);

        return new ElementRotation(
                origin,
                axis,
                angle,
                rescale
        );
    }

    public String resolveTexture(
            String reference,
            Map<String, String> textures
    ) {
        if (reference == null || textures == null) {
            return null;
        }

        String current = reference.trim();
        Set<String> visited = new LinkedHashSet<>();

        while (current.startsWith("#")) {
            String key = current.substring(1).trim();

            if (key.isBlank()
                    || !visited.add(key)
                    || !textures.containsKey(key)) {
                return null;
            }

            current = textures.get(key);

            if (current == null) {
                return null;
            }

            current = current.trim();
        }

        return current.isBlank() ? null : current;
    }

    private List<Double> readVector(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return Collections.emptyList();
        }

        JsonArray array = element.getAsJsonArray();
        List<Double> values = new ArrayList<>();

        for (JsonElement value : array) {
            if (!value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()) {
                return Collections.emptyList();
            }

            try {
                double number = value.getAsDouble();

                if (!Double.isFinite(number)) {
                    return Collections.emptyList();
                }

                values.add(number);
            } catch (Exception exception) {
                return Collections.emptyList();
            }
        }

        return values;
    }

    private String getString(JsonObject object, String key) {
        if (object == null || !object.has(key)) {
            return null;
        }

        JsonElement element = object.get(key);

        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            return null;
        }

        return element.getAsString();
    }

    private boolean getBoolean(
            JsonObject object,
            String key,
            boolean defaultValue
    ) {
        if (object == null || !object.has(key)) {
            return defaultValue;
        }

        JsonElement element = object.get(key);

        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()) {
            return defaultValue;
        }

        return element.getAsBoolean();
    }

    private int getInteger(
            JsonObject object,
            String key,
            int defaultValue
    ) {
        if (object == null || !object.has(key)) {
            return defaultValue;
        }

        try {
            return object.get(key).getAsInt();
        } catch (Exception exception) {
            return defaultValue;
        }
    }

    private double getNumber(
            JsonObject object,
            String key,
            double defaultValue
    ) {
        if (object == null || !object.has(key)) {
            return defaultValue;
        }

        try {
            double value = object.get(key).getAsDouble();

            return Double.isFinite(value)
                    ? value
                    : defaultValue;
        } catch (Exception exception) {
            return defaultValue;
        }
    }

    public record ModelData(
            File source,
            String parent,
            Map<String, String> textures,
            List<ElementData> elements,
            List<FaceData> faces,
            JsonObject rawJson
    ) {
        public ModelData {
            textures = Collections.unmodifiableMap(
                    new LinkedHashMap<>(textures)
            );

            elements = Collections.unmodifiableList(
                    new ArrayList<>(elements)
            );

            faces = Collections.unmodifiableList(
                    new ArrayList<>(faces)
            );

            rawJson = rawJson.deepCopy();
        }

        public boolean hasParent() {
            return parent != null && !parent.isBlank();
        }

        public boolean hasElements() {
            return !elements.isEmpty();
        }

        public int getFaceCount() {
            return faces.size();
        }

        public int getElementCount() {
            return elements.size();
        }
    }

    public record ElementData(
            int index,
            List<Double> from,
            List<Double> to,
            ElementRotation rotation,
            boolean shade,
            List<FaceData> faces
    ) {
        public ElementData {
            from = Collections.unmodifiableList(
                    new ArrayList<>(from)
            );

            to = Collections.unmodifiableList(
                    new ArrayList<>(to)
            );

            faces = Collections.unmodifiableList(
                    new ArrayList<>(faces)
            );
        }
    }

    public record FaceData(
            int elementIndex,
            String direction,
            String texture,
            List<Double> uv,
            boolean hasExplicitUv,
            int rotation,
            String cullface,
            int tintIndex
    ) {
        public FaceData {
            uv = Collections.unmodifiableList(
                    new ArrayList<>(uv)
            );
        }

        public boolean hasUv() {
            return uv.size() == 4;
        }

        public double getU1() {
            return hasUv() ? uv.get(0) : 0.0;
        }

        public double getV1() {
            return hasUv() ? uv.get(1) : 0.0;
        }

        public double getU2() {
            return hasUv() ? uv.get(2) : 16.0;
        }

        public double getV2() {
            return hasUv() ? uv.get(3) : 16.0;
        }
    }

    public record ElementRotation(
            List<Double> origin,
            String axis,
            double angle,
            boolean rescale
    ) {
        public ElementRotation {
            origin = Collections.unmodifiableList(
                    new ArrayList<>(origin)
            );
        }
    }
}

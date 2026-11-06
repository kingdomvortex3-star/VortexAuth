
package id.vortexconverter.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JavaModelReader {

    public record Vector3(double x, double y, double z) {}

    public record Face(
            String direction,
            String texture,
            int rotation,
            double[] uv
    ) {}

    public record Element(
            Vector3 from,
            Vector3 to,
            Vector3 rotation,
            Vector3 origin,
            List<Face> faces
    ) {}

    public record JavaModel(
            String parent,
            String ambientOcclusion,
            List<String> textures,
            List<Element> elements
    ) {}

    public JavaModel read(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Model file does not exist.");
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader)
                    .getAsJsonObject();

            return new JavaModel(
                    getString(root, "parent"),
                    getString(root, "ambientocclusion"),
                    readTextures(root),
                    readElements(root)
            );
        } catch (RuntimeException exception) {
            throw new IOException(
                    "Invalid Java model: " + exception.getMessage(),
                    exception
            );
        }
    }

    private List<String> readTextures(JsonObject root) {
        List<String> textures = new ArrayList<>();

        if (!root.has("textures")
                || !root.get("textures").isJsonObject()) {
            return textures;
        }

        for (Map.Entry<String, JsonElement> entry
                : root.getAsJsonObject("textures").entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                textures.add(
                        entry.getKey() + "="
                                + entry.getValue().getAsString()
                );
            }
        }

        return textures;
    }

    private List<Element> readElements(JsonObject root)
            throws IOException {

        List<Element> elements = new ArrayList<>();

        if (!root.has("elements")
                || !root.get("elements").isJsonArray()) {
            return elements;
        }

        for (JsonElement entry : root.getAsJsonArray("elements")) {
            JsonObject object = entry.getAsJsonObject();

            Vector3 from = readVector(object, "from");
            Vector3 to = readVector(object, "to");

            if (from == null || to == null) {
                throw new IOException("Element missing from/to.");
            }

            Vector3 rotation = new Vector3(0, 0, 0);
            Vector3 origin = new Vector3(0, 0, 0);

            if (object.has("rotation")
                    && object.get("rotation").isJsonObject()) {

                JsonObject rotationObject =
                        object.getAsJsonObject("rotation");

                double angle = rotationObject.has("angle")
                        ? rotationObject.get("angle").getAsDouble()
                        : 0;

                String axis = getString(rotationObject, "axis");

                rotation = switch (axis == null ? "" : axis) {
                    case "x" -> new Vector3(angle, 0, 0);
                    case "y" -> new Vector3(0, angle, 0);
                    case "z" -> new Vector3(0, 0, angle);
                    default -> new Vector3(0, 0, 0);
                };

                Vector3 pivot = readVector(rotationObject, "origin");

                if (pivot != null) {
                    origin = pivot;
                }
            }

            elements.add(new Element(
                    from,
                    to,
                    rotation,
                    origin,
                    readFaces(object)
            ));
        }

        return elements;
    }

    private List<Face> readFaces(JsonObject object) {
        List<Face> faces = new ArrayList<>();

        if (!object.has("faces")
                || !object.get("faces").isJsonObject()) {
            return faces;
        }

        JsonObject faceObject = object.getAsJsonObject("faces");

        for (Map.Entry<String, JsonElement> entry
                : faceObject.entrySet()) {

            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            JsonObject face = entry.getValue().getAsJsonObject();

            String texture = getString(face, "texture");

            int rotation = face.has("rotation")
                    ? face.get("rotation").getAsInt()
                    : 0;

            double[] uv = null;

            if (face.has("uv") && face.get("uv").isJsonArray()) {
                JsonArray array = face.getAsJsonArray("uv");

                if (array.size() == 4) {
                    uv = new double[4];

                    for (int i = 0; i < 4; i++) {
                        uv[i] = array.get(i).getAsDouble();
                    }
                }
            }

            faces.add(new Face(
                    entry.getKey(),
                    texture,
                    rotation,
                    uv
            ));
        }

        return faces;
    }

    private Vector3 readVector(JsonObject object, String key) {
        if (!object.has(key)
                || !object.get(key).isJsonArray()) {
            return null;
        }

        JsonArray array = object.getAsJsonArray(key);

        if (array.size() != 3) {
            return null;
        }

        return new Vector3(
                array.get(0).getAsDouble(),
                array.get(1).getAsDouble(),
                array.get(2).getAsDouble()
        );
    }

    private String getString(JsonObject object, String key) {
        if (!object.has(key)
                || object.get(key).isJsonNull()
                || !object.get(key).isJsonPrimitive()) {
            return null;
        }

        return object.get(key).getAsString();
    }
}

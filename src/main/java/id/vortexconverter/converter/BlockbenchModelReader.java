
package id.vortexconverter.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockbenchModelReader {

    public record Vector3(double x, double y, double z) {}

    public record Cube(
            String uuid,
            Vector3 from,
            Vector3 to,
            Vector3 origin,
            Vector3 rotation,
            String name
    ) {}

    public record Bone(
            String uuid,
            String name,
            String parent,
            Vector3 pivot,
            Vector3 rotation,
            List<Cube> cubes,
            List<Bone> children
    ) {}

    public record DataPoint(
            Vector3 position,
            Vector3 rotation,
            Vector3 scale
    ) {}

    public record Keyframe(
            double time,
            String channel,
            String interpolation,
            List<DataPoint> dataPoints
    ) {}

    public record Animation(
            String name,
            String loop,
            double length,
            double overrideSpeed,
            Map<String, List<Keyframe>> boneAnimations
    ) {}

    public record BlockbenchModel(
            String name,
            int textureWidth,
            int textureHeight,
            List<Bone> bones,
            List<Animation> animations
    ) {}

    private final Map<String, Cube> cubeIndex = new LinkedHashMap<>();
    private final Map<String, JsonObject> boneIndex = new LinkedHashMap<>();

    public BlockbenchModel read(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("BBModel file does not exist.");
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader)
                    .getAsJsonObject();

            cubeIndex.clear();
            boneIndex.clear();

            String name = string(root, "name", "unnamed");

            JsonObject resolution = object(root, "resolution");

            int textureWidth = integer(resolution, "width", 16);
            int textureHeight = integer(resolution, "height", 16);

            indexCubes(root);
            indexBones(root);

            List<Bone> bones = readBones(root);
            List<Animation> animations = readAnimations(root);

            return new BlockbenchModel(
                    name,
                    textureWidth,
                    textureHeight,
                    Collections.unmodifiableList(bones),
                    Collections.unmodifiableList(animations)
            );

        } catch (RuntimeException exception) {
            throw new IOException(
                    "Failed to read BBModel: " + exception.getMessage(),
                    exception
            );
        }
    }

    private void indexCubes(JsonObject root) {
        JsonArray elements = array(root, "elements");

        for (JsonElement element : elements) {
            JsonObject object = element.getAsJsonObject();

            String uuid = string(object, "uuid", "");

            if (uuid.isBlank()) {
                continue;
            }

            Vector3 from = vector(object, "from");
            Vector3 to = vector(object, "to");
            Vector3 origin = vector(object, "origin");
            Vector3 rotation = vector(object, "rotation");

            cubeIndex.put(uuid, new Cube(
                    uuid,
                    from,
                    to,
                    origin,
                    rotation,
                    string(object, "name", uuid)
            ));
        }
    }

    private void indexBones(JsonObject root) {
        JsonArray outliner = array(root, "outliner");

        for (JsonElement element : outliner) {
            indexBoneTree(element);
        }
    }

    private void indexBoneTree(JsonElement element) {
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();

        String uuid = string(object, "uuid", "");

        if (!uuid.isBlank() && object.has("children")) {
            boneIndex.put(uuid, object);
        }

        for (JsonElement child : array(object, "children")) {
            indexBoneTree(child);
        }
    }

    private List<Bone> readBones(JsonObject root) {
        List<Bone> bones = new ArrayList<>();

        for (JsonElement element : array(root, "outliner")) {
            Bone bone = readBone(element, null);

            if (bone != null) {
                bones.add(bone);
            }
        }

        return bones;
    }

    private Bone readBone(JsonElement element, String parent) {
        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();

        String uuid = string(object, "uuid", "");

        if (!object.has("children")) {
            return null;
        }

        List<Cube> cubes = new ArrayList<>();
        List<Bone> children = new ArrayList<>();

        for (JsonElement child : array(object, "children")) {
            if (child.isJsonPrimitive()) {
                String cubeUuid = child.getAsString();
                Cube cube = cubeIndex.get(cubeUuid);

                if (cube != null) {
                    cubes.add(cube);
                }
            } else if (child.isJsonObject()) {
                Bone nested = readBone(child, uuid);

                if (nested != null) {
                    children.add(nested);
                }
            }
        }

        return new Bone(
                uuid,
                string(object, "name", uuid),
                parent,
                vector(object, "origin"),
                vector(object, "rotation"),
                Collections.unmodifiableList(cubes),
                Collections.unmodifiableList(children)
        );
    }

    private List<Animation> readAnimations(JsonObject root) {
        List<Animation> animations = new ArrayList<>();

        for (JsonElement element : array(root, "animations")) {
            JsonObject object = element.getAsJsonObject();

            Map<String, List<Keyframe>> boneAnimations =
                    new LinkedHashMap<>();

            JsonObject animators = object(object, "animators");

            for (Map.Entry<String, JsonElement> entry
                    : animators.entrySet()) {

                if (!entry.getValue().isJsonObject()) {
                    continue;
                }

                JsonObject animator = entry.getValue().getAsJsonObject();

                List<Keyframe> keyframes = readKeyframes(animator);

                boneAnimations.put(
                        entry.getKey(),
                        Collections.unmodifiableList(keyframes)
                );
            }

            animations.add(new Animation(
                    string(object, "name", "unnamed"),
                    string(object, "loop", "false"),
                    decimal(object, "length", 0),
                    decimal(object, "override_speed", 0),
                    Collections.unmodifiableMap(boneAnimations)
            ));
        }

        return animations;
    }

    private List<Keyframe> readKeyframes(JsonObject animator) {
        List<Keyframe> keyframes = new ArrayList<>();

        for (JsonElement element : array(animator, "keyframes")) {
            JsonObject object = element.getAsJsonObject();

            List<DataPoint> points = new ArrayList<>();

            for (JsonElement point : array(object, "data_points")) {
                if (!point.isJsonObject()) {
                    continue;
                }

                JsonObject data = point.getAsJsonObject();

                points.add(new DataPoint(
                        stringVector(data, "x", "y", "z", "0"),
                        stringVector(data, "x", "y", "z", "0"),
                        scaleVector(data)
                ));
            }

            keyframes.add(new Keyframe(
                    decimal(object, "time", 0),
                    string(object, "channel", "rotation"),
                    string(object, "interpolation", "linear"),
                    Collections.unmodifiableList(points)
            ));
        }

        return keyframes;
    }

    private Vector3 stringVector(
            JsonObject object,
            String x,
            String y,
            String z,
            String fallback
    ) {
        return new Vector3(
                decimalString(object, x, fallback),
                decimalString(object, y, fallback),
                decimalString(object, z, fallback)
        );
    }

    private Vector3 scaleVector(JsonObject object) {
        return stringVector(object, "x", "y", "z", "1");
    }

    private double decimalString(
            JsonObject object,
            String key,
            String fallback
    ) {
        try {
            return Double.parseDouble(string(object, key, fallback));
        } catch (NumberFormatException exception) {
            return Double.parseDouble(fallback);
        }
    }

    private Vector3 vector(JsonObject object, String key) {
        JsonArray array = array(object, key);

        if (array.size() != 3) {
            return new Vector3(0, 0, 0);
        }

        return new Vector3(
                array.get(0).getAsDouble(),
                array.get(1).getAsDouble(),
                array.get(2).getAsDouble()
        );
    }

    private JsonObject object(JsonObject object, String key) {
        if (object == null || !object.has(key)
                || !object.get(key).isJsonObject()) {
            return new JsonObject();
        }

        return object.getAsJsonObject(key);
    }

    private JsonArray array(JsonObject object, String key) {
        if (object == null || !object.has(key)
                || !object.get(key).isJsonArray()) {
            return new JsonArray();
        }

        return object.getAsJsonArray(key);
    }

    private String string(
            JsonObject object,
            String key,
            String fallback
    ) {
        if (object == null || !object.has(key)
                || object.get(key).isJsonNull()) {
            return fallback;
        }

        return object.get(key).getAsString();
    }

    private int integer(
            JsonObject object,
            String key,
            int fallback
    ) {
        try {
            return object.get(key).getAsInt();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private double decimal(
            JsonObject object,
            String key,
            double fallback
    ) {
        try {
            return object.get(key).getAsDouble();
        } catch (Exception exception) {
            return fallback;
        }
    }
}

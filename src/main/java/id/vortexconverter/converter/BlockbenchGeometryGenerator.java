
package id.vortexconverter.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlockbenchGeometryGenerator {

    public JsonObject generate(
            BlockbenchModelReader.BlockbenchModel model,
            String identifier,
            int textureWidth,
            int textureHeight
    ) {
        return generate(
                model,
                identifier,
                textureWidth,
                textureHeight,
                Map.of()
        );
    }

    public JsonObject generate(
            BlockbenchModelReader.BlockbenchModel model,
            String identifier,
            int textureWidth,
            int textureHeight,
            Map<String, BlockbenchTextureMapper.CubeUV> uvMappings
    ) {
        if (model == null) {
            throw new IllegalArgumentException(
                    "Blockbench model cannot be null"
            );
        }

        if (identifier == null || identifier.isBlank()) {
            identifier = "geometry.vortex.model";
        }

        if (textureWidth <= 0) {
            textureWidth = model.textureWidth();
        }

        if (textureHeight <= 0) {
            textureHeight = model.textureHeight();
        }

        if (textureWidth <= 0) textureWidth = 64;
        if (textureHeight <= 0) textureHeight = 64;

        if (uvMappings == null) {
            uvMappings = Map.of();
        }

        Map<String, String> boneNames = new HashMap<>();
        collectBoneNames(model.bones(), boneNames);

        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.12.0");

        JsonObject geometry = new JsonObject();
        JsonObject description = new JsonObject();

        description.addProperty("identifier", identifier);
        description.addProperty("texture_width", textureWidth);
        description.addProperty("texture_height", textureHeight);
        description.addProperty("visible_bounds_width", 4.0);
        description.addProperty("visible_bounds_height", 4.0);
        description.addProperty("visible_bounds_offset", 1.0);

        geometry.add("description", description);

        JsonArray bones = new JsonArray();

        if (model.bones() != null) {
            for (BlockbenchModelReader.Bone bone : model.bones()) {
                addBoneRecursive(
                        bones,
                        bone,
                        boneNames,
                        uvMappings
                );
            }
        }

        geometry.add("bones", bones);

        JsonArray geometries = new JsonArray();
        geometries.add(geometry);

        root.add("minecraft:geometry", geometries);

        return root;
    }

    private void collectBoneNames(
            List<BlockbenchModelReader.Bone> source,
            Map<String, String> names
    ) {
        if (source == null) {
            return;
        }

        for (BlockbenchModelReader.Bone bone : source) {
            if (bone == null) {
                continue;
            }

            if (bone.uuid() != null && !bone.uuid().isBlank()) {
                names.put(
                        bone.uuid(),
                        sanitizeBoneName(bone.name())
                );
            }

            collectBoneNames(bone.children(), names);
        }
    }

    private void addBoneRecursive(
            JsonArray output,
            BlockbenchModelReader.Bone bone,
            Map<String, String> boneNames,
            Map<String, BlockbenchTextureMapper.CubeUV> uvMappings
    ) {
        if (bone == null) {
            return;
        }

        JsonObject result = new JsonObject();

        result.addProperty(
                "name",
                sanitizeBoneName(bone.name())
        );

        if (bone.parent() != null && !bone.parent().isBlank()) {
            String parentName = boneNames.get(bone.parent());

            if (parentName != null) {
                result.addProperty("parent", parentName);
            }
        }

        result.add("pivot", vector(bone.pivot()));

        if (bone.rotation() != null) {
            result.add("rotation", vector(bone.rotation()));
        }

        JsonArray cubes = new JsonArray();

        if (bone.cubes() != null) {
            for (BlockbenchModelReader.Cube cube : bone.cubes()) {
                JsonObject cubeJson = createCube(
                        cube,
                        uvMappings
                );

                if (cubeJson != null) {
                    cubes.add(cubeJson);
                }
            }
        }

        if (!cubes.isEmpty()) {
            result.add("cubes", cubes);
        }

        output.add(result);

        if (bone.children() != null) {
            for (BlockbenchModelReader.Bone child : bone.children()) {
                addBoneRecursive(
                        output,
                        child,
                        boneNames,
                        uvMappings
                );
            }
        }
    }

    private JsonObject createCube(
            BlockbenchModelReader.Cube cube,
            Map<String, BlockbenchTextureMapper.CubeUV> uvMappings
    ) {
        if (cube == null || cube.from() == null || cube.to() == null) {
            return null;
        }

        BlockbenchModelReader.Vector3 from = cube.from();
        BlockbenchModelReader.Vector3 to = cube.to();

        double sizeX = to.x() - from.x();
        double sizeY = to.y() - from.y();
        double sizeZ = to.z() - from.z();

        if (sizeX < 0 || sizeY < 0 || sizeZ < 0) {
            return null;
        }

        JsonObject result = new JsonObject();

        JsonArray origin = new JsonArray();
        origin.add(from.x());
        origin.add(from.y());
        origin.add(from.z());

        JsonArray size = new JsonArray();
        size.add(sizeX);
        size.add(sizeY);
        size.add(sizeZ);

        result.add("origin", origin);
        result.add("size", size);

        if (cube.origin() != null) {
            result.add("pivot", vector(cube.origin()));
        }

        if (cube.rotation() != null) {
            result.add("rotation", vector(cube.rotation()));
        }

        if (cube.uuid() != null && !cube.uuid().isBlank()) {
            BlockbenchTextureMapper.CubeUV cubeUV =
                    uvMappings.get(cube.uuid());

            if (cubeUV != null) {
                BlockbenchTextureMapper mapper =
                        new BlockbenchTextureMapper();

                JsonObject uv = mapper.toBedrockUV(cubeUV);

                if (!uv.isEmpty()) {
                    result.add("uv", uv);
                }
            }
        }

        return result;
    }

    private JsonArray vector(
            BlockbenchModelReader.Vector3 vector
    ) {
        JsonArray result = new JsonArray();

        if (vector == null) {
            result.add(0);
            result.add(0);
            result.add(0);
            return result;
        }

        result.add(vector.x());
        result.add(vector.y());
        result.add(vector.z());

        return result;
    }

    private String sanitizeBoneName(String name) {
        if (name == null || name.isBlank()) {
            return "bone";
        }

        String sanitized = name
                .toLowerCase()
                .replaceAll("[^a-z0-9_]", "_");

        if (sanitized.isBlank()) {
            return "bone";
        }

        if (Character.isDigit(sanitized.charAt(0))) {
            sanitized = "bone_" + sanitized;
        }

        return sanitized;
    }
}

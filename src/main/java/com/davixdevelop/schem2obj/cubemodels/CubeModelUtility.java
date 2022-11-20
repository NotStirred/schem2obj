package com.davixdevelop.schem2obj.cubemodels;

import com.davixdevelop.schem2obj.Constants;
import com.davixdevelop.schem2obj.Orientation;
import com.davixdevelop.schem2obj.blockmodels.BlockModel;
import com.davixdevelop.schem2obj.blockmodels.CubeElement;
import com.davixdevelop.schem2obj.blockstates.AdjacentBlockState;
import com.davixdevelop.schem2obj.cubemodels.model.CubeFace;
import com.davixdevelop.schem2obj.materials.IMaterial;
import com.davixdevelop.schem2obj.materials.Material;
import com.davixdevelop.schem2obj.models.VariantModels;
import com.davixdevelop.schem2obj.namespace.Namespace;
import com.davixdevelop.schem2obj.util.ArrayVector;
import com.davixdevelop.schem2obj.util.ImageUtility;
import com.davixdevelop.schem2obj.wavefront.IWavefrontObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class CubeModelUtility {
    public static Random RANDOM = new Random();

    /**
     * Extract default materials from models and return a map with texture variables and names of the material
     * for each variant model
     * @param models An array of models
     * @return map of key: texture variable, value: name of default material
     */
    public static HashMap<String, HashMap<String, String>> modelsToMaterials(VariantModels[] models, Namespace blockNamespace) {
        HashMap<String, HashMap<String, String>> textureMaterialsPerRootModel = new HashMap<>();

        for(VariantModels variantModels : models) {

            for (BlockModel model : variantModels.getModels()) {
                HashMap<String, String> modelTextures = model.getTextures().getTextures();

                //Get textures from block
                for (String key : modelTextures.keySet()) {
                    String rootModelName = variantModels.getVariant().getModel();

                    if(!textureMaterialsPerRootModel.containsKey(rootModelName)){
                        HashMap<String, String> textureMaterials = new HashMap<>();
                        textureMaterialsPerRootModel.put(rootModelName, textureMaterials);
                    }

                    HashMap<String, String> textureMaterials = textureMaterialsPerRootModel.get(rootModelName);


                    String value = modelTextures.get(key);

                    //Check if texture value doesn't have a variable (raw value, ex. block/dirt)
                    if (!value.startsWith("#")) {

                        String materialName = value;

                        generateOrGetMaterial(materialName, blockNamespace);

                        if (!textureMaterials.containsKey(key))
                            textureMaterials.put(key, materialName);

                    } else {
                        //Check if texture materials already contains a key with the same variable name (removed # from the value)
                        if (textureMaterials.containsKey(value.substring(1))) {
                            //If it does, place the key with the actual value of the variable (ex. #all -> block/dirt)
                            textureMaterials.put(key, textureMaterials.get(value.substring(1)));
                        }
                    }

                }
            }
        }

        return textureMaterialsPerRootModel;
    }

    /**
     * Get material or generate it
     * @param materialPath The path of material, ex. blocks/fire or entity/black-bed
     * @param blockNamespace The namespace of the block the material uses
     */
    public static void generateOrGetMaterial(String materialPath, Namespace blockNamespace){
        if (Constants.BLOCK_MATERIALS.containsMaterial(materialPath)) {
            if(!Constants.BLOCK_MATERIALS.usedMaterials().contains(materialPath)){
                //If material isn't yet used, but It's in BLOCK_MATERIALS collection, it means It's a custom material, added from a resource pack
                //Modify the material to include the lightValue of the block
                IMaterial material = Constants.BLOCK_MATERIALS.getMaterial(materialPath);
                if(blockNamespace.getLightValue() != 0.0)
                    material.setEmissionStrength(blockNamespace.getLightValue() / 16);
                Constants.BLOCK_MATERIALS.setMaterial(materialPath, material);
            }
        }else {
            Material material = new Material();
            //ToDo: Implement this in LitBlockWavefrontObject
                        /*boolean isLit = false;
                        //Loop through the variants and check if the model root parent name is the same same as the model name
                        //If it is, check if the variant model start's with lit_ and set isLit to true
                        //This approach enables a material to be named lit_ + texture name and still keep the texture name the same
                        for(BlockState.Variant variant : variants){
                            if(variant.getModel().equals(model.getRootParent())){
                                if(variant.getModel().startsWith("lit_")) {
                                    isLit = true;
                                    break;
                                }
                            }
                        }*/

            //If material is a block or is an entity and doesn't contain - in it's material path
            //use the material path for the diffuse texture path
            if(materialPath.startsWith("blocks") || (materialPath.startsWith("entity") && !materialPath.contains("-"))) {
                material.setDiffuseTexturePath(materialPath);

            }else if(materialPath.startsWith("entity") && materialPath.contains("-")){
                String materialName = textureName(materialPath);
                //If material path contains a -, it means the texture for that material is in a subfolder with the name of the entity
                //Ex: black-bed -> diffuseTexturePath = entity/bed/black
                material.setDiffuseTexturePath(String.format("entity/%s/%s", blockNamespace.getType(), materialName.substring(0, materialName.indexOf('-'))));
            }

            material.setEmissionStrength(blockNamespace.getLightValue());
            material.setName(textureName(materialPath));
            Constants.BLOCK_MATERIALS.setMaterial(materialPath, material);
        }


    }

    /**
     * Rotate a uv by a any angle
     * @param uvs The original unmodified uv list
     * @param angle The angle to shift rotate the uv's
     * @param rotationOrigin The origin of rotation. A Double array [x, y]
     * @return
     */
    public static void rotateUV(List<Double[]> uvs, Double angle, Double[] rotationOrigin){
        //Set the rotation matrix in the axis Z
        ArrayVector.MatrixRotation uvRotation = new ArrayVector.MatrixRotation(-angle,"Z");
        for(int c = 0; c < uvs.size(); c++){
            Double[] uv = uvs.get(c);
            //Rotate the uv with rotatePoint by construction in vector that has an z value of 0
            Double[] new_uv = rotatePoint(new Double[] {uv[0], uv[1], 0.0}, uvRotation, rotationOrigin);
            uv[0] = round(new_uv[0], 6);
            uv[1] = round(new_uv[1], 6);

            uvs.set(c, uv);
        }
    }

    /**
     * Rotate a uv, by shifting the positions of the uv.
     * @param uvs The original unmodified uv list
     * @param angle The angle to shift rotate the uv's
     * @return The rotate uv's
     */
    public static void shiftRotateUV(ArrayList<Double[]> uvs, Double angle){

        //Make sure the angle is is between
        if(angle > 360.0 || angle < -360.0)
            angle %= 360.0;

        ArrayList<Double[]> shifted = new ArrayList<>(uvs);

        //If angle can be divided by 90 shifting the uv's by a offset
        if(angle % 90 == 0) {
            Double offset = Math.abs(angle) / 90;

            if(angle > 0)
                offset *= -1;


            for (int c = 0; c < shifted.size(); c++) {
                int shift = offset.intValue();
                if (c + shift >= shifted.size() || c + shift < 0) {
                    if (shift > 0)
                        shift = c + shift - shifted.size();
                    else
                        shift = c + shift + shifted.size();

                } else
                    shift = c + shift;

                shifted.set(c, uvs.get(shift));
            }
        }else{
            double median = Math.abs(angle) / 90.0;
            //Get the indexes to the start and end point that the median lies between

            boolean even = Math.ceil(median) % 2 == 0;

            for(int c = 0; c < shifted.size(); c++){
                Double shiftedMedian = c + median;

                Double iA = Math.floor(shiftedMedian) % 4;
                Double iC = Math.ceil(shiftedMedian) % 4;


                if(angle > 0) {
                    if (even) {
                        iA = iA - 2;
                        if (iA < 0.0)
                            iA += 4;
                    } else {
                        iC = iC - 2;
                        if(iC < 0.0)
                            iC += 4;
                    }
                }

                Double[] A = uvs.get(iA.intValue());
                Double[] C = uvs.get(iC.intValue());

                Double length = Math.sqrt(Math.pow(C[0] - A[0] ,2) + Math.pow(C[1] - A[1], 2));
                Double move_distance = (length * (Math.abs(angle) % 90)) / 90;

                Double[] uv = uvs.get(c);

                if(A[0].equals(C[0])) {
                    if(A[1] < C[1])
                        shifted.set(c, new Double[]{A[0], A[1] + move_distance});
                    else
                        shifted.set(c, new Double[]{A[0], C[1] + move_distance});
                }else {
                    if(A[0] < C[0])
                        shifted.set(c, new Double[]{A[0] + move_distance, A[1]});
                    else
                        shifted.set(c, new Double[]{C[0] + move_distance, A[1]});
                }
            }
        }

        for(int c = 0; c < shifted.size(); c++)
            uvs.set(c, shifted.get(c));


    }

    public static ArrayList<Double[]> offsetUV(ArrayList<Double[]> uvs, Double x, Double y){
        for(int c = 0; c < uvs.size(); c++ ){
            Double[] uv = uvs.get(c);
            if(x > 0.0)
                uv[0] = uv[0] - x;
            if(y > 0.0)
                uv[1] = uv[1] - y;

            uvs.set(c, uv);
        }

        return uvs;
    }

    public static Double[] getUVFaceOrigin(ArrayList<Double[]> UVFace){
        Double x1 = UVFace.stream().min(Comparator.comparing(v -> v[0])).get()[0]; //Min x
        Double x2 = UVFace.stream().max(Comparator.comparing(v -> v[0])).get()[0]; //Max x
        Double y1 = UVFace.stream().min(Comparator.comparing(v -> v[1])).get()[1]; //Min y
        Double y2 = UVFace.stream().max(Comparator.comparing(v -> v[1])).get()[1]; //Max y

        return new Double[] {(x1 + x2) / 2, (y1 + y2) / 2, 0.0};
    }

    public static Double[] getRandomUV(int stack_size){
        //Get the uv height of one stack
        Double stack_height = 1.0 / stack_size;
        //Get random index of stack
        int y = new Float(RANDOM.nextFloat() / stack_height.floatValue()).intValue();

        //Default is selected bottom stack
        Double[] uv = new Double[]{0.0, 0.0, 1.0, round(stack_height, 6)};
        if(y > 0){
            uv[1] = y * stack_height;
            uv[3] = (y + 1) * stack_height;
        }

        return uv;
    }

    /**
     * Get the order of corners per face orientation
     * @param orientation The orientation of the face, ex. NORTH
     * @return A array of Corners
     */
    public static String[] getCornerPerOrientation(Orientation orientation){
        String[] corners = new String[]{"A","B","C","D"};
        switch (orientation){
            case NORTH:
                corners = new String[]{"M","F","C","D"}; //Orientation M:7 F:4 C:2 D:3
                break;
            case SOUTH:
                corners = new String[]{"A","B","G","H"}; //Orientation A:0 B:1 G:5 H:6
                break;
            case UP:
                corners = new String[]{"B","C","F","G"}; //Orientation B:1 C:2 F:4 G:5
                break;
            case DOWN:
                corners = new String[]{"D","A","H","M"}; //Orientation D:3 A:0 H:6 M:7
                break;
            case WEST:
                corners = new String[]{"D","C","B","A"}; //Orientation D:3 C:2 B:1 A:0
                break;
            case EAST:
                corners = new String[]{"H","G","F","M"}; //Orientation H:6 G:5 F:4 M:7
                break;
        }

        return corners;
    }

    /**
     * Create a liquid cube (water or lava)
     * @param level The level of the "liquid"
     * @param check The adjacent check to perform
     * @param stillMaterial The material to apply on still blocks
     * @param flowingMaterial The material to apply on liquid blocks
     * @param vertices The vertices of the liquid
     * @param normalsArray The normals of the liquid
     * @param textureCoordinates The UV's of the liquid
     */
    /*public static void createLiquidCube(Integer level, IAdjacentCheck check, String stillMaterial, String flowingMaterial, HashedDoubleList vertices, ArrayList<Double[]> normalsArray, HashedDoubleList textureCoordinates){
        //Key: corner (ex. A), Value: Index of vertex
        Map<String, Double[]> Corners = new HashMap<>();

        Set<String> addFace = new HashSet<>();

        //Get above block
        Namespace adjacentBlock = Constants.LOADED_SCHEMATIC.getNamespace(
                Constants.LOADED_SCHEMATIC.getPosX(),
                Constants.LOADED_SCHEMATIC.getPosY() + 1,
                Constants.LOADED_SCHEMATIC.getPosZ());
        //Check if above block is other than the liquid
        //If it is, generate the surface of the liquid
        if(adjacentBlock != null){
            if(check.checkCollision(adjacentBlock, 0, null)) {
                //Add up face
                //Up face uses B C F G corners
                Corners.put("B", new Double[]{0.0, 0.0, level / 16.0});
                Corners.put("C", new Double[]{0.0, 1.0, level / 16.0});
                Corners.put("F", new Double[]{1.0, 1.0, level / 16.0});
                Corners.put("G", new Double[]{1.0, 0.0, level / 16.0});

                addFace.add("up");
            }
        }

        //Get block on east
        adjacentBlock = Constants.LOADED_SCHEMATIC.getNamespace(
                Constants.LOADED_SCHEMATIC.getPosX() + 1,
                Constants.LOADED_SCHEMATIC.getPosY(),
                Constants.LOADED_SCHEMATIC.getPosZ());

        //If block on the east is null or is other than the liquid add east face
        if(adjacentBlock == null || check.checkCollision(adjacentBlock, 0, null)){
            //East face uses H G F M corners
            if(!Corners.containsKey("F"))
                Corners.put("F", new Double[]{1.0,1.0,1.0});
            if(!Corners.containsKey("G"))
                Corners.put("G", new Double[]{1.0,0.0,1.0});

            Corners.put("M", new Double[]{1.0,1.0,0.0});
            Corners.put("H", new Double[]{1.0,0.0,0.0});

            addFace.add("east");
        }else if(!check.checkCollision(adjacentBlock, 0, null)){
            int adjacentLevel = Integer.getInteger(adjacentBlock.getData().get("level"));

            if(!Corners.containsKey("F"))
                Corners.put("F", new Double[]{1.0,1.0,1.0});
            else{
                Double[] vert = Corners.get("F");
                vert[2] = (vert[2] + (adjacentLevel / 16.0)) / 2;
                Corners.put("F", vert);
            }
            if(!Corners.containsKey("G"))
                Corners.put("G", new Double[]{1.0,0.0,1.0});
            else {
                Double[] vert = Corners.get("G");
                vert[2] = (vert[2] + (adjacentLevel / 16.0)) / 2;
                Corners.put("G", vert);
            }

            Corners.put("M", new Double[]{1.0,1.0,0.0});
            Corners.put("H", new Double[]{1.0,0.0,0.0});

            addFace.add("east");
        }

        //Get block on north
        adjacentBlock = Constants.LOADED_SCHEMATIC.getNamespace(
                Constants.LOADED_SCHEMATIC.getPosX(),
                Constants.LOADED_SCHEMATIC.getPosY(),
                Constants.LOADED_SCHEMATIC.getPosZ() - 1);

        //If block on the north is null or is other than the liquid add north face
        if(adjacentBlock == null || check.checkCollision(adjacentBlock, 0, null)){
            //North face uses M F C D corners
            if(!Corners.containsKey("C"))
                Corners.put("C", new Double[]{0.0,1.0,1.0});
            if(!Corners.containsKey("F"))
                Corners.put("F", new Double[]{1.0,1.0,1.0});

            Corners.put("D", new Double[]{0.0,1.0,0.0});
            Corners.put("M", new Double[]{1.0,1.0,0.0});

            addFace.add("north");
        }else if(!check.checkCollision(adjacentBlock, 0, null)){
            int adjacentLevel = Integer.getInteger(adjacentBlock.getData().get("level"));

            //North face uses M F C D corners
            if(!Corners.containsKey("C"))
                Corners.put("C", new Double[]{1.0,1.0,1.0});
            else{
                Double[] vert = Corners.get("C");
                vert[2] = (vert[2] + (adjacentLevel / 16.0)) / 2;
                Corners.put("C", vert);
            }
            if(!Corners.containsKey("F"))
                Corners.put("F", new Double[]{1.0,0.0,1.0});
            else {
                Double[] vert = Corners.get("F");
                vert[2] = (vert[2] + (adjacentLevel / 16.0)) / 2;
                Corners.put("F", vert);
            }

            Corners.put("D", new Double[]{0.0,1.0,0.0});
            Corners.put("M", new Double[]{1.0,1.0,0.0});

            addFace.add("north");
        }
    }*/

    /**
     * Create 8 or less vertices for the cube
     * @param A The start corner of the cube
     * @param F The end corner of the cube
     * @return An map of vertices of the cube, where the key (String) represents a corner
     */
    public static Map<String, Double[]> createCubeVerticesFromPoints(Double[] A, Double[] F, Set<String> faces, ArrayVector.MatrixRotation rotationX, ArrayVector.MatrixRotation rotationY){
        Map<String, Double[]> vertices = new HashMap<>();

        //Array to keep track of which corners to add to the cube
        //The indexes are the following: 0:A , 1:B, 2:C, 3:D, 4:F, 5:G, 6:H, 7:M
        Boolean[] addCorners = new Boolean[] {false,false,false,false,false,false,false,false};

        if(faces.contains("north"))
        {
            //North face uses M F C D corners
             markCorners(addCorners, new Integer[]{7,4,2,3});
        }

        if(faces.contains("south")){
            //South face uses A B G H corners
             markCorners(addCorners, new Integer[]{0,1,5,6});
        }

        if(faces.contains("up")){
            //Up face uses B C F G corners
             markCorners(addCorners, new Integer[]{1,2,4,5});
        }

        if(faces.contains("down")){
            //Down face uses D A H M corners
             markCorners(addCorners, new Integer[]{3,0,6,7});
        }

        if(faces.contains("east")){
            //East face uses H G F M corners
             markCorners(addCorners, new Integer[]{6,5,4,7});
        }

        if(faces.contains("west")){
            //West face uses D C B A corners
             markCorners(addCorners, new Integer[]{3,2,1,0});
        }




        //Check if height, width or lenght of cube is zero and increase it by 0.000002 to avoid overlapping face (Z-fighting)
        if((A[2].equals(F[2])) || (A[1].equals(F[1])) || (A[0].equals(F[0])))
        {
            //Check height
            increaseOverlappingCube(A, F, 2);
            //Check length
            increaseOverlappingCube(A, F, 1);
            //Check width
            increaseOverlappingCube(A, F, 0);
        }




        if(addCorners[0])//Corner A
            vertices.put("A",A);
        if(addCorners[1]) //Corner B
            vertices.put("B", new Double[]{A[0], A[1], F[2]});
        if(addCorners[2]) //Corner C
            vertices.put("C", new Double[]{A[0], F[1], F[2]});
        if(addCorners[3]) //Corner D
            vertices.put("D",new Double[]{A[0], F[1], A[2]});
        if(addCorners[4]) //Corner F
            vertices.put("F",F);
        if(addCorners[5]) //Corner G
            vertices.put("G",new Double[]{F[0], A[1], F[2]});
        if(addCorners[6]) //Corner H
            vertices.put("H", new Double[]{F[0], A[1], A[2]});
        if(addCorners[7]) //Corner M
            vertices.put("M", new Double[]{F[0], F[1], A[2]});

        return  vertices;
    }

    /**
     *
     * @param A
     * @param F
     * @param axisIndex
     */
    private static void increaseOverlappingCube(Double[] A, Double[] F, int axisIndex){
        if(A[axisIndex].equals(F[axisIndex])){
            Double OVERLAP_SIZE = 0.0009;
            if(A[axisIndex].equals(0.5)) {
                A[axisIndex] = round(A[axisIndex] - OVERLAP_SIZE, 6);
                F[axisIndex] = round(F[axisIndex] + OVERLAP_SIZE, 6);
            }
            else if(A[axisIndex] < 0.5) {
                OVERLAP_SIZE /= 2.0;
                F[2] = round(F[2] + OVERLAP_SIZE, 6);
            }else if(A[axisIndex] > 0.5) {
                OVERLAP_SIZE /= 2.0;
                A[axisIndex] = round(A[axisIndex] - OVERLAP_SIZE, 6);
            }

        }
    }

    private static void markCorners(Boolean[] addCorners, Integer[] indexes){
        for(int index : indexes)
            addCorners[index] = true;
    }

    /**
     * Move the block in the space to the desired position
     * @param cubeModel The block cube model to translate
     * @param position The position of the block in the space [x, y, z]
     * @param spaceSize The size of the space [width, length, height]
     * @return
     */
    public static void translateCubeModel(ICubeModel cubeModel, Integer[] position, Integer[] spaceSize){
        //Value by how much to move each vert (vert + translate)
        Double translateX = position[0] - (spaceSize[0].doubleValue() / 2);
        Double translateY = (position[1] * -1) + ((spaceSize[1].doubleValue() / 2) - 1);
        //Double translateZ = position[2].doubleValue();// - (spaceSize[2].doubleValue());
        Double[] translate = new Double[]{translateX, translateY, position[2].doubleValue()};


        List<ICube> cubes = cubeModel.getCubes();

        //Loop through cubes
        for(ICube cube : cubes){
            CubeFace[] cubeFaces = cube.getFaces();

            for(int f = 0; f < cubeFaces.length; f++){
                CubeFace cubeFace = cubeFaces[f];

                if(cubeFace != null){
                    List<Double[]> vertices = cubeFace.getCorners();

                    //Sum each vertex and translate
                    for(int c = 0; c < vertices.size(); c++)
                        vertices.set(c, ArrayVector.add(vertices.get(c), translate));

                    //Set the vertices back to face corners
                    //cubeFace.setCorners(vertices);
                }

                //cube.setCubeFace(f, cubeFace);
            }
        }
    }

    /**
     * Rotate a point with the given rotation matrix around the given origin of rotation
     * @param point A Double array representing a point in a XYZ space
     * @param rotation A rotation matrix for the X, Y or Z axis
     * @param origin The rotation origin in the XYZ space
     * @return The rotated point
     */
    public static Double[] rotatePoint(Double[] point, ArrayVector.MatrixRotation rotation, Double[] origin){
        //Subtract the point by block origin, so that the origin of the block becomes 0,0,0
        point = ArrayVector.subtract(point, origin);

        //Rotate the point with the rotation matrix
        point = rotation.rotate(point, 1.0);

        //Add the rotated point and rotation origin, so that the origin of the block becomes the rotation origin again
        point = ArrayVector.add(point, origin);

        //Round the values to 6 decimals
        for(int c = 0; c < point.length; c++)
            point[c] = round(point[c], 6);

        return point;
    }

    /**
     * Scale point on given axis from the origin by the scale
     * @param point A Double array representing a point in a XYZ space
     * @param scaleX A double value from 0.0 on forward (ex. 1.0 in 100%, meaning unchanged point)
     * @param scaleY A double value from 0.0 on forward (ex. 1.0 in 100%, meaning unchanged point)
     * @param scaleZ A double value from 0.0 on forward (ex. 1.0 in 100%, meaning unchanged point)
     * @param origin The scale origin in the XYZ space
     * @return The scale point
     */
    public static Double[] scalePoint(Double[] point, Double scaleX, Double scaleY, Double scaleZ, Double[] origin){
        //Subtract the point by block origin, so that the origin of the block becomes 0,0,0
        point = ArrayVector.subtract(point, origin);

        //Scale the point on the given axis
        point[0] *= scaleX;
        point[1] *= scaleY;
        point[2] *= scaleZ;

        //Add the scaled point and scale origin, so that the origin of the block becomes the scale origin again
        point = ArrayVector.add(point, origin);

        //Round the scaled point on the given axis to 6 decimals
        for(int c = 0; c < point.length; c++)
            point[c] = round(point[c], 6);

        return point;
    }

    public static Double round(Double value, int decimals){
        return new BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Check if two cube models bounding boxes are connected on faceOrientation and adjacentFaceOrientation.
     * @param cubeModel The original parent object
     * @param adjacentCubeModel The parent object to check
     * @param faceOrientation //The name of the face on the object to check
     * @param adjacentFaceOrientation //The name of the face on the parent object to check
     * @return True if the two faces are connected, else false
     */
    public static boolean checkFacing(ICubeModel cubeModel, ICubeModel adjacentCubeModel, Orientation faceOrientation, Orientation adjacentFaceOrientation){
        if(adjacentCubeModel == null)
            return false;

        if(cubeModel.checkCollision(adjacentCubeModel)){
            List<ICube> cubes = cubeModel.getCubes();
            List<ICube> adjacentCubes = adjacentCubeModel.getCubes();

            if(cubes.size() > 0 && adjacentCubes.size() > 0){
                Integer faceIndex = faceOrientation.getOrder();
                Integer adjacentFaceIndex = adjacentFaceOrientation.getOrder();

                for(ICube cube : cubes){
                    CubeFace cubeFace = cube.getFaces()[faceIndex];

                    if(cubeFace != null && cubeFace.isCullface()){

                        for(ICube adjacentCube : adjacentCubes){
                            CubeFace adjacentCubeFace = adjacentCube.getFaces()[adjacentFaceIndex];

                            if(adjacentCubeFace != null && adjacentCubeFace.isCullface()) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    public static void getAdjacentNamespace_NSWE(Namespace modified, IAdjacentCheck check){
        //Check north
        Namespace adjacentBlock = Constants.LOADED_SCHEMATIC.getNamespace(
                Constants.LOADED_SCHEMATIC.getPosX(),
                Constants.LOADED_SCHEMATIC.getPosY(),
                Constants.LOADED_SCHEMATIC.getPosZ() - 1);
        if(adjacentBlock != null){
            if(check.checkCollision(adjacentBlock, 0, "north"))
                modified.getData().put("north", "true");
        }

        //Check south
        adjacentBlock = Constants.LOADED_SCHEMATIC.getNamespace(
                Constants.LOADED_SCHEMATIC.getPosX(),
                Constants.LOADED_SCHEMATIC.getPosY(),
                Constants.LOADED_SCHEMATIC.getPosZ() + 1);
        if(adjacentBlock != null){
            if(check.checkCollision(adjacentBlock, 0, "south"))
                modified.getData().put("south", "true");
        }

        //Check west
        adjacentBlock = Constants.LOADED_SCHEMATIC.getNamespace(
                Constants.LOADED_SCHEMATIC.getPosX() - 1,
                Constants.LOADED_SCHEMATIC.getPosY(),
                Constants.LOADED_SCHEMATIC.getPosZ());
        if(adjacentBlock != null){
            if(check.checkCollision(adjacentBlock, 0, "west"))
                modified.getData().put("west", "true");
        }

        //Check east
        adjacentBlock = Constants.LOADED_SCHEMATIC.getNamespace(
                Constants.LOADED_SCHEMATIC.getPosX() + 1,
                Constants.LOADED_SCHEMATIC.getPosY(),
                Constants.LOADED_SCHEMATIC.getPosZ());
        if(adjacentBlock != null){
            if(check.checkCollision(adjacentBlock, 0, "east"))
                modified.getData().put("east", "true");
        }
    }

    public static void getAdjacentNamespace_AdjacentState(Namespace stockBlock, Namespace modified, AdjacentBlockState adjacentBlockStates, IAdjacentCheck check){
        //Get list of order of orientations to check
        List<String> checkOrder = adjacentBlockStates.getCheckOrder();

        for(String orientation : checkOrder){
            int x = Constants.LOADED_SCHEMATIC.getPosX();
            int y = Constants.LOADED_SCHEMATIC.getPosY();
            int z = Constants.LOADED_SCHEMATIC.getPosZ();


            String orientation_raw = (orientation.endsWith("-1") || orientation.endsWith("+1")) ? orientation.substring(0, orientation.length() - 2) : orientation;
            switch (orientation_raw){
                case "south":
                    z += 1;
                    break;
                case "east":
                    x += 1;
                    break;
                case "north":
                    z -= 1;
                    break;
                case "west":
                    x -= 1;
                    break;
            }

            if(orientation.endsWith("+1"))
                y += 1;
            else if(orientation.endsWith("-1"))
                y -= 1;

            Namespace adjacentBlock = Constants.LOADED_SCHEMATIC.getNamespace(x, y, z);
            if(adjacentBlock != null) {
                if(check.checkCollision(adjacentBlock,
                        (orientation.endsWith("+1")) ? 1 : (orientation.endsWith("-1") ? -1 : 0),
                        orientation_raw)){
                    Map<String, String> statesToApply = adjacentBlockStates.getStates(orientation, stockBlock.getData(), adjacentBlock.getData());
                    if(statesToApply != null)
                        Namespace.copyStatesToNamespace(modified, statesToApply);
                }
            }
        }

    }

    public static String getKey_NSWE(Namespace blockNamespace){
        return String.format("%s:north=%s,south=%s,east=%s,west=%s",
                blockNamespace.getName(),
                blockNamespace.getData().get("north"),
                blockNamespace.getData().get("south"),
                blockNamespace.getData().get("east"),
                blockNamespace.getData().get("west"));
    }

    /**
     * Get the plane (axis) of the face
     * @param plane A double array with the value of the plane (axis)
     * @param vertices Vertices of the face
     * @return The index of the axis (0->X, 1->Y->, 2->Z)
     */
    public static int getFacePlane(Double plane[], List<Double[]> vertices){
        Boolean x = null;
        Boolean y = null;
        Boolean z = null;

        Double[] temp = new Double[3];

        Double[] v1 = vertices.get(0);
        for(int v = 1; v < vertices.size(); v++){
            Double[] v2 = vertices.get(v);

            if(v1[0].equals(v2[0])){
                temp[0] = v1[0];
                if(x == null)
                    x = true;
            } else
                x = false;

            if(v1[1].equals(v2[1])){
                temp[1] = v1[1];
                if(y == null)
                    y = true;
            } else
                y = false;

            if(v1[2].equals(v2[2])){
                temp[2] = v1[2];
                if(z == null)
                    z = true;
            } else
                z = false;

        }

        if(x != null && x) {
            plane[0] = temp[0];
            return 0;
        }

        if(y != null && y) {
            plane[1] = temp[1];
            return 1;
        }

        if(z != null && z) {
            plane[2] = temp[2];
            return 2;
        }

        //Face is rotated
        //Calculate the face normal
        Double[] a = vertices.get(0);
        Double[] b = vertices.get(1);
        Double[] c = vertices.get(2);
        Double[] face_normal = ArrayVector.multiply(ArrayVector.subtract(b, a), ArrayVector.subtract(c, a));

        //First 3 values is the face of the normal, while the
        plane[0] = face_normal[0];
        plane[1] = face_normal[1];
        plane[2] = face_normal[2];

        Double x1 = vertices.stream().min(Comparator.comparing(v -> v[0])).get()[0]; //Min x
        Double x2 = vertices.stream().max(Comparator.comparing(v -> v[0])).get()[0]; //Max x
        Double y1 = vertices.stream().min(Comparator.comparing(v -> v[1])).get()[1]; //Min y
        Double y2 = vertices.stream().max(Comparator.comparing(v -> v[1])).get()[1]; //Max y
        Double z1 = vertices.stream().min(Comparator.comparing(v -> v[2])).get()[2]; //Min y
        Double z2 = vertices.stream().max(Comparator.comparing(v -> v[2])).get()[2]; //Max y

        plane[3] = (x1 + x2) / 2;
        plane[4] = (y1 + y2) / 2;
        plane[5] = (z1 + z2) / 2;



        return -1;

    }

    /**
     *
     * @param orientation
     * @param cubeCorners
     * @param cubes
     */
    public static void avoidOverlapping(Orientation orientation, Map<String, Double[]> cubeCorners, List<ICube> cubes){

        double OVERLAP_SIZE = 0.00045;

        //Get the order of corners for the face
        String[] faceCornersOrder = getCornerPerOrientation(orientation);

        ArrayList<Double[]> faceVertices = new ArrayList<>();
        //Get the vertices for the face
        for(String corner : faceCornersOrder)
            faceVertices.add(cubeCorners.get(corner));

        //Get face plane
        Double[] facePlane = new Double[6];
        int planeIndex = getFacePlane(facePlane, faceVertices);

        boolean increased = false;

        double vector_distance = 0.0;

        //Loop thorough all faces in the cubes and check if they overlap
        for(ICube cube : cubes){

            //Get all faces of cube
            CubeFace[] cubeFaces = cube.getFaces();

            for(CubeFace face : cubeFaces){
                if(face == null)
                    continue;

                List<Double[]> faceVertices2 = face.getCorners();

                Double[] facePlane2 = new Double[6];
                int planeIndex2 = getFacePlane(facePlane2, faceVertices2);

                if(planeIndex != -1 && planeIndex2 != -1) {
                    //If both faces are on the same plane, increase the first plane axis
                    if (planeIndex == planeIndex2 && facePlane[planeIndex].equals(facePlane2[planeIndex2])) {
                        increased = true;
                        facePlane[planeIndex] += OVERLAP_SIZE;
                    }
                }else{
                    //If both faces have the same normal, and origin
                    if(facePlane[0].equals(facePlane2[0]) && facePlane[1].equals(facePlane2[1]) && facePlane[2].equals(facePlane2[2]) &&
                            facePlane[3].equals(facePlane2[3]) && facePlane[4].equals(facePlane2[4]) && facePlane[5].equals(facePlane2[5])){
                        increased = true;
                        vector_distance += OVERLAP_SIZE;
                        //Move the plane origin on the normal vector by vector distance

                        Double[] OriginNormal = new Double[]{
                                facePlane[3] - facePlane[0],
                                facePlane[4] - facePlane[1],
                                facePlane[5] - facePlane[2]
                        };

                        Double[] movedOrigin = ArrayVector.add(
                                new Double[]{facePlane[3], facePlane[4], facePlane[5]},
                                ArrayVector.multiply(OriginNormal, vector_distance));

                        facePlane[3] = round(movedOrigin[0],6);
                        facePlane[4] = round(movedOrigin[1],6);
                        facePlane[5] = round(movedOrigin[2],6);
                    }
                }
            }
        }

        //Update the corner vertices with to the new plane
        if(increased){
            for(String corner : faceCornersOrder){
                Double[] vert = cubeCorners.get(corner);
                if(planeIndex != -1)
                    vert[planeIndex] = round(facePlane[planeIndex], 6);
                else{
                    Double[] VertexNormal = new Double[]{
                            vert[0] - facePlane[0],
                            vert[1] - facePlane[1],
                            vert[2] - facePlane[2]
                    };

                    vert = ArrayVector.add(vert, ArrayVector.multiply(VertexNormal, vector_distance));

                    vert[0] = round(vert[0], 6);
                    vert[1] = round(vert[1], 6);
                    vert[2] = round(vert[2], 6);
                }
                cubeCorners.put(corner, vert);
            }
        }


    }

    /**
     * Get the the starting and ending point of a line, bound to the block bound (0.0,0.0 -> 1.0, 1.0), if we would draw a line through A1 and B1
     *-------------          -B-----------
    *| B1        |           | .         |
    *|   .       |    ->     |   .       |
    *|    .      |    ->     |    .      |
    *|     A1    |           |     .     |
    *-------------           -------A-----
     *
     * @param A1 The starting point of the original line
     * @param B1 The ending point of the original line
     * @param A The starting point of the original line bound to the block bound
     * @param B The ending point of the original line bound to the block bound
     */
    public static void getBoundingLineSegment(Double[] A1, Double[] B1, Double[] A, Double[] B){
        //Calculate the linear function of this line
        Double k = (B1[1] - A1[1]) / (B1[0] - A1[0]);
        Double n = A1[1] - (k * A1[0]);

        //Calculate the A and B point of the line bound to the block bounds (0.0,0.0 to 1.0,1.0)
                                    /*-B-----------
                                      | .         |
                                      |   .       |
                                      |    .      |
                                      |     .     |
                                      -------A-----
                                     */
        B[0] = 0.0;
        B[1] = n;
        //If the line touches the ordinate out out the block bound, it means it touches the top of the block bound
        if(n > 1.0){
            B[0] = (n - 1.0) / -k;
            B[1] = 1.0;
        }
        A[0] = 1.0;
        A[1] = k + n;
        //If the line touches the right side of the block bound bellow the abscissa (z < 0.0), it means it touches the bottom of the block bound
        if(A[1] < 0.0){
            A[0] = n / -k;
            A[1] = 0.0;
        }
    }

    /**
     * Set the UV's of a face in a clockwise orientation
     * @param face The face of the
     * @param from
     * @param to
     * @return
     */
    public static ArrayList<Double[]> setAndRotateUVFace(CubeElement.CubeFace face, Orientation orientation, Double[] from, Double[] to){
        ArrayList<Double[]> UVFace = new ArrayList<>();
        if(face.getUv() == null){
            /*
            UVFace.add(new Double[]{from[0], to[1]}); //1
            UVFace.add(new Double[]{from[0], from[1]}); //2
            UVFace.add(new Double[]{to[0], from[1]}); //3
            UVFace.add(new Double[]{to[0], to[1]}); //4
            */

            switch (orientation){
                case UP:
                case DOWN:
                    UVFace.add(new Double[]{to[0], to[1]}); //4
                    UVFace.add(new Double[]{to[0], from[1]}); //3
                    UVFace.add(new Double[]{from[0], from[1]}); //2
                    UVFace.add(new Double[]{from[0], to[1]}); //1
                    break;
                case NORTH:
                case SOUTH:
                    UVFace.add(new Double[]{to[0], to[2]});
                    UVFace.add(new Double[]{to[0], from[2]});
                    UVFace.add(new Double[]{from[0], from[2]});
                    UVFace.add(new Double[]{from[0], to[2]});
                    break;
                case WEST:
                case EAST:
                    UVFace.add(new Double[]{to[1], to[2]});
                    UVFace.add(new Double[]{to[1], from[2]});
                    UVFace.add(new Double[]{from[1], from[2]});
                    UVFace.add(new Double[]{from[1], to[2]});
                    break;
            }



        }else{
            //MC face uv is a 4 item array [x1, y1, x2, y2]
            Double[] rawUV = face.getUv();

            UVFace.add(new Double[]{rawUV[0], rawUV[1]}); //x1,y1 //1
            UVFace.add(new Double[]{rawUV[0], rawUV[3]}); //x1,y2 //2
            UVFace.add(new Double[]{rawUV[2], rawUV[3]}); //x2,y2 //3
            UVFace.add(new Double[]{rawUV[2], rawUV[1]}); //x2,y1 //4



            /*switch (orientation){
                case "north":
                    UVFace.add(defaultUV.get(1)); //2
                    UVFace.add(defaultUV.get(0)); //1
                    UVFace.add(defaultUV.get(3)); //4
                    UVFace.add(defaultUV.get(2)); //3
                case "south":
                    UVFace.add(defaultUV.get(2)); //3
                    UVFace.add(defaultUV.get(3)); //4
                    UVFace.add(defaultUV.get(0)); //1
                    UVFace.add(defaultUV.get(1)); //2
                    break;
                case "east":
                    UVFace = defaultUV;
                    break;
                case "west":
                case "up":
                case "down":
                default:
                    UVFace = defaultUV;
                    break;
            }*/
        }

        //Rotate uv, to simulate rotation of texture
        if(face.getRotation() != null){
            //Calculate the origin of the UV face
            Double[] faceOrigin = getUVFaceOrigin(UVFace);

            if(face.getUv() == null)
                rotateUV(UVFace, (orientation.equals(Orientation.SOUTH) ||  orientation.equals(Orientation.NORTH)) ? -face.getRotation() : face.getRotation(), faceOrigin);
            else if(face.getRotation() % 90 == 0)
                shiftRotateUV(UVFace, face.getRotation());

        }

        return UVFace;
    }

    public static void convertCubeElementToCubeModel(CubeElement element, boolean uvLock, ArrayVector.MatrixRotation rotationX, ArrayVector.MatrixRotation rotationY, HashMap<String, String> modelsMaterials, CubeModel cubeModel){
        //Variable to store cube corner and their vertices index
        //Key: corner (ex. A), Value: Index of vertex
       // Map<String, Integer> CornersIndex = new HashMap<>();
        //Variable to store index of UV's

        //Get starting point of cube
        Double[] from = element.getFrom();
        //Get end point of cube
        Double[] to = element.getTo();

        //Create vertices for each corner of a face that the cube uses and add them to the object vertices
        Map<String, Double[]> cubeCorners = createCubeVerticesFromPoints(from, to, element.getFaces().keySet(), rotationX, rotationY);

        //Array to store index to material per face (See Orientation.DIRECTIONS for order of faces)
        Integer[] materialFaces = new Integer[6];
        //Array to store which faces should be exported (See Orientation.DIRECTIONS for order of faces)
        Boolean[] generatedFaces = new Boolean[]{false, false, false, false, false, false};
        //Array to store cube faces (See Orientation.DIRECTIONS for order of faces)
        CubeFace[] cubeFaces = new CubeFace[6];

        //Check if element has rotation
        if (element.getRotation() != null) {
            CubeElement.CubeRotation cubeRotation = element.getRotation();

            //Construct matrix rotation based on the axis and angle
            ArrayVector.MatrixRotation matrixRotation = new ArrayVector.MatrixRotation(-cubeRotation.getAngle(), cubeRotation.getAxis());

            Double[] rotation_origin = Constants.BLOCK_ORIGIN;
            if(cubeRotation.getOrigin() != null)
                rotation_origin = cubeRotation.getOrigin();

            //loop through the corners (vertices) and rotate each vertex
            for (String corner : cubeCorners.keySet())
                cubeCorners.put(corner, rotatePoint(cubeCorners.get(corner), matrixRotation, rotation_origin));

            if(cubeRotation.getRescale()){
                //The min/max values (points) of axis
                Double[] min_x = null;
                Double[] max_x = null;
                Double[] min_y = null;
                Double[] max_y = null;
                Double[] min_z = null;
                Double[] max_z = null;

                //Find min/max values of axis
                for(String corner : cubeCorners.keySet()){
                    Double[] vert = cubeCorners.get(corner);

                    if(min_x == null){
                        min_x = vert;
                        max_x = min_x;
                        min_y = vert;
                        max_y = min_y;
                        min_z = vert;
                        max_z = min_z;
                    }else{
                        if(vert[0] > max_x[0])
                            max_x = vert;
                        if(vert[0] < min_x[0])
                            min_x = vert;

                        if(vert[1] > max_y[1])
                            max_y = vert;
                        if(vert[1] < min_y[1])
                            min_y = vert;

                        if(vert[2] > max_z[2])
                            max_z = vert;
                        if(vert[2] < min_z[2])
                            min_z = vert;
                    }
                }

                //Temporary rotate the cube back and scale
                /*ArrayVector.MatrixRotation counterRotation = new ArrayVector.MatrixRotation(cubeRotation.getAngle(), cubeRotation.getAxis());
                for (String corner : cubeCorners.keySet())
                    cubeCorners.put(corner, WavefrontUtility.rotatePoint(cubeCorners.get(corner), counterRotation, rotation_origin));*/

                Double scale_x = null;
                Double scale_y = null;
                Double scale_z = null;

                //Calculate by how to scale (multiply) each point on each axis to get a full block

                switch (cubeRotation.getAxis()){
                    case "X":
                        //If element has more than one face, calulcate the pre-scale hypotenuse based on the entire cube
                        //Else calculate it based on the face
                        if(element.getFaces().size() > 1) {
                            double desiredHypotenuse = 1.0 / Math.cos(Math.toRadians(cubeRotation.getAngle()));
                            double pre_scaleHypotenuse = (max_z[1] - min_z[1]) / Math.cos(Math.toRadians(cubeRotation.getAngle()));
                            scale_y = desiredHypotenuse / pre_scaleHypotenuse;
                            scale_z = scale_y;
                            scale_x = 1.0;
                        }
                        else {
                            String faceOrientation = element.getFaces().keySet().stream().findFirst().get();
                            switch (faceOrientation){
                                case "up":
                                case "down":
                                case "east":
                                case "west":
                                    //Get the line segment end points of the face, when viewing the face straight on from x axis (the abscissa is the y axis, and the ordinate is the z axis)
                                    /*-------------
                                      | B1        |
                                      |   .       |
                                      |    .      |
                                      |     A1    |
                                      -------------
                                     */
                                    Double[] A1 = new Double[]{min_y[1], min_z[2]};
                                    Double[] B1 = new Double[]{max_y[1], max_z[2]};

                                    Double[] A = new Double[]{0.0, 0.0};
                                    Double[] B = new Double[]{0.0, 0.0};

                                    getBoundingLineSegment(A1, B1, A, B);
                                    //Replace the y and z values of the faces, with the A and B points

                                    for(String corner : cubeCorners.keySet()){
                                        Double[] vert = cubeCorners.get(corner);
                                        if(vert[1].equals(min_y[1]))
                                            vert[1] = A[0];
                                        else if(vert[1].equals(max_y[1]))
                                            vert[1] = B[0];

                                        if(vert[2].equals(min_z[2]))
                                            vert[2] = A[1];
                                        else if(vert[2].equals(max_z[1]))
                                            vert[2] = B[1];
                                    }
                                case "north":
                                case "south":
                                    scale_y = 1.0 / (max_y[1] - min_y[1]);
                                    scale_z = 1.0 / (max_z[2] - min_z[2]);
                                    scale_x = 1.0;
                            }
                        }


                        break;
                    case "Y":
                        if(element.getFaces().size() > 1) {
                            double desiredHypotenuse = 1.0 / Math.cos(Math.toRadians(cubeRotation.getAngle()));
                            double pre_scaleHypotenuse = (max_z[0] - min_z[0]) / Math.cos(Math.toRadians(cubeRotation.getAngle()));

                            scale_x = desiredHypotenuse / pre_scaleHypotenuse;
                            scale_z = scale_x;
                            scale_y = 1.0;
                        }else {
                            String faceOrientation = element.getFaces().keySet().stream().findFirst().get();
                            switch (faceOrientation){
                                case "up":
                                case "down":
                                case "south":
                                case "north":
                                    //Get the line segment end points of the face, when viewing the face straight on from y axis (the abscissa is the x axis, and the ordinate is the z axis)
                                    /*-------------
                                      | B1        |
                                      |   .       |
                                      |    .      |
                                      |     A1    |
                                      -------------
                                     */
                                    Double[] A1 = new Double[]{min_x[0], min_z[2]};
                                    Double[] B1 = new Double[]{max_x[0], max_z[2]};

                                    Double[] A = new Double[]{0.0, 0.0};
                                    Double[] B = new Double[]{0.0, 0.0};

                                    getBoundingLineSegment(A1, B1, A, B);
                                    //Replace the y and z values of the faces, with the A and B points

                                    for(String corner : cubeCorners.keySet()){
                                        Double[] vert = cubeCorners.get(corner);
                                        if(vert[0].equals(min_x[0]))
                                            vert[0] = A[0];
                                        else if(vert[0].equals(max_x[0]))
                                            vert[0] = B[0];

                                        if(vert[2].equals(min_z[2]))
                                            vert[2] = A[1];
                                        else if(vert[2].equals(max_z[1]))
                                            vert[2] = B[1];
                                    }
                                case "west":
                                case "east":
                                    scale_x = 1.0 / (max_x[0] - min_x[0]);
                                    scale_z = 1.0 / (max_z[2] - min_z[2]);
                                    scale_y = 1.0;
                            }
                        }
                        break;
                    case "Z":
                        scale_x = 1.0 / (max_x[0] - min_x[0]);
                        scale_y = 1.0 / (max_y[1] - min_y[1]);
                        scale_z = 1.0;

                        break;
                }

                if(scale_x != null || scale_y != null || scale_z != null) {
                    for (String corner : cubeCorners.keySet()) {
                        Double[] point = cubeCorners.get(corner);

                        //Scale the point on each axis
                        point = scalePoint(point, scale_x, scale_y, scale_z, rotation_origin);

                        cubeCorners.put(corner, point);

                    }
                }


                //Rotate the cube again
                /*for (String corner : cubeCorners.keySet())
                    cubeCorners.put(corner, WavefrontUtility.rotatePoint(cubeCorners.get(corner), matrixRotation, rotation_origin));*/

            }
        }


        //Loop through the cube corners and to rotate them if variant specifies that it should be rotated
        if(rotationX != null || rotationY != null) {
            for (String corner : cubeCorners.keySet()) {
                if (rotationX != null)
                    cubeCorners.put(corner, rotatePoint(cubeCorners.get(corner), rotationX, Constants.BLOCK_ORIGIN));

                if (rotationY != null)
                    cubeCorners.put(corner, rotatePoint(cubeCorners.get(corner), rotationY, Constants.BLOCK_ORIGIN));
            }
        }

        //Get element faces
        HashMap<String, CubeElement.CubeFace> elementFaces = element.getFaces();

        //Check if cube only has one face
        if(elementFaces.size() == 1 && cubeModel.getCubes().size() > 0){
            //If it has, check if it overlaps with any faces, and increase the distance between the overlapping faces
            avoidOverlapping(Orientation.getOrientation(elementFaces.keySet().stream().findFirst().get()), cubeCorners, cubeModel.getCubes());
        }

        //Append cube corner to object vertices and get the indexes to vertex's and normals in verticesAndNormals
        /*for (String corner : cubeCorners.keySet()){
            int VertexIndex = 0;
            if(vertices.containsKey(cubeCorners.get(corner)))
                VertexIndex = vertices.getIndex(cubeCorners.get(corner));
            else
                VertexIndex = vertices.put(cubeCorners.get(corner));
            CornersIndex.put(corner, VertexIndex);
        }*/




        //Map to store what material is used per face before any kind of rotation has been done on the faces
        //Key: Orientation, value: material name
        HashMap<Orientation, String> materialPerOrientation = new HashMap<>();

        for (String faceName : elementFaces.keySet()) {
            CubeElement.CubeFace face = elementFaces.get(faceName);

            Orientation faceOrientation = Orientation.getOrientation(faceName);

            //Get the variable of the face (ex. #all)
            String faceTextureVariable = face.getTexture().substring(1);
            //Get the actual value of the material variable (ex. blocks/dirt)
            String faceMaterial = modelsMaterials.get(faceTextureVariable);

            materialPerOrientation.put(faceOrientation, faceMaterial);
        }

        for (String faceName : elementFaces.keySet()) {

            CubeElement.CubeFace face = elementFaces.get(faceName);

            Orientation faceOrientation = Orientation.getOrientation(faceName);

            //Get the face uv's, or set them, if It's not defined in the uv field
            List<Double[]> faceUV = setAndRotateUVFace(face, faceOrientation, to, from);

            if (uvLock) {
                if (rotationX != null) {
                    //If face orientation is west or east rotate the uv coords by the rotation X
                    //on the origin 0.5 0.5
                    if (faceOrientation.equals(Orientation.WEST) || faceOrientation.equals(Orientation.EAST))
                        rotateUV(faceUV, rotationX.getRot(), new Double[]{0.5, 0.5, 0.0});
                    else {
                        /*//Else rotate orientationCoord by closest right angle (ex, 100 -> 90)
                        ArrayVector.MatrixRotation rotation = rotationX;

                        //If the rotation angle on x isn't a right angle, move the uv coords by the modulu 90 (ex. 100 % 90 = 10 / 90 -> 0.1111..)
                        if (rotationX.getRot() % 90 != 0) {
                            Double angle = rotation.getRot() / 90;
                            rotation = new ArrayVector.MatrixRotation(Math.floor(angle), "X");
                            Double offsetX = (rotationX.getRot() % 90) / 90;
                            faceUV = WavefrontUtility.offsetUV(faceUV, offsetX, 0.0);
                        }*/
                        Double[] newOrientationCoord = new Double[]{faceOrientation.getXOffset().doubleValue(), faceOrientation.getYOffset().doubleValue(), faceOrientation.getZOffset().doubleValue()};
                        newOrientationCoord = rotatePoint(newOrientationCoord, rotationX, new Double[]{0.0, 0.0, 0.0});
                        faceOrientation = Orientation.getOrientation(newOrientationCoord[0].intValue(), newOrientationCoord[1].intValue(), newOrientationCoord[2].intValue());
                    }
                }

                if (rotationY != null) {
                    //If face orientation is top or down, rotate by the rotation Y
                    //on the origin 0.5 0.5
                    if (faceOrientation.equals(Orientation.UP) || faceOrientation.equals(Orientation.DOWN))
                        rotateUV(faceUV, rotationY.getRot(), new Double[]{0.5, 0.5, 0.0});
                    else {
                        /*//Else rotate orientationCoord by closest right angle (ex, 100 -> 90)
                        ArrayVector.MatrixRotation rotation = rotationY;

                        //If the rotation angle on x isn't a right angle, move the uv coords by the modulu 90 (ex. 100 % 90 = 10 / 90 -> 0.1111..)
                        if (rotationY.getRot() % 90 != 0) {
                            Double angle = rotation.getRot() / 90;
                            rotation = new ArrayVector.MatrixRotation(Math.floor(angle), "Z");
                            Double offsetY = (rotationX.getRot() % 90) / 90;
                            faceUV = WavefrontUtility.offsetUV(faceUV, 0.0, offsetY);
                        }*/

                        Double[] newOrientationCoord = new Double[]{faceOrientation.getXOffset().doubleValue(), faceOrientation.getYOffset().doubleValue(), faceOrientation.getZOffset().doubleValue()};
                        newOrientationCoord = rotatePoint(newOrientationCoord, rotationY, new Double[]{0.0, 0.0, 0.0});
                        faceOrientation = Orientation.getOrientation(newOrientationCoord[0].intValue(), newOrientationCoord[1].intValue(), newOrientationCoord[2].intValue());
                    }
                }
            }else{
                if(rotationX != null){
                    if(!faceOrientation.equals(Orientation.WEST) && !faceOrientation.equals(Orientation.EAST)){
                        Double[] newOrientationCoord = new Double[]{faceOrientation.getXOffset().doubleValue(), faceOrientation.getYOffset().doubleValue(), faceOrientation.getZOffset().doubleValue()};
                        newOrientationCoord = rotatePoint(newOrientationCoord, rotationX, new Double[]{0.0, 0.0, 0.0});
                        faceOrientation = Orientation.getOrientation(newOrientationCoord[0].intValue(), newOrientationCoord[1].intValue(), newOrientationCoord[2].intValue());
                    }
                }

                if(rotationY != null){
                    if(!faceOrientation.equals(Orientation.DOWN) && !faceOrientation.equals(Orientation.UP)) {
                        Double[] newOrientationCoord = new Double[]{faceOrientation.getXOffset().doubleValue(), faceOrientation.getYOffset().doubleValue(), faceOrientation.getZOffset().doubleValue()};
                        newOrientationCoord = rotatePoint(newOrientationCoord, rotationY, new Double[]{0.0, 0.0, 0.0});
                        faceOrientation = Orientation.getOrientation(newOrientationCoord[0].intValue(), newOrientationCoord[1].intValue(), newOrientationCoord[2].intValue());
                    }
                }
            }

            //Get the material of the the face
            String faceMaterial = materialPerOrientation.get((!uvLock || materialPerOrientation.size() == 1) ? Orientation.getOrientation(faceName) : ((rotationX != null && !faceOrientation.equals(Orientation.UP) && !faceOrientation.equals(Orientation.DOWN)) || (rotationY != null && (faceOrientation.equals(Orientation.UP) || faceOrientation.equals(Orientation.DOWN))) ? faceOrientation : Orientation.getOrientation(faceName)));

            //If the face material is null, ignore the face
            //Ex, if uv lock is on, the rotation x is 270, and the cube doesn't have a south face (meaning there is no material on that orientation)
            //The original orientation is up, and after applying the rotation of x 270 on orientation, the face orientation becomes south.
            //And since uv lock is on and the rotation is set to x so the materials per orientation stay the same, even after rotating the face,
            //the new orientation of the uv face, should not have any material on it, so we ignore it
            if(faceMaterial == null)
                continue;

            ///Append custom uv's cords to texture coordinates
            /*UVIndexes = new Integer[faceUV.size()];
            for (int c = 0; c < faceUV.size(); c++) {
                Double[] uv = faceUV.get(c);
                if (textureCoordinates.containsKey(uv[0], uv[1])) {
                    UVIndexes[c] = textureCoordinates.getIndex(uv[0], uv[1]);
                } else {
                    UVIndexes[c] = textureCoordinates.put(uv);
                }
            }*/

            //Create the wavefront face out of the cube face
            //ArrayList<Integer[]> wvFace = WavefrontUtility.createWavefrontFace(CornersIndex, UVIndexes, key);

            //ArrayList<Integer> boundingMaterialFaceIndexes = null;
            //String faceOrientation = null;

            //Check if added face has any transparent parts
            IMaterial material = Constants.BLOCK_MATERIALS.getMaterial(faceMaterial);
            boolean hasTransparency = ImageUtility.hasAlpha(material, faceUV);
            //If it has, change the material to the transparent variant of the material
            if(hasTransparency){
                //Get the transparent variant name of the material
                String transparentTextureName = String.format("%s_transparent", faceMaterial);
                //Check if the transparent variant doesn't exist yet, and create it by cloning the base material
                if(!Constants.BLOCK_MATERIALS.containsMaterial(transparentTextureName)){
                    Constants.BLOCK_MATERIALS.setMaterial(transparentTextureName, material.clone());

                    //Modify the clone of the base material
                    IMaterial transparent_material = Constants.BLOCK_MATERIALS.getMaterial(transparentTextureName);
                    transparent_material.setName(String.format("%s_transparent", transparent_material.getName()));
                    transparent_material.setTransparency(true);
                }

                //Check if cube model doesn't have the transparent variant of the material yet, and create it
                //Add transparent variant of the material to the cube model material, if it does not exist yet
                cubeModel.putMaterial(transparentTextureName);

                //Finally set the face material to the transparent variant of the material
                faceMaterial = transparentTextureName;
            }


            boolean isCullface = face.getCullface() != null;
            //Get the face index from the faceOrientation
            Integer faceIndex = faceOrientation.getOrder();

            //Get the corners names for the original face orientation
            String[] cornerNames = getCornerPerOrientation(Orientation.getOrientation(faceName));
            //Create Double[] list to store the face vertices
            List<Double[]> faceCorners = new ArrayList<>();
            for(String cornerName : cornerNames){
                faceCorners.add(cubeCorners.get(cornerName));
            }

            //Mark which material the face uses
            materialFaces[faceIndex] = cubeModel.getMaterials().getIndex(faceMaterial);
            //Mark that the face is generated
            generatedFaces[faceIndex] = true;

            //Create cube face and append it to the cube
            CubeFace cubeFace = new CubeFace(faceCorners, faceUV, faceMaterial, isCullface);
            cubeFaces[faceIndex] = cubeFace;
        }

        //Create cube from cube faces
        Cube cube = new Cube(materialFaces, generatedFaces, cubeFaces);
        //Append the cube to the cube model
        cubeModel.addCube(cube);
    }

    /**
     * Return the name of the texture path (ex. blocks/dirt -> dirt)
     * @param texture The texture path
     * @return The name of the texture
     */
    public static String textureName(String texture){
        if(texture.contains("/")){
            return texture.substring(texture.lastIndexOf("/") + 1);
        }else if(texture.contains("\\"))
            return texture.substring(texture.lastIndexOf("\\") + 1);
        else
            return texture;
    }



    /*
    public static void convertCubeToWavefront(CubeElement element, boolean uvLock, ArrayVector.MatrixRotation rotationX, ArrayVector.MatrixRotation rotationY, HashedDoubleList vertices, HashedDoubleList textureCoordinates, HashMap<String, ArrayList<ArrayList<Integer[]>>> faces, HashMap<String, HashMap<String, ArrayList<Integer>>> boundingFaces, HashMap<String, String> modelsMaterials){
        //Variable to store cube corner and their vertices index
        //Key: corner (ex. A), Value: Index of vertex
        Map<String, Integer> CornersIndex = new HashMap<>();
        //Variable to store index of UV's
        Integer[] UVIndexes = new Integer[]{0,1,2,3};

        //Get starting point of cube
        Double[] from = element.getFrom();
        //Get end point of cube
        Double[] to = element.getTo();

        //Create vertices for each corner of a face that the cube uses and add them to the object vertices
        Map<String, Double[]> cubeCorners = createCubeVerticesFromPoints(from, to, element.getFaces().keySet(), rotationX, rotationY);


        //Check if element has rotation
        if (element.getRotation() != null) {
            CubeElement.CubeRotation cubeRotation = element.getRotation();

            //Construct matrix rotation based on the axis and angle
            ArrayVector.MatrixRotation matrixRotation = new ArrayVector.MatrixRotation(-cubeRotation.getAngle(), cubeRotation.getAxis());

            Double[] rotation_origin = Constants.BLOCK_ORIGIN;
            if(cubeRotation.getOrigin() != null)
                rotation_origin = cubeRotation.getOrigin();

            //loop through the corners (vertices) and rotate each vertex
            for (String corner : cubeCorners.keySet())
                cubeCorners.put(corner, rotatePoint(cubeCorners.get(corner), matrixRotation, rotation_origin));

            if(cubeRotation.getRescale()){
                //The min/max values (points) of axis
                Double[] min_x = null;
                Double[] max_x = null;
                Double[] min_y = null;
                Double[] max_y = null;
                Double[] min_z = null;
                Double[] max_z = null;

                //Find min/max values of axis
                for(String corner : cubeCorners.keySet()){
                    Double[] vert = cubeCorners.get(corner);

                    if(min_x == null){
                        min_x = vert;
                        max_x = min_x;
                        min_y = vert;
                        max_y = min_y;
                        min_z = vert;
                        max_z = min_z;
                    }else{
                        if(vert[0] > max_x[0])
                            max_x = vert;
                        if(vert[0] < min_x[0])
                            min_x = vert;

                        if(vert[1] > max_y[1])
                            max_y = vert;
                        if(vert[1] < min_y[1])
                            min_y = vert;

                        if(vert[2] > max_z[2])
                            max_z = vert;
                        if(vert[2] < min_z[2])
                            min_z = vert;
                    }
                }

                //Temporary rotate the cube back and scale
                /*ArrayVector.MatrixRotation counterRotation = new ArrayVector.MatrixRotation(cubeRotation.getAngle(), cubeRotation.getAxis());
                for (String corner : cubeCorners.keySet())
                    cubeCorners.put(corner, WavefrontUtility.rotatePoint(cubeCorners.get(corner), counterRotation, rotation_origin));*/

                /*Double scale_x = null;
                Double scale_y = null;
                Double scale_z = null;

                //Calculate by how to scale (multiply) each point on each axis to get a full block

                switch (cubeRotation.getAxis()){
                    case "X":
                        //If element has more than one face, calulcate the pre-scale hypotenuse based on the entire cube
                        //Else calculate it based on the face
                        if(element.getFaces().size() > 1) {
                            double desiredHypotenuse = 1.0 / Math.cos(Math.toRadians(cubeRotation.getAngle()));
                            double pre_scaleHypotenuse = (max_z[1] - min_z[1]) / Math.cos(Math.toRadians(cubeRotation.getAngle()));
                            scale_y = desiredHypotenuse / pre_scaleHypotenuse;
                            scale_z = scale_y;
                            scale_x = 1.0;
                        }
                        else {
                            String faceOrientation = element.getFaces().keySet().stream().findFirst().get();
                            switch (faceOrientation){
                                case "up":
                                case "down":
                                case "east":
                                case "west":
                                    //Get the line segment end points of the face, when viewing the face straight on from x axis (the abscissa is the y axis, and the ordinate is the z axis)
                                    /*-------------
                                      | B1        |
                                      |   .       |
                                      |    .      |
                                      |     A1    |
                                      -------------
                                     */
                                    /*Double[] A1 = new Double[]{min_y[1], min_z[2]};
                                    Double[] B1 = new Double[]{max_y[1], max_z[2]};

                                    Double[] A = new Double[]{0.0, 0.0};
                                    Double[] B = new Double[]{0.0, 0.0};

                                    getBoundingLineSegment(A1, B1, A, B);
                                    //Replace the y and z values of the faces, with the A and B points

                                    for(String corner : cubeCorners.keySet()){
                                        Double[] vert = cubeCorners.get(corner);
                                        if(vert[1].equals(min_y[1]))
                                            vert[1] = A[0];
                                        else if(vert[1].equals(max_y[1]))
                                            vert[1] = B[0];

                                        if(vert[2].equals(min_z[2]))
                                            vert[2] = A[1];
                                        else if(vert[2].equals(max_z[1]))
                                            vert[2] = B[1];
                                    }
                                case "north":
                                case "south":
                                    scale_y = 1.0 / (max_y[1] - min_y[1]);
                                    scale_z = 1.0 / (max_z[2] - min_z[2]);
                                    scale_x = 1.0;
                            }
                        }


                        break;
                    case "Y":
                        if(element.getFaces().size() > 1) {
                            double desiredHypotenuse = 1.0 / Math.cos(Math.toRadians(cubeRotation.getAngle()));
                            double pre_scaleHypotenuse = (max_z[0] - min_z[0]) / Math.cos(Math.toRadians(cubeRotation.getAngle()));

                            scale_x = desiredHypotenuse / pre_scaleHypotenuse;
                            scale_z = scale_x;
                            scale_y = 1.0;
                        }else {
                            String faceOrientation = element.getFaces().keySet().stream().findFirst().get();
                            switch (faceOrientation){
                                case "up":
                                case "down":
                                case "south":
                                case "north":
                                    //Get the line segment end points of the face, when viewing the face straight on from y axis (the abscissa is the x axis, and the ordinate is the z axis)
                                    /*-------------
                                      | B1        |
                                      |   .       |
                                      |    .      |
                                      |     A1    |
                                      -------------
                                     */
                                    /*Double[] A1 = new Double[]{min_x[0], min_z[2]};
                                    Double[] B1 = new Double[]{max_x[0], max_z[2]};

                                    Double[] A = new Double[]{0.0, 0.0};
                                    Double[] B = new Double[]{0.0, 0.0};

                                    getBoundingLineSegment(A1, B1, A, B);
                                    //Replace the y and z values of the faces, with the A and B points

                                    for(String corner : cubeCorners.keySet()){
                                        Double[] vert = cubeCorners.get(corner);
                                        if(vert[0].equals(min_x[0]))
                                            vert[0] = A[0];
                                        else if(vert[0].equals(max_x[0]))
                                            vert[0] = B[0];

                                        if(vert[2].equals(min_z[2]))
                                            vert[2] = A[1];
                                        else if(vert[2].equals(max_z[1]))
                                            vert[2] = B[1];
                                    }
                                case "west":
                                case "east":
                                    scale_x = 1.0 / (max_x[0] - min_x[0]);
                                    scale_z = 1.0 / (max_z[2] - min_z[2]);
                                    scale_y = 1.0;
                            }
                        }
                        break;
                    case "Z":
                        scale_x = 1.0 / (max_x[0] - min_x[0]);
                        scale_y = 1.0 / (max_y[1] - min_y[1]);
                        scale_z = 1.0;

                        break;
                }

                if(scale_x != null || scale_y != null || scale_z != null) {
                    for (String corner : cubeCorners.keySet()) {
                        Double[] point = cubeCorners.get(corner);

                        //Scale the point on each axis
                        point = scalePoint(point, scale_x, scale_y, scale_z, rotation_origin);

                        cubeCorners.put(corner, point);

                    }
                }


                //Rotate the cube again
                /*for (String corner : cubeCorners.keySet())
                    cubeCorners.put(corner, WavefrontUtility.rotatePoint(cubeCorners.get(corner), matrixRotation, rotation_origin));*/

            /*}
        }


        //Loop through the cube corners and to rotate them if variant specifies that it should be rotated
        if(rotationX != null || rotationY != null) {
            for (String corner : cubeCorners.keySet()) {
                if (rotationX != null)
                    cubeCorners.put(corner, rotatePoint(cubeCorners.get(corner), rotationX, Constants.BLOCK_ORIGIN));

                if (rotationY != null)
                    cubeCorners.put(corner, rotatePoint(cubeCorners.get(corner), rotationY, Constants.BLOCK_ORIGIN));
            }
        }

        //Get element faces
        HashMap<String, CubeElement.CubeFace> elementFaces = element.getFaces();

        //Check if cube only has one face
        if(elementFaces.size() == 1 && faces.size() > 0){
            //If it has, check if it overlaps with any faces, and increase the distance between the overlapping faces
            avoidOverlapping(elementFaces.keySet().stream().findFirst().get(), cubeCorners, vertices, faces);
        }

        //Append cube corner to object vertices and get the indexes to vertex's and normals in verticesAndNormals
        for (String corner : cubeCorners.keySet()){
            int VertexIndex = 0;
            if(vertices.containsKey(cubeCorners.get(corner)))
                VertexIndex = vertices.getIndex(cubeCorners.get(corner));
            else
                VertexIndex = vertices.put(cubeCorners.get(corner));
            CornersIndex.put(corner, VertexIndex);
        }




        //Map to store what material is used per face before any kind of rotation has been done on the faces
        //Key: Orientation in coords (ex. up -> 0,0,1, east -> 1,0,0...)
        HashMap<List<Integer>, String> materialPerOrientation = new HashMap<>();

        for (String originalOrientation : elementFaces.keySet()) {
            CubeElement.CubeFace face = elementFaces.get(originalOrientation);

            //Get the coord of the orientation of the face
            List<Integer> orientationCoord = orientationToCoords(originalOrientation);

            //Get the variable of the face (ex. #all)
            String faceTextureVariable = face.getTexture().substring(1);
            //Get the actual value of the material variable (ex. blocks/dirt)
            String faceMaterial = modelsMaterials.get(faceTextureVariable);

            materialPerOrientation.put(orientationCoord, faceMaterial);

            if (!faces.containsKey(faceMaterial)) {
                //Create an empty collection which It's key is the face material, if faces does not contain it yet
                ArrayList<ArrayList<Integer[]>> textureFaces = new ArrayList<>();
                faces.put(faceMaterial, textureFaces);
            }
        }

        for (String key : elementFaces.keySet()) {

            CubeElement.CubeFace face = elementFaces.get(key);

            //Get the coord of the orientation of the face
            List<Integer> orientationCoord = orientationToCoords(key);

            //Get the face uv's, or set them, if It's not defined in the uv field
            ArrayList<Double[]> faceUV = WavefrontUtility.setAndRotateUVFace(face, key, to, from);

            if (uvLock) {
                if (rotationX != null) {
                    //If face orientation is west or east rotate the uv coords by the rotation X
                    //on the origin 0.5 0.5
                    if (key.equals("west") || key.equals("east"))
                        rotateUV(faceUV, rotationX.getRot(), new Double[]{0.5, 0.5, 0.0});
                    else {*/
                        /*
                        //Else rotate orientationCoord by closest right angle (ex, 100 -> 90)
                        ArrayVector.MatrixRotation rotation = rotationX;

                        //If the rotation angle on x isn't a right angle, move the uv coords by the modulu 90 (ex. 100 % 90 = 10 / 90 -> 0.1111..)
                        if (rotationX.getRot() % 90 != 0) {
                            Double angle = rotation.getRot() / 90;
                            rotation = new ArrayVector.MatrixRotation(Math.floor(angle), "X");
                            Double offsetX = (rotationX.getRot() % 90) / 90;
                            faceUV = WavefrontUtility.offsetUV(faceUV, offsetX, 0.0);
                        }*/
                        /*Double[] newOrientationCoord = new Double[]{orientationCoord.get(0).doubleValue(), orientationCoord.get(1).doubleValue(), orientationCoord.get(2).doubleValue()};
                        newOrientationCoord = rotatePoint(newOrientationCoord, rotationX, new Double[]{0.0, 0.0, 0.0});
                        orientationCoord.set(0, newOrientationCoord[0].intValue());
                        orientationCoord.set(1, newOrientationCoord[1].intValue());
                        orientationCoord.set(2, newOrientationCoord[2].intValue());
                    }
                }

                if (rotationY != null) {
                    //If face orientation is top or down, rotate by the rotation Y
                    //on the origin 0.5 0.5
                    if (orientationCoord.get(2) == 1 || orientationCoord.get(2) == -1)
                         rotateUV(faceUV, rotationY.getRot(), new Double[]{0.5, 0.5, 0.0});
                    else {*/
                        /*//Else rotate orientationCoord by closest right angle (ex, 100 -> 90)
                        ArrayVector.MatrixRotation rotation = rotationY;

                        //If the rotation angle on x isn't a right angle, move the uv coords by the modulu 90 (ex. 100 % 90 = 10 / 90 -> 0.1111..)
                        if (rotationY.getRot() % 90 != 0) {
                            Double angle = rotation.getRot() / 90;
                            rotation = new ArrayVector.MatrixRotation(Math.floor(angle), "Z");
                            Double offsetY = (rotationX.getRot() % 90) / 90;
                            faceUV = WavefrontUtility.offsetUV(faceUV, 0.0, offsetY);
                        }*/
                        /*Double[] newOrientationCoord = new Double[]{orientationCoord.get(0).doubleValue(), orientationCoord.get(1).doubleValue(), orientationCoord.get(2).doubleValue()};
                        newOrientationCoord = rotatePoint(newOrientationCoord, rotationY, new Double[]{0.0, 0.0, 0.0});
                        orientationCoord.set(0, newOrientationCoord[0].intValue());
                        orientationCoord.set(1, newOrientationCoord[1].intValue());
                        orientationCoord.set(2, newOrientationCoord[2].intValue());
                    }
                }
            }else{
                if(rotationX != null){
                    if(!key.equals("west") && !key.equals("east")){
                        Double[] newOrientationCoord = new Double[]{orientationCoord.get(0).doubleValue(), orientationCoord.get(1).doubleValue(), orientationCoord.get(2).doubleValue()};
                        newOrientationCoord = rotatePoint(newOrientationCoord, rotationX, new Double[]{0.0, 0.0, 0.0});
                        orientationCoord.set(0, newOrientationCoord[0].intValue());
                        orientationCoord.set(1, newOrientationCoord[1].intValue());
                        orientationCoord.set(2, newOrientationCoord[2].intValue());
                    }
                }

                if(rotationY != null){
                    if(orientationCoord.get(2) != -1 && orientationCoord.get(2) != 1) {
                        Double[] newOrientationCoord = new Double[]{orientationCoord.get(0).doubleValue(), orientationCoord.get(1).doubleValue(), orientationCoord.get(2).doubleValue()};
                        newOrientationCoord = rotatePoint(newOrientationCoord, rotationY, new Double[]{0.0, 0.0, 0.0});
                        orientationCoord.set(0, newOrientationCoord[0].intValue());
                        orientationCoord.set(1, newOrientationCoord[1].intValue());
                        orientationCoord.set(2, newOrientationCoord[2].intValue());
                    }
                }
            }

            //Get the material of the the face
            String faceMaterial = materialPerOrientation.get((!uvLock || materialPerOrientation.size() == 1) ? orientationToCoords(key) : ((rotationX != null && (orientationCoord.get(2) != 1 || orientationCoord.get(2) != -1)) || (rotationY != null && ( orientationCoord.get(2) == 1 || orientationCoord.get(2) == -1)) ? orientationCoord : orientationToCoords(key)));

            //If the face material is null, ignore the face
            //Ex, if uv lock is on, the rotation x is 270, and the cube doesn't have a south face (meaning there is no material on that orientation)
            //The original orientation is up, and after applying the rotation of x 270 on orientation, the face orientation becomes south.
            //And since uv lock is on and the rotation is set to x so the materials per orientation stay the same, even after rotating the face,
            //the new orientation of the uv face, should not have any material on it, so we ignore it
            if(faceMaterial == null)
                continue;

            ///Append custom uv's cords to texture coordinates
            UVIndexes = new Integer[faceUV.size()];
            for (int c = 0; c < faceUV.size(); c++) {
                Double[] uv = faceUV.get(c);
                if (textureCoordinates.containsKey(uv[0], uv[1])) {
                    UVIndexes[c] = textureCoordinates.getIndex(uv[0], uv[1]);
                } else {
                    UVIndexes[c] = textureCoordinates.put(uv);
                }
            }

            //Create the wavefront face out of the cube face
            ArrayList<Integer[]> wvFace = WavefrontUtility.createWavefrontFace(CornersIndex, UVIndexes, key);

            ArrayList<Integer> boundingMaterialFaceIndexes = null;
            String faceOrientation = null;

            //Check if added face has any transparent parts
            IMaterial material = Constants.BLOCK_MATERIALS.getMaterial(faceMaterial);
            boolean hasTransparency = ImageUtility.hasAlpha(material, faceUV);
            //If it has, change the material to the transparent variant of the material
            if(hasTransparency){
                //Get the transparent variant name of the material
                String transparentTextureName = String.format("%s_transparent", faceMaterial);
                //Check if the transparent variant doesn't exist yet, and create it by cloning the base material
                if(!Constants.BLOCK_MATERIALS.containsMaterial(transparentTextureName)){
                    Constants.BLOCK_MATERIALS.setMaterial(transparentTextureName, material.clone());

                    //Modify the clone of the base material
                    IMaterial transparent_material = Constants.BLOCK_MATERIALS.getMaterial(transparentTextureName);
                    transparent_material.setName(String.format("%s_transparent", transparent_material.getName()));
                    transparent_material.setTransparency(true);
                }

                //Check if faces doesn't have a entry with the transparent variant of the texture yet, and create it
                if(!faces.containsKey(transparentTextureName)){
                    ArrayList<ArrayList<Integer[]>> materialFaces = new ArrayList<>();
                    faces.put(transparentTextureName, materialFaces);
                }

                //Finally set the face material to the transparent variant of the material
                faceMaterial = transparentTextureName;
            }

            //Face is a cullface, add it to the the boundingFace
            if (face.getCullface() != null) {
                //Get the orientation from the orientationCoord
                faceOrientation = coordOrientationToOrientation(orientationCoord);


                if (faceOrientation != null) {
                    //Put orientation if It's not yet present
                    if (!boundingFaces.containsKey(faceOrientation))
                        boundingFaces.put(faceOrientation, new HashMap<>());

                    //Get the material faces of orientation
                    HashMap<String, ArrayList<Integer>> boundingMaterialFaces = boundingFaces.get(faceOrientation);

                    //Put material into bounding material faces if It's not yet present
                    if (!boundingMaterialFaces.containsKey(faceMaterial)){
                        boundingMaterialFaces.put(faceMaterial, new ArrayList<>());
                        boundingFaces.put(faceOrientation, boundingMaterialFaces);
                    }

                    boundingMaterialFaceIndexes = boundingMaterialFaces.get(faceMaterial);
                }
            }



            //Append the wavefront face to the collection faces of the material, that the new face uses
            ArrayList<ArrayList<Integer[]>> textureFaces = faces.get(faceMaterial);
            textureFaces.add(wvFace);//Add the index of the added face to the face indexes the material that faces the bounding box bounds
            if (faceOrientation != null) {
                boundingMaterialFaceIndexes.add(textureFaces.size() - 1);
            }
        }
    }
    */
}

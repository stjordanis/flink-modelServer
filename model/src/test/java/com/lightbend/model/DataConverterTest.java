package com.lightbend.model;

import com.google.protobuf.ByteString;
import com.lightbend.model.tensorflow.TensorFlowBundleModel;
import org.dmg.pmml.*;
import org.jpmml.evaluator.InputField;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;

public class DataConverterTest {

    private static byte[] baddata = new byte[0];


    private static String tfmodeloptimized = "model/TF/optimized/optimized_WineQuality.pb";
    private static String tfmodelsaved = "model/TF/saved/";
    private static String pmmlmodel = "model/PMML/winequalityDecisionTreeClassification.pmml";
    private static String name = "test";
    private static String description = "test";
    private static String dataType = "simple";

    private static String bundleTag = "serve";
    private static String bundleSignature = "serving_default";
    private static String bundleInputs = "inputs";
    private static TensorFlowBundleModel.Field input = new TensorFlowBundleModel.Field("image_tensor", null, Arrays.asList(-1, -1, -1, 3));
    private static List<String> bundleoutputs = Arrays.asList("detection_classes", "detection_boxes", "num_detections","detection_scores");
    private static List<TensorFlowBundleModel.Field> output = Arrays.asList(
            new TensorFlowBundleModel.Field("detection_classes", null, Arrays.asList(-1, 100)),
            new TensorFlowBundleModel.Field("detection_boxes", null, Arrays.asList(-1, 100, 4)),
            new TensorFlowBundleModel.Field("num_detections", null, Arrays.asList(-1)),
            new TensorFlowBundleModel.Field("detection_scores", null, Arrays.asList(-1, 100))
    );


    // PMML input fields
    // InputField{name=fixed acidity, dataType=DOUBLE, opType=CONTINUOUS},
    // InputField{name=volatile acidity, dataType=DOUBLE, opType=CONTINUOUS},
    // InputField{name=residual sugar, dataType=DOUBLE, opType=CONTINUOUS},
    // InputField{name=free sulfur dioxide, dataType=DOUBLE, opType=CONTINUOUS},
    // InputField{name=total sulfur dioxide, dataType=DOUBLE, opType=CONTINUOUS},
    // InputField{name=pH, dataType=DOUBLE, opType=CONTINUOUS},
    // InputField{name=sulphates, dataType=DOUBLE, opType=CONTINUOUS},
    // InputField{name=alcohol, dataType=DOUBLE, opType=CONTINUOUS}]


    private static DataField[] pmmlInputs = {
            new DataField(new FieldName("fixed acidity"), OpType.CONTINUOUS, DataType.DOUBLE),
            new DataField(new FieldName("volatile acidity"), OpType.CONTINUOUS, DataType.DOUBLE),
            new DataField(new FieldName("residual sugar"), OpType.CONTINUOUS, DataType.DOUBLE),
            new DataField(new FieldName("free sulfur dioxide"), OpType.CONTINUOUS, DataType.DOUBLE),
            new DataField(new FieldName("total sulfur dioxide"), OpType.CONTINUOUS, DataType.DOUBLE),
            new DataField(new FieldName("pH"), OpType.CONTINUOUS, DataType.DOUBLE),
            new DataField(new FieldName("sulphates"), OpType.CONTINUOUS, DataType.DOUBLE),
            new DataField(new FieldName("alcohol"), OpType.CONTINUOUS, DataType.DOUBLE)};

    @BeforeClass
    public static void oneTimeSetUp() {
        // Set resolver
        DataConverter.setResolver(new SimpleFactoryResolver());
    }

    @Test
    public void testPMML() {
        // Get PMML model from File
        byte[] model = getModel(pmmlmodel);
        // Build input record
        byte[] record = getbinaryContent(model, null, Modeldescriptor.ModelDescriptor.ModelType.PMML);
        // Convert input record
        Optional<ModelToServe> result = DataConverter.convertModel(record);
        // validate it
        validateModelToServe(result, model, null, Modeldescriptor.ModelDescriptor.ModelType.PMML);
        // Build PMML model
        Optional<Model> pmml = DataConverter.toModel(result.get());

        // Validate
        assertTrue("PMML Model created", pmml.isPresent());
        valdatePMMLModel(pmml.get());

        // Simply copy the model
        Model copyDirect = DataConverter.copy(pmml.get());
        // Validate
        assertEquals("Copy equal to source", pmml.get(), copyDirect);

        // Create model from binary
        Model direct = DataConverter.restore(Modeldescriptor.ModelDescriptor.ModelType.PMML.getNumber(), model);

        // Validate it
        valdatePMMLModel(direct);
    }

    @Test
    public void testPMMLBadData() {
        // Get PMML model from File
        byte[] model = new byte[0];
        // Build input record
        byte[] record = getbinaryContent(model, null, Modeldescriptor.ModelDescriptor.ModelType.PMML);
        // Convert input record
        Optional<ModelToServe> result = DataConverter.convertModel(record);
        // validate it
        validateModelToServe(result, model, null, Modeldescriptor.ModelDescriptor.ModelType.PMML);
        // Build PMML model
        Optional<Model> pmml = DataConverter.toModel(result.get());

        // Validate
        assertFalse("PMML Model is not created", pmml.isPresent());
    }

    @Test
    public void testTFOptimized() {
        // Get TF model from File
        byte[] model = getModel(tfmodeloptimized);
        // Build input record
        byte[] record = getbinaryContent(model, null, Modeldescriptor.ModelDescriptor.ModelType.TENSORFLOW);
        // Convert input record
        Optional<ModelToServe> result = DataConverter.convertModel(record);
        // validate it
        validateModelToServe(result, model, null, Modeldescriptor.ModelDescriptor.ModelType.TENSORFLOW);
        // Build TF model
        Optional<Model> tf = DataConverter.toModel(result.get());

        // Validate
        assertTrue("TF Model created", tf.isPresent());
        valdateTFModel(tf.get());

        // Simply copy the model
        Model copyDirect = DataConverter.copy(tf.get());
        // Validate
        assertEquals("Copy equal to source", tf.get(), copyDirect);

        // Create model from binary
        Model direct = DataConverter.restore(Modeldescriptor.ModelDescriptor.ModelType.TENSORFLOW.getNumber(), model);

        // Validate it
        valdateTFModel(direct);
    }

    @Test
    public void testTFOptimizedBadData() {
        // Get TF model from File
        byte[] model = new byte[0];
        // Build input record
        byte[] record = getbinaryContent(model, null, Modeldescriptor.ModelDescriptor.ModelType.TENSORFLOW);
        // Convert input record
        Optional<ModelToServe> result = DataConverter.convertModel(record);
        // validate it
        validateModelToServe(result, model, null, Modeldescriptor.ModelDescriptor.ModelType.TENSORFLOW);
        // Build TF model
        Optional<Model> tf = DataConverter.toModel(result.get());

        // Validate
        assertFalse("TF Model created", tf.isPresent());
    }

    @Test
    public void testTFBundled() {
        // Get TF model from File
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(tfmodelsaved).getFile());
        String model = file.getPath();
        // Build input record
        byte[] record = getbinaryContent(null, model, Modeldescriptor.ModelDescriptor.ModelType.TENSORFLOWSAVED);
        // Convert input record
        Optional<ModelToServe> result = DataConverter.convertModel(record);
        // validate it
        validateModelToServe(result, null, model, Modeldescriptor.ModelDescriptor.ModelType.TENSORFLOWSAVED);
        // Build TF model
        Optional<Model> tf = DataConverter.toModel(result.get());

        // Validate
        assertTrue("TF Model created", tf.isPresent());
        valdateTFBundleModel(tf.get());

        // Simply copy the model
        Model copyDirect = DataConverter.copy(tf.get());
        // Validate
        assertEquals("Copy equal to source", tf.get(), copyDirect);
        valdateTFBundleModel(copyDirect);

        // Create model from binary
        Model direct = DataConverter.restore(Modeldescriptor.ModelDescriptor.ModelType.TENSORFLOWSAVED.getNumber(), model.getBytes());

        // Validate it
        valdateTFBundleModel(direct);
    }

    @Test
    public void testTFBundledBadData() {
        String model = new String();
        // Build input record
        byte[] record = getbinaryContent(null, model, Modeldescriptor.ModelDescriptor.ModelType.TENSORFLOWSAVED);
        // Convert input record
        Optional<ModelToServe> result = DataConverter.convertModel(record);
        // validate it
        validateModelToServe(result, null, model, Modeldescriptor.ModelDescriptor.ModelType.TENSORFLOWSAVED);
        // Build TF model
        Optional<Model> tf = DataConverter.toModel(result.get());

        // Validate
        assertFalse("TF Model is not created", tf.isPresent());
    }

        private void valdatePMMLModel(Model pmml){
        assertTrue(pmml instanceof SimplePMMLModel);
        SimplePMMLModel pmmlModel = (SimplePMMLModel)pmml;
        assertNotEquals("PMML is created",null, pmmlModel.getPmml());
        assertNotEquals("PMML Evaluator is created", null, pmmlModel.getEvaluator());
        assertEquals("PMML input is correct", "quality", pmmlModel.getTname().toString());
        Iterator<InputField> inputsIterator = pmmlModel.getInputFields().iterator();
        for(DataField field : pmmlInputs) {
            Field<?> recieved = inputsIterator.next().getField();
            assertEquals("Output field name", field.getName().getValue(), recieved.getName().getValue());
            assertEquals("Output field type",field.getDataType().value(), recieved.getDataType().value());
            assertEquals("Output field operation type",field.getOpType().value(), recieved.getOpType().value());
        }
    }

    private void valdateTFModel(Model tf) {
        assertTrue(tf instanceof SimpleTensorflowModel);
        SimpleTensorflowModel tfModel = (SimpleTensorflowModel) tf;
        assertNotEquals("Graph is created",null, tfModel.getGrapth());
        assertNotEquals("Session is created",null, tfModel.getSession());
    }

    private void valdateTFBundleModel(Model tf) {
        assertTrue(tf instanceof SimpleTensorFlowBundleModel);
        SimpleTensorFlowBundleModel tfModel = (SimpleTensorFlowBundleModel) tf;
        assertNotEquals("Graph is created",null, tfModel.getGraph());
        assertNotEquals("Session is created",null, tfModel.getSession());
        assertEquals(1, tfModel.getTags().size());
        assertEquals("Tag is correct", bundleTag, tfModel.getTags().get(0));
        assertEquals("Number of signatures is correct",1, tfModel.getSignatures().size());
        Map.Entry<String, TensorFlowBundleModel.Signature> sigEntry = tfModel.getSignatures().entrySet().iterator().next();
        assertEquals("Signature name is correct", bundleSignature, sigEntry.getKey());
        TensorFlowBundleModel.Signature sign = sigEntry.getValue();
        assertEquals("Number of inputs is correct",1, sign.getInputs().size());
        Map.Entry<String, TensorFlowBundleModel.Field> inputEntry = sign.getInputs().entrySet().iterator().next();
        assertEquals("Input name is correct", bundleInputs, inputEntry.getKey());
        assertEquals("Input name is correct", input.getName(), inputEntry.getValue().getName());
        assertArrayEquals("Input shape is correct", input.getShape().toArray(new Integer[0]), inputEntry.getValue().getShape().toArray(new Integer[0]));
        assertEquals("Number of outputs is correct",4, sign.getOutputs().size());
        Iterator<TensorFlowBundleModel.Field> outputIterator = output.iterator();
        for (String outputName : bundleoutputs) {
            TensorFlowBundleModel.Field current = sign.getOutputs().get(outputName);
            assertNotEquals("Output name is correct", null, current);
            TensorFlowBundleModel.Field field = outputIterator.next();
            assertEquals("Output name is correct", field.getName(), current.getName());
            assertArrayEquals("Output shape is correct", field.getShape().toArray(new Integer[0]), current.getShape().toArray(new Integer[0]));
        }
    }

    private void validateModelToServe(Optional<ModelToServe> modelToServe, byte[] model, String location, Modeldescriptor.ModelDescriptor.ModelType type){
        assertTrue("Model is created", modelToServe.isPresent());
        assertEquals("Model type is correct", type, modelToServe.get().getModelType());
        assertEquals("Data type is correct", dataType, modelToServe.get().getDataType());
        assertEquals("Model name is correct", name, modelToServe.get().getName());
        assertEquals("Model description is correct", description, modelToServe.get().getDescription());
        if(model != null)
            assertArrayEquals("Model data is correct", model, modelToServe.get().getModelData());
        else
            assertEquals("Model location is correct", location, modelToServe.get().getModelDataLocation());
    }

    private byte[] getModel(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        try {
            return Files.readAllBytes(Paths.get(file.getPath()));
        }catch(Throwable t){
            return baddata;
        }
    }

    private byte[] getbinaryContent(byte[] pByteArray, String location, Modeldescriptor.ModelDescriptor.ModelType type) {

        Modeldescriptor.ModelDescriptor.Builder builder = Modeldescriptor.ModelDescriptor.newBuilder();
        builder.setModeltype(type);
        builder.setDataType(dataType);
        if(location != null)
            builder.setLocation(location);
        else
            builder.setData(ByteString.copyFrom(pByteArray));
        builder.setName(name);
        builder.setDescription(description);
        Modeldescriptor.ModelDescriptor record = builder.build();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try{
            record.writeTo(bos);
            return bos.toByteArray();
        }catch(Throwable t){
            return baddata;
        }
    }
}

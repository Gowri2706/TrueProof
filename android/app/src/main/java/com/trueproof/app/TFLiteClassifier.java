package com.trueproof.app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class TFLiteClassifier {

    private static final String MODEL_NAME = "trueproof_model.tflite";
    private static final int INPUT_SIZE = 224;
    private static final int PIXEL_SIZE = 3;

    private final Interpreter interpreter;

    public TFLiteClassifier(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        interpreter = new Interpreter(loadModelFile(context), options);
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor =
                context.getAssets().openFd(MODEL_NAME);

        FileInputStream inputStream =
                new FileInputStream(fileDescriptor.getFileDescriptor());

        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        MappedByteBuffer mappedByteBuffer =
                fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        startOffset,
                        declaredLength
                );

        fileChannel.close();
        inputStream.close();
        fileDescriptor.close();

        return mappedByteBuffer;
    }

    public Result classify(Bitmap bitmap) {

        Bitmap resized = Bitmap.createScaledBitmap(
                bitmap,
                INPUT_SIZE,
                INPUT_SIZE,
                false
        );

        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(
                INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE * 4
        );

        inputBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];

        resized.getPixels(
                pixels,
                0,
                INPUT_SIZE,
                0,
                0,
                INPUT_SIZE,
                INPUT_SIZE
        );

        for (int pixel : pixels) {

            float r = (pixel >> 16) & 0xFF;
            float g = (pixel >> 8) & 0xFF;
            float b = pixel & 0xFF;

            // IMPORTANT:
            // Model expects raw 0-255 RGB values.
            // Do NOT divide by 255 here.
            inputBuffer.putFloat(r);
            inputBuffer.putFloat(g);
            inputBuffer.putFloat(b);
        }

        inputBuffer.rewind();

        float[][] output = new float[1][1];

        interpreter.run(inputBuffer, output);

        float probabilityGenuine = output[0][0];

        String verdict;

        if (probabilityGenuine >= 0.5f) {
            verdict = "ORIGINAL";
        } else {
            verdict = "EDITED";
        }

        float confidence;

        if (probabilityGenuine >= 0.5f) {
            confidence = probabilityGenuine;
        } else {
            confidence = 1.0f - probabilityGenuine;
        }

        resized.recycle();

        return new Result(
                verdict,
                probabilityGenuine,
                confidence
        );
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
    }

    public static class Result {

        public final String verdict;
        public final float probabilityGenuine;
        public final float confidence;

        public Result(
                String verdict,
                float probabilityGenuine,
                float confidence
        ) {
            this.verdict = verdict;
            this.probabilityGenuine = probabilityGenuine;
            this.confidence = confidence;
        }
    }
}
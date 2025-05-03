package com.yuwen.centershipcontroller.Utils;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;

/**
 * @author COLORFUL
 */
public class QRCodeProcessor {
    private static final String TAG = "QRCodeProcessor";
    private final MultiFormatReader reader = new MultiFormatReader();
    private final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

    public QRCodeProcessor() {
        hints.put(DecodeHintType.POSSIBLE_FORMATS, BarcodeFormat.QR_CODE);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);
    }

    public interface DecodeCallback {
        void onDecodeSuccess(Result result);
        void onDecodeFailed();
    }

    public void decodeImage(@NonNull Image image, DecodeCallback callback) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            Log.e(TAG, "Unsupported image format");
            callback.onDecodeFailed();
            return;
        }

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        int width = image.getWidth();
        int height = image.getHeight();
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                data, width, height, 0, 0, width, height, false);

        processImage(new BinaryBitmap(new HybridBinarizer(source)), callback);
    }

    public void decodeBitmap(@NonNull Bitmap bitmap, DecodeCallback callback) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        byte[] data = new byte[pixels.length];

        for (int i = 0; i < pixels.length; i++) {
            data[i] = (byte) ((pixels[i] & 0xFF) == 0xFF ? 0 : 1);
        }

        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                data, bitmap.getWidth(), bitmap.getHeight(), 0, 0, bitmap.getWidth(), bitmap.getHeight(), false);

        processImage(new BinaryBitmap(new HybridBinarizer(source)), callback);
    }

    private void processImage(BinaryBitmap bitmap, @NonNull DecodeCallback callback) {
        try {
            Result result = reader.decodeWithState(bitmap);
            callback.onDecodeSuccess(result);
        } catch (NotFoundException e) {
            callback.onDecodeFailed();
        } finally {
            reader.reset();
        }
    }
}
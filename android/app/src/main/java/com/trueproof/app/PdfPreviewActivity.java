package com.trueproof.app;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PdfPreviewActivity extends AppCompatActivity {

    private File pdfFile;

    private ImageView imgPdfPreview;
    private Button btnSaveToDevice;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(
                LocaleHelper.onAttach(newBase)
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pdf_preview);

        imgPdfPreview =
                findViewById(R.id.imgPdfPreview);

        btnSaveToDevice =
                findViewById(R.id.btnSaveToDevice);

        btnSaveToDevice.setEnabled(false);

        btnSaveToDevice.setOnClickListener(
                v -> saveToDevice()
        );

        generateLocalPdf();
    }

    private void generateLocalPdf() {

        Toast.makeText(
                this,
                "Preparing PDF report...",
                Toast.LENGTH_SHORT
        ).show();

        String imagePath =
                getIntent().getStringExtra("image_path");

        String verdict =
                getIntent().getStringExtra("verdict");

        double confidenceValue =
                getIntent().getDoubleExtra(
                        "confidence",
                        0
                );

        int confidence =
                (int) Math.round(confidenceValue);

        String reasons =
                getIntent().getStringExtra("reasons");

        if (verdict == null
                || verdict.trim().isEmpty()) {

            verdict = "UNKNOWN";
        }

        try {

            pdfFile =
                    PdfReportGenerator.generate(
                            this,
                            imagePath,
                            verdict,
                            confidence,
                            reasons
                    );

            if (pdfFile == null
                    || !pdfFile.exists()
                    || pdfFile.length() == 0) {

                Toast.makeText(
                        this,
                        "PDF could not be created",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            renderPdfPreview();

        } catch (Exception e) {

            e.printStackTrace();

            Toast.makeText(
                    this,
                    "PDF generation failed:\n"
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void renderPdfPreview() {

        if (pdfFile == null
                || !pdfFile.exists()) {

            Toast.makeText(
                    this,
                    "PDF file not found",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        android.os.ParcelFileDescriptor fileDescriptor =
                null;

        PdfRenderer renderer =
                null;

        PdfRenderer.Page page =
                null;

        try {

            fileDescriptor =
                    android.os.ParcelFileDescriptor.open(
                            pdfFile,
                            android.os.ParcelFileDescriptor
                                    .MODE_READ_ONLY
                    );

            renderer =
                    new PdfRenderer(
                            fileDescriptor
                    );

            if (renderer.getPageCount() == 0) {

                Toast.makeText(
                        this,
                        "PDF has no pages",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            page =
                    renderer.openPage(0);

            int screenWidth =
                    getResources()
                            .getDisplayMetrics()
                            .widthPixels;

            int previewWidth =
                    screenWidth - dpToPx(40);

            if (previewWidth <= 0) {
                previewWidth = page.getWidth();
            }

            float ratio =
                    (float) page.getHeight()
                            / (float) page.getWidth();

            int previewHeight =
                    Math.round(
                            previewWidth * ratio
                    );

            Bitmap bitmap =
                    Bitmap.createBitmap(
                            previewWidth,
                            previewHeight,
                            Bitmap.Config.ARGB_8888
                    );

            bitmap.eraseColor(Color.WHITE);

            page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            );

            imgPdfPreview.setImageBitmap(bitmap);

            btnSaveToDevice.setEnabled(true);

            Toast.makeText(
                    this,
                    "PDF preview ready",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {

            e.printStackTrace();

            Toast.makeText(
                    this,
                    "Could not preview PDF:\n"
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

        } finally {

            try {
                if (page != null) {
                    page.close();
                }
            } catch (Exception ignored) {
            }

            try {
                if (renderer != null) {
                    renderer.close();
                }
            } catch (Exception ignored) {
            }

            try {
                if (fileDescriptor != null) {
                    fileDescriptor.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void saveToDevice() {

        if (pdfFile == null
                || !pdfFile.exists()
                || pdfFile.length() == 0) {

            Toast.makeText(
                    this,
                    "PDF is not ready",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String fileName =
                "TrueShotAI_Report_"
                        + System.currentTimeMillis()
                        + ".pdf";

        try {

            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.Q) {

                ContentValues values =
                        new ContentValues();

                values.put(
                        MediaStore.Downloads.DISPLAY_NAME,
                        fileName
                );

                values.put(
                        MediaStore.Downloads.MIME_TYPE,
                        "application/pdf"
                );

                values.put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS
                );

                values.put(
                        MediaStore.Downloads.IS_PENDING,
                        1
                );

                Uri uri =
                        getContentResolver()
                                .insert(
                                        MediaStore.Downloads
                                                .EXTERNAL_CONTENT_URI,
                                        values
                                );

                if (uri == null) {

                    throw new IOException(
                            "Could not create Downloads file"
                    );
                }

                try (
                        InputStream input =
                                new FileInputStream(pdfFile);

                        OutputStream output =
                                getContentResolver()
                                        .openOutputStream(uri)
                ) {

                    if (output == null) {

                        throw new IOException(
                                "Could not open Downloads file"
                        );
                    }

                    byte[] buffer =
                            new byte[8192];

                    int length;

                    while (
                            (length =
                                    input.read(buffer)) > 0
                    ) {

                        output.write(
                                buffer,
                                0,
                                length
                        );
                    }

                    output.flush();
                }

                ContentValues completedValues =
                        new ContentValues();

                completedValues.put(
                        MediaStore.Downloads.IS_PENDING,
                        0
                );

                getContentResolver().update(
                        uri,
                        completedValues,
                        null,
                        null
                );

            } else {

                File downloadsFolder =
                        Environment
                                .getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_DOWNLOADS
                                );

                if (!downloadsFolder.exists()) {

                    if (!downloadsFolder.mkdirs()) {

                        throw new IOException(
                                "Could not create Downloads folder"
                        );
                    }
                }

                File destination =
                        new File(
                                downloadsFolder,
                                fileName
                        );

                try (
                        InputStream input =
                                new FileInputStream(pdfFile);

                        OutputStream output =
                                new java.io.FileOutputStream(
                                        destination
                                )
                ) {

                    byte[] buffer =
                            new byte[8192];

                    int length;

                    while (
                            (length =
                                    input.read(buffer)) > 0
                    ) {

                        output.write(
                                buffer,
                                0,
                                length
                        );
                    }

                    output.flush();
                }
            }

            Toast.makeText(
                    this,
                    "PDF saved to Downloads",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {

            e.printStackTrace();

            Toast.makeText(
                    this,
                    "Failed to save PDF:\n"
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private int dpToPx(int dp) {

        return Math.round(
                dp * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
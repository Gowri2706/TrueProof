package com.trueproof.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PdfReportGenerator {

    // =========================================================
    // A4 PAGE
    // =========================================================

    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;


    // =========================================================
    // COLORS
    // =========================================================

    private static final int WHITE = Color.WHITE;
    private static final int BLACK = Color.rgb(30, 30, 30);

    private static final int DARK_GREEN =
            Color.rgb(25, 62, 52);

    private static final int GREEN =
            Color.rgb(30, 130, 85);

    private static final int LIGHT_GREEN =
            Color.rgb(232, 247, 239);

    private static final int RED =
            Color.rgb(190, 55, 70);

    private static final int LIGHT_RED =
            Color.rgb(252, 235, 238);

    private static final int GOLD =
            Color.rgb(180, 135, 45);

    private static final int GREY =
            Color.rgb(105, 105, 105);

    private static final int LIGHT_GREY =
            Color.rgb(245, 247, 246);

    private static final int BORDER =
            Color.rgb(210, 218, 214);


    // =========================================================
    // MAIN GENERATOR
    // =========================================================

    public static File generate(
            Context context,
            String imagePath,
            String verdict,
            int confidence,
            String reason
    ) throws IOException {

        PdfDocument document = new PdfDocument();

        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(
                        PAGE_WIDTH,
                        PAGE_HEIGHT,
                        1
                ).create();

        PdfDocument.Page page =
                document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();

        Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setTypeface(
                Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.NORMAL
                )
        );


        // =====================================================
        // WHITE PAGE
        // =====================================================

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(WHITE);

        canvas.drawRect(
                0,
                0,
                PAGE_WIDTH,
                PAGE_HEIGHT,
                paint
        );


        // =====================================================
        // BACKGROUND WATERMARK
        //
        // Drawn first so all report content appears above it.
        // =====================================================

        drawCenterWatermark(
                context,
                canvas
        );


        // =====================================================
        // VERDICT STATE
        // =====================================================

        boolean edited =
                verdict != null
                        && (
                        verdict.equalsIgnoreCase("EDITED")
                                || verdict.equalsIgnoreCase("FAKE")
                                || verdict.equalsIgnoreCase("SUSPICIOUS")
                );

        int verdictColor =
                edited ? RED : GREEN;

        int verdictBackground =
                edited ? LIGHT_RED : LIGHT_GREEN;

        String finalVerdict =
                verdict == null
                        || verdict.trim().isEmpty()
                        ? "UNKNOWN"
                        : verdict.toUpperCase(Locale.getDefault());


        // =====================================================
        // HEADER
        // =====================================================

        drawHeader(
                context,
                canvas,
                paint
        );


        // =====================================================
        // REPORT INFORMATION
        // =====================================================

        String reportId =
                "TSA-"
                        + new SimpleDateFormat(
                        "yyyyMMdd-HHmmss",
                        Locale.getDefault()
                ).format(new Date());

        String dateTime =
                new SimpleDateFormat(
                        "dd MMM yyyy · hh:mm a",
                        Locale.getDefault()
                ).format(new Date());


        float infoTop = 132;
        float boxHeight = 82;


        // =====================================================
        // LEFT INFO BOX
        // =====================================================

        drawRoundedBox(
                canvas,
                paint,
                new RectF(
                        32,
                        infoTop,
                        292,
                        infoTop + boxHeight
                ),
                WHITE,
                BORDER,
                8
        );


        // =====================================================
        // RIGHT RESULT BOX
        // =====================================================

        drawRoundedBox(
                canvas,
                paint,
                new RectF(
                        303,
                        infoTop,
                        563,
                        infoTop + boxHeight
                ),
                WHITE,
                BORDER,
                8
        );


        // =====================================================
        // LEFT LABELS
        // =====================================================

        drawText(
                canvas,
                paint,
                "REPORT ID",
                47,
                infoTop + 20,
                8,
                GREY,
                Typeface.NORMAL
        );


        drawText(
                canvas,
                paint,
                "DATE",
                47,
                infoTop + 51,
                8,
                GREY,
                Typeface.NORMAL
        );


        drawText(
                canvas,
                paint,
                "GENERATED BY",
                165,
                infoTop + 20,
                8,
                GREY,
                Typeface.NORMAL
        );


        drawText(
                canvas,
                paint,
                "True Shot AI",
                165,
                infoTop + 35,
                9,
                BLACK,
                Typeface.BOLD
        );


        drawText(
                canvas,
                paint,
                reportId,
                47,
                infoTop + 35,
                9,
                BLACK,
                Typeface.BOLD
        );


        drawText(
                canvas,
                paint,
                dateTime,
                47,
                infoTop + 66,
                9,
                BLACK,
                Typeface.BOLD
        );


        // =====================================================
        // RESULT BOX TEXT
        // =====================================================

        drawText(
                canvas,
                paint,
                "RESULT",
                318,
                infoTop + 20,
                8,
                GREY,
                Typeface.NORMAL
        );


        drawText(
                canvas,
                paint,
                finalVerdict,
                318,
                infoTop + 43,
                15,
                verdictColor,
                Typeface.BOLD
        );


        drawText(
                canvas,
                paint,
                "CONFIDENCE",
                455,
                infoTop + 20,
                8,
                GREY,
                Typeface.NORMAL
        );


        drawText(
                canvas,
                paint,
                confidence + "%",
                455,
                infoTop + 43,
                15,
                BLACK,
                Typeface.BOLD
        );


        // =====================================================
        // SUBMITTED SCREENSHOT TITLE
        // =====================================================

        float screenshotTitleY = 290;


        drawText(
                canvas,
                paint,
                "SUBMITTED SCREENSHOT",
                32,
                screenshotTitleY,
                11,
                DARK_GREEN,
                Typeface.BOLD
        );


        // =====================================================
        // SCREENSHOT
        // =====================================================

        RectF screenshotArea =
                new RectF(
                        32,
                        screenshotTitleY + 12,
                        563,
                        465
                );


        drawScreenshot(
                canvas,
                imagePath,
                screenshotArea
        );


        // =====================================================
        // FINDINGS TITLE
        // =====================================================

        float findingsTop = 505;


        drawText(
                canvas,
                paint,
                "AI ANALYSIS FINDINGS",
                32,
                findingsTop,
                11,
                DARK_GREEN,
                Typeface.BOLD
        );


        // =====================================================
        // FINDINGS TABLE HEADER
        // =====================================================

        float tableTop =
                findingsTop + 12;


        paint.setStyle(Paint.Style.FILL);
        paint.setColor(DARK_GREEN);


        canvas.drawRect(
                32,
                tableTop,
                563,
                tableTop + 28,
                paint
        );


        drawText(
                canvas,
                paint,
                "#",
                47,
                tableTop + 18,
                8,
                WHITE,
                Typeface.BOLD
        );


        drawText(
                canvas,
                paint,
                "AI ANALYSIS FINDING",
                77,
                tableTop + 18,
                8,
                WHITE,
                Typeface.BOLD
        );


        // =====================================================
        // FINDINGS
        // =====================================================

        float rowY =
                tableTop + 28;


        String[] findings =
                parseFindings(reason);


        int findingIndex = 1;


        for (String finding : findings) {

            if (finding == null) {
                continue;
            }


            finding =
                    finding.trim();


            if (finding.isEmpty()) {
                continue;
            }


            if (rowY > 555) {
                break;
            }


            paint.setStyle(Paint.Style.FILL);


            paint.setColor(
                    findingIndex % 2 == 0
                            ? WHITE
                            : LIGHT_GREY
            );


            canvas.drawRect(
                    32,
                    rowY,
                    563,
                    rowY + 27,
                    paint
            );


            drawText(
                    canvas,
                    paint,
                    String.valueOf(findingIndex),
                    48,
                    rowY + 18,
                    8,
                    GOLD,
                    Typeface.BOLD
            );


            String shortFinding =
                    shortenText(
                            finding,
                            67
                    );


            drawText(
                    canvas,
                    paint,
                    shortFinding,
                    77,
                    rowY + 18,
                    8,
                    BLACK,
                    Typeface.NORMAL
            );


            rowY += 27;

            findingIndex++;
        }


        // =====================================================
        // EMPTY FINDINGS
        // =====================================================

        if (findingIndex == 1) {

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(WHITE);


            canvas.drawRect(
                    32,
                    rowY,
                    563,
                    rowY + 27,
                    paint
            );


            drawText(
                    canvas,
                    paint,
                    "1",
                    48,
                    rowY + 18,
                    8,
                    GOLD,
                    Typeface.BOLD
            );


            drawText(
                    canvas,
                    paint,
                    "No additional findings were provided.",
                    77,
                    rowY + 18,
                    8,
                    GREY,
                    Typeface.NORMAL
            );


            rowY += 27;
        }


        // =====================================================
        // FINAL VERDICT
        // =====================================================

        float verdictTop =
                Math.max(
                        rowY + 14,
                        585
                );


        if (verdictTop > 635) {
            verdictTop = 635;
        }


        RectF verdictBox =
                new RectF(
                        32,
                        verdictTop,
                        563,
                        verdictTop + 54
                );


        drawRoundedBox(
                canvas,
                paint,
                verdictBox,
                verdictBackground,
                verdictColor,
                8
        );


        // =====================================================
        // VERDICT ICON
        // =====================================================

        if (edited) {

            drawWarningIcon(
                    canvas,
                    paint,
                    55,
                    verdictTop + 27,
                    verdictColor
            );

        } else {

            drawCheckIcon(
                    canvas,
                    paint,
                    55,
                    verdictTop + 27,
                    verdictColor
            );
        }


        drawText(
                canvas,
                paint,
                "FINAL VERDICT",
                78,
                verdictTop + 21,
                8,
                GREY,
                Typeface.BOLD
        );


        String finalText =
                edited
                        ? "The screenshot appears EDITED / FAKE."
                        : "The screenshot appears ORIGINAL.";


        drawText(
                canvas,
                paint,
                finalText,
                78,
                verdictTop + 40,
                11,
                verdictColor,
                Typeface.BOLD
        );


        // =====================================================
        // NOTES + IMPORTANT
        // =====================================================

        float notesTop =
                verdictTop + 75;


        drawText(
                canvas,
                paint,
                "NOTES",
                32,
                notesTop,
                9,
                DARK_GREEN,
                Typeface.BOLD
        );


        drawText(
                canvas,
                paint,
                "This report is generated automatically by True Shot AI.",
                32,
                notesTop + 17,
                7,
                GREY,
                Typeface.NORMAL
        );


        drawText(
                canvas,
                paint,
                "AI analysis is intended as verification assistance.",
                32,
                notesTop + 30,
                7,
                GREY,
                Typeface.NORMAL
        );


        drawText(
                canvas,
                paint,
                "IMPORTANT",
                315,
                notesTop,
                9,
                DARK_GREEN,
                Typeface.BOLD
        );


        drawText(
                canvas,
                paint,
                "This result should not be treated as independent",
                315,
                notesTop + 17,
                7,
                GREY,
                Typeface.NORMAL
        );


        drawText(
                canvas,
                paint,
                "financial proof or confirmation of a transaction.",
                315,
                notesTop + 30,
                7,
                GREY,
                Typeface.NORMAL
        );


        // =====================================================
        // FOOTER
        // =====================================================

        float footerY =
                PAGE_HEIGHT - 43;


        paint.setStyle(Paint.Style.FILL);
        paint.setColor(BORDER);


        canvas.drawRect(
                32,
                footerY,
                563,
                footerY + 1,
                paint
        );


        drawText(
                canvas,
                paint,
                "Page 1 / 1",
                32,
                footerY + 19,
                7,
                GREY,
                Typeface.NORMAL
        );


        drawText(
                canvas,
                paint,
                "Generated by True Shot AI",
                225,
                footerY + 19,
                7,
                GREY,
                Typeface.NORMAL
        );


        drawText(
                canvas,
                paint,
                "This is a computer-generated report",
                380,
                footerY + 19,
                7,
                GREY,
                Typeface.NORMAL
        );


        // =====================================================
        // FINISH PAGE
        // =====================================================

        document.finishPage(page);


        // =====================================================
        // SAVE PDF
        // =====================================================

        File outputDir =
                context.getExternalFilesDir("reports");


        if (outputDir == null) {
            outputDir = context.getFilesDir();
        }


        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }


        String fileName =
                "TrueShotAI_Report_"
                        + new SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.getDefault()
                ).format(new Date())
                        + ".pdf";


        File outputFile =
                new File(
                        outputDir,
                        fileName
                );


        FileOutputStream outputStream =
                new FileOutputStream(outputFile);


        document.writeTo(outputStream);


        outputStream.flush();
        outputStream.close();


        document.close();


        return outputFile;
    }


    // =========================================================
    // HEADER
    // =========================================================

    private static void drawHeader(
            Context context,
            Canvas canvas,
            Paint paint
    ) {

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(DARK_GREEN);


        canvas.drawRect(
                0,
                0,
                PAGE_WIDTH,
                108,
                paint
        );


        // =====================================================
        // HEADER TITLE
        // =====================================================

        drawText(
                canvas,
                paint,
                "True Shot AI",
                32,
                38,
                21,
                WHITE,
                Typeface.BOLD
        );


        drawText(
                canvas,
                paint,
                "AI Screenshot Verification Report",
                32,
                67,
                11,
                Color.rgb(220, 235, 228),
                Typeface.NORMAL
        );


        drawText(
                canvas,
                paint,
                "Automated screenshot authenticity analysis",
                32,
                87,
                7,
                Color.rgb(190, 215, 205),
                Typeface.NORMAL
        );


        // =====================================================
        // CLEAR LOGO - TOP RIGHT
        // =====================================================

        Bitmap logo =
                BitmapFactory.decodeResource(
                        context.getResources(),
                        R.drawable.logo_header
                );


        if (logo != null) {

            Paint logoPaint =
                    new Paint(Paint.ANTI_ALIAS_FLAG);


            logoPaint.setFilterBitmap(true);
            logoPaint.setAlpha(255);


            RectF logoArea =
                    new RectF(
                            405,
                            0,
                            600,
                            108
                    );


            drawBitmapContain(
                    canvas,
                    logo,
                    logoArea,
                    logoPaint
            );


            logo.recycle();
        }
    }


    // =========================================================
    // BACKGROUND WATERMARK
    // =========================================================

    private static void drawCenterWatermark(
            Context context,
            Canvas canvas
    ) {

        Bitmap watermark =
                BitmapFactory.decodeResource(
                        context.getResources(),
                        R.drawable.logo_watermark
                );


        if (watermark == null) {
            return;
        }


        Paint watermarkPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);


        watermarkPaint.setFilterBitmap(true);

        // IMPORTANT:
        // logo_watermark.png already contains its own
        // transparency.
        //
        // Do NOT fade it again here.
        watermarkPaint.setAlpha(255);


        // =====================================================
        // WATERMARK POSITION + SIZE
        // =====================================================

        float size = 500f;

        float centerX = PAGE_WIDTH / 2f - 35f;

        float centerY =
                380f;


        RectF rect =
                new RectF(
                        centerX - size / 2f,
                        centerY - size / 2f,
                        centerX + size / 2f,
                        centerY + size / 2f
                );


        // =====================================================
        // DIAGONAL / CROSS WATERMARK
        // =====================================================

        canvas.save();


        canvas.rotate(
                -35f,
                centerX,
                centerY
        );


        canvas.drawBitmap(
                watermark,
                null,
                rect,
                watermarkPaint
        );


        canvas.restore();


        watermark.recycle();
    }


    // =========================================================
    // SCREENSHOT
    // =========================================================

    private static void drawScreenshot(
            Canvas canvas,
            String imagePath,
            RectF area
    ) {

        Bitmap screenshot = null;


        if (imagePath != null
                && !imagePath.trim().isEmpty()) {

            screenshot =
                    BitmapFactory.decodeFile(
                            imagePath
                    );
        }


        // =====================================================
        // SCREENSHOT NOT FOUND
        // =====================================================

        if (screenshot == null) {

            drawRoundedBox(
                    canvas,
                    new Paint(Paint.ANTI_ALIAS_FLAG),
                    area,
                    Color.rgb(248, 248, 248),
                    BORDER,
                    6
            );


            Paint textPaint =
                    new Paint(Paint.ANTI_ALIAS_FLAG);


            textPaint.setTypeface(
                    Typeface.DEFAULT
            );


            textPaint.setTextSize(8);
            textPaint.setColor(GREY);


            canvas.drawText(
                    "Screenshot unavailable",
                    area.centerX() - 45,
                    area.centerY(),
                    textPaint
            );


            return;
        }


        // =====================================================
        // SCREENSHOT IMAGE
        //
        // No shadow.
        // No background box.
        // =====================================================

        Paint imagePaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);


        imagePaint.setFilterBitmap(true);


        float maxWidth = 155;
        float maxHeight = 285;


        float scale =
                Math.min(
                        maxWidth / screenshot.getWidth(),
                        maxHeight / screenshot.getHeight()
                );


        float width =
                screenshot.getWidth() * scale;


        float height =
                screenshot.getHeight() * scale;


        // =====================================================
        // SCREENSHOT LEFT POSITION
        //
        // Moved slightly toward the left side.
        // =====================================================

        float left = 200f;


        float top =
                area.centerY()
                        - height / 2f;


        RectF destination =
                new RectF(
                        left,
                        top,
                        left + width,
                        top + height
                );


        canvas.drawBitmap(
                screenshot,
                null,
                destination,
                imagePaint
        );


        screenshot.recycle();
    }


    // =========================================================
    // FINDINGS PARSER
    // =========================================================

    private static String[] parseFindings(
            String reason
    ) {

        if (reason == null
                || reason.trim().isEmpty()) {

            return new String[0];
        }


        String normalized =
                reason.replace(
                        "\r\n",
                        "|"
                ).replace(
                        "\n",
                        "|"
                );


        return normalized.split("\\|");
    }


    // =========================================================
    // SHORTEN TEXT
    // =========================================================

    private static String shortenText(
            String text,
            int maxCharacters
    ) {

        if (text == null) {
            return "";
        }


        text = text.trim();


        if (text.length() <= maxCharacters) {
            return text;
        }


        return text.substring(
                0,
                Math.max(
                        0,
                        maxCharacters - 3
                )
        ) + "...";
    }


    // =========================================================
    // ROUNDED BOX
    // =========================================================

    private static void drawRoundedBox(
            Canvas canvas,
            Paint paint,
            RectF rect,
            int fillColor,
            int borderColor,
            float radius
    ) {

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fillColor);


        canvas.drawRoundRect(
                rect,
                radius,
                radius,
                paint
        );


        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(borderColor);


        canvas.drawRoundRect(
                rect,
                radius,
                radius,
                paint
        );


        paint.setStyle(Paint.Style.FILL);
    }


    // =========================================================
    // TEXT HELPER
    // =========================================================

    private static void drawText(
            Canvas canvas,
            Paint paint,
            String text,
            float x,
            float y,
            float size,
            int color,
            int style
    ) {

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);


        paint.setTypeface(
                Typeface.create(
                        Typeface.DEFAULT,
                        style
                )
        );


        canvas.drawText(
                text,
                x,
                y,
                paint
        );
    }


    // =========================================================
    // BITMAP CONTAIN
    // =========================================================

    private static void drawBitmapContain(
            Canvas canvas,
            Bitmap bitmap,
            RectF target,
            Paint paint
    ) {

        float bitmapWidth =
                bitmap.getWidth();


        float bitmapHeight =
                bitmap.getHeight();


        if (bitmapWidth <= 0
                || bitmapHeight <= 0) {

            return;
        }


        float scale =
                Math.min(
                        target.width() / bitmapWidth,
                        target.height() / bitmapHeight
                );


        float width =
                bitmapWidth * scale;


        float height =
                bitmapHeight * scale;


        float left =
                target.centerX()
                        - width / 2f;


        float top =
                target.centerY()
                        - height / 2f;


        RectF destination =
                new RectF(
                        left,
                        top,
                        left + width,
                        top + height
                );


        canvas.drawBitmap(
                bitmap,
                null,
                destination,
                paint
        );
    }


    // =========================================================
    // WARNING ICON
    // =========================================================

    private static void drawWarningIcon(
            Canvas canvas,
            Paint paint,
            float centerX,
            float centerY,
            int color
    ) {

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);


        Path triangle =
                new Path();


        triangle.moveTo(
                centerX,
                centerY - 11
        );


        triangle.lineTo(
                centerX - 11,
                centerY + 9
        );


        triangle.lineTo(
                centerX + 11,
                centerY + 9
        );


        triangle.close();


        canvas.drawPath(
                triangle,
                paint
        );


        canvas.drawLine(
                centerX,
                centerY - 5,
                centerX,
                centerY + 3,
                paint
        );


        canvas.drawCircle(
                centerX,
                centerY + 6,
                1,
                paint
        );


        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }


    // =========================================================
    // CHECK ICON
    // =========================================================

    private static void drawCheckIcon(
            Canvas canvas,
            Paint paint,
            float centerX,
            float centerY,
            int color
    ) {

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);


        Path check =
                new Path();


        check.moveTo(
                centerX - 9,
                centerY
        );


        check.lineTo(
                centerX - 3,
                centerY + 6
        );


        check.lineTo(
                centerX + 10,
                centerY - 8
        );


        canvas.drawPath(
                check,
                paint
        );


        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }
}

package com.trueproof.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "trueproof.db";
    private static final int DB_VERSION = 2;

    public static final String TABLE = "scan_history";
    public static final String COL_ID = "id";
    public static final String COL_SCAN_ID = "scan_id";
    public static final String COL_AMOUNT = "amount";
    public static final String COL_UPI = "upi_id";
    public static final String COL_VERDICT = "verdict";
    public static final String COL_CONFIDENCE = "confidence";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_IMAGE_PATH = "image_path";
    public static final String COL_USER_ID = "user_id";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SCAN_ID + " TEXT, " +
                COL_AMOUNT + " TEXT, " +
                COL_UPI + " TEXT, " +
                COL_VERDICT + " TEXT, " +
                COL_CONFIDENCE + " REAL, " +
                COL_TIMESTAMP + " INTEGER, " +
                COL_IMAGE_PATH + " TEXT, " +
                COL_USER_ID + " TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion) {

        if (oldVersion < 2) {
            db.execSQL(
                    "ALTER TABLE " + TABLE +
                            " ADD COLUMN " + COL_USER_ID + " TEXT"
            );
        }
    }

    // Save a scan under the currently logged-in user's ID.
    public long insertScan(HistoryItem item, String userId) {

        if (userId == null || userId.trim().isEmpty()) {
            return -1;
        }

        SQLiteDatabase db = getWritableDatabase();

        try {
            ContentValues values = new ContentValues();

            values.put(COL_SCAN_ID, item.scanId);
            values.put(COL_AMOUNT, item.amount);
            values.put(COL_UPI, item.upiId);
            values.put(COL_VERDICT, item.verdict);
            values.put(COL_CONFIDENCE, item.confidence);
            values.put(COL_TIMESTAMP, item.timestamp);
            values.put(COL_IMAGE_PATH, item.imagePath);
            values.put(COL_USER_ID, userId);

            return db.insert(TABLE, null, values);

        } finally {
            db.close();
        }
    }

    // Return only scans belonging to the requested user.
    public List<HistoryItem> getScansForUser(String userId) {

        List<HistoryItem> list = new ArrayList<>();

        if (userId == null || userId.trim().isEmpty()) {
            return list;
        }

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.query(
                TABLE,
                null,
                COL_USER_ID + " = ?",
                new String[]{userId},
                null,
                null,
                COL_TIMESTAMP + " DESC")) {

            while (c.moveToNext()) {
                HistoryItem item = new HistoryItem();

                item.scanId = c.getString(
                        c.getColumnIndexOrThrow(COL_SCAN_ID));

                item.amount = c.getString(
                        c.getColumnIndexOrThrow(COL_AMOUNT));

                item.upiId = c.getString(
                        c.getColumnIndexOrThrow(COL_UPI));

                item.verdict = c.getString(
                        c.getColumnIndexOrThrow(COL_VERDICT));

                item.confidence = c.getDouble(
                        c.getColumnIndexOrThrow(COL_CONFIDENCE));

                item.timestamp = c.getLong(
                        c.getColumnIndexOrThrow(COL_TIMESTAMP));

                item.imagePath = c.getString(
                        c.getColumnIndexOrThrow(COL_IMAGE_PATH));

                list.add(item);
            }

        } finally {
            db.close();
        }

        return list;
    }
}

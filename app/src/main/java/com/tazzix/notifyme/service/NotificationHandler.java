package com.tazzix.notifyme.service;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Icon;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.tazzix.notifyme.misc.Const;
import com.tazzix.notifyme.misc.DatabaseHelper;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class NotificationHandler {

	public static final String BROADCAST = "com.tazzix.notifyme.update";
	private static final String LOCK = "lock";

	private Context context;
	private SharedPreferences sp;

	// Access a Cloud Firestore instance from your Activity
	private FirebaseFirestore db = FirebaseFirestore.getInstance();

	NotificationHandler(Context context) {
		this.context = context;
		sp = PreferenceManager.getDefaultSharedPreferences(context);
	}

	void handlePosted(StatusBarNotification sbn) {
		if(sbn.isOngoing() && !sp.getBoolean(Const.PREF_ONGOING, false)) {
			if(Const.DEBUG) System.out.println("posted ongoing!");
			return;
		}
		boolean text = sp.getBoolean(Const.PREF_TEXT, true);
		NotificationObject no = new NotificationObject(context, sbn, text, -1);
		log(DatabaseHelper.PostedEntry.TABLE_NAME, DatabaseHelper.PostedEntry.COLUMN_NAME_CONTENT, no, true, sbn);
	}

	void handleRemoved(StatusBarNotification sbn, int reason) {
		if(sbn.isOngoing() && !sp.getBoolean(Const.PREF_ONGOING, false)) {
			if(Const.DEBUG) System.out.println("removed ongoing!");
			return;
		}
		NotificationObject no = new NotificationObject(context, sbn, false, reason);
		log(DatabaseHelper.RemovedEntry.TABLE_NAME, DatabaseHelper.RemovedEntry.COLUMN_NAME_CONTENT, no, false, sbn);
	}

	private void log(String tableName, String columnName, NotificationObject no, boolean added, StatusBarNotification sbn) {
		String content = no.toString();

		if (added) {
			String title = no.getTitle()!=null&&no.getTitle().length()>0?no.getTitle():no.getTitleBig();
			String text = no.getText()!=null&&no.getText().length()>0?no.getText():no.getTextBig();
			//Icon icon = sbn.getNotification().getLargeIcon()!=null?sbn.getNotification().getLargeIcon():sbn.getNotification().getSmallIcon();
			// Create a new user with a first and last name
			Map<String, Object> notification = new HashMap<>();
			ArrayList<Object> notifications = new ArrayList<>();
			notification.put("app", no.getAppName());
			notification.put("pkg", sbn.getPackageName());
			//notification.put("icon", icon);
			notification.put("text", text);
			notification.put("title", title);
			notification.put("subtext", no.getTextSub());
			notification.put("time", new Timestamp(new Date(no.getPostTime())));
			notifications.add(notification);

			Map<String, Object> user = new HashMap<>();
			user.put("UserID", System.currentTimeMillis()); // FIXME
			user.put("notifications", notifications);

			// Add a new document with a generated ID
			db.collection("users")
					.add(user)
					.addOnSuccessListener(documentReference -> Log.d("NOTIFYME", "DocumentSnapshot added with ID: " + documentReference.getId()))
					.addOnFailureListener(e -> Log.w("NOTIFYME", "Error adding document", e));
		}
		try {
			//if(content != null) {
				synchronized (LOCK) {
					DatabaseHelper dbHelper = new DatabaseHelper(context);
					SQLiteDatabase db = dbHelper.getWritableDatabase();
					ContentValues values = new ContentValues();
					values.put(columnName, content);
					db.insert(tableName, "null", values);
					db.close();
					dbHelper.close();
				}

				Intent local = new Intent();
				local.setAction(BROADCAST);
				LocalBroadcastManager.getInstance(context).sendBroadcast(local);
			//}
		} catch (Exception e) {
			if(Const.DEBUG) e.printStackTrace();
		}
	}

}

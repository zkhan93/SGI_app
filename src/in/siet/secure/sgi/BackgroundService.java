package in.siet.secure.sgi;

import in.siet.secure.Util.Utility;
import in.siet.secure.contants.Constants;
import in.siet.secure.dao.DbHelper;

import java.io.UnsupportedEncodingException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.content.LocalBroadcastManager;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.SyncHttpClient;

/**
 * SENDER IS ONE WHO IS SENDING US MESSAGE
 * 
 * @author swati
 * 
 */
public class BackgroundService extends Service {

	private SharedPreferences spref;
	static String TAG = "in.siet.secure.sgi.BackgroundActivity";
	String sender_lid; // ye bhejny waly ki b-11-136 jaisi id
	private WakeLock wake;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		spref = getApplicationContext().getSharedPreferences(
				Constants.pref_file_name, Context.MODE_PRIVATE);
		if (spref != null) {
			if ((sender_lid = spref
					.getString(Constants.PREF_KEYS.user_id, null)) != null)
				handleIntent(intent); // if logged in
		} else {
			Utility.log(TAG, "pref is null");
		}
		return START_STICKY;
	}

	/**
	 * Hold a partial wake lock so that you don't get killed ;)
	 */
	private void holdWakeLock() {
		PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
		if (wake == null)
			wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		if (!wake.isHeld())
			wake.acquire();
	}

	/**
	 * release the wake lock
	 */
	private void releaseLock() {
		if (wake != null && wake.isHeld())
			wake.release();
	}

	public void handleIntent(Intent intent) {

		holdWakeLock();
		// sendToServer();
		// getMessagesFromServer();
		sync();
	}

	public void getMessagesFromServer() {
		new GetMessagesFromServer().execute();
	}

	public void getNotificationsFromServer() {
		// new GetNotificationsFromServer().execute();
	}

	@Override
	public void onDestroy() {
		releaseLock();
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/**
	 * get data(messages and/or notification) from server if any and send
	 * acknowledgment for the same
	 * 
	 * @author Zeeshan Khan
	 * 
	 */
	private class GetMessagesFromServer extends
			AsyncTask<Void, JSONArray, JSONObject> {
		JSONObject ack_ids = new JSONObject();

		@Override
		protected JSONObject doInBackground(Void... params) {
			RequestParams reqparams = new RequestParams();
			Utility.putCredentials(reqparams, spref);
			SyncHttpClient client = new SyncHttpClient();

			final String user_id = spref.getString(Constants.PREF_KEYS.user_id,
					null); // this is me :D
			client.get(Utility.getBaseURL() + "query/give_me_messages",
					reqparams, new JsonHttpResponseHandler() {
						@Override
						public void onSuccess(int statusCode, Header[] headers,
								JSONArray response) {
							try {
								if (response.length() > 0) {

									DbHelper db = new DbHelper(
											getApplicationContext());

									ack_ids.put(
											Constants.JSONKEYS.MESSAGES.ACK,
											db.fillMessages(response, user_id));

									Intent intent = new Intent(
											getApplicationContext(),
											ChatActivity.class);

									Utility.buildNotification(
											getApplicationContext(),
											MainActivity.class, intent,
											"New Messages",
											"You have new messages");

								} else {
									Utility.log(TAG, "no messages");
								}
							} catch (Exception e) {
								Utility.DEBUG(e);
							}
						}

						@Override
						public void onFailure(int statusCode, Header[] headers,
								Throwable throwable, JSONObject errorResponse) {
							Utility.log(TAG, throwable.getLocalizedMessage());
						}

						@Override
						public void onFailure(int statusCode, Header[] headers,
								String responseString, Throwable throwable) {
							Utility.log(TAG, throwable.getLocalizedMessage());
						}

						@Override
						public void onFailure(int statusCode, Header[] headers,
								Throwable throwable, JSONArray errorResponse) {
							Utility.log(TAG, throwable.getLocalizedMessage());
						}
					});
			return ack_ids;
		}

		@Override
		protected void onPostExecute(JSONObject ack_ids) {
			sendAck(ack_ids);
		}
	}

	/**
	 * send JSONArray(2 size) containing JSONArray of messages and JSONArray of
	 * notifications (First two lines will be the users credentials to match) to
	 * server containing data to send and receive the the for itself if any
	 * 
	 * 1. get pending messages from database 2. get pending notifications from
	 * database in case user is faculty 3. create JSON string with user
	 * credentials out of data collected above 4. send to server 5. receive data
	 * from server (messages and notification) 6. fill database accordingly 7.
	 * trigger notification action
	 */
	public void sync() {
		JSONObject data_to_send = new JSONObject();
		StringBuilder strb = new StringBuilder();

		try {
			/**
			 * set user credentials
			 */
			strb.append(spref.getString(Constants.PREF_KEYS.encripted_user_id,
					null).trim());
			strb.append(Constants.NEW_LINE);

			strb.append(spref.getString(Constants.PREF_KEYS.token, null).trim());
			strb.append(Constants.NEW_LINE);
			strb.append(spref.getBoolean(Constants.PREF_KEYS.is_faculty, false));
			strb.append(Constants.NEW_LINE);
			/**
			 * set pending messages
			 */
			JSONArray pending_messages = getPendingMessages();
			if (pending_messages.length() > 0)
				data_to_send.put(Constants.JSONKEYS.MESSAGES.MESSAGES,
						pending_messages);

			/**
			 * set pending notifications if user is faculty
			 */
			if (spref.getBoolean(Constants.PREF_KEYS.is_faculty, false)) {
				JSONArray pending_notifications = getPendingNotifications();
				if (pending_notifications.length() > 0)
					data_to_send.put(
							Constants.JSONKEYS.NOTIFICATIONS.NOTIFICATIONS,
							pending_notifications);
			}
		} catch (JSONException e) {
			Utility.DEBUG(e);
		}
		/**
		 * convert to one data to be send, hit server only if you have some data
		 */
		if (data_to_send.has(Constants.JSONKEYS.NOTIFICATIONS.NOTIFICATIONS)
				|| data_to_send.has(Constants.JSONKEYS.MESSAGES.MESSAGES))
			strb.append(data_to_send.toString());

		HttpEntity body = null;
		try {
			body = new StringEntity(strb.toString());
		} catch (Exception e) {
			Utility.DEBUG(e);
		}
		/**
		 * send to server either credentials only or with some pending data
		 */
		Utility.log(TAG, "sending this " + strb.toString());
		new SendDataToServer().execute(body);

	}

	private JSONArray getPendingNotifications() {
		JSONArray notifications = new JSONArray();
		JSONObject notification;

		String query = "select n.text,n.subject,n.time,m.course,m.branch,m.year,m.section,n._id from notification as n join user_mapper as m on n.target=m._id and n.state=? and n.sender=(select _id from user where login_id=?)";
		SQLiteDatabase db = new DbHelper(getApplicationContext()).getDb();
		String[] args = { String.valueOf(Constants.STATE.PENDING),
				spref.getString(Constants.PREF_KEYS.user_id, null) };
		Cursor c = db.rawQuery(query, args);
		if (c.moveToFirst()) {
			while (!c.isAfterLast()) {
				try {
					notification = new JSONObject();
					notification.put(Constants.JSONKEYS.NOTIFICATIONS.TEXT,
							c.getString(0));
					notification.put(Constants.JSONKEYS.NOTIFICATIONS.SUBJECT,
							c.getString(1));
					notification.put(Constants.JSONKEYS.NOTIFICATIONS.TIME,
							c.getLong(2));
					notification.put(Constants.JSONKEYS.NOTIFICATIONS.COURSE,
							c.getString(3));
					notification.put(Constants.JSONKEYS.NOTIFICATIONS.BRANCH,
							c.getString(4));
					notification.put(Constants.JSONKEYS.NOTIFICATIONS.YEAR,
							c.getString(5));
					notification.put(Constants.JSONKEYS.NOTIFICATIONS.SECTION,
							c.getString(6));
					notification.put(Constants.JSONKEYS.NOTIFICATIONS.ID,
							c.getInt(7));
					notifications.put(notification);
				} catch (Exception e) {
					Utility.DEBUG(e);
				}
				c.moveToNext();
			}
			c.close();
		}

		return notifications;

	}

	private JSONArray getPendingMessages() {
		SQLiteDatabase db = new DbHelper(getApplicationContext()).getDb();
		String query = "select u.login_id,m.text,m.is_group_msg,m.time,m._id from messages as m join user as u on m.receiver=u._id where m.sender=(select _id from user where login_id=?) and m.state=?";
		String[] args = { spref.getString(Constants.PREF_KEYS.user_id, null),
				String.valueOf(Constants.STATE.PENDING) };
		Cursor c = db.rawQuery(query, args);
		JSONArray messages = new JSONArray();
		JSONObject message;
		if (c.moveToFirst()) {
			message = new JSONObject();
			while (!c.isAfterLast()) {
				try {
					// sender is the user no need t oput it in every message
					// object
					message.put(Constants.JSONKEYS.MESSAGES.RECEIVER,
							c.getString(0));
					message.put(Constants.JSONKEYS.MESSAGES.TEXT,
							c.getString(1));
					message.put(Constants.JSONKEYS.MESSAGES.IS_GROUP_MESSAGE,
							c.getInt(2));
					message.put(Constants.JSONKEYS.MESSAGES.TIME, c.getLong(3));
					message.put(Constants.JSONKEYS.MESSAGES.ID, c.getInt(4));
					messages.put(message);
				} catch (JSONException e) {
					Utility.DEBUG(e);
				}
				c.moveToNext();
			}
		}
		c.close();

		return messages;

	}

	/**
	 * Send acknowledgment for received messages and notifications
	 * 
	 * @param msg_and_noti_ids
	 *            {@link JSONObject} containing two {@link JSONArray} first one
	 *            will contain IDs of messages and second one will contain IDs
	 *            of notification
	 */
	public void sendAck(JSONObject msg_and_noti_ids) {
		AsyncHttpClient client = new AsyncHttpClient();
		StringBuilder strb = new StringBuilder();
		/**
		 * set user credentials
		 */
		strb.append(spref
				.getString(Constants.PREF_KEYS.encripted_user_id, null).trim());
		strb.append(Constants.NEW_LINE);
		strb.append(spref.getString(Constants.PREF_KEYS.token, null).trim());
		strb.append(Constants.NEW_LINE);
		strb.append(msg_and_noti_ids);
		HttpEntity entity = null;
		try {
			entity = new StringEntity(strb.toString());
		} catch (UnsupportedEncodingException e) {
			Utility.DEBUG(e);
		}
		Utility.log(TAG, "ack sending " + strb.toString());
		client.addHeader("Content-Type", "application/json");
		client.post(getApplicationContext(), Utility.getBaseURL()
				+ "query/receive_ack", entity, null,
				new JsonHttpResponseHandler() {
					@Override
					public void onSuccess(int statusCode, Header[] headers,
							JSONObject response) {
						Utility.log(TAG, "ack received " + response.toString());
					}

					@Override
					public void onFailure(int statusCode, Header[] headers,
							Throwable throwable, JSONObject errorResponse) {
						Utility.log(
								TAG,
								"on failure sendAck "
										+ throwable.getLocalizedMessage());
					}

					@Override
					public void onFailure(int statusCode, Header[] headers,
							String responseString, Throwable throwable) {
						Utility.log(
								TAG,
								"on failure sendAck "
										+ throwable.getLocalizedMessage());
					}

					@Override
					public void onFailure(int statusCode, Header[] headers,
							Throwable throwable, JSONArray errorResponse) {
						Utility.log(
								TAG,
								"on failure sendAck "
										+ throwable.getLocalizedMessage());
					}
				});
	}

	class SendDataToServer extends AsyncTask<HttpEntity, Void, Void> {

		JSONObject ids_to_send = new JSONObject();

		@Override
		protected Void doInBackground(HttpEntity... params) {
			SyncHttpClient client = new SyncHttpClient();

			client.addHeader("Content-Type", "application/json");
			client.post(getApplicationContext(), Utility.getBaseURL()
					+ "query/sync", params[0], null,
					new JsonHttpResponseHandler() {
						/**
						 * got data(messages and notifications and
						 * acknowledgment for the data we have send) form server
						 * insert it into database
						 */
						@Override
						public void onSuccess(int statusCode, Header[] headers,
								JSONObject response) {
							Utility.log(TAG, "received this: " + response);
							try {
								// get acks from data received from server and
								// update database
								JSONObject acks = new JSONObject();
								// get the ack ids for messages
								if (response
										.has(Constants.JSONKEYS.MESSAGES.ACK))
									acks.put(
											Constants.JSONKEYS.MESSAGES.ACK,
											response.getJSONArray(Constants.JSONKEYS.MESSAGES.ACK));
								// get ack ids for notifications
								if (response
										.has(Constants.JSONKEYS.NOTIFICATIONS.ACK))
									acks.put(
											Constants.JSONKEYS.NOTIFICATIONS.ACK,
											response.getJSONArray(Constants.JSONKEYS.NOTIFICATIONS.ACK));

								if (acks.has(Constants.JSONKEYS.NOTIFICATIONS.ACK)
										|| acks.has(Constants.JSONKEYS.MESSAGES.ACK))
									new DbHelper(getApplicationContext())
											.receivedAck(acks);

								// get notification and insert it into db(help
								// of
								// DbHelper)
								DbHelper db = new DbHelper(
										getApplicationContext());
								if (response
										.has(Constants.JSONKEYS.NOTIFICATIONS.NOTIFICATIONS)) {
									ids_to_send
											.put(Constants.JSONKEYS.NOTIFICATIONS.ACK,
													db.fillNotifications(response
															.getJSONArray(Constants.JSONKEYS.NOTIFICATIONS.NOTIFICATIONS)));
									sendBroadcast(Constants.LOCAL_INTENT_ACTION.RELOAD_NOTIFICATIONS);
								}
								// get messages and insert it into db(help of
								// DbHelper)
								if (response
										.has(Constants.JSONKEYS.MESSAGES.MESSAGES)) {
									ids_to_send.put(
											Constants.JSONKEYS.MESSAGES.ACK,
											db.fillMessages(
													response.getJSONArray(Constants.JSONKEYS.MESSAGES.MESSAGES),
													sender_lid));
								}

							} catch (JSONException e) {
								Utility.DEBUG(e);
							}
						}

						@Override
						public void onFailure(int statusCode, Header[] headers,
								String responseString, Throwable throwable) {
							Utility.log(
									TAG,
									"on failure sync "
											+ throwable.getLocalizedMessage());
						}

						@Override
						public void onFailure(int statusCode, Header[] headers,
								Throwable throwable, JSONArray errorResponse) {
							Utility.log(
									TAG,
									"on failure sync "
											+ throwable.getLocalizedMessage());
						}

						@Override
						public void onFailure(int statusCode, Header[] headers,
								Throwable throwable, JSONObject errorResponse) {
							Utility.log(
									TAG,
									"on failure sync "
											+ throwable.getLocalizedMessage());
						}
						// override all sucess and failure methods
					});
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			// on main thread
			if (ids_to_send.has(Constants.JSONKEYS.NOTIFICATIONS.ACK)
					|| ids_to_send.has(Constants.JSONKEYS.MESSAGES.ACK))
				sendAck(ids_to_send);
		}
	}

	private void sendBroadcast(String action) {
		Intent intent = new Intent(action);
		LocalBroadcastManager.getInstance(getApplicationContext())
				.sendBroadcast(intent);
	}
}
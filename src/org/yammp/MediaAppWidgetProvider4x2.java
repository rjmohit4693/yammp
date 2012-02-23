/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yammp;

import org.yammp.app.MusicBrowserActivity;
import org.yammp.app.MusicPlaybackActivity;
import org.yammp.util.MusicUtils;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Environment;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Simple widget to show currently playing album art along with play/pause and
 * next track buttons.
 */
public class MediaAppWidgetProvider4x2 extends AppWidgetProvider implements Constants {

	static final String TAG = "MusicAppWidgetProvider4x2";

	public static final String CMDAPPWIDGETUPDATE = "appwidgetupdate4x2";

	private static MediaAppWidgetProvider4x2 sInstance;

	static synchronized MediaAppWidgetProvider4x2 getInstance() {

		if (sInstance == null) {
			sInstance = new MediaAppWidgetProvider4x2();
		}
		return sInstance;
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

		defaultAppWidget(context, appWidgetIds);

		// Send broadcast intent to any running MediaPlaybackService so it can
		// wrap around with an immediate update.
		Intent updateIntent = new Intent(MusicPlaybackService.SERVICECMD);
		updateIntent.putExtra(MusicPlaybackService.CMDNAME,
				MediaAppWidgetProvider4x2.CMDAPPWIDGETUPDATE);
		updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
		updateIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
		context.sendBroadcast(updateIntent);
	}

	/**
	 * Initialize given widgets to default state, where we launch Music on
	 * default click and hide actions if service not running.
	 */
	private void defaultAppWidget(Context context, int[] appWidgetIds) {

		final Resources res = context.getResources();
		final RemoteViews views = new RemoteViews(context.getPackageName(),
				R.layout.album_appwidget4x2);

		views.setViewVisibility(R.id.albumname, View.GONE);
		views.setViewVisibility(R.id.trackname, View.GONE);
		views.setTextViewText(R.id.artistname, res.getText(R.string.widget_initial_text));
		views.setImageViewResource(R.id.albumart, R.drawable.ic_mp_albumart_unknown);

		linkButtons(context, views, false /* not playing */);
		pushUpdate(context, appWidgetIds, views);
	}

	private void pushUpdate(Context context, int[] appWidgetIds, RemoteViews views) {

		// Update specific list of appWidgetIds if given, otherwise default to
		// all
		final AppWidgetManager gm = AppWidgetManager.getInstance(context);
		if (appWidgetIds != null) {
			gm.updateAppWidget(appWidgetIds, views);
		} else {
			gm.updateAppWidget(new ComponentName(context, this.getClass()), views);
		}
	}

	/**
	 * Check against {@link AppWidgetManager} if there are any instances of this
	 * widget.
	 */
	private boolean hasInstances(Context context) {

		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, this
				.getClass()));
		return (appWidgetIds.length > 0);
	}

	/**
	 * Handle a change notification coming over from
	 * {@link MusicPlaybackService}
	 */
	void notifyChange(MusicPlaybackService service, String what) {

		if (hasInstances(service)) {
			if (BROADCAST_META_CHANGED.equals(what) || BROADCAST_PLAYSTATE_CHANGED.equals(what)
					|| BROADCAST_REPEATMODE_CHANGED.equals(what)
					|| BROADCAST_SHUFFLEMODE_CHANGED.equals(what)) {
				performUpdate(service, null);
			}
		}
	}

	/**
	 * Update all active widget instances by pushing changes
	 */
	void performUpdate(MusicPlaybackService service, int[] appWidgetIds) {

		final Resources res = service.getResources();
		final RemoteViews views = new RemoteViews(service.getPackageName(),
				R.layout.album_appwidget4x2);

		CharSequence artistName = service.getArtistName();
		CharSequence albumName = service.getAlbumName();
		CharSequence trackName = service.getTrackName();
		long albumId = service.getAlbumId();
		long songId = service.getAudioId();
		CharSequence errorState = null;

		// Format title string with track number, or show SD card message
		String status = Environment.getExternalStorageState();
		if (status.equals(Environment.MEDIA_SHARED) || status.equals(Environment.MEDIA_UNMOUNTED)) {
			errorState = res.getText(R.string.sdcard_busy_title);
		} else if (status.equals(Environment.MEDIA_REMOVED)) {
			errorState = res.getText(R.string.sdcard_missing_title);
		} else if (trackName == null) {
			errorState = res.getText(R.string.emptyplaylist);
		}

		if (errorState != null) {
			// Show error state to user
			views.setViewVisibility(R.id.albumname, View.GONE);
			views.setViewVisibility(R.id.trackname, View.GONE);
			views.setTextViewText(R.id.artistname, errorState);
			views.setImageViewResource(R.id.albumart, R.drawable.ic_mp_albumart_unknown);
		} else {
			// No error, so show normal titles and artwork
			views.setViewVisibility(R.id.albumname, View.VISIBLE);
			views.setViewVisibility(R.id.trackname, View.VISIBLE);
			views.setTextViewText(R.id.artistname, artistName);
			views.setTextViewText(R.id.albumname, albumName);
			views.setTextViewText(R.id.trackname, trackName);
			// Set album art
			Uri uri = MusicUtils.getArtworkUri(service, songId, albumId);
			if (uri != null) {
				views.setImageViewUri(R.id.albumart, uri);
			} else {
				views.setImageViewResource(R.id.albumart, R.drawable.ic_mp_albumart_unknown);
			}
		}

		// Set correct drawable for pause state
		final boolean playing = service.isPlaying();
		if (playing) {
			views.setImageViewResource(R.id.control_play, R.drawable.btn_playback_ic_pause);
		} else {
			views.setImageViewResource(R.id.control_play, R.drawable.btn_playback_ic_play);
		}

		// Set correct drawable for repeat state
		switch (service.getRepeatMode()) {
			case MusicPlaybackService.REPEAT_ALL:
				views.setImageViewResource(R.id.control_repeat, R.drawable.ic_mp_repeat_all_btn);
				break;
			case MusicPlaybackService.REPEAT_CURRENT:
				views.setImageViewResource(R.id.control_repeat, R.drawable.ic_mp_repeat_once_btn);
				break;
			default:
				views.setImageViewResource(R.id.control_repeat, R.drawable.ic_mp_repeat_off_btn);
				break;
		}

		// Set correct drawable for shuffle state
		switch (service.getShuffleMode()) {
			case SHUFFLE_NONE:
				views.setImageViewResource(R.id.control_shuffle, R.drawable.ic_mp_shuffle_off_btn);
				break;
			default:
				views.setImageViewResource(R.id.control_shuffle, R.drawable.ic_mp_shuffle_on_btn);
				break;
		}
		// Link actions buttons to intents
		linkButtons(service, views, playing);

		pushUpdate(service, appWidgetIds, views);
	}

	/**
	 * Link up various button actions using {@link PendingIntents}.
	 * 
	 * @param playerActive
	 *            True if player is active in background, which means widget
	 *            click will launch {@link MusicPlaybackActivity}, otherwise we
	 *            launch {@link MusicBrowserActivity}.
	 */
	private void linkButtons(Context context, RemoteViews views, boolean playerActive) {

		// Connect up various buttons and touch events
		Intent intent;
		PendingIntent pendingIntent;

		final ComponentName serviceName = new ComponentName(context, MusicPlaybackService.class);

		if (playerActive) {
			intent = new Intent(context, MusicPlaybackActivity.class);
			pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
			views.setOnClickPendingIntent(R.id.albumart, pendingIntent);
			views.setOnClickPendingIntent(R.id.info, pendingIntent);
		} else {
			intent = new Intent(context, MusicBrowserActivity.class);
			pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
			views.setOnClickPendingIntent(R.id.albumart, pendingIntent);
			views.setOnClickPendingIntent(R.id.info, pendingIntent);
		}

		intent = new Intent(MusicPlaybackService.TOGGLEPAUSE_ACTION);
		intent.setComponent(serviceName);
		pendingIntent = PendingIntent.getService(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.control_play, pendingIntent);

		intent = new Intent(MusicPlaybackService.NEXT_ACTION);
		intent.setComponent(serviceName);
		pendingIntent = PendingIntent.getService(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.control_next, pendingIntent);

		intent = new Intent(MusicPlaybackService.PREVIOUS_ACTION);
		intent.setComponent(serviceName);
		pendingIntent = PendingIntent.getService(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.control_prev, pendingIntent);

		intent = new Intent(MusicPlaybackService.CYCLEREPEAT_ACTION);
		intent.setComponent(serviceName);
		pendingIntent = PendingIntent.getService(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.control_repeat, pendingIntent);

		intent = new Intent(MusicPlaybackService.TOGGLESHUFFLE_ACTION);
		intent.setComponent(serviceName);
		pendingIntent = PendingIntent.getService(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.control_shuffle, pendingIntent);
	}
}

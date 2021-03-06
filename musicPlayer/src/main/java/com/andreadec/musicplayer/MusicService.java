/*
 * Copyright 2012-2019 Andrea De Cesare
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andreadec.musicplayer;

import java.io.*;
import java.util.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.media.MediaPlayer.*;
import android.media.audiofx.*;
import android.os.*;
import android.preference.*;
import android.telephony.*;
import android.view.KeyEvent;
import android.widget.RemoteViews;
import com.andreadec.musicplayer.models.*;

public class MusicService extends Service implements OnCompletionListener {
	private final static int METADATA_KEY_ARTWORK = 100;
	private final static int NOTIFICATION_ID = 1;
	private final static String NOTIFICATION_CHANNEL = "MusicPlayerNotification";
	private final static String WAKE_LOCK_TAG = "MusicPlayer:WakeLock";
	public final static int PLAY_MODE_NORMAL = 0, PLAY_MODE_SHUFFLE = 1, PLAY_MODE_REPEAT_ONE = 2, PLAY_MODE_REPEAT_ALL = 3;
	
	private final IBinder musicBinder = new MusicBinder();	
	private NotificationManager notificationManager;
	private NotificationChannel notificationChannel;
	private Notification notification;
	private SharedPreferences preferences;
	
	private PendingIntent pendingIntent;
	private PendingIntent quitPendingIntent;
	private PendingIntent previousPendingIntent;
	private PendingIntent playpausePendingIntent;
	private PendingIntent nextPendingIntent;
	
	private PlayableItem currentPlayingItem;
	
	private MediaPlayer mediaPlayer;
	private BassBoost bassBoost;
	private boolean bassBoostAvailable;
	
	private int playMode;
	private Random random;
	
	private TelephonyManager telephonyManager;
	private MusicPhoneStateListener phoneStateListener;
	private AudioManager audioManager;
	private ComponentName mediaButtonReceiverComponent;
	private BroadcastReceiver broadcastReceiver;
	
	private ShakeListener shakeListener;
	
	private Bitmap icon;
	private RemoteControlClient remoteControlClient;
	
	private PowerManager.WakeLock wakeLock;
	
	/**
	 * Called when the service is created.
	 */
	@Override
	public void onCreate() {
		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
		
		// Initialize the telephony manager
		telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
		phoneStateListener = new MusicPhoneStateListener();
		notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL, "Music Player", NotificationManager.IMPORTANCE_LOW);
			notificationManager.createNotificationChannel(notificationChannel);
		}
		
		// Initialize pending intents
		quitPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.andreadec.musicplayer.quit"), 0);
		previousPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.andreadec.musicplayer.previous"), 0);
		playpausePendingIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.andreadec.musicplayer.playpause"), 0);
		nextPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.andreadec.musicplayer.next"), 0);
		pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT);
		
		// Read saved user preferences
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
				
		// Initialize the media player
		mediaPlayer = new MediaPlayer();
		mediaPlayer.setOnCompletionListener(this);
		mediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK); // Enable the wake lock to keep CPU running when the screen is switched off
		
		playMode = preferences.getInt(Preferences.PREFERENCE_PLAY_MODE, Preferences.DEFAULT_PLAY_MODE);
		try { // This may fail if the device doesn't support bass boost
			bassBoost = new BassBoost(1, mediaPlayer.getAudioSessionId());
			bassBoost.setEnabled(preferences.getBoolean(Preferences.PREFERENCE_BASSBOOST, Preferences.DEFAULT_BASSBOOST));
			setBassBoostStrength(preferences.getInt(Preferences.PREFERENCE_BASSBOOSTSTRENGTH, Preferences.DEFAULT_BASSBOOSTSTRENGTH));
			bassBoostAvailable = true;
		} catch(Exception e) {
			bassBoostAvailable = false;
		}
		random = new Random(System.nanoTime()); // Necessary for song shuffle
		
		shakeListener = new ShakeListener(this);
		if(preferences.getBoolean(Preferences.PREFERENCE_SHAKEENABLED, Preferences.DEFAULT_SHAKEENABLED)) {
			shakeListener.enable();
		}
		
		telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE); // Start listen for telephony events
		
		// Inizialize the audio manager
		audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		mediaButtonReceiverComponent = new ComponentName(getPackageName(), MediaButtonReceiver.class.getName());
		audioManager.registerMediaButtonEventReceiver(mediaButtonReceiverComponent);
		audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
		
		// Initialize remote control client
        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(mediaButtonReceiverComponent);
        PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent, 0);

        remoteControlClient = new RemoteControlClient(mediaPendingIntent);
        remoteControlClient.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE | RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS | RemoteControlClient.FLAG_KEY_MEDIA_NEXT);
        audioManager.registerRemoteControlClient(remoteControlClient);
		
		updateNotification();
		
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.andreadec.musicplayer.quit");
		intentFilter.addAction("com.andreadec.musicplayer.previous");
		intentFilter.addAction("com.andreadec.musicplayer.previousNoRestart");
		intentFilter.addAction("com.andreadec.musicplayer.playpause");
		intentFilter.addAction("com.andreadec.musicplayer.next");
		intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            	String action = intent.getAction();

                switch(action) {
                    case "com.andreadec.musicplayer.quit":
                        sendBroadcast(new Intent("com.andreadec.musicplayer.quitactivity"));
                        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                        stopSelf();
                        return;
                    case "com.andreadec.musicplayer.previous":
                        previousItem(false);
                        break;
                    case "com.andreadec.musicplayer.previousNoRestart":
                        previousItem(true);
                        break;
                    case "com.andreadec.musicplayer.playpause":
                        playPause();
                        break;
                    case "com.andreadec.musicplayer.next":
                        nextItem();
                        break;
                    case AudioManager.ACTION_AUDIO_BECOMING_NOISY:
                        if(preferences.getBoolean(Preferences.PREFERENCE_STOPPLAYINGWHENHEADSETDISCONNECTED, Preferences.DEFAULT_STOPPLAYINGWHENHEADSETDISCONNECTED)) {
                            pause();
                        }
                        break;
                }
            }
        };
        registerReceiver(broadcastReceiver, intentFilter);
        
        if(!isPlaying()) {
        	loadLastSong();
        }
        
        startForeground(NOTIFICATION_ID, notification);
	}
	
	/* Called when service is started. */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}
	
	// Returns true if the song has been successfully loaded
	private void loadLastSong() {
		if(preferences.getBoolean(Preferences.PREFERENCE_OPENLASTSONGONSTART, Preferences.DEFAULT_OPENLASTSONGONSTART)) {
	        String lastPlayingSong = preferences.getString(Preferences.PREFERENCE_LASTPLAYINGSONG, Preferences.DEFAULT_LASTPLAYINGSONG);
        	long lastPlayingSongFromPlaylistId = preferences.getLong(Preferences.PREFERENCE_LASTPLAYINGSONGFROMPLAYLISTID, Preferences.DEFAULT_LASTPLAYINGSONGFROMPLAYLISTID);
        	if(lastPlayingSong!=null && (new File(lastPlayingSong).exists())) {
        		if(lastPlayingSongFromPlaylistId!=-1) {
        			PlaylistSong savedSong = Playlists.getSavedSongFromPlaylist(lastPlayingSongFromPlaylistId);
        			if(savedSong!=null) {
        				playItem(savedSong, false);
        			}
        		} else {
        			File songDirectory = new File(lastPlayingSong).getParentFile();
        			BrowserSong song = new BrowserSong(lastPlayingSong, new BrowserDirectory(songDirectory));
        			((MusicPlayerApplication)getApplication()).gotoDirectory(songDirectory);
        			playItem(song, false);
        		}
		        if(preferences.getBoolean(Preferences.PREFERENCE_SAVESONGPOSITION, Preferences.DEFAULT_SAVESONGPOSITION)) {
		        	int lastSongPosition = preferences.getInt(Preferences.PREFERENCE_LASTSONGPOSITION, Preferences.DEFAULT_LASTSONGPOSITION);
		        	if(lastSongPosition<getDuration()) seekTo(lastSongPosition);
		        }
        	}
        }
	}
	
	/* Called when the activity is destroyed. */
	@Override
	public void onDestroy() {
		telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE); // Stop listen for telephony events
		notificationManager.cancel(NOTIFICATION_ID);
		unregisterReceiver(broadcastReceiver); // Disable broadcast receiver
		
		SharedPreferences.Editor editor = preferences.edit();
		editor.putInt(Preferences.PREFERENCE_PLAY_MODE, playMode);
		if(bassBoostAvailable) {
			editor.putBoolean(Preferences.PREFERENCE_BASSBOOST, getBassBoostEnabled());
			editor.putInt(Preferences.PREFERENCE_BASSBOOSTSTRENGTH, getBassBoostStrength());
		} else {
			editor.remove(Preferences.PREFERENCE_BASSBOOST);
			editor.remove(Preferences.PREFERENCE_BASSBOOSTSTRENGTH);
		}
		editor.putBoolean(Preferences.PREFERENCE_SHAKEENABLED, isShakeEnabled());
		if(currentPlayingItem!=null) {
			if(currentPlayingItem instanceof BrowserSong) {
				editor.putString(Preferences.PREFERENCE_LASTPLAYINGSONG, currentPlayingItem.getPlayableUri());
				editor.putInt(Preferences.PREFERENCE_LASTSONGPOSITION, getCurrentPosition());
				editor.putLong(Preferences.PREFERENCE_LASTPLAYINGSONGFROMPLAYLISTID, -1);
			} else if(currentPlayingItem instanceof PlaylistSong) {
				editor.putString(Preferences.PREFERENCE_LASTPLAYINGSONG, currentPlayingItem.getPlayableUri());
				editor.putInt(Preferences.PREFERENCE_LASTSONGPOSITION, getCurrentPosition());
				editor.putLong(Preferences.PREFERENCE_LASTPLAYINGSONGFROMPLAYLISTID, ((PlaylistSong)currentPlayingItem).getId());
			} else {
				editor.putString(Preferences.PREFERENCE_LASTPLAYINGSONG, null);
				editor.putLong(Preferences.PREFERENCE_LASTPLAYINGSONGFROMPLAYLISTID, -1);
			}
		} else {
			editor.putString(Preferences.PREFERENCE_LASTPLAYINGSONG, null);
			editor.putLong(Preferences.PREFERENCE_LASTPLAYINGSONGFROMPLAYLISTID, -1);
		}

		BrowserDirectory currentDir = ((MusicPlayerApplication)getApplication()).getCurrentDirectory();
		if(currentDir!=null) editor.putString(Preferences.PREFERENCE_LASTDIRECTORY, currentDir.getDirectory().getAbsolutePath());
		editor.apply();
		
		audioManager.unregisterRemoteControlClient(remoteControlClient);
		audioManager.unregisterMediaButtonEventReceiver(mediaButtonReceiverComponent);
		audioManager.abandonAudioFocus(null);
		shakeListener.disable();
		mediaPlayer.release();
		stopForeground(true);
		
		wakeLockRelease(); // Just to be sure the wakelock has been released! ;-)
	}
	
	private void wakeLockAcquire() {
		if(!wakeLock.isHeld()) wakeLock.acquire();
	}
	private void wakeLockRelease() {
		if(wakeLock.isHeld()) wakeLock.release();
	}
	
	public boolean playItem(PlayableItem item) {
		return playItem(item, true);
	}
	
	public boolean playItem(PlayableItem item, boolean startPlaying) {
		wakeLockAcquire();
		currentPlayingItem = item;
		mediaPlayer.reset();
		mediaPlayer.setOnCompletionListener(null);
		try {
			mediaPlayer.setDataSource(item.getPlayableUri());
			try {
				mediaPlayer.prepare();
			} catch (IOException e) {
				currentPlayingItem = null;
				return false;
			}
			mediaPlayer.setOnCompletionListener(this);
			if(startPlaying) mediaPlayer.start();
			
			updateNotification();
			if(startPlaying) {
				sendBroadcast(new Intent("com.andreadec.musicplayer.newsong")); // Sends a broadcast to the activity
			}
			
			return true;
		} catch (Exception e) {
			currentPlayingItem = null;
			updateNotification();
			sendBroadcast(new Intent("com.andreadec.musicplayer.newsong")); // Sends a broadcast to the activity
			return false;
		}
	}
	

	/* BASS BOOST */
	public boolean getBassBoostAvailable() {
		return bassBoostAvailable;
	}
	public boolean toggleBassBoost() {
		boolean newState = !bassBoost.getEnabled();
		bassBoost.setEnabled(newState);
		return newState;
	}
	public boolean getBassBoostEnabled() {
		if(!bassBoostAvailable || bassBoost==null) return false;
		return bassBoost.getEnabled();
	}
	public void setBassBoostStrength(int strength) {
		bassBoost.setStrength((short)strength);
	}
	public int getBassBoostStrength() {
		return bassBoost.getRoundedStrength();
	}
	
	
	/* SHAKE SENSOR */
	public boolean isShakeEnabled() {
		return shakeListener.isEnabled();
	}
	
	public void toggleShake() {
		if(shakeListener.isEnabled()) shakeListener.disable();
		else shakeListener.enable();
	}	
	
	
	/* Updates the notification and the remote control client. */
	private void updateNotification() {
        Bitmap image = null;
        if(currentPlayingItem!=null && currentPlayingItem.hasImage()) {
            image = ((MusicPlayerApplication)getApplication()).imagesCache.getImageSync(currentPlayingItem);
        }

		/* Update remote control client */
        if(currentPlayingItem==null) {
            remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
        } else {
            if(isPlaying()) {
                remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
            } else {
                remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
            }
            RemoteControlClient.MetadataEditor metadataEditor = remoteControlClient.editMetadata(true);
            metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, currentPlayingItem.getTitle());
            metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, currentPlayingItem.getArtist());
            metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, currentPlayingItem.getArtist());
            metadataEditor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, getDuration());
            if(currentPlayingItem.hasImage()) {
                metadataEditor.putBitmap(METADATA_KEY_ARTWORK, image);
            } else {
                metadataEditor.putBitmap(METADATA_KEY_ARTWORK, icon.copy(icon.getConfig(), false));
            }
            metadataEditor.apply();
        }

        sendPlayingStateBroadcast();
		
		/* Update notification */
		Notification.Builder notificationBuilder = new Notification.Builder(this);
		notificationBuilder.setSmallIcon(R.drawable.audio_white);
        notificationBuilder.setContentIntent(pendingIntent);
        notificationBuilder.setOngoing(true);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			notificationBuilder.setChannelId(NOTIFICATION_CHANNEL);
		}

        if(Build.VERSION.SDK_INT >= 21) {
            int playPauseIcon = isPlaying() ? R.drawable.button_pause : R.drawable.button_play;
            if (currentPlayingItem == null) {
                notificationBuilder.setContentTitle(getString(R.string.noSong));
                notificationBuilder.setContentText(getString(R.string.app_name));
                notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher));
            } else {
                notificationBuilder.setContentTitle(currentPlayingItem.getTitle());
                notificationBuilder.setContentText(currentPlayingItem.getArtist());
                if (image == null) {
                    notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher));
                } else {
                    notificationBuilder.setLargeIcon(image);
                }
            }
            notificationBuilder.addAction(R.drawable.button_quit, getString(R.string.quit), quitPendingIntent);
            notificationBuilder.addAction(R.drawable.button_previous, getString(R.string.previous), previousPendingIntent);
            notificationBuilder.addAction(playPauseIcon, getString(R.string.pause), playpausePendingIntent);
            notificationBuilder.addAction(R.drawable.button_next, getString(R.string.next), nextPendingIntent);
            notificationBuilder.setColor(getResources().getColor(R.color.primaryDark));
            notificationBuilder.setStyle(new Notification.MediaStyle().setShowActionsInCompactView(2));
            notification = notificationBuilder.build();
        } else {
            int playPauseIcon = isPlaying() ? R.drawable.pause : R.drawable.play;
            notificationBuilder.setContentTitle(getResources().getString(R.string.app_name));

            RemoteViews notificationLayout = new RemoteViews(getPackageName(), R.layout.layout_notification);

            if (currentPlayingItem == null) {
                notificationLayout.setTextViewText(R.id.textViewArtist, getString(R.string.app_name));
                notificationLayout.setTextViewText(R.id.textViewTitle, getString(R.string.noSong));
                notificationLayout.setImageViewBitmap(R.id.imageViewNotification, BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher));
            } else {
                String title = currentPlayingItem.getArtist();
                if (!title.equals("")) title += " - ";
                title += currentPlayingItem.getTitle();
                notificationBuilder.setContentText(title);
                notificationLayout.setTextViewText(R.id.textViewArtist, currentPlayingItem.getArtist());
                notificationLayout.setTextViewText(R.id.textViewTitle, currentPlayingItem.getTitle());
                if (image != null) {
                    notificationLayout.setImageViewBitmap(R.id.imageViewNotification, image);
                } else {
                    notificationLayout.setImageViewBitmap(R.id.imageViewNotification, BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher));
                }
            }
            notificationLayout.setOnClickPendingIntent(R.id.buttonNotificationQuit, quitPendingIntent);
            notificationLayout.setOnClickPendingIntent(R.id.buttonNotificationPrevious, previousPendingIntent);
            notificationLayout.setImageViewResource(R.id.buttonNotificationPlayPause, playPauseIcon);
            notificationLayout.setOnClickPendingIntent(R.id.buttonNotificationPlayPause, playpausePendingIntent);
            notificationLayout.setOnClickPendingIntent(R.id.buttonNotificationNext, nextPendingIntent);
            notification = notificationBuilder.build();
            notification.bigContentView = notificationLayout;
        }
		
		notificationManager.notify(NOTIFICATION_ID, notification);
	}

    private void sendPlayingStateBroadcast() {
        if(!preferences.getBoolean(Preferences.PREFERENCE_SHARE_PLAYBACK_STATE, Preferences.DEFAULT_SHARE_PLAYBACK_STATE)) return;
        Intent intent = new Intent();
        //intent.setAction("com.android.music.metachanged");
        intent.setAction("com.android.music.playstatechanged");
        Bundle bundle = new Bundle();
        if(currentPlayingItem!=null && (currentPlayingItem instanceof BrowserSong || currentPlayingItem instanceof PlaylistSong)) {
            bundle.putString("track", currentPlayingItem.getTitle());
            bundle.putString("artist", currentPlayingItem.getArtist());
            bundle.putLong("duration", mediaPlayer.getDuration());
            bundle.putLong("position", mediaPlayer.getCurrentPosition());
            bundle.putBoolean("playing", mediaPlayer.isPlaying());
        } else {
            bundle.putBoolean("playing", false);
        }
        intent.putExtras(bundle);
        sendBroadcast(intent);
    }
	
	/* Toggles play/pause status. */
	public void playPause() {
		if(currentPlayingItem==null) return;
		if (mediaPlayer.isPlaying()) {
			mediaPlayer.pause();
			wakeLockRelease();
		} else {
			wakeLockAcquire();
			mediaPlayer.start();
		}
		updateNotification();
		sendBroadcast(new Intent("com.andreadec.musicplayer.playpausechanged"));
	}
	
	/* Starts playing song. */
	public void play() {
		if(currentPlayingItem==null) return;
		if(!mediaPlayer.isPlaying()) mediaPlayer.start();
		updateNotification();
		sendBroadcast(new Intent("com.andreadec.musicplayer.playpausechanged"));
	}
	
	/* Pauses playing song. */
	public void pause() {
		if(currentPlayingItem==null) return;
		if (mediaPlayer.isPlaying()) mediaPlayer.pause();
		updateNotification();
		sendBroadcast(new Intent("com.andreadec.musicplayer.playpausechanged"));
	}
	
	/* Seeks to a position. */
	public void seekTo(int progress) {
		mediaPlayer.seekTo(progress);
        sendPlayingStateBroadcast();
	}
	
	/* Plays the previous song */
	public void previousItem(boolean noRestart) {
		if(currentPlayingItem==null) return;
		
		if(!noRestart && getCurrentPosition()>2000) {
			playItem(currentPlayingItem);
			return;
		}

		switch (playMode) {
			case PLAY_MODE_REPEAT_ONE:
				playItem(currentPlayingItem);
				return;
			case PLAY_MODE_SHUFFLE:
				randomItem();
				return;
		}
		
		PlayableItem previousItem = currentPlayingItem.getPrevious();
		if(previousItem!=null) {
			playItem(previousItem);
		}
	}
	
	/* Plays the next song */
	public void nextItem() {
		if(currentPlayingItem==null) {
			return;
		}

		switch (playMode) {
			case PLAY_MODE_REPEAT_ONE:
				playItem(currentPlayingItem);
				return;
			case PLAY_MODE_SHUFFLE:
				randomItem();
				return;
		}
		
		PlayableItem nextItem = currentPlayingItem.getNext(playMode==PLAY_MODE_REPEAT_ALL);
		if(nextItem==null) {
			if(!isPlaying()) wakeLockRelease();
            sendBroadcast(new Intent("com.andreadec.musicplayer.newsong")); // Notify the activity that there are no more songs to be played
            updateNotification();
		} else {
			playItem(nextItem);
		}
	}
	
	private void randomItem() {
		PlayableItem randomItem = currentPlayingItem.getRandom(random);
		if(randomItem!=null) {
			playItem(randomItem);
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return musicBinder;
	}
	
	public class MusicBinder extends Binder {
		public MusicService getService() {
			return MusicService.this;
		}
	}
	
	/* Gets current playing item. */
	public PlayableItem getCurrentPlayingItem() {
		return currentPlayingItem;
	}
	
	/* Gets current song durations. */
	public int getDuration() {
		if(currentPlayingItem==null) return 100;
		return mediaPlayer.getDuration();
	}
	/* Gets current position in the song. */
	public int getCurrentPosition() {
		if(currentPlayingItem==null) return 0;
		return mediaPlayer.getCurrentPosition();
	}
	/* Checks if a song is currently being played */
	public boolean isPlaying() {
		if(currentPlayingItem==null) return false;
		return mediaPlayer.isPlaying();
	}

	@Override
	public void onCompletion(MediaPlayer player) {
		nextItem();
	}

	public int getPlayMode() {
		return playMode;
	}
	public void setPlayMode(int playMode) {
		this.playMode = playMode;
	}

	/* Phone state listener class. */
	private class MusicPhoneStateListener extends PhoneStateListener {
		private boolean wasPlaying = false;
		public void onCallStateChanged(int state, String incomingNumber) {
	    	switch(state) {
	            case TelephonyManager.CALL_STATE_IDLE:
	            	if(preferences.getBoolean(Preferences.PREFERENCE_RESTARTPLAYBACKAFTERPHONECALL, Preferences.DEFAULT_RESTARTPLAYBACKAFTERPHONECALL) && wasPlaying) play();
	                break;
	            case TelephonyManager.CALL_STATE_OFFHOOK:
				case TelephonyManager.CALL_STATE_RINGING:
					wasPlaying = isPlaying();
	            	pause();
	                break;
			}
	    }
	}
	
	public static class MediaButtonReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			if(event.getAction()!=KeyEvent.ACTION_DOWN) return;
			switch(event.getKeyCode()) {
			case KeyEvent.KEYCODE_MEDIA_PLAY:
			case KeyEvent.KEYCODE_MEDIA_PAUSE:
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
				context.sendBroadcast(new Intent("com.andreadec.musicplayer.playpause"));
				return;
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				context.sendBroadcast(new Intent("com.andreadec.musicplayer.previousNoRestart"));
				return;
			case KeyEvent.KEYCODE_MEDIA_NEXT:
				context.sendBroadcast(new Intent("com.andreadec.musicplayer.next"));
				return;
			}
		}
	}
}

/*
 * Copyright 2013-2019 Andrea De Cesare
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

public class Preferences {
	// Preferences
	public final static String PREFERENCE_BASEFOLDER = "baseFolder";
	public final static String PREFERENCE_LASTDIRECTORY = "lastDirectory";
	public final static String PREFERENCE_LASTPLAYINGSONG = "lastPlayingSong";
	public final static String PREFERENCE_LASTSONGPOSITION = "lastSongPosition";
	public final static String PREFERENCE_LASTPLAYINGSONGFROMPLAYLISTID = "lastPlyaingSongFromPlaylistId";
	public final static String PREFERENCE_LASTPAGE = "lastPage";
	public final static String PREFERENCE_PLAY_MODE = "playMode";
	public final static String PREFERENCE_BASSBOOST = "bassBoost";
	public final static String PREFERENCE_BASSBOOSTSTRENGTH = "bassBoostStrength";
	public final static String PREFERENCE_SHAKEENABLED = "shakeEnabled";
	public final static String PREFERENCE_SONGSSORTINGMETHOD = "songsSortingMethod";
	public final static String PREFERENCE_DISABLELOCKSCREEN = "disableLockScreen";
	public final static String PREFERENCE_STOPPLAYINGWHENHEADSETDISCONNECTED = "stopPlayingWhenHeadsetDisconnected";
	public final static String PREFERENCE_OPENLASTSONGONSTART = "openLastSongOnStart";
	public final static String PREFERENCE_SAVESONGPOSITION = "saveSongPosition";
	public final static String PREFERENCE_OPENLASTPAGEONSTART = "openLastPageOnStart";
	public final static String PREFERENCE_RESTARTPLAYBACKAFTERPHONECALL = "restartPlaybackAfterPhoneCall";
	public final static String PREFERENCE_PODCASTSDIRECTORY = "podcastsDirectory";
	public final static String PREFERENCE_ENABLEBACKDOUBLEPRESSTOQUITAPP = "enableBackDoublePressToQuitApp";
	public final static String PREFERENCE_SHOWRELATIVEPATHUNDERBASEDIRECTORY = "showRelativePathUnderBaseDirectory";
	public final static String PREFERENCE_ENABLEGESTURES = "enableGestures";
	public final static String PREFERENCE_SHOWPLAYBACKCONTROLS = "showPlaybackControls";
    public final static String PREFERENCE_SHARE_PLAYBACK_STATE = "sharePlaybackState";
	public final static String PREFERENCE_SHAKEINTERVAL = "shakeInterval";
	public final static String PREFERENCE_SHAKETHRESHOLD = "shakeThreshold";
	public final static String PREFERENCE_SHAKEACTION = "shakeAction";
	
	// Default preferences values
	public final static String DEFAULT_SONGSSORTINGMETHOD = "nat";
	public final static boolean DEFAULT_DISABLELOCKSCREEN = false;
	public final static String DEFAULT_BASEFOLDER = null;
	public final static int DEFAULT_PLAY_MODE = 0;
	public final static boolean DEFAULT_BASSBOOST = false;
	public final static int DEFAULT_BASSBOOSTSTRENGTH = 0;
	public final static boolean DEFAULT_SHAKEENABLED = false;
	public final static String DEFAULT_LASTDIRECTORY = null;
	public final static boolean DEFAULT_STOPPLAYINGWHENHEADSETDISCONNECTED = false;
	public final static boolean DEFAULT_OPENLASTSONGONSTART = false;
	public final static String DEFAULT_LASTPLAYINGSONG = null;
	public final static boolean DEFAULT_OPENLASTPAGEONSTART = false;
	public final static int DEFAULT_LASTPAGE = 0;
	public final static int DEFAULT_LASTPLAYINGSONGFROMPLAYLISTID = -1;
	public final static boolean DEFAULT_SAVESONGPOSITION = false;
	public final static int DEFAULT_LASTSONGPOSITION = 0;
	public final static boolean DEFAULT_RESTARTPLAYBACKAFTERPHONECALL = false;
	public final static String DEFAULT_SHAKEINTERVAL = null;
	public final static String DEFAULT_SHAKETHRESHOLD = null;
	public final static String DEFAULT_SHAKEACTION = "playpause";
	public final static boolean DEFAULT_ENABLEBACKDOUBLEPRESSTOQUITAPP = true;
	public final static boolean DEFAULT_SHOWRELATIVEPATHUNDERBASEDIRECTORY = true;
	public final static boolean DEFAULT_ENABLEGESTURES = false;
	public final static boolean DEFAULT_SHOWPLAYBACKCONTROLS = true;
    public final static boolean DEFAULT_SHARE_PLAYBACK_STATE = false;
}

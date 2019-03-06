package com.lufinkey.react.spotify;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.PlaybackState;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class Convert
{
	public static JSONObject toJSONObject(ReadableMap readableMap) {
		try {
			JSONObject object = new JSONObject();
			ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
			while (iterator.hasNextKey()) {
				String key = iterator.nextKey();
				switch (readableMap.getType(key)) {
					case Null:
						object.put(key, JSONObject.NULL);
						break;
					case Boolean:
						object.put(key, readableMap.getBoolean(key));
						break;
					case Number:
						object.put(key, readableMap.getDouble(key));
						break;
					case String:
						object.put(key, readableMap.getString(key));
						break;
					case Map:
						object.put(key, toJSONObject(readableMap.getMap(key)));
						break;
					case Array:
						object.put(key, toJSONArray(readableMap.getArray(key)));
						break;
				}
			}
			return object;
		}
		catch(JSONException e) {
			return null;
		}
	}

	public static JSONArray toJSONArray(ReadableArray readableArray) {
		try {
			JSONArray array = new JSONArray();
			for (int i = 0; i < readableArray.size(); i++) {
				switch (readableArray.getType(i)) {
					case Null:
						break;
					case Boolean:
						array.put(readableArray.getBoolean(i));
						break;
					case Number:
						array.put(readableArray.getDouble(i));
						break;
					case String:
						array.put(readableArray.getString(i));
						break;
					case Map:
						array.put(toJSONObject(readableArray.getMap(i)));
						break;
					case Array:
						array.put(toJSONArray(readableArray.getArray(i)));
						break;
				}
			}
			return array;
		}
		catch(JSONException e) {
			return null;
		}
	}

	public static WritableMap fromJSONObject(JSONObject jsonObject) {
		try {
			WritableMap map = Arguments.createMap();
			Iterator<String> iterator = jsonObject.keys();
			while (iterator.hasNext()) {
				String key = iterator.next();
				Object value = jsonObject.get(key);
				if (value instanceof JSONObject) {
					map.putMap(key, fromJSONObject((JSONObject) value));
				} else if (value instanceof JSONArray) {
					map.putArray(key, fromJSONArray((JSONArray) value));
				} else if (value instanceof Boolean) {
					map.putBoolean(key, (Boolean) value);
				} else if (value instanceof Integer) {
					map.putInt(key, (Integer) value);
				} else if (value instanceof Double) {
					map.putDouble(key, (Double) value);
				} else if (value instanceof String) {
					map.putString(key, (String) value);
				} else {
					map.putString(key, value.toString());
				}
			}
			return map;
		}
		catch(JSONException e) {
			return null;
		}
	}

	public static WritableArray fromJSONArray(JSONArray jsonArray) {
		try {
			WritableArray array = Arguments.createArray();
			for (int i = 0; i < jsonArray.length(); i++) {
				Object value = jsonArray.get(i);
				if (value instanceof JSONObject) {
					array.pushMap(fromJSONObject((JSONObject) value));
				} else if (value instanceof JSONArray) {
					array.pushArray(fromJSONArray((JSONArray) value));
				} else if (value instanceof Boolean) {
					array.pushBoolean((Boolean) value);
				} else if (value instanceof Integer) {
					array.pushInt((Integer) value);
				} else if (value instanceof Double) {
					array.pushDouble((Double) value);
				} else if (value instanceof String) {
					array.pushString((String) value);
				} else {
					array.pushString(value.toString());
				}
			}
			return array;
		}
		catch(JSONException e) {
			return null;
		}
	}

	public static ReadableMap fromRNSpotifyError(SpotifyError error) {
		if(error==null) {
			return null;
		}
		return error.toReactObject();
	}

	public static String joinedIntoString(ReadableArray array, String delimiter) {
		String str = "";
		for(int i=0; i<array.size(); i++) {
			if(i == 0) {
				str = array.getString(i);
			}
			else {
				str += delimiter + array.getString(i);
			}
		}
		return str;
	}

	public static WritableMap toWritableMap(ReadableMap map) {
		WritableMap mutMap = Arguments.createMap();
		if(map != null) {
			mutMap.merge(map);
		}
		return mutMap;
	}

	public static WritableMap fromPlaybackState(PlaybackState playbackState) {
		if(playbackState == null) {
			return null;
		}
		WritableMap map = Arguments.createMap();
		map.putBoolean("playing", playbackState.isPlaying);
		map.putBoolean("repeating", playbackState.isRepeating);
		map.putBoolean("shuffling", playbackState.isShuffling);
		map.putBoolean("activeDevice", playbackState.isActiveDevice);
		map.putDouble("position", ((double)playbackState.positionMs)/1000.0);
		return map;
	}

	public static WritableMap fromPlaybackTrack(Metadata.Track track, Metadata metadata) {
		if(track == null) {
			return null;
		}
		WritableMap map = Arguments.createMap();
		map.putString("name", track.name);
		map.putString("uri", track.uri);
		map.putString("contextName", metadata.contextName);
		map.putString("contextUri", metadata.contextUri);
		map.putString("artistName", track.artistName);
		map.putString("artistUri", track.artistUri);
		map.putString("albumName", track.albumName);
		map.putString("albumUri", track.albumUri);
		map.putString("albumCoverArtURL", track.albumCoverWebUrl);
		map.putDouble("duration", ((double)track.durationMs)/1000.0);
		map.putInt("indexInContext", (int)track.indexInContext);
		return map;
	}

	public static WritableMap fromPlaybackMetadata(Metadata metadata) {
		if(metadata == null) {
			return null;
		}
		WritableMap map = Arguments.createMap();
		map.putMap("prevTrack", fromPlaybackTrack(metadata.prevTrack, metadata));
		map.putMap("currentTrack", fromPlaybackTrack(metadata.currentTrack, metadata));
		map.putMap("nextTrack", fromPlaybackTrack(metadata.nextTrack, metadata));
		return map;
	}

	public static WritableMap fromSessionData(SessionData session) {
		if(session == null) {
			return null;
		}
		WritableMap map = Arguments.createMap();
		map.putString("accessToken", session.accessToken);
		map.putString("refreshToken",session.refreshToken);
		map.putDouble("expireTime", session.expireDate.getTime());
		WritableArray scopes = Arguments.createArray();
		if(session.scopes != null) {
			for(String scope : session.scopes) {
				scopes.pushString(scope);
			}
		}
		map.putArray("scopes", scopes);
		return map;
	}
}

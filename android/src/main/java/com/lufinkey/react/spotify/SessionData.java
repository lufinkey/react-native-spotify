package com.lufinkey.react.spotify;

import android.content.SharedPreferences;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;

import java.util.Calendar;
import java.util.Date;

public class SessionData {
	public String accessToken = null;
	public Date expireDate = null;
	public String refreshToken = null;
	public String[] scopes = null;

	public boolean isValid() {
		if(accessToken == null || expireDate == null || expireDate.getTime() < (new Date()).getTime()) {
			return false;
		}
		return true;
	}

	public boolean hasScope(String scope) {
		if(scopes == null) {
			return false;
		}
		for(String cmpScope : scopes) {
			if(scope.equals(cmpScope)) {
				return true;
			}
		}
		return false;
	}

	public void save(SharedPreferences prefs) {
		SharedPreferences.Editor prefsEditor = prefs.edit();
		// access token
		if(accessToken != null) {
			prefsEditor.putString("accessToken", accessToken);
		} else {
			prefsEditor.remove("accessToken");
		}
		// expire date
		if(expireDate != null) {
			prefsEditor.putLong("expireTime", expireDate.getTime());
		} else {
			prefsEditor.remove("expireTime");
		}
		// refresh token
		if(refreshToken != null) {
			prefsEditor.putString("refreshToken", refreshToken);
		} else {
			prefsEditor.remove("refreshToken");
		}
		// scopes
		if(scopes != null) {
			String scope = "";
			for(int i=0; i<scopes.length; i++) {
				scope += scopes[i];
				if(i < (scopes.length-1)) {
					scope += ",";
				}
			}
			prefsEditor.putString("scopes", scope);
		}
		else {
			prefsEditor.remove("scopes");
		}
		prefsEditor.commit();
	}

	public static SessionData from(SharedPreferences prefs) {
		String accessToken = prefs.getString("accessToken", null);
		long expireTime = prefs.getLong("expireTime", 0);
		if(accessToken == null || expireTime == 0) {
			return null;
		}
		String refreshToken = prefs.getString("refreshToken", null);
		String scope = prefs.getString("scopes", null);
		String[] scopes = null;
		if(scope != null) {
			scopes = scope.split(",");
		}
		SessionData session = new SessionData();
		session.accessToken = accessToken;
		session.expireDate = new Date(expireTime);
		session.refreshToken = refreshToken;
		session.scopes = scopes;
		return session;
	}

	public static SessionData from(ReadableMap map) throws SpotifyError {
		String accessToken = map.getString("accessToken");
		if(accessToken == null) {
			throw SpotifyError.getMissingOptionError("accessToken");
		}
		double expireTime = map.getDouble("expireTime");
		if(expireTime == 0) {
			throw SpotifyError.getMissingOptionError("expireTime");
		}
		String refreshToken = map.getString("refreshToken");
		String[] scopes = null;
		ReadableArray scope = map.getArray("scopes");
		if(scope != null) {
			scopes = new String[scope.size()];
			for(int i=0; i<scope.size(); i++) {
				scopes[i] = scope.getString(i);
			}
		}
		SessionData session = new SessionData();
		session.accessToken = accessToken;
		session.expireDate = new Date((long)expireTime);
		session.refreshToken = refreshToken;
		session.scopes = scopes;
		return session;
	}

	public static Date getExpireDate(int expireSeconds) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());
		calendar.add(Calendar.SECOND, expireSeconds);
		return calendar.getTime();
	}
}

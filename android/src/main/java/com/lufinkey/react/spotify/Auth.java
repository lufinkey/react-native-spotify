package com.lufinkey.react.spotify;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.android.volley.NetworkResponse;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.spotify.sdk.android.player.Connectivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

public class Auth
{
	public ReactApplicationContext reactContext = null;

	public String sessionUserDefaultsKey = null;

	private String clientID = null;
	private String tokenRefreshURL = null;

	private SessionData session = null;

	private boolean renewingSession = false;
	private boolean retryRenewalUntilResponse = false;
	private final ArrayList<Completion<Boolean>> renewCallbacks = new ArrayList<>();
	private final ArrayList<Completion<Boolean>> renewUntilResponseCallbacks = new ArrayList<>();

	public String getClientID() {
		return clientID;
	}

	public String getTokenRefreshURL() {
		return tokenRefreshURL;
	}

	public SessionData getSession() {
		return session;
	}




	public void load(LoginOptions options) {
		if(sessionUserDefaultsKey == null) {
			return;
		}
		SharedPreferences prefs = reactContext.getCurrentActivity().getSharedPreferences(sessionUserDefaultsKey, Context.MODE_PRIVATE);
		session = SessionData.from(prefs);
		if(session != null) {
			clientID = options.clientID;
			tokenRefreshURL = options.tokenRefreshURL;
		}
	}

	public void save() {
		if (sessionUserDefaultsKey == null) {
			return;
		}
		SharedPreferences prefs = reactContext.getCurrentActivity().getSharedPreferences(sessionUserDefaultsKey, Context.MODE_PRIVATE);
		if(session != null) {
			session.save(prefs);
		}
		else {
			SharedPreferences.Editor prefsEditor = prefs.edit();
			prefsEditor.clear();
			prefsEditor.commit();
		}
	}

	public void startSession(SessionData session, LoginOptions options) {
		this.session = session;
		clientID = options.clientID;
		tokenRefreshURL = options.tokenRefreshURL;
		save();
	}

	public void clearSession() {
		//clearCookies("https://accounts.spotify.com");
		session = null;
		clientID = null;
		tokenRefreshURL = null;
		save();
	}

	public boolean isLoggedIn() {
		if(session != null && session.accessToken != null) {
			return true;
		}
		return false;
	}

	public boolean isSessionValid() {
		if(session != null && session.isValid()) {
			return true;
		}
		return false;
	}

	public boolean hasStreamingScope() {
		if(session == null) {
			return false;
		}
		return session.hasScope("streaming");
	}

	public boolean canRefreshSession() {
		if(session != null && session.refreshToken != null && tokenRefreshURL != null) {
			return true;
		}
		return false;
	}




	private static HashMap<String,String> getCookies(android.webkit.CookieManager cookieManager, String url) {
		String bigcookie = cookieManager.getCookie(url);
		if(bigcookie==null) {
			return new HashMap<>();
		}
		HashMap<String, String> cookies = new HashMap<String,String>();
		String[] cookieParts = bigcookie.split(";");
		for(int i=0; i<cookieParts.length; i++) {
			String[] cookie = cookieParts[i].split("=");
			if(cookie.length == 2) {
				cookies.put(cookie[0].trim(), cookie[1].trim());
			}
		}
		return cookies;
	}

	public String getCookie(String url, String cookie) {
		return getCookies(android.webkit.CookieManager.getInstance(), url).get(cookie);
	}

	public void clearCookies(String url) {
		android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
		HashMap<String, String> cookies = getCookies(cookieManager, url);
		for(String key : cookies.keySet()) {
			cookieManager.setCookie(url, key+"=");
		}
		if(Build.VERSION.SDK_INT >= 21) {
			cookieManager.flush();
		}
	}




	public void renewSessionIfNeeded(final Completion<Boolean> completion, boolean waitForDefinitiveResponse) {
		if(session == null || session.accessToken == null && session.isValid()) {
			// not logged in or session does not need renewal
			completion.resolve(false);
		}
		else if(session.refreshToken == null) {
			// no refresh token to renew session with, so the session has expired
			completion.reject(new SpotifyError(SpotifyError.Code.SessionExpired));
		}
		else {
			// renew the session
			renewSession(new Completion<Boolean>() {
				@Override
				public void onReject(SpotifyError error) {
					completion.reject(error);
				}

				@Override
				public void onResolve(Boolean renewed) {
					completion.resolve(renewed);
				}
			}, waitForDefinitiveResponse);
		}
	}

	public void renewSession(final Completion<Boolean> completion, boolean waitForDefinitiveResponse) {
		if(!canRefreshSession()) {
			completion.resolve(false);
			return;
		}

		// add completion to be called when the renewal finishes
		if(completion != null) {
			if(waitForDefinitiveResponse) {
				synchronized (renewUntilResponseCallbacks) {
					renewUntilResponseCallbacks.add(completion);
				}
			}
			else {
				synchronized (renewCallbacks) {
					renewCallbacks.add(completion);
				}
			}
		}

		// determine whether to retry renewal if a definitive response isn't given
		if(waitForDefinitiveResponse) {
			retryRenewalUntilResponse = true;
		}

		// if we're already in the process of renewing the session, don't continue
		if(renewingSession) {
			return;
		}
		renewingSession = true;

		// create request body
		WritableMap params = Arguments.createMap();
		params.putString("refresh_token", session.refreshToken);

		// perform token refresh
		performTokenURLRequest(tokenRefreshURL, Utils.makeQueryString(params), new Completion<JSONObject>() {
			@Override
			public void onComplete(JSONObject response, SpotifyError error)
			{
				renewingSession = false;

				// determine if session was renewed
				boolean renewed = false;
				if(error == null && session != null && session.refreshToken != null) {
					try {
						String newAccessToken = response.getString("access_token");
						int newExpireTime = response.getInt("expires_in");
						if(session.accessToken != null) {
							session.accessToken = newAccessToken;
							session.expireDate = SessionData.getExpireDate(newExpireTime);
							save();
							renewed = true;
						}
					}
					catch(JSONException e) {
						// was not renewed
						error = new SpotifyError(SpotifyError.Code.BadResponse, "Missing expected response parameters");
					}
				}

				// call renewal callbacks
				ArrayList<Completion<Boolean>> tmpRenewCallbacks;
				synchronized(renewCallbacks) {
					tmpRenewCallbacks = new ArrayList<>(renewCallbacks);
					renewCallbacks.clear();
				}
				for(Completion<Boolean> completion : tmpRenewCallbacks) {
					if(error != null) {
						completion.reject(error);
					}
					else {
						completion.resolve(renewed);
					}
				}

				// ensure an actual session renewal error (a reason to be logged out)
				if(error != null
					// make sure error code is not a timeout or lack of connection
					&& (error.getCode().equals(SpotifyError.getHTTPError(0).getCode())
						|| error.getCode().equals(SpotifyError.getHTTPError(408).getCode())
						|| error.getCode().equals(SpotifyError.getHTTPError(504).getCode())
						|| error.getCode().equals(SpotifyError.getHTTPError(598).getCode())
						|| error.getCode().equals(SpotifyError.getHTTPError(599).getCode())
						|| Utils.getNetworkConnectivity() == Connectivity.OFFLINE)) {
					error = null;
				}

				// check if the session was renewed, or if it got a failure error
				if(renewed || error != null) {
					// renewal has reached a success or an error
					retryRenewalUntilResponse = false;

					// call renewal callbacks
					ArrayList<Completion<Boolean>> tmpRenewUntilResponseCallbacks;
					synchronized(renewUntilResponseCallbacks) {
						tmpRenewUntilResponseCallbacks = new ArrayList<>(renewUntilResponseCallbacks);
						renewUntilResponseCallbacks.clear();
					}
					for(Completion<Boolean> completion : tmpRenewUntilResponseCallbacks) {
						if(error != null) {
							completion.reject(error);
						}
						else {
							completion.resolve(renewed);
						}
					}
				}
				else if(retryRenewalUntilResponse) {
					// retry session renewal in 2000ms
					Handler handler = new Handler();
					handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							// retry session renewal
							renewSession(null, true);
						}
					}, 2000);
				}
			}
		});
	}




	public static void performTokenURLRequest(String url, String body, final Completion<JSONObject> completion) {
		Utils.doHTTPRequest(url, "POST", null, (body!=null ? body.getBytes() : null), new Completion<NetworkResponse>() {
			@Override
			public void onComplete(NetworkResponse response, SpotifyError error)
			{
				if(response==null) {
					completion.reject(error);
					return;
				}

				try {
					JSONObject responseObj = new JSONObject(Utils.getResponseString(response));
					if(responseObj.has("error")) {
						completion.reject(new SpotifyError(responseObj.getString("error"), responseObj.getString("error_description")));
						return;
					}

					completion.resolve(responseObj);
				}
				catch(JSONException e) {
					if(error == null) {
						if(response.statusCode >= 200 && response.statusCode < 300) {
							completion.reject(new SpotifyError(SpotifyError.Code.BadResponse));
						}
						else {
							completion.reject(SpotifyError.getHTTPError(response.statusCode));
						}
					}
					else
					{
						completion.reject(error);
					}
				}
			}
		});
	}

	public static void swapCodeForToken(String code, String url, final Completion<SessionData> completion) {
		WritableMap params = Arguments.createMap();
		params.putString("code", code);

		performTokenURLRequest(url, Utils.makeQueryString(params), new Completion<JSONObject>() {
			@Override
			public void onReject(SpotifyError error) {
				completion.reject(error);
			}

			@Override
			public void onResolve(JSONObject response) {
				String accessToken = (String)Utils.getObject("access_token", response);
				Integer expireSeconds = (Integer)Utils.getObject("expires_in", response);
				String refreshToken = (String)Utils.getObject("refresh_token", response);
				String scope = (String)Utils.getObject("scope", response);
				if(accessToken == null || !(accessToken instanceof String) || expireSeconds == null || !(expireSeconds instanceof Integer)) {
					completion.reject(new SpotifyError(SpotifyError.Code.BadResponse, "Missing expected response parameters"));
					return;
				}
				String[] scopes = null;
				if(scope != null) {
					scopes = scope.split(" ");
				}
				SessionData session = new SessionData();
				session.accessToken = accessToken;
				session.expireDate = SessionData.getExpireDate(expireSeconds);
				session.refreshToken = refreshToken;
				session.scopes = scopes;
				completion.resolve(session);
			}
		});
	}
}

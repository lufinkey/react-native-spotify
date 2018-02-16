package com.lufinkey.react.spotify;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;

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
	public String clientID = null;
	public String redirectURL = null;
	public String sessionUserDefaultsKey = null;
	public String[] requestedScopes = {};
	public String tokenSwapURL = null;
	public String tokenRefreshURL = null;

	private String accessToken = null;
	private Date expireDate = null;
	private String refreshToken = null;

	private boolean renewingSession = false;
	private boolean retryRenewalUntilResponse = false;
	private final ArrayList<Completion<Boolean>> renewCallbacks = new ArrayList<>();
	private final ArrayList<Completion<Boolean>> renewUntilResponseCallbacks = new ArrayList<>();


	public void load()
	{
		if(sessionUserDefaultsKey == null)
		{
			return;
		}
		SharedPreferences prefs = reactContext.getCurrentActivity().getSharedPreferences(sessionUserDefaultsKey, Context.MODE_PRIVATE);
		accessToken = prefs.getString("accessToken", null);
		refreshToken = prefs.getString("refreshToken", null);
		long expireTime = prefs.getLong("expireTime", 0);
		if(expireTime == 0)
		{
			expireDate = null;
		}
		else
		{
			expireDate = new Date(expireTime);
		}
	}

	public void save()
	{
		if (sessionUserDefaultsKey != null)
		{
			SharedPreferences prefs = reactContext.getCurrentActivity().getSharedPreferences(sessionUserDefaultsKey, Context.MODE_PRIVATE);
			SharedPreferences.Editor prefsEditor = prefs.edit();
			prefsEditor.putString("accessToken", accessToken);
			if(expireDate != null)
			{
				prefsEditor.putLong("expireTime", expireDate.getTime());
			}
			else
			{
				prefsEditor.putLong("expireTime", 0);
			}
			prefsEditor.putString("refreshToken", refreshToken);
			prefsEditor.apply();
		}
	}

	private static HashMap<String,String> getCookies(android.webkit.CookieManager cookieManager, String url)
	{
		String bigcookie = cookieManager.getCookie(url);
		if(bigcookie==null)
		{
			return new HashMap<>();
		}
		HashMap<String, String> cookies = new HashMap<String,String>();
		String[] cookieParts = bigcookie.split(";");
		for(int i=0; i<cookieParts.length; i++)
		{
			String[] cookie = cookieParts[i].split("=");
			if(cookie.length == 2)
			{
				cookies.put(cookie[0].trim(), cookie[1].trim());
			}
		}
		return cookies;
	}

	public String getCookie(String url, String cookie)
	{
		return getCookies(android.webkit.CookieManager.getInstance(), url).get(cookie);
	}

	public void clearCookies(String url)
	{
		android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
		HashMap<String, String> cookies = getCookies(cookieManager, url);
		for(String key : cookies.keySet())
		{
			cookieManager.setCookie(url, key+"=");
		}
		if(Build.VERSION.SDK_INT >= 21)
		{
			cookieManager.flush();
		}
	}

	private Date getExpireDate(int expireSeconds)
	{
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());
		calendar.add(Calendar.SECOND, expireSeconds);
		return calendar.getTime();
	}

	public String getAccessToken()
	{
		return accessToken;
	}

	public String getRefreshToken()
	{
		return refreshToken;
	}

	public Date getExpireDate()
	{
		return expireDate;
	}

	public void clearSession()
	{
		//clearCookies("https://accounts.spotify.com");

		accessToken = null;
		refreshToken = null;
		expireDate = null;
		save();
	}

	public boolean isLoggedIn()
	{
		if(accessToken != null)
		{
			return true;
		}
		return false;
	}

	public boolean isSessionValid()
	{
		if(expireDate==null || expireDate.before(new Date()))
		{
			return false;
		}
		return true;
	}

	public boolean hasPlayerScope()
	{
		if(requestedScopes==null)
		{
			return false;
		}
		for(int i=0; i<requestedScopes.length; i++)
		{
			if(requestedScopes[i].equals("streaming"))
			{
				return true;
			}
		}
		return false;
	}

	public void applyAuthAccessToken(String accessToken, int expiresIn)
	{
		this.refreshToken = null;
		this.accessToken = accessToken;
		this.expireDate = getExpireDate(expiresIn);
		save();
	}

	public void performTokenURLRequest(String url, String body, final Completion<JSONObject> completion)
	{
		Utils.doHTTPRequest(url, "POST", null, (body!=null ? body.getBytes() : null), new Completion<NetworkResponse>() {
			@Override
			public void onComplete(NetworkResponse response, SpotifyError error)
			{
				if(response==null)
				{
					completion.reject(error);
					return;
				}

				try
				{
					JSONObject responseObj = new JSONObject(Utils.getResponseString(response));
					if(responseObj.has("error"))
					{
						completion.reject(new SpotifyError(responseObj.getString("error"), responseObj.getString("error_description")));
						return;
					}

					completion.resolve(responseObj);
				}
				catch(JSONException e)
				{
					if(error == null)
					{
						if(response.statusCode >= 200 && response.statusCode < 300)
						{
							completion.reject(new SpotifyError(SpotifyError.Code.BadResponse));
						}
						else
						{
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

	public void swapCodeForToken(String code, final Completion<String> completion)
	{
		if(tokenSwapURL==null)
		{

			completion.reject(SpotifyError.getMissingOptionError("tokenSwapURL"));
			return;
		}

		WritableMap params = Arguments.createMap();
		params.putString("code", code);

		performTokenURLRequest(tokenSwapURL, Utils.makeQueryString(params), new Completion<JSONObject>() {
			@Override
			public void onReject(SpotifyError error)
			{
				completion.reject(error);
			}

			@Override
			public void onResolve(JSONObject response)
			{
				try
				{
					accessToken = response.getString("access_token");
					refreshToken = response.getString("refresh_token");
					expireDate = getExpireDate(response.getInt("expires_in"));
				}
				catch(JSONException e)
				{
					accessToken = null;
					refreshToken = null;
					expireDate = null;
					completion.reject(new SpotifyError(SpotifyError.Code.BadResponse, "Missing expected response parameters"));
					return;
				}

				save();
				completion.resolve(accessToken);
			}
		});
	}

	public void renewSessionIfNeeded(final Completion<Boolean> completion, boolean waitForDefinitiveResponse)
	{
		if(accessToken == null)
		{
			// not logged in
			completion.resolve(false);
		}
		else if(isSessionValid())
		{
			// session does not need renewal
			completion.resolve(false);
		}
		else if(refreshToken==null)
		{
			// no refresh token to renew session with, so the session has expired
			completion.reject(new SpotifyError(SpotifyError.Code.SessionExpired));
		}
		else
		{
			// renew the session
			renewSession(new Completion<Boolean>() {
				@Override
				public void onReject(SpotifyError error)
				{
					completion.reject(error);
				}

				@Override
				public void onResolve(Boolean renewed)
				{
					completion.resolve(renewed);
				}
			}, waitForDefinitiveResponse);
		}
	}

	public void renewSession(final Completion<Boolean> completion, boolean waitForDefinitiveResponse)
	{
		if(tokenRefreshURL==null)
		{
			completion.resolve(false);
			return;
		}
		else if(refreshToken==null)
		{
			completion.resolve(false);
			return;
		}

		// add completion to be called when the renewal finishes
		if(completion != null)
		{
			if(waitForDefinitiveResponse)
			{
				synchronized (renewUntilResponseCallbacks)
				{
					renewUntilResponseCallbacks.add(completion);
				}
			}
			else
			{
				synchronized (renewCallbacks)
				{
					renewCallbacks.add(completion);
				}
			}
		}

		// determine whether to retry renewal if a definitive response isn't given
		if(waitForDefinitiveResponse)
		{
			retryRenewalUntilResponse = true;
		}

		// if we're already in the process of renewing the session, don't continue
		if(renewingSession)
		{
			return;
		}
		renewingSession = true;

		// create request body
		WritableMap params = Arguments.createMap();
		params.putString("refresh_token", refreshToken);

		// perform token refresh
		performTokenURLRequest(tokenRefreshURL, Utils.makeQueryString(params), new Completion<JSONObject>() {
			@Override
			public void onComplete(JSONObject response, SpotifyError error)
			{
				renewingSession = false;

				// determine if session was renewed
				boolean renewed = false;
				if(error == null)
				{
					try
					{
						String newAccessToken = response.getString("access_token");
						int newExpireTime = response.getInt("expires_in");
						if(accessToken != null)
						{
							accessToken = newAccessToken;
							expireDate = getExpireDate(newExpireTime);
							save();
							renewed = true;
						}
					}
					catch(JSONException e)
					{
						// was not renewed
						error = new SpotifyError(SpotifyError.Code.BadResponse, "Missing expected response parameters");
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
					|| Utils.getNetworkConnectivity() == Connectivity.OFFLINE))
				{
					error = null;
				}

				// call renewal callbacks
				ArrayList<Completion<Boolean>> tmpRenewCallbacks;
				synchronized(renewCallbacks)
				{
					tmpRenewCallbacks = new ArrayList<>(renewCallbacks);
					renewCallbacks.clear();
				}
				for(Completion<Boolean> completion : tmpRenewCallbacks)
				{
					if(error != null)
					{
						completion.reject(error);
					}
					else
					{
						completion.resolve(renewed);
					}
				}

				// check if the session was renewed, or if it got a failure error
				if(renewed || error != null)
				{
					// renewal has reached a success or an error
					retryRenewalUntilResponse = false;

					// call renewal callbacks
					ArrayList<Completion<Boolean>> tmpRenewUntilResponseCallbacks;
					synchronized(renewUntilResponseCallbacks)
					{
						tmpRenewUntilResponseCallbacks = new ArrayList<>(renewUntilResponseCallbacks);
						renewUntilResponseCallbacks.clear();
					}
					for(Completion<Boolean> completion : tmpRenewUntilResponseCallbacks)
					{
						if(error != null)
						{
							completion.reject(error);
						}
						else
						{
							completion.resolve(renewed);
						}
					}
				}
				else if(retryRenewalUntilResponse)
				{
					// retry session renewal in 2000ms
					Handler handler = new Handler();
					handler.postDelayed(new Runnable() {
						@Override
						public void run()
						{
							// retry session renewal
							renewSession(null, true);
						}
					}, 2000);
				}
			}
		});
	}
}

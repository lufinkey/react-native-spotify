package com.lufinkey.react.spotify;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;

import com.android.volley.NetworkResponse;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
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
	private final ArrayList<CompletionBlock<Boolean>> renewCallbacks = new ArrayList<>();
	private final ArrayList<CompletionBlock<Boolean>> renewUntilResponseCallbacks = new ArrayList<>();


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
			prefsEditor.putLong("expireTime", expireDate.getTime());
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

	public void performTokenURLRequest(String url, String body, final CompletionBlock<JSONObject> completion)
	{
		Utils.doHTTPRequest(url, "POST", null, (body!=null ? body.getBytes() : null), new CompletionBlock<NetworkResponse>() {
			@Override
			public void invoke(NetworkResponse response, SpotifyError error)
			{
				if(response==null)
				{
					completion.invoke(null, error);
					return;
				}

				try
				{
					JSONObject responseObj = new JSONObject(Utils.getResponseString(response));
					if(responseObj.has("error"))
					{
						completion.invoke(null, new SpotifyError(responseObj.getString("error"), responseObj.getString("error_description")));
						return;
					}

					completion.invoke(responseObj, null);
				}
				catch(JSONException e)
				{
					if(error == null)
					{
						if(response.statusCode >= 200 && response.statusCode < 300)
						{
							completion.invoke(null, new SpotifyError(SpotifyError.Code.RCTSpotifyErrorBadResponse));
						}
						else
						{
							completion.invoke(null, SpotifyError.getHTTPError(response.statusCode));
						}
					}
					else
					{
						completion.invoke(null, error);
					}
				}
			}
		});
	}

	public void swapCodeForToken(String code, final CompletionBlock<String> completion)
	{
		if(tokenSwapURL==null)
		{

			completion.invoke(null, new SpotifyError(SpotifyError.Code.RCTSpotifyErrorMissingParameter, "cannot swap code for token without tokenSwapURL option"));
			return;
		}

		WritableMap params = Arguments.createMap();
		params.putString("code", code);

		performTokenURLRequest(tokenSwapURL, Utils.makeQueryString(params), new CompletionBlock<JSONObject>() {
			@Override
			public void invoke(JSONObject response, SpotifyError error)
			{
				if(error!=null)
				{
					completion.invoke(null, error);
					return;
				}

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
					completion.invoke(null, new SpotifyError(SpotifyError.Code.RCTSpotifyErrorBadResponse, "missing expected response parameters"));
					return;
				}

				save();
				completion.invoke(accessToken, null);
			}
		});
	}

	public void renewSessionIfNeeded(final CompletionBlock<Boolean> completion)
	{
		if(isSessionValid())
		{
			completion.invoke(true, null);
		}
		else if(refreshToken==null)
		{
			completion.invoke(false, null);
		}
		else
		{
			renewSession(new CompletionBlock<Boolean>() {
				@Override
				public void invoke(Boolean success, SpotifyError error)
				{
					completion.invoke(success, error);
				}
			}, false);
		}
	}

	public void renewSession(final CompletionBlock<Boolean> completion, boolean waitForResponse)
	{
		if(tokenRefreshURL==null)
		{
			completion.invoke(false, null);
			return;
		}
		else if(refreshToken==null)
		{
			completion.invoke(false, new SpotifyError(SpotifyError.Code.RCTSpotifyErrorAuthorizationFailed, "Can't refresh session without a refresh token"));
			return;
		}

		// add completion to callbacks
		if(completion != null)
		{
			if(waitForResponse)
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

		// determine whether to retry renewal
		if(waitForResponse)
		{
			retryRenewalUntilResponse = true;
		}

		// if we're already renewing the session, don't continue
		if(renewingSession || retryRenewalUntilResponse)
		{
			return;
		}
		renewingSession = true;

		WritableMap params = Arguments.createMap();
		params.putString("refresh_token", refreshToken);

		performTokenURLRequest(tokenRefreshURL, Utils.makeQueryString(params), new CompletionBlock<JSONObject>() {
			@Override
			public void invoke(JSONObject response, SpotifyError error)
			{
				renewingSession = false;

				// determine if session was renewed
				boolean renewed = false;
				if(error != null)
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
						error = new SpotifyError(SpotifyError.Code.RCTSpotifyErrorBadResponse, "Missing expected response parameters");
					}
				}

				// call renewal callbacks
				ArrayList<CompletionBlock<Boolean>> tmpRenewCallbacks;
				synchronized(renewCallbacks)
				{
					tmpRenewCallbacks = new ArrayList<>(renewCallbacks);
					renewCallbacks.clear();
				}
				for(CompletionBlock<Boolean> callback : tmpRenewCallbacks)
				{
					callback.invoke(renewed, error);
				}

				// check if the session was renewed, or if a login error was given
				if(renewed || (error != null
							// ensure error code is not a timeout or lack of connection
							&& !error.getCode().equals(SpotifyError.getHTTPError(0).getCode())
							&& !error.getCode().equals(SpotifyError.getHTTPError(408).getCode())
							&& !error.getCode().equals(SpotifyError.getHTTPError(504).getCode())
							&& !error.getCode().equals(SpotifyError.getHTTPError(598).getCode())
							&& !error.getCode().equals(SpotifyError.getHTTPError(599).getCode())))
				{
					// renewal has reached a success or an error
					retryRenewalUntilResponse = false;

					// call renewal callbacks
					ArrayList<CompletionBlock<Boolean>> tmpRenewUntilResponseCallbacks;
					synchronized(renewUntilResponseCallbacks)
					{
						tmpRenewUntilResponseCallbacks = new ArrayList<>(renewUntilResponseCallbacks);
						renewUntilResponseCallbacks.clear();
					}
					for(CompletionBlock<Boolean> callback : tmpRenewUntilResponseCallbacks)
					{
						callback.invoke(renewed, error);
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

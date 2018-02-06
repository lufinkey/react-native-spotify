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
		expireDate = new Date(prefs.getLong("expireTime", 0));
		refreshToken = prefs.getString("refreshToken", null);
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
		save();
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

	public void swapCodeForToken(String code, final CompletionBlock<String> completion)
	{
		if(tokenSwapURL==null)
		{
			completion.invoke(null, new SpotifyError(SpotifyError.Code.MISSING_PARAMETERS, "cannot swap code for token without tokenSwapURL option"));
			return;
		}

		WritableMap params = Arguments.createMap();
		params.putString("code", code);

		String url = tokenSwapURL;
		String body = Utils.makeQueryString(params);

		Utils.doHTTPRequest(url, "POST", null, body.getBytes(), new CompletionBlock<NetworkResponse>() {
			@Override
			public void invoke(NetworkResponse response, SpotifyError error)
			{
				if(response==null)
				{
					completion.invoke(null, error);
				}
				else
				{
					String responseStr = Utils.getResponseString(response);

					JSONObject responseObj;
					try
					{
						responseObj = new JSONObject(responseStr);
					}
					catch(JSONException e)
					{
						completion.invoke(null, new SpotifyError(SpotifyError.Code.REQUEST_ERROR, "Invalid response format"));
						return;
					}

					try
					{
						if(responseObj.has("error"))
						{
							if(error!=null)
							{
								completion.invoke(null, new SpotifyError(SpotifyError.SPOTIFY_AUTH_DOMAIN, error.getCode(), responseObj.getString("error_description")));
							}
							else
							{
								completion.invoke(null, new SpotifyError(SpotifyError.Code.REQUEST_ERROR, responseObj.getString("error_description")));
							}
							return;
						}

						accessToken = responseObj.getString("access_token");
						refreshToken = responseObj.getString("refresh_token");
						expireDate = getExpireDate(responseObj.getInt("expires_in"));
						save();
					}
					catch(JSONException e)
					{
						completion.invoke(null, new SpotifyError(SpotifyError.Code.REQUEST_ERROR, "Missing expected response parameters"));
					}
					completion.invoke(accessToken, null);
				}
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
			completion.invoke(false, new SpotifyError(SpotifyError.Code.AUTHORIZATION_FAILED, "Can't refresh session without a refresh token"));
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

		String url = tokenRefreshURL;
		String body = Utils.makeQueryString(params);

		Utils.doHTTPRequest(url, "POST", null, body.getBytes(), new CompletionBlock<NetworkResponse>() {
			@Override
			public void invoke(NetworkResponse response, SpotifyError error)
			{
				renewingSession = false;

				// determine whether session was renewed
				boolean renewed = false;
				if(response != null)
				{
					String responseStr = Utils.getResponseString(response);

					JSONObject responseObj;
					try
					{
						responseObj = new JSONObject(responseStr);
						if(responseObj.has("error"))
						{
							error = new SpotifyError(SpotifyError.SPOTIFY_AUTH_DOMAIN, response.statusCode, responseObj.getString("error_description"));
						}
						else
						{
							accessToken = responseObj.getString("access_token");
							expireDate = getExpireDate(responseObj.getInt("expires_in"));
							save();
							renewed = true;
						}
					}
					catch(JSONException e)
					{
						error = new SpotifyError(SpotifyError.Code.REQUEST_ERROR, "Invalid response format");
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
				if(renewed ||
						(!renewed && (error == null || (error != null && error.getDomain().equals(SpotifyError.SPOTIFY_AUTH_DOMAIN)
								// ensure error code is not a timeout
								&& error.getCode() != 408 && error.getCode() != 504 && error.getCode() != 598 && error.getCode() != 599))))
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

package com.lufinkey.react.spotify;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.android.volley.Network;
import com.android.volley.NetworkResponse;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;

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

	public void clearCookies(String url)
	{
		android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
		String bigcookie = cookieManager.getCookie(url);
		if(bigcookie==null)
		{
			return;
		}
		String[] cookies = bigcookie.split(";");
		for(int i=0; i<cookies.length; i++)
		{
			String[] cookieParts = cookies[i].split("=");
			if(cookieParts.length == 2)
			{
				cookieManager.setCookie(url, cookieParts[0].trim()+"=");
			}
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

	public void clearSession()
	{
		clearCookies("https://accounts.spotify.com");

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

	void showAuthActivity(final CompletionBlock<Boolean> completion)
	{
		//check for missing options
		if(clientID == null)
		{
			completion.invoke(
					false,
					new SpotifyError(
							SpotifyError.Code.MISSING_PARAMETERS,
							"missing option clientID"));
			return;
		}

		//decide response type
		AuthenticationResponse.Type responseType = AuthenticationResponse.Type.TOKEN;
		if(tokenSwapURL!=null)
		{
			responseType = AuthenticationResponse.Type.CODE;
		}

		//ensure no conflicting callbacks
		if(AuthActivity.request != null || AuthActivity.currentActivity != null)
		{
			System.out.println("login is already being called");
			completion.invoke(
					false,
					new SpotifyError(
							SpotifyError.Code.CONFLICTING_CALLBACKS,
							"Cannot call showAuthActivity while it is already being called"));
			return;
		}

		//show auth activity
		AuthActivity.request = new AuthenticationRequest.Builder(clientID, responseType, redirectURL)
				.setScopes(requestedScopes)
				.build();
		//wait for AuthActivity.onActivityResult
		AuthActivity.completion = new CompletionBlock<AuthenticationResponse>() {
			@Override
			public void invoke(final AuthenticationResponse response, final SpotifyError error)
			{
				if(error != null)
				{
					AuthActivity.currentActivity.onFinishCompletion = new CompletionBlock<Void>() {
						@Override
						public void invoke(Void obj, SpotifyError unusedError)
						{
							completion.invoke(false, error);
						}
					};
					AuthActivity.currentActivity.finish();
					AuthActivity.currentActivity = null;
					return;
				}

				switch(response.getType())
				{
					default:
						AuthActivity.currentActivity.onFinishCompletion = new CompletionBlock<Void>() {
							@Override
							public void invoke(Void obj, SpotifyError unusedError)
							{
								completion.invoke(false, null);
							}
						};
						AuthActivity.currentActivity.finish();
						AuthActivity.currentActivity = null;
						break;

					case ERROR:
						AuthActivity.currentActivity.onFinishCompletion = new CompletionBlock<Void>() {
							@Override
							public void invoke(Void obj, SpotifyError unusedError)
							{
								completion.invoke(
										false,
										new SpotifyError(SpotifyError.Code.AUTHORIZATION_FAILED, response.getError())
								);
							}
						};
						AuthActivity.currentActivity.finish();
						AuthActivity.currentActivity = null;
						break;

					case CODE:
						swapCodeForToken(response.getCode(), new CompletionBlock<String>() {
							@Override
							public void invoke(String accessToken, SpotifyError error)
							{
								if(error!=null)
								{
									completion.invoke(false, error);
									return;
								}
								completion.invoke(true, null);
							}
						});
						break;

					case TOKEN:
						refreshToken = null;
						accessToken = response.getAccessToken();
						expireDate = getExpireDate(response.getExpiresIn());
						save();
						completion.invoke(true, null);
						break;
				}
			}
		};

		Activity activity = reactContext.getCurrentActivity();
		activity.startActivity(new Intent(activity, AuthActivity.class));
	}

	private void swapCodeForToken(String code, final CompletionBlock<String> completion)
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
			});
		}
	}

	public void renewSession(final CompletionBlock<Boolean> completion)
	{
		if(tokenRefreshURL==null)
		{
			completion.invoke(false, new SpotifyError(SpotifyError.Code.MISSING_PARAMETERS, "Cannot renew session without tokenRefreshURL option"));
		}
		else if(refreshToken==null)
		{
			clearSession();
			completion.invoke(false, new SpotifyError(SpotifyError.Code.AUTHORIZATION_FAILED, "Can't refresh session without a refresh token"));
		}
		else
		{
			WritableMap params = Arguments.createMap();
			params.putString("refresh_token", refreshToken);

			String url = tokenRefreshURL;
			String body = Utils.makeQueryString(params);

			Utils.doHTTPRequest(url, "POST", null, body.getBytes(), new CompletionBlock<NetworkResponse>() {
				@Override
				public void invoke(NetworkResponse response, SpotifyError error)
				{
					if(response==null)
					{
						completion.invoke(false, error);
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
							completion.invoke(false, new SpotifyError(SpotifyError.Code.REQUEST_ERROR, "Invalid response format"));
							return;
						}

						try
						{
							if(responseObj.has("error"))
							{
								if(error!=null)
								{
									completion.invoke(false, new SpotifyError(SpotifyError.SPOTIFY_AUTH_DOMAIN, error.getCode(), responseObj.getString("error_description")));
								}
								else
								{
									completion.invoke(false, new SpotifyError(SpotifyError.Code.REQUEST_ERROR, responseObj.getString("error_description")));
								}
								return;
							}

							accessToken = responseObj.getString("access_token");
							expireDate = getExpireDate(responseObj.getInt("expires_in"));
							save();
						}
						catch(JSONException e)
						{
							completion.invoke(false, new SpotifyError(SpotifyError.Code.REQUEST_ERROR, "Missing expected response parameters"));
						}
						completion.invoke(true, null);
					}
				}
			});
		}
	}
}

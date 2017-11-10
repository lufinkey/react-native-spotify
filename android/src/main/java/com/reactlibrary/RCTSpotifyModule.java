
package com.reactlibrary;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.WindowManager;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import com.spotify.sdk.android.authentication.*;
import com.spotify.sdk.android.player.*;
import com.spotify.sdk.android.player.Error;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.CookieManager;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Cookie;

public class RCTSpotifyModule extends ReactContextBaseJavaModule implements Player.NotificationCallback, ConnectionStateCallback
{
	private final ReactApplicationContext reactContext;

	private boolean initialized;

	private BroadcastReceiver networkStateReceiver;

	private SpotifyPlayer player;
	private final ArrayList<RCTSpotifyCallback<Boolean>> playerLoginResponses;

	private ReadableMap options;

	public RCTSpotifyModule(ReactApplicationContext reactContext)
	{
		super(reactContext);
		this.reactContext = reactContext;

		initialized = false;

		networkStateReceiver = null;

		player = null;
		playerLoginResponses = new ArrayList<>();

		options = null;
	}

	private Object nullobj()
	{
		return null;
	}

	private void clearCookies(String url)
	{
		android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
		String bigcookie = cookieManager.getCookie(url);
		String[] cookies = bigcookie.split(";");
		for(int i=0; i<cookies.length; i++)
		{
			String[] cookieParts = cookies[i].split("=");
			if(cookieParts.length == 2)
			{
				cookieManager.setCookie(url, cookieParts[0].trim()+"=");
			}
		}
		if (Build.VERSION.SDK_INT >= 21)
		{
			cookieManager.flush();
		}
	}

	Activity getMainActivity()
	{
		return reactContext.getCurrentActivity();
	}

	private Connectivity getNetworkConnectivity(Context context)
	{
		ConnectivityManager connectivityManager;
		connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
		if (activeNetwork != null && activeNetwork.isConnected())
		{
			return Connectivity.fromNetworkType(activeNetwork.getType());
		}
		else
		{
			return Connectivity.OFFLINE;
		}
	}

	@Override
	public String getName()
	{
		return "RCTSpotify";
	}

	@ReactMethod
	//test()
	public void test()
	{
		System.out.println("ayy lmao");
	}

	@ReactMethod
	//initialize(options, (loggedIn, error?))
	public void initialize(ReadableMap options, final Callback callback)
	{
		System.out.println("initialize");
		if(initialized)
		{
			System.out.println("already initialized. Finishing initialization");
			callback.invoke(
					false,
					new RCTSpotifyError(
							RCTSpotifyError.Code.ALREADY_INITIALIZED,
							"Spotify has already been initialized").toReactObject()
			);
			return;
		}

		if(options==null)
		{
			options = Arguments.createMap();
		}
		this.options = options;

		//try to log back in
		logBackInIfNeeded(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean loggedIn, RCTSpotifyError error) {
				ReadableMap errorObj = null;
				if(error!=null)
				{
					errorObj = error.toReactObject();
				}
				callback.invoke(
						loggedIn.booleanValue(),
						errorObj
				);
			}
		});
	}

	private void logBackInIfNeeded(final RCTSpotifyCallback<Boolean> completion)
	{
		String accessToken = getAccessToken();
		if(accessToken == null)
		{
			System.out.println("access token is null. Finishing initialization");
			completion.invoke(false, null);
			return;
		}
		//TODO refresh access token if needed
		initializePlayer(accessToken, new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean loggedIn, RCTSpotifyError error)
			{
				completion.invoke(loggedIn, error);
			}
		});
	}

	private void initializePlayer(final String accessToken, final RCTSpotifyCallback<Boolean> completion)
	{
		System.out.println("initializePlayer");
		//get clientID
		String clientID = options.getString("clientID");
		if(clientID == null)
		{
			System.out.println("client id is null");
			completion.invoke(
					false,
					new RCTSpotifyError(
							RCTSpotifyError.Code.MISSING_PARAMETERS,
							"missing option clientID")
			);
			return;
		}

		//check if player already exists
		if(player != null)
		{
			loginPlayer(getAccessToken(), new RCTSpotifyCallback<Boolean>() {
				@Override
				public void invoke(Boolean loggedIn, RCTSpotifyError error)
				{
					completion.invoke(loggedIn, error);
				}
			});
			return;
		}

		//initialize player
		Config playerConfig = new Config(getMainActivity().getApplicationContext(), accessToken, clientID);
		player = Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver(){
			@Override
			public void onError(Throwable error)
			{
				System.out.println("Player onError");
				Spotify.destroyPlayer(player);
				player = null;
				completion.invoke(false, new RCTSpotifyError(RCTSpotifyError.Code.INITIALIZATION_FAILED, error.getLocalizedMessage()));
			}

			@Override
			public void onInitialized(SpotifyPlayer newPlayer)
			{
				System.out.println("Player onInitialized");

				player = newPlayer;

				//setup player
				player.setConnectivityStatus(playerOperationCallback, getNetworkConnectivity(getMainActivity()));
				player.addNotificationCallback(RCTSpotifyModule.this);
				player.addConnectionStateCallback(RCTSpotifyModule.this);

				loginPlayer(accessToken, new RCTSpotifyCallback<Boolean>() {
					@Override
					public void invoke(Boolean loggedIn, RCTSpotifyError error)
					{
						completion.invoke(loggedIn, error);
					}
				});
			}
		});
	}

	private void loginPlayer(final String accessToken, final RCTSpotifyCallback<Boolean> completion)
	{
		System.out.println("loginPlayer");

		boolean loggedIn = false;

		synchronized(playerLoginResponses)
		{
			if(player.isLoggedIn())
			{
				loggedIn = true;
			}
			else
			{
				//wait for RCTSpotifyModule.onLoggedIn
				// or RCTSpotifyModule.onLoginFailed
				playerLoginResponses.add(new RCTSpotifyCallback<Boolean>() {
					@Override
					public void invoke(Boolean loggedIn, RCTSpotifyError error)
					{
						if (loggedIn)
						{
							setAccessToken(accessToken);
						}
						completion.invoke(loggedIn, error);
					}
				});
			}
		}

		if(loggedIn)
		{
			setAccessToken(accessToken);
			completion.invoke(true, null);
		}
		else
		{
			player.login(accessToken);
		}
	}

	@ReactMethod
	//login((loggedIn, error?))
	public void login(final Callback callback)
	{
		//get required options
		String clientID = options.getString("clientID");
		String redirectURL = options.getString("redirectURL");
		ReadableArray scopes = options.getArray("scopes");
		String missingOption = null;
		if(clientID == null)
		{
			missingOption = "clientID";
		}
		else if(redirectURL == null)
		{
			missingOption = "redirectURL";
		}
		else if(scopes == null)
		{
			missingOption = "scopes";
		}
		if(missingOption != null)
		{
			System.out.println("missing login option "+clientID);
			callback.invoke(
					false,
					new RCTSpotifyError(
							RCTSpotifyError.Code.MISSING_PARAMETERS,
							"missing option "+clientID).toReactObject()
			);
			return;
		}
		//get string array of scopes
		String[] scopesArr = new String[scopes.size()];
		for(int i=0; i<scopes.size(); i++)
		{
			scopesArr[i] = scopes.getString(i);
		}

		//ensure no conflicting callbacks
		if(SpotifyAuthActivity.request != null || SpotifyAuthActivity.currentActivity != null)
		{
			System.out.println("login is already being called");
			callback.invoke(
					false,
					new RCTSpotifyError(
							RCTSpotifyError.Code.CONFLICTING_CALLBACKS,
							"Cannot call login while login is already being called").toReactObject()
			);
			return;
		}

		//show auth activity
		SpotifyAuthActivity.request = new AuthenticationRequest.Builder(clientID, AuthenticationResponse.Type.TOKEN, redirectURL)
				.setScopes(scopesArr)
				.build();
		//wait for SpotifyAuthActivity.onActivityResult
		SpotifyAuthActivity.completion = new RCTSpotifyCallback<AuthenticationResponse>() {
			@Override
			public void invoke(AuthenticationResponse response, RCTSpotifyError error)
			{
				if(error != null)
				{
					System.out.println("error with spotify auth activity");
					SpotifyAuthActivity.currentActivity.finish();
					SpotifyAuthActivity.currentActivity = null;
					callback.invoke(
							false,
							error.toReactObject()
					);
					return;
				}

				switch(response.getType())
				{
					default:
						System.out.println("user cancelled login activity");
						SpotifyAuthActivity.currentActivity.finish();
						SpotifyAuthActivity.currentActivity = null;
						callback.invoke(
								false,
								nullobj()
						);
						break;

					case ERROR:
						System.out.println("error with login activity");
						SpotifyAuthActivity.currentActivity.finish();
						SpotifyAuthActivity.currentActivity = null;
						callback.invoke(
								false,
								new RCTSpotifyError(
										RCTSpotifyError.Code.AUTHORIZATION_FAILED,
										response.getError()).toReactObject()
						);
						break;

					case TOKEN:
						System.out.println("got that access token boi");
						//disable activity interaction
						SpotifyAuthActivity.currentActivity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
								WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

						//initialize player
						initializePlayer(response.getAccessToken(), new RCTSpotifyCallback<Boolean>() {
							@Override
							public void invoke(Boolean loggedIn, RCTSpotifyError error)
							{
								//re-enable activity interaction and dismiss
								SpotifyAuthActivity.currentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
								SpotifyAuthActivity.currentActivity.finish();
								SpotifyAuthActivity.currentActivity = null;

								//perform callback
								ReadableMap errorObj = null;
								if(error!=null)
								{
									errorObj = error.toReactObject();
								}
								callback.invoke(
										loggedIn.booleanValue(),
										errorObj
								);
							}
						});
						break;
				}
			}
		};

		Activity activity = getMainActivity();
		activity.startActivity(new Intent(activity, SpotifyAuthActivity.class));
	}

	@ReactMethod
	public void logout(final Callback callback)
	{
		if(!isLoggedIn())
		{
			callback.invoke(nullobj());
			return;
		}

		//destroy the player
		Spotify.destroyPlayer(player);
		player = null;

		//delete accessToken and cookies
		setAccessToken(null);
		clearCookies("https://accounts.spotify.com");

		callback.invoke(nullobj());
	}

	@ReactMethod
	//isLoggedIn()
	public boolean isLoggedIn()
	{
		if(player != null && player.isLoggedIn())
		{
			return true;
		}
		return false;
	}

	@ReactMethod
	//getAccessToken()
	public String getAccessToken()
	{
		String sessionUserDefaultsKey = options.getString("sessionUserDefaultsKey");
		if(sessionUserDefaultsKey == null)
		{
			return null;
		}
		SharedPreferences prefs = getMainActivity().getSharedPreferences(sessionUserDefaultsKey, Context.MODE_PRIVATE);
		return prefs.getString("accessToken", null);
	}

	@ReactMethod
	//setAccessToken(String accessToken)
	public void setAccessToken(String accessToken)
	{
		boolean shouldOverwrite = false;
		String oldAccessToken = getAccessToken();
		if((oldAccessToken==null && accessToken!=null)
			|| (oldAccessToken!=null && accessToken==null)
				|| (oldAccessToken!=null && accessToken!=null && !oldAccessToken.equals(accessToken)))
		{
			shouldOverwrite = true;
		}
		if(shouldOverwrite)
		{
			String sessionUserDefaultsKey = options.getString("sessionUserDefaultsKey");
			if (sessionUserDefaultsKey != null)
			{
				SharedPreferences prefs = getMainActivity().getSharedPreferences(sessionUserDefaultsKey, Context.MODE_PRIVATE);
				SharedPreferences.Editor prefsEditor = prefs.edit();
				prefsEditor.putString("accessToken", accessToken);
				prefsEditor.apply();
			}
		}
	}

	@ReactMethod
	//handleAuthURL(url)
	public boolean handleAuthURL(String url)
	{
		//TODO for some reason we don't use this on Android, despite having to give a redirectURL
		return false;
	}



	void prepareForRequest(final RCTSpotifyCallback<Boolean> completion)
	{
		logBackInIfNeeded(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean loggedIn, RCTSpotifyError error) {
				if(!loggedIn)
				{
					if(error==null)
					{
						error = new RCTSpotifyError(RCTSpotifyError.Code.NOT_LOGGED_IN, "You are not logged in");
					}
					completion.invoke(false, error);
				}
				else
				{
					completion.invoke(true, error);
				}
			}
		});
	}

	private RequestQueue requestQueue = null;

	public void doHTTPRequest(String url, String method, final ReadableMap params, final boolean jsonBody, final HashMap<String,String> headers, final RCTSpotifyCallback<String> completion)
	{
		if(requestQueue == null)
		{
			requestQueue = Volley.newRequestQueue(getMainActivity());
		}

		//parse request method
		int requestMethod;
		if(method.equals("GET"))
		{
			requestMethod = Request.Method.GET;
		}
		else if(method.equals("POST"))
		{
			requestMethod = Request.Method.POST;
		}
		else if(method.equals("DELETE"))
		{
			requestMethod = Request.Method.DELETE;
		}
		else if(method.equals("PUT"))
		{
			requestMethod = Request.Method.PUT;
		}
		else if(method.equals("HEAD"))
		{
			requestMethod = Request.Method.HEAD;
		}
		else if(method.equals("PATCH"))
		{
			requestMethod = Request.Method.PATCH;
		}
		else
		{
			completion.invoke(null, new RCTSpotifyError(RCTSpotifyError.Code.BAD_PARAMETERS, "invalid request method "+method));
			return;
		}

		//append query string to url if necessary
		if(!jsonBody && params!=null)
		{
			url += "?";
			HashMap<String, Object> map = params.toHashMap();
			boolean firstArg = true;
			for(String key : map.keySet())
			{
				if(firstArg)
				{
					firstArg = false;
				}
				else
				{
					url += "&";
				}
				String value = map.get(key).toString();
				try
				{
					url += URLEncoder.encode(key, "UTF-8")+"="+URLEncoder.encode(value, "UTF-8");
				}
				catch (UnsupportedEncodingException e)
				{
					e.printStackTrace();
					break;
				}
			}
		}

		//make request
		StringRequest request = new StringRequest(requestMethod, url, new Response.Listener<String>() {
			@Override
			public void onResponse(String response)
			{
				completion.invoke(response, null);
			}
		}, new Response.ErrorListener() {
			@Override
			public void onErrorResponse(VolleyError error)
			{
				completion.invoke(null, new RCTSpotifyError(RCTSpotifyError.Code.REQUEST_ERROR, error.getLocalizedMessage()));
			}
		}) {
			@Override
			public Map<String, String> getHeaders()
			{
				return headers;
			}

			@Override
			public String getBodyContentType()
			{
				if(jsonBody)
				{
					return "application/json; charset=utf-8";
				}
				return super.getBodyContentType();
			}

			@Override
			public byte[] getBody() throws AuthFailureError
			{
				if(jsonBody && params!=null)
				{
					JSONObject obj = RCTSpotifyConvert.toJSONObject(params);
					if(obj == null)
					{
						return null;
					}
					return obj.toString().getBytes();
				}
				return null;
			}
		};

		//do request
		requestQueue.add(request);
	}

	void doAPIRequest(String endpoint, final String method, final ReadableMap params, final boolean jsonBody, final RCTSpotifyCallback<ReadableMap> completion)
	{
		prepareForRequest(new RCTSpotifyCallback<Boolean>(){
			@Override
			public void invoke(Boolean success, RCTSpotifyError error)
			{
				HashMap<String, String> headers = new HashMap<>();
				String accessToken = getAccessToken();
				if(accessToken != null)
				{
					headers.put("Authorization", "Bearer "+accessToken);
				}
				//TODO add authorization to headers
				doHTTPRequest("https://api.spotify.com/v1/", method, params, jsonBody, headers, new RCTSpotifyCallback<String>() {
					@Override
					public void invoke(String response, RCTSpotifyError error) {
						if(error != null)
						{
							completion.invoke(null, error);
							return;
						}

						JSONObject responseObj;
						try
						{
							responseObj = new JSONObject(response);
						}
						catch (JSONException e)
						{
							completion.invoke(null, new RCTSpotifyError(RCTSpotifyError.Code.REQUEST_ERROR, "Invalid response format"));
							return;
						}

						try
						{
							JSONObject errorObj = responseObj.getJSONObject("error");
							completion.invoke(RCTSpotifyConvert.fromJSONObject(responseObj),
									new RCTSpotifyError(RCTSpotifyError.Code.REQUEST_ERROR,
											errorObj.getString("message")));
							return;
						}
						catch(JSONException e)
						{
							//do nothing. this means we don't have an error
						}

						completion.invoke(RCTSpotifyConvert.fromJSONObject(responseObj), null);
					}
				});
			}
		});
	}

	@ReactMethod
	//sendRequest(endpoint, method, params, isJSONBody, (result?, error?))
	void sendRequest(String endpoint, String method, ReadableMap params, boolean jsonBody, final Callback callback)
	{
		//
	}



	private final Player.OperationCallback playerOperationCallback = new Player.OperationCallback() {
		@Override
		public void onSuccess()
		{
			//TODO handle success
			System.out.println("Player.OperationCallback.onSuccess");
		}

		@Override
		public void onError(com.spotify.sdk.android.player.Error error)
		{
			//TODO handle error
			System.out.println("Player.OperationCallback.onError");
		}
	};



	//ConnectionStateCallback

	@Override
	public void onLoggedIn()
	{
		System.out.println("onLoggedIn");

		//handle loginPlayer callbacks
		ArrayList<RCTSpotifyCallback<Boolean>> loginResponses;
		synchronized(playerLoginResponses)
		{
			loginResponses = new ArrayList<>(playerLoginResponses);
			playerLoginResponses.clear();
		}
		for(RCTSpotifyCallback<Boolean> response : loginResponses)
		{
			response.invoke(true, null);
		}
	}

	@Override
	public void onLoggedOut()
	{
		System.out.println("onLoggedOut");

		//handle loginPlayer callbacks
		ArrayList<RCTSpotifyCallback<Boolean>> loginResponses;
		synchronized(playerLoginResponses)
		{
			loginResponses = new ArrayList<>(playerLoginResponses);
			playerLoginResponses.clear();
		}
		for(RCTSpotifyCallback<Boolean> response : loginResponses)
		{
			response.invoke(false, new RCTSpotifyError(RCTSpotifyError.Code.NOT_LOGGED_IN, "You have been logged out"));
		}
	}

	@Override
	public void onLoginFailed(com.spotify.sdk.android.player.Error error)
	{
		System.out.println("onLoginFailed");

		//handle loginPlayer callbacks
		ArrayList<RCTSpotifyCallback<Boolean>> loginResponses;
		synchronized(playerLoginResponses)
		{
			loginResponses = new ArrayList<>(playerLoginResponses);
			playerLoginResponses.clear();
		}
		for(RCTSpotifyCallback<Boolean> response : loginResponses)
		{
			response.invoke(false, new RCTSpotifyError(RCTSpotifyError.Code.AUTHORIZATION_FAILED, "login failed: "+error));
		}
	}

	@Override
	public void onTemporaryError()
	{
		System.out.println("onTemporaryError");
	}

	@Override
	public void onConnectionMessage(String s)
	{
		//
	}



	//Player.NotificationCallback

	@Override
	public void onPlaybackEvent(PlayerEvent playerEvent)
	{
		//
	}

	@Override
	public void onPlaybackError(Error error)
	{
		//
	}
}

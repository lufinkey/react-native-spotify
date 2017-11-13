
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

import okhttp3.Call;
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

	@Override
	public String getName()
	{
		return "RCTSpotify";
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

	private boolean hasPlayerScope()
	{
		if(this.options == null)
		{
			return false;
		}
		ReadableArray scopes = options.getArray("scopes");
		if(scopes==null)
		{
			return false;
		}
		for(int i=0; i<scopes.size(); i++)
		{
			String scope = scopes.getString(i);
			if(scope!=null && scope.equals("streaming"))
			{
				return true;
			}
		}
		return false;
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
		initializePlayerIfNeeded(accessToken, new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean loggedIn, RCTSpotifyError error)
			{
				completion.invoke(loggedIn, error);
			}
		});
	}

	private void initializePlayerIfNeeded(final String accessToken, final RCTSpotifyCallback<Boolean> completion)
	{
		System.out.println("initializePlayer");

		//make sure we have the player scope
		if(!hasPlayerScope())
		{
			completion.invoke(true, null);
			return;
		}

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
				player.setConnectivityStatus(connectivityStatusCallback, getNetworkConnectivity(getMainActivity()));
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
			System.out.println("missing login option "+missingOption);
			callback.invoke(
					false,
					new RCTSpotifyError(
							RCTSpotifyError.Code.MISSING_PARAMETERS,
							"missing option "+missingOption).toReactObject()
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
			public void invoke(final AuthenticationResponse response, final RCTSpotifyError error)
			{
				if(error != null)
				{
					System.out.println("error with spotify auth activity");
					SpotifyAuthActivity.currentActivity.onFinishCompletion = new RCTSpotifyCallback<Void>() {
						@Override
						public void invoke(Void obj, RCTSpotifyError unusedError)
						{
							callback.invoke(false, error.toReactObject());
						}
					};
					SpotifyAuthActivity.currentActivity.finish();
					SpotifyAuthActivity.currentActivity = null;
					return;
				}

				switch(response.getType())
				{
					default:
						System.out.println("user cancelled login activity");
						SpotifyAuthActivity.currentActivity.onFinishCompletion = new RCTSpotifyCallback<Void>() {
							@Override
							public void invoke(Void obj, RCTSpotifyError unusedError)
							{
								callback.invoke(false, nullobj());
							}
						};
						SpotifyAuthActivity.currentActivity.finish();
						SpotifyAuthActivity.currentActivity = null;
						break;

					case ERROR:
						System.out.println("error with login activity");
						SpotifyAuthActivity.currentActivity.onFinishCompletion = new RCTSpotifyCallback<Void>() {
							@Override
							public void invoke(Void obj, RCTSpotifyError unusedError)
							{
								callback.invoke(
										false,
										new RCTSpotifyError(RCTSpotifyError.Code.AUTHORIZATION_FAILED, response.getError()).toReactObject()
								);
							}
						};
						SpotifyAuthActivity.currentActivity.finish();
						SpotifyAuthActivity.currentActivity = null;
						break;

					case TOKEN:
						System.out.println("got that access token boi");
						//disable activity interaction
						SpotifyAuthActivity.currentActivity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
								WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

						//initialize player
						initializePlayerIfNeeded(response.getAccessToken(), new RCTSpotifyCallback<Boolean>() {
							@Override
							public void invoke(final Boolean loggedIn, final RCTSpotifyError error)
							{
								//re-enable activity interaction and dismiss
								SpotifyAuthActivity.currentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
								SpotifyAuthActivity.currentActivity.onFinishCompletion = new RCTSpotifyCallback<Void>() {
									@Override
									public void invoke(Void obj, RCTSpotifyError unusedError)
									{
										//perform callback
										ReadableMap errorObj = null;
										if(error!=null)
										{
											errorObj = error.toReactObject();
										}
										callback.invoke(loggedIn.booleanValue(), errorObj);
									}
								};
								SpotifyAuthActivity.currentActivity.finish();
								SpotifyAuthActivity.currentActivity = null;
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
	//logout((error?))
	public void logout(final Callback callback)
	{
		if(!isLoggedIn())
		{
			if(callback!=null)
			{
				callback.invoke(nullobj());
			}
			return;
		}

		//destroy the player
		Spotify.destroyPlayer(player);
		player = null;

		//delete accessToken and cookies
		setAccessToken(null);
		clearCookies("https://accounts.spotify.com");

		if(callback!=null)
		{
			callback.invoke(nullobj());
		}
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




	void prepareForPlayer(final RCTSpotifyCallback<Boolean> completion)
	{
		logBackInIfNeeded(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean loggedIn, RCTSpotifyError error) {
				if(error==null)
				{
					if(!loggedIn)
					{
						error = new RCTSpotifyError(RCTSpotifyError.Code.NOT_LOGGED_IN, "You are not logged in");
					}
					else if(player==null)
					{
						error = new RCTSpotifyError(RCTSpotifyError.Code.PLAYER_NOT_INITIALIZED, "Player is not initialized");
					}
				}
				if(error!=null)
				{
					completion.invoke(false, error);
					return;
				}
				completion.invoke(true, null);
			}
		});
	}

	@ReactMethod
	//playURI(spotifyURI, startIndex, startPosition, (error?))
	void playURI(final String spotifyURI, final int startIndex, final double startPosition, final Callback callback)
	{
		if(spotifyURI==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("spotifyURI"));
			return;
		}
		prepareForPlayer(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean success, RCTSpotifyError error)
			{
				if(error!=null)
				{
					if(callback!=null)
					{
						callback.invoke(error.toReactObject());
					}
					return;
				}

				player.playUri( new Player.OperationCallback() {
					@Override
					public void onError(com.spotify.sdk.android.player.Error error)
					{
						callback.invoke(new RCTSpotifyError(error).toReactObject());
					}

					@Override
					public void onSuccess()
					{
						callback.invoke(nullobj());
					}
				}, spotifyURI, startIndex, (int)(startPosition*1000));
			}
		});
	}

	@ReactMethod
	//queueURI(spotifyURI, (error?))
	void queueURI(final String spotifyURI, final Callback callback)
	{
		if(spotifyURI==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("spotifyURI"));
			return;
		}
		prepareForPlayer(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean success, RCTSpotifyError error)
			{
				if(error!=null)
				{
					if(callback!=null)
					{
						callback.invoke(error.toReactObject());
					}
					return;
				}

				player.queue(new Player.OperationCallback() {
					@Override
					public void onError(com.spotify.sdk.android.player.Error error)
					{
						callback.invoke(new RCTSpotifyError(error).toReactObject());
					}

					@Override
					public void onSuccess()
					{
						callback.invoke(nullobj());
					}
				}, spotifyURI);
			}
		});
	}

	@ReactMethod
	//setVolume(volume, (error?))
	void setVolume(double volume, final Callback callback)
	{
		//TODO implement this with a custom AudioController
		callback.invoke(new RCTSpotifyError(RCTSpotifyError.Code.NOT_IMPLEMENTED, "setVolume does not work on android"));
	}

	@ReactMethod
	//getVolume()
	double getVolume()
	{
		//TODO implement this with a custom AudioController
		return 1.0;
	}

	@ReactMethod
	//setPlaying(playing, (error?))
	void setPlaying(final boolean playing, final Callback callback)
	{
		prepareForPlayer(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean success, RCTSpotifyError error)
			{
				if(error!=null)
				{
					if(callback!=null)
					{
						callback.invoke(error.toReactObject());
					}
					return;
				}
				PlaybackState state = player.getPlaybackState();
				if((!playing && !state.isPlaying) || (playing && state.isPlaying))
				{
					callback.invoke(nullobj());
					return;
				}

				if(playing)
				{
					player.resume(new Player.OperationCallback(){
						@Override
						public void onError(com.spotify.sdk.android.player.Error error)
						{
							callback.invoke(new RCTSpotifyError(error).toReactObject());
						}

						@Override
						public void onSuccess()
						{
							callback.invoke(nullobj());
						}
					});
				}
				else
				{
					player.pause(new Player.OperationCallback(){
						@Override
						public void onError(com.spotify.sdk.android.player.Error error)
						{
							callback.invoke(new RCTSpotifyError(error).toReactObject());
						}

						@Override
						public void onSuccess()
						{
							callback.invoke(nullobj());
						}
					});
				}
			}
		});
	}

	@ReactMethod
	//getPlaybackState()
	ReadableMap getPlaybackState()
	{
		if(player==null)
		{
			return null;
		}
		return RCTSpotifyConvert.fromPlaybackState(player.getPlaybackState());
	}

	@ReactMethod
	//skipToNext((error?))
	void skipToNext(final Callback callback)
	{
		prepareForPlayer(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean success, RCTSpotifyError error)
			{
				if(error!=null)
				{
					if(callback!=null)
					{
						callback.invoke(error.toReactObject());
					}
					return;
				}
				player.skipToNext(new Player.OperationCallback() {
					@Override
					public void onError(Error error)
					{
						callback.invoke(new RCTSpotifyError(error).toReactObject());
					}

					@Override
					public void onSuccess()
					{
						callback.invoke(nullobj());
					}
				});
			}
		});
	}

	@ReactMethod
	//skipToPrevious((error?))
	void skipToPrevious(final Callback callback)
	{
		prepareForPlayer(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean success, RCTSpotifyError error)
			{
				if(error!=null)
				{
					if(callback!=null)
					{
						callback.invoke(error.toReactObject());
					}
					return;
				}
				player.skipToPrevious(new Player.OperationCallback() {
					@Override
					public void onError(Error error)
					{
						callback.invoke(new RCTSpotifyError(error).toReactObject());
					}

					@Override
					public void onSuccess()
					{
						callback.invoke(nullobj());
					}
				});
			}
		});
	}

	@ReactMethod
	//setShuffling(shuffling, (error?))
	void setShuffling(final boolean shuffling, final Callback callback)
	{
		prepareForPlayer(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean success, RCTSpotifyError error)
			{
				if(error!=null)
				{
					if(callback!=null)
					{
						callback.invoke(error.toReactObject());
					}
					return;
				}
				player.setShuffle(new Player.OperationCallback() {
					@Override
					public void onError(Error error)
					{
						callback.invoke(new RCTSpotifyError(error).toReactObject());
					}

					@Override
					public void onSuccess()
					{
						callback.invoke(nullobj());
					}
				}, shuffling);
			}
		});
	}

	@ReactMethod
	//setRepeating(repeating, (error?))
	void setRepeating(final boolean repeating, final Callback callback)
	{
		prepareForPlayer(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean obj, RCTSpotifyError error)
			{
				if(error!=null)
				{
					if(callback!=null)
					{
						callback.invoke(error.toReactObject());
					}
					return;
				}
				player.setRepeat(new Player.OperationCallback() {
					@Override
					public void onError(Error error)
					{
						callback.invoke(new RCTSpotifyError(error).toReactObject());
					}

					@Override
					public void onSuccess()
					{
						callback.invoke(nullobj());
					}
				}, repeating);
			}
		});
	}




	void prepareForRequest(final RCTSpotifyCallback<Boolean> completion)
	{
		logBackInIfNeeded(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean loggedIn, RCTSpotifyError error) {
				if(!loggedIn && error==null)
				{
					error = new RCTSpotifyError(RCTSpotifyError.Code.NOT_LOGGED_IN, "You are not logged in");
				}
				if(error!=null)
				{
					completion.invoke(false, error);
					return;
				}
				completion.invoke(true, null);
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
				System.out.println("got http request response");
				completion.invoke(response, null);
			}
		}, new Response.ErrorListener() {
			@Override
			public void onErrorResponse(VolleyError error)
			{
				System.out.println("got volley error");
				String errorMessage = error.getMessage();
				completion.invoke(null, new RCTSpotifyError(RCTSpotifyError.Code.REQUEST_ERROR, "error: "+errorMessage));
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

	void doAPIRequest(final String endpoint, final String method, final ReadableMap params, final boolean jsonBody, final RCTSpotifyCallback<ReadableMap> completion)
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
				doHTTPRequest("https://api.spotify.com/v1/"+endpoint, method, params, jsonBody, headers, new RCTSpotifyCallback<String>() {
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
		doAPIRequest(endpoint, method, params, jsonBody, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap responseObj, RCTSpotifyError error)
			{
				ReadableMap errorObj = null;
				if(error!=null)
				{
					errorObj = error.toReactObject();
				}
				if(callback!=null)
				{
					callback.invoke(responseObj, errorObj);
				}
			}
		});
	}



	@ReactMethod
	//search(query, types, options?, (result?, error?))
	void search(String query, ReadableArray types, ReadableMap options, final Callback callback)
	{
		if(query==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("query"));
			return;
		}
		else if(types==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("types"));
			return;
		}

		WritableMap body = Arguments.createMap();
		if(options!=null)
		{
			body.merge(options);
		}
		body.putString("q", query);
		String type = "";
		for(int i=0; i<types.size(); i++)
		{
			if(i==0)
			{
				type = types.getString(i);
			}
			else
			{
				type += ","+types.getString(i);
			}
		}
		body.putString("type", type);

		doAPIRequest("search", "GET", body, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getAlbum(albumID, options?, (result?, error?))
	void getAlbum(String albumID, ReadableMap options, final Callback callback)
	{
		if(albumID==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("albumID"));
			return;
		}
		doAPIRequest("albums/"+albumID, "GET", options, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getAlbums(albumIDs, options?, (result?, error?))
	void getAlbums(ReadableArray albumIDs, ReadableMap options, final Callback callback)
	{
		if(albumIDs==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("albumIDs"));
			return;
		}
		WritableMap body = RCTSpotifyConvert.toWritableMap(options);
		body.putString("ids", RCTSpotifyConvert.joinedIntoString(albumIDs, ","));
		doAPIRequest("albums", "GET", body, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getAlbumTracks(albumID, options?, (result?, error?))
	void getAlbumTracks(String albumID, ReadableMap options, final Callback callback)
	{
		if(albumID==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("albumID"));
			return;
		}
		doAPIRequest("albums/"+albumID+"/tracks", "GET", options, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getArtist(artistID, options?, (result?, error?))
	void getArtist(String artistID, ReadableMap options, final Callback callback)
	{
		if(artistID==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("artistID"));
			return;
		}
		doAPIRequest("artists/"+artistID, "GET", options, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getArtists(artistIDs, options?, (result?, error?))
	void getArtists(ReadableArray artistIDs, ReadableMap options, final Callback callback)
	{
		if(artistIDs==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("artistIDs"));
			return;
		}
		WritableMap body = RCTSpotifyConvert.toWritableMap(options);
		body.putString("ids", RCTSpotifyConvert.joinedIntoString(artistIDs, ","));
		doAPIRequest("artists", "GET", body, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getArtistAlbums(artistID, options?, (result?, error?))
	void getArtistAlbums(String artistID, ReadableMap options, final Callback callback)
	{
		if(artistID==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("artistID"));
			return;
		}
		doAPIRequest("artists/"+artistID+"/albums", "GET", options, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getArtistTopTracks(artistID, country, options?, (result?, error?))
	void getArtistTopTracks(String artistID, ReadableMap options, final Callback callback)
	{
		if(artistID==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("artistID"));
			return;
		}
		doAPIRequest("artists/"+artistID+"/top-tracks", "GET", options, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getArtistRelatedArtists(artistID, options?, (result?, error?))
	void getArtistRelatedArtists(String artistID, ReadableMap options, final Callback callback)
	{
		if(artistID==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("artistID"));
			return;
		}
		doAPIRequest("artists/"+artistID+"/related-artists", "GET", options, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getTrack(trackID, options?, (result?, error?))
	void getTrack(String trackID, ReadableMap options, final Callback callback)
	{
		if(trackID==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("trackID"));
			return;
		}
		doAPIRequest("tracks/"+trackID, "GET", options, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getTracks(trackIDs, options?, (result?, error?))
	void getTracks(ReadableArray trackIDs, ReadableMap options, final Callback callback)
	{
		if(trackIDs==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("trackIDs"));
			return;
		}
		WritableMap body = RCTSpotifyConvert.toWritableMap(options);
		body.putString("ids", RCTSpotifyConvert.joinedIntoString(trackIDs, ","));
		doAPIRequest("tracks", "GET", body, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getTrackAudioAnalysis(trackID, options?, (result?, error?))
	void getTrackAudioAnalysis(String trackID, ReadableMap options, final Callback callback)
	{
		if(trackID==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("trackID"));
			return;
		}
		doAPIRequest("audio-analysis/"+trackID, "GET", options, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getTrackAudioFeatures(trackID, options?, (result?, error?))
	void getTrackAudioFeatures(String trackID, ReadableMap options, final Callback callback)
	{
		if(trackID==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("trackID"));
			return;
		}
		doAPIRequest("audio-features/"+trackID, "GET", options, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}

	@ReactMethod
	//getTracks(trackIDs, options?, (result?, error?))
	void getTracksAudioFeatures(ReadableArray trackIDs, ReadableMap options, final Callback callback)
	{
		if(trackIDs==null)
		{
			callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("trackIDs"));
			return;
		}
		WritableMap body = RCTSpotifyConvert.toWritableMap(options);
		body.putString("ids", RCTSpotifyConvert.joinedIntoString(trackIDs, ","));
		doAPIRequest("audio-features", "GET", body, false, new RCTSpotifyCallback<ReadableMap>() {
			@Override
			public void invoke(ReadableMap resultObj, RCTSpotifyError error)
			{
				callback.invoke(resultObj, RCTSpotifyConvert.fromRCTSpotifyError(error));
			}
		});
	}



	private final Player.OperationCallback connectivityStatusCallback = new Player.OperationCallback() {
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
			response.invoke(false, new RCTSpotifyError(error));
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

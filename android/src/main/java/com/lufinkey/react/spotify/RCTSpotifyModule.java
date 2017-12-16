package com.lufinkey.react.spotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telecom.Call;
import android.view.WindowManager;

import com.android.volley.NetworkResponse;
import com.android.volley.toolbox.HttpHeaderParser;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import com.spotify.sdk.android.player.*;
import com.spotify.sdk.android.player.Error;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;

public class RCTSpotifyModule extends ReactContextBaseJavaModule implements Player.NotificationCallback, ConnectionStateCallback
{
	private final ReactApplicationContext reactContext;

	private boolean initialized;

	private BroadcastReceiver networkStateReceiver;

	private Auth auth;
	private SpotifyPlayer player;
	private final ArrayList<CompletionBlock<Boolean>> playerLoginResponses;

	private ReadableMap options;

	public RCTSpotifyModule(ReactApplicationContext reactContext)
	{
		super(reactContext);
		this.reactContext = reactContext;
		Utils.reactContext = reactContext;

		initialized = false;

		networkStateReceiver = null;

		auth = null;
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
		System.out.println("initializing Spotify module");
		if(initialized)
		{
			System.out.println("already initialized. Finishing initialization");
			if(callback!=null)
			{
				callback.invoke(
						false,
						new SpotifyError(
								SpotifyError.Code.ALREADY_INITIALIZED,
								"Spotify has already been initialized").toReactObject()
				);
			}
			return;
		}

		if(options==null)
		{
			options = Arguments.createMap();
		}
		this.options = options;
		auth = new Auth();
		auth.reactContext = reactContext;
		if(options.hasKey("clientID"))
		{
			auth.clientID = options.getString("clientID");
		}
		if(options.hasKey("redirectURL"))
		{
			auth.redirectURL = options.getString("redirectURL");
		}
		if(options.hasKey("sessionUserDefaultsKey"))
		{
			auth.sessionUserDefaultsKey = options.getString("sessionUserDefaultsKey");
		}
		ReadableArray scopes = null;
		if(options.hasKey("scopes"))
		{
			scopes = options.getArray("scopes");
		}
		if(scopes!=null)
		{
			String[] requestedScopes = new String[scopes.size()];
			for(int i=0; i<scopes.size(); i++)
			{
				requestedScopes[i] = scopes.getString(i);
			}
			auth.requestedScopes = requestedScopes;
		}
		if(options.hasKey("tokenSwapURL"))
		{
			auth.tokenSwapURL = options.getString("tokenSwapURL");
		}
		if(options.hasKey("tokenRefreshURL"))
		{
			auth.tokenRefreshURL = options.getString("tokenRefreshURL");
		}
		auth.load();

		//try to log back in
		logBackInIfNeeded(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean loggedIn, SpotifyError error) {
				ReadableMap errorObj = null;
				if(error!=null)
				{
					errorObj = error.toReactObject();
				}
				initialized = true;
				if(callback!=null)
				{
					callback.invoke(
							loggedIn,
							errorObj
					);
				}
			}
		});
	}

	@ReactMethod(isBlockingSynchronousMethod = true)
	//isInitialized()
	Boolean isInitialized()
	{
		return initialized;
	}

	@ReactMethod
	//isInitializedAsync((initialized))
	void isInitializedAsync(final Callback callback)
	{
		callback.invoke(isInitialized());
	}

	private void logBackInIfNeeded(final CompletionBlock<Boolean> completion)
	{
		auth.renewSessionIfNeeded(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean success, SpotifyError error)
			{
				if(error!=null)
				{
					completion.invoke(false, error);
				}
				else if(!success)
				{
					completion.invoke(false, null);
				}
				else if(auth.getAccessToken()==null)
				{
					completion.invoke(false, null);
				}
				else
				{
					initializePlayerIfNeeded(auth.getAccessToken(), new CompletionBlock<Boolean>() {
						@Override
						public void invoke(Boolean loggedIn, SpotifyError error)
						{
							completion.invoke(loggedIn, error);
						}
					});
				}
			}
		});
	}

	private void initializePlayerIfNeeded(final String accessToken, final CompletionBlock<Boolean> completion)
	{
		//make sure we have the player scope
		if(!auth.hasPlayerScope())
		{
			completion.invoke(true, null);
			return;
		}

		//check for clientID
		if(auth.clientID == null)
		{
			completion.invoke(
					false,
					new SpotifyError(
							SpotifyError.Code.MISSING_PARAMETERS,
							"missing option clientID")
			);
			return;
		}

		//check if player already exists
		if(player != null)
		{
			loginPlayer(auth.getAccessToken(), new CompletionBlock<Boolean>() {
				@Override
				public void invoke(Boolean loggedIn, SpotifyError error)
				{
					completion.invoke(loggedIn, error);
				}
			});
			return;
		}

		//initialize player
		Config playerConfig = new Config(reactContext.getCurrentActivity().getApplicationContext(), accessToken, auth.clientID);
		player = Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver(){
			@Override
			public void onError(Throwable error)
			{
				Spotify.destroyPlayer(player);
				player = null;
				completion.invoke(false, new SpotifyError(SpotifyError.Code.INITIALIZATION_FAILED, error.getLocalizedMessage()));
			}

			@Override
			public void onInitialized(SpotifyPlayer newPlayer)
			{
				player = newPlayer;

				//setup player
				player.setConnectivityStatus(connectivityStatusCallback, getNetworkConnectivity(reactContext.getCurrentActivity()));
				player.addNotificationCallback(RCTSpotifyModule.this);
				player.addConnectionStateCallback(RCTSpotifyModule.this);

				loginPlayer(accessToken, new CompletionBlock<Boolean>() {
					@Override
					public void invoke(Boolean loggedIn, SpotifyError error)
					{
						completion.invoke(loggedIn, error);
					}
				});
			}
		});
	}

	private void loginPlayer(final String accessToken, final CompletionBlock<Boolean> completion)
	{
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
				playerLoginResponses.add(new CompletionBlock<Boolean>() {
					@Override
					public void invoke(Boolean loggedIn, SpotifyError error)
					{
						completion.invoke(loggedIn, error);
					}
				});
			}
		}

		if(loggedIn)
		{
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
		auth.showAuthActivity(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean loggedIn, SpotifyError error)
			{
				if(!loggedIn || error!=null)
				{
					if(callback!=null)
					{
						callback.invoke(loggedIn, Convert.fromRCTSpotifyError(error));
					}
					return;
				}
				//disable activity interaction
				AuthActivity.currentActivity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
						WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
				//initialize player
				initializePlayerIfNeeded(auth.getAccessToken(), new CompletionBlock<Boolean>() {
					@Override
					public void invoke(final Boolean loggedIn, final SpotifyError error)
					{
						//re-enable activity interaction and dismiss
						AuthActivity.currentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
						AuthActivity.currentActivity.onFinishCompletion = new CompletionBlock<Void>() {
							@Override
							public void invoke(Void obj, SpotifyError unusedError)
							{
								//perform callback
								if(callback!=null)
								{
									callback.invoke(loggedIn, Convert.fromRCTSpotifyError(error));
								}
							}
						};
						AuthActivity.currentActivity.finish();
						AuthActivity.currentActivity = null;
					}
				});
			}
		});
	}

	@ReactMethod
	//logout((error?))
	public void logout(final Callback callback)
	{
		//destroy the player
		if(player != null)
		{
			player.logout();
			Spotify.destroyPlayer(player);
			player = null;
		}

		//clear session
		auth.clearSession();

		if(callback!=null)
		{
			callback.invoke(nullobj());
		}
	}

	@ReactMethod(isBlockingSynchronousMethod = true)
	//isLoggedIn()
	public Boolean isLoggedIn()
	{
		if(!initialized)
		{
			return false;
		}
		else if(auth.getAccessToken()==null)
		{
			return false;
		}
		else if(auth.hasPlayerScope() && (player==null || !player.isLoggedIn()))
		{
			return false;
		}
		return true;
	}

	@ReactMethod
	//isLoggedInAsync((loggedIn))
	public void isLoggedInAsync(final Callback callback)
	{
		callback.invoke(isLoggedIn());
	}

	@ReactMethod
	//handleAuthURL(url)
	public Boolean handleAuthURL(String url)
	{
		System.out.println("Sent URL to handleAuthURL: "+url);
		//TODO for some reason we don't use this on Android, despite having to give a redirectURL
		return false;
	}




	void prepareForPlayer(final CompletionBlock<Boolean> completion)
	{
		logBackInIfNeeded(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean loggedIn, SpotifyError error) {
				error = null;
				if(!initialized)
				{
					error = new SpotifyError(SpotifyError.Code.NOT_INITIALIZED, "Spotify has not been initiaized");
				}
				else if(player==null)
				{
					error = SpotifyError.fromSDKError(SpotifyError.getNativeCode(Error.kSpErrorUninitialized));
				}
				completion.invoke(loggedIn, error);
			}
		});
	}

	@ReactMethod
	//playURI(spotifyURI, startIndex, startPosition, (error?))
	public void playURI(final String spotifyURI, final int startIndex, final double startPosition, final Callback callback)
	{
		if(spotifyURI==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("spotifyURI"));
			}
			return;
		}
		prepareForPlayer(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean success, SpotifyError error)
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
						if(callback!=null)
						{
							callback.invoke(new SpotifyError(error).toReactObject());
						}
					}

					@Override
					public void onSuccess()
					{
						if(callback!=null)
						{
							callback.invoke(nullobj());
						}
					}
				}, spotifyURI, startIndex, (int)(startPosition*1000));
			}
		});
	}

	@ReactMethod
	//queueURI(spotifyURI, (error?))
	public void queueURI(final String spotifyURI, final Callback callback)
	{
		if(spotifyURI==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("spotifyURI"));
			}
			return;
		}
		prepareForPlayer(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean success, SpotifyError error)
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
						if(callback!=null)
						{
							callback.invoke(new SpotifyError(error).toReactObject());
						}
					}

					@Override
					public void onSuccess()
					{
						if(callback!=null)
						{
							callback.invoke(nullobj());
						}
					}
				}, spotifyURI);
			}
		});
	}

	@ReactMethod
	//setVolume(volume, (error?))
	public void setVolume(double volume, final Callback callback)
	{
		//TODO implement this with a custom AudioController
		callback.invoke(new SpotifyError(SpotifyError.Code.NOT_IMPLEMENTED, "setVolume does not work on android"));
	}

	@ReactMethod(isBlockingSynchronousMethod = true)
	//getVolume()
	public Double getVolume()
	{
		//TODO implement this with a custom AudioController
		return 1.0;
	}

	@ReactMethod
	//getVolumeAsync((volume))
	public void getVolumeAsync(final Callback callback)
	{
		callback.invoke(getVolume());
	}

	@ReactMethod
	//setPlaying(playing, (error?))
	public void setPlaying(final boolean playing, final Callback callback)
	{
		prepareForPlayer(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean success, SpotifyError error)
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
					if(callback!=null)
					{
						callback.invoke(nullobj());
					}
					return;
				}

				if(playing)
				{
					player.resume(new Player.OperationCallback(){
						@Override
						public void onError(com.spotify.sdk.android.player.Error error)
						{
							if(callback!=null)
							{
								callback.invoke(new SpotifyError(error).toReactObject());
							}
						}

						@Override
						public void onSuccess()
						{
							if(callback!=null)
							{
								callback.invoke(nullobj());
							}
						}
					});
				}
				else
				{
					player.pause(new Player.OperationCallback(){
						@Override
						public void onError(com.spotify.sdk.android.player.Error error)
						{
							if(callback!=null)
							{
								callback.invoke(new SpotifyError(error).toReactObject());
							}
						}

						@Override
						public void onSuccess()
						{
							if(callback!=null)
							{
								callback.invoke(nullobj());
							}
						}
					});
				}
			}
		});
	}

	@ReactMethod(isBlockingSynchronousMethod = true)
	//getPlaybackState()
	public WritableMap getPlaybackState()
	{
		if(player==null)
		{
			return null;
		}
		return Convert.fromPlaybackState(player.getPlaybackState());
	}

	@ReactMethod
	//getPlaybackStateAsync((playbackState))
	public void getPlaybackStateAsync(final Callback callback)
	{
		callback.invoke(getPlaybackState());
	}

	@ReactMethod
	//skipToNext((error?))
	public void skipToNext(final Callback callback)
	{
		prepareForPlayer(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean success, SpotifyError error)
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
						if(callback!=null)
						{
							callback.invoke(new SpotifyError(error).toReactObject());
						}
					}

					@Override
					public void onSuccess()
					{
						if(callback!=null)
						{
							callback.invoke(nullobj());
						}
					}
				});
			}
		});
	}

	@ReactMethod
	//skipToPrevious((error?))
	public void skipToPrevious(final Callback callback)
	{
		prepareForPlayer(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean success, SpotifyError error)
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
						if(callback!=null)
						{
							callback.invoke(new SpotifyError(error).toReactObject());
						}
					}

					@Override
					public void onSuccess()
					{
						if(callback!=null)
						{
							callback.invoke(nullobj());
						}
					}
				});
			}
		});
	}

	@ReactMethod
	//seekToPosition(position, (error?))
	public void seekToPosition(final double position, final Callback callback)
	{
		prepareForPlayer(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean obj, SpotifyError error)
			{
				if(error!=null)
				{
					if(callback!=null)
					{
						callback.invoke(error.toReactObject());
					}
					return;
				}
				player.seekToPosition(new Player.OperationCallback() {
					@Override
					public void onError(Error error)
					{
						if(callback!=null)
						{
							callback.invoke(new SpotifyError(error).toReactObject());
						}
					}

					@Override
					public void onSuccess()
					{
						if(callback!=null)
						{
							callback.invoke(nullobj());
						}
					}
				}, (int)(position*1000));
			}
		});
	}

	@ReactMethod
	//setShuffling(shuffling, (error?))
	public void setShuffling(final boolean shuffling, final Callback callback)
	{
		prepareForPlayer(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean success, SpotifyError error)
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
						if(callback!=null)
						{
							callback.invoke(new SpotifyError(error).toReactObject());
						}
					}

					@Override
					public void onSuccess()
					{
						if(callback!=null)
						{
							callback.invoke(nullobj());
						}
					}
				}, shuffling);
			}
		});
	}

	@ReactMethod
	//setRepeating(repeating, (error?))
	public void setRepeating(final boolean repeating, final Callback callback)
	{
		prepareForPlayer(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean obj, SpotifyError error)
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
						if(callback!=null)
						{
							callback.invoke(new SpotifyError(error).toReactObject());
						}
					}

					@Override
					public void onSuccess()
					{
						if(callback!=null)
						{
							callback.invoke(nullobj());
						}
					}
				}, repeating);
			}
		});
	}




	void prepareForRequest(final CompletionBlock<Boolean> completion)
	{
		logBackInIfNeeded(new CompletionBlock<Boolean>() {
			@Override
			public void invoke(Boolean loggedIn, SpotifyError error) {
				error = null;
				if(!initialized)
				{
					error = new SpotifyError(SpotifyError.Code.NOT_INITIALIZED, "Spotify has not been initiaized");
				}
				else if(auth.getAccessToken()==null)
				{
					error = new SpotifyError(SpotifyError.Code.NOT_LOGGED_IN, "You are not logged in");
				}
				completion.invoke(loggedIn, error);
			}
		});
	}

	void doAPIRequest(final String endpoint, final String method, final ReadableMap params, final boolean jsonBody, final CompletionBlock<Object> completion)
	{
		prepareForRequest(new CompletionBlock<Boolean>(){
			@Override
			public void invoke(Boolean success, SpotifyError error)
			{
				HashMap<String, String> headers = new HashMap<>();
				String accessToken = auth.getAccessToken();
				if(accessToken != null)
				{
					headers.put("Authorization", "Bearer "+accessToken);
				}

				String url = "https://api.spotify.com/"+endpoint;

				//append query string to url if necessary
				if(!jsonBody && params!=null && method.equalsIgnoreCase("GET"))
				{
					url += "?"+Utils.makeQueryString(params);
				}

				//create request body
				byte[] body = null;
				if(params!=null)
				{
					if(jsonBody)
					{
						JSONObject obj = Convert.toJSONObject(params);
						if (obj != null)
						{
							body = obj.toString().getBytes();
						}
					}
					else if(!method.equalsIgnoreCase("GET"))
					{
						body = Utils.makeQueryString(params).getBytes();
					}
				}

				if(jsonBody)
				{
					headers.put("Content-Type", "application/json");
				}

				Utils.doHTTPRequest(url, method, headers, body, new CompletionBlock<NetworkResponse>() {
					@Override
					public void invoke(NetworkResponse response, SpotifyError error) {
						if(response==null)
						{
							completion.invoke(null, error);
							return;
						}

						String responseStr = Utils.getResponseString(response);

						JSONObject resultObj = null;
						String contentType = response.headers.get("Content-Type");
						if(contentType!=null && contentType.equalsIgnoreCase("application/json") && response.statusCode!=204)
						{
							try
							{
								resultObj = new JSONObject(responseStr);
							}
							catch (JSONException e)
							{
								completion.invoke(null, new SpotifyError(SpotifyError.Code.REQUEST_ERROR, "Invalid response format"));
								return;
							}
						}

						if(resultObj != null)
						{
							try
							{
								String errorDescription = resultObj.getString("error_description");
								error = new SpotifyError(SpotifyError.SPOTIFY_WEB_DOMAIN, response.statusCode, errorDescription);
							}
							catch(JSONException e1)
							{
								try
								{
									JSONObject errorObj = resultObj.getJSONObject("error");
									error = new SpotifyError(SpotifyError.SPOTIFY_WEB_DOMAIN, errorObj.getInt("status"), errorObj.getString("message"));
								}
								catch(JSONException e2)
								{
									//do nothing. this means we don't have an error
								}
							}
						}

						Object result = null;
						if(resultObj != null)
						{
							result = Convert.fromJSONObject(resultObj);
						}
						else
						{
							result = responseStr;
						}
						completion.invoke(result, error);
					}
				});
			}
		});
	}

	@ReactMethod
	//sendRequest(endpoint, method, params, isJSONBody, (result?, error?))
	public void sendRequest(String endpoint, String method, ReadableMap params, boolean jsonBody, final Callback callback)
	{
		doAPIRequest(endpoint, method, params, jsonBody, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object responseObj, SpotifyError error)
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
	//getMe((result?, error?))
	public void getMe(final Callback callback)
	{
		doAPIRequest("v1/me", "GET", null, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}



	@ReactMethod
	//search(query, types, options?, (result?, error?))
	public void search(String query, ReadableArray types, ReadableMap options, final Callback callback)
	{
		if(query==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("query"));
			}
			return;
		}
		else if(types==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("types"));
			}
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

		doAPIRequest("v1/search", "GET", body, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getAlbum(albumID, options?, (result?, error?))
	public void getAlbum(String albumID, ReadableMap options, final Callback callback)
	{
		if(albumID==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("albumID"));
			}
			return;
		}
		doAPIRequest("v1/albums/"+albumID, "GET", options, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getAlbums(albumIDs, options?, (result?, error?))
	public void getAlbums(ReadableArray albumIDs, ReadableMap options, final Callback callback)
	{
		if(albumIDs==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("albumIDs"));
			}
			return;
		}
		WritableMap body = Convert.toWritableMap(options);
		body.putString("ids", Convert.joinedIntoString(albumIDs, ","));
		doAPIRequest("v1/albums", "GET", body, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getAlbumTracks(albumID, options?, (result?, error?))
	public void getAlbumTracks(String albumID, ReadableMap options, final Callback callback)
	{
		if(albumID==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("albumID"));
			}
			return;
		}
		doAPIRequest("v1/albums/"+albumID+"/tracks", "GET", options, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getArtist(artistID, options?, (result?, error?))
	public void getArtist(String artistID, ReadableMap options, final Callback callback)
	{
		if(artistID==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("artistID"));
			}
			return;
		}
		doAPIRequest("v1/artists/"+artistID, "GET", options, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getArtists(artistIDs, options?, (result?, error?))
	public void getArtists(ReadableArray artistIDs, ReadableMap options, final Callback callback)
	{
		if(artistIDs==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("artistIDs"));
			}
			return;
		}
		WritableMap body = Convert.toWritableMap(options);
		body.putString("ids", Convert.joinedIntoString(artistIDs, ","));
		doAPIRequest("v1/artists", "GET", body, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getArtistAlbums(artistID, options?, (result?, error?))
	public void getArtistAlbums(String artistID, ReadableMap options, final Callback callback)
	{
		if(artistID==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("artistID"));
			}
			return;
		}
		doAPIRequest("v1/artists/"+artistID+"/albums", "GET", options, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getArtistTopTracks(artistID, country, options?, (result?, error?))
	public void getArtistTopTracks(String artistID, ReadableMap options, final Callback callback)
	{
		if(artistID==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("artistID"));
			}
			return;
		}
		doAPIRequest("v1/artists/"+artistID+"/top-tracks", "GET", options, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getArtistRelatedArtists(artistID, options?, (result?, error?))
	public void getArtistRelatedArtists(String artistID, ReadableMap options, final Callback callback)
	{
		if(artistID==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("artistID"));
			}
			return;
		}
		doAPIRequest("v1/artists/"+artistID+"/related-artists", "GET", options, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getTrack(trackID, options?, (result?, error?))
	public void getTrack(String trackID, ReadableMap options, final Callback callback)
	{
		if(trackID==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("trackID"));
			}
			return;
		}
		doAPIRequest("v1/tracks/"+trackID, "GET", options, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getTracks(trackIDs, options?, (result?, error?))
	public void getTracks(ReadableArray trackIDs, ReadableMap options, final Callback callback)
	{
		if(trackIDs==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("trackIDs"));
			}
			return;
		}
		WritableMap body = Convert.toWritableMap(options);
		body.putString("ids", Convert.joinedIntoString(trackIDs, ","));
		doAPIRequest("v1/tracks", "GET", body, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getTrackAudioAnalysis(trackID, options?, (result?, error?))
	public void getTrackAudioAnalysis(String trackID, ReadableMap options, final Callback callback)
	{
		if(trackID==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("trackID"));
			}
			return;
		}
		doAPIRequest("v1/audio-analysis/"+trackID, "GET", options, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getTrackAudioFeatures(trackID, options?, (result?, error?))
	public void getTrackAudioFeatures(String trackID, ReadableMap options, final Callback callback)
	{
		if(trackID==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("trackID"));
			}
			return;
		}
		doAPIRequest("v1/audio-features/"+trackID, "GET", options, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}

	@ReactMethod
	//getTracks(trackIDs, options?, (result?, error?))
	public void getTracksAudioFeatures(ReadableArray trackIDs, ReadableMap options, final Callback callback)
	{
		if(trackIDs==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), SpotifyError.getNullParameterError("trackIDs"));
			}
			return;
		}
		WritableMap body = Convert.toWritableMap(options);
		body.putString("ids", Convert.joinedIntoString(trackIDs, ","));
		doAPIRequest("v1/audio-features", "GET", body, false, new CompletionBlock<Object>() {
			@Override
			public void invoke(Object resultObj, SpotifyError error)
			{
				if(callback!=null)
				{
					callback.invoke(resultObj, Convert.fromRCTSpotifyError(error));
				}
			}
		});
	}



	private final Player.OperationCallback connectivityStatusCallback = new Player.OperationCallback() {
		@Override
		public void onSuccess()
		{
			//TODO handle success
		}

		@Override
		public void onError(com.spotify.sdk.android.player.Error error)
		{
			//TODO handle error
			System.out.println("Spotify Connectivity Error: "+error.toString());
		}
	};



	//ConnectionStateCallback

	@Override
	public void onLoggedIn()
	{
		//handle loginPlayer callbacks
		ArrayList<CompletionBlock<Boolean>> loginResponses;
		synchronized(playerLoginResponses)
		{
			loginResponses = new ArrayList<>(playerLoginResponses);
			playerLoginResponses.clear();
		}
		for(CompletionBlock<Boolean> response : loginResponses)
		{
			response.invoke(true, null);
		}
	}

	@Override
	public void onLoggedOut()
	{
		//handle loginPlayer callbacks
		ArrayList<CompletionBlock<Boolean>> loginResponses;
		synchronized(playerLoginResponses)
		{
			loginResponses = new ArrayList<>(playerLoginResponses);
			playerLoginResponses.clear();
		}
		for(CompletionBlock<Boolean> response : loginResponses)
		{
			response.invoke(false, new SpotifyError(SpotifyError.Code.NOT_LOGGED_IN, "You have been logged out"));
		}
	}

	@Override
	public void onLoginFailed(com.spotify.sdk.android.player.Error error)
	{
		//handle loginPlayer callbacks
		ArrayList<CompletionBlock<Boolean>> loginResponses;
		synchronized(playerLoginResponses)
		{
			loginResponses = new ArrayList<>(playerLoginResponses);
			playerLoginResponses.clear();
		}
		for(CompletionBlock<Boolean> response : loginResponses)
		{
			response.invoke(false, new SpotifyError(error));
		}
	}

	@Override
	public void onTemporaryError()
	{
		//TODO handle temporary connection error
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

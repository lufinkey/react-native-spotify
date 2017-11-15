package com.lufinkey.react.spotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.WindowManager;

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

import java.util.ArrayList;
import java.util.HashMap;

public class RCTSpotifyModule extends ReactContextBaseJavaModule implements Player.NotificationCallback, ConnectionStateCallback
{
	private final ReactApplicationContext reactContext;

	private boolean initialized;

	private BroadcastReceiver networkStateReceiver;

	private Auth auth;
	private SpotifyPlayer player;
	private final ArrayList<RCTSpotifyCallback<Boolean>> playerLoginResponses;

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
		auth = new Auth();
		auth.reactContext = reactContext;
		auth.clientID = options.getString("clientID");
		auth.redirectURL = options.getString("redirectURL");
		auth.sessionUserDefaultsKey = options.getString("sessionUserDefaultsKey");
		ReadableArray scopes = options.getArray("scopes");
		if(scopes!=null)
		{
			String[] requestedScopes = new String[scopes.size()];
			for(int i=0; i<scopes.size(); i++)
			{
				requestedScopes[i] = scopes.getString(i);
			}
			auth.requestedScopes = requestedScopes;
		}
		auth.tokenSwapURL = options.getString("tokenSwapURL");
		auth.tokenRefreshURL = options.getString("tokenRefreshURL");
		auth.load();

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
		auth.renewSessionIfNeeded(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean success, RCTSpotifyError error)
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
					initializePlayerIfNeeded(auth.getAccessToken(), new RCTSpotifyCallback<Boolean>() {
						@Override
						public void invoke(Boolean loggedIn, RCTSpotifyError error)
						{
							completion.invoke(loggedIn, error);
						}
					});
				}
			}
		});
	}

	private void initializePlayerIfNeeded(final String accessToken, final RCTSpotifyCallback<Boolean> completion)
	{
		System.out.println("initializePlayer");

		//make sure we have the player scope
		if(!auth.hasPlayerScope())
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
			loginPlayer(auth.getAccessToken(), new RCTSpotifyCallback<Boolean>() {
				@Override
				public void invoke(Boolean loggedIn, RCTSpotifyError error)
				{
					completion.invoke(loggedIn, error);
				}
			});
			return;
		}

		//initialize player
		Config playerConfig = new Config(reactContext.getCurrentActivity().getApplicationContext(), accessToken, clientID);
		player = Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver(){
			@Override
			public void onError(Throwable error)
			{
				Spotify.destroyPlayer(player);
				player = null;
				completion.invoke(false, new RCTSpotifyError(RCTSpotifyError.Code.INITIALIZATION_FAILED, error.getLocalizedMessage()));
			}

			@Override
			public void onInitialized(SpotifyPlayer newPlayer)
			{
				player = newPlayer;

				//setup player
				player.setConnectivityStatus(connectivityStatusCallback, getNetworkConnectivity(reactContext.getCurrentActivity()));
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
		auth.showAuthActivity(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean loggedIn, RCTSpotifyError error)
			{
				if(!loggedIn || error!=null)
				{
					callback.invoke(loggedIn, RCTSpotifyConvert.fromRCTSpotifyError(error));
					return;
				}
				//disable activity interaction
				SpotifyAuthActivity.currentActivity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
						WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
				//initialize player
				initializePlayerIfNeeded(auth.getAccessToken(), new RCTSpotifyCallback<Boolean>() {
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
								callback.invoke(loggedIn, RCTSpotifyConvert.fromRCTSpotifyError(error));
							}
						};
						SpotifyAuthActivity.currentActivity.finish();
						SpotifyAuthActivity.currentActivity = null;
					}
				});
			}
		});
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
		player.logout();
		Spotify.destroyPlayer(player);
		player = null;

		//clear session
		auth.clearSession();

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
				error = null;
				if(!initialized)
				{
					error = new RCTSpotifyError(RCTSpotifyError.Code.NOT_INITIALIZED, "Spotify has not been initiaized");
				}
				else if(player==null)
				{
					error = RCTSpotifyError.fromSDKError(RCTSpotifyError.getNativeCode(Error.kSpErrorUninitialized));
				}
				completion.invoke(loggedIn, error);
			}
		});
	}

	@ReactMethod
	//playURI(spotifyURI, startIndex, startPosition, (error?))
	void playURI(final String spotifyURI, final int startIndex, final double startPosition, final Callback callback)
	{
		if(spotifyURI==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("spotifyURI"));
			}
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
						if(callback!=null)
						{
							callback.invoke(new RCTSpotifyError(error).toReactObject());
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
	void queueURI(final String spotifyURI, final Callback callback)
	{
		if(spotifyURI==null)
		{
			if(callback!=null)
			{
				callback.invoke(nullobj(), RCTSpotifyError.getNullParameterError("spotifyURI"));
			}
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
								callback.invoke(new RCTSpotifyError(error).toReactObject());
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
								callback.invoke(new RCTSpotifyError(error).toReactObject());
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
						if(callback!=null)
						{
							callback.invoke(new RCTSpotifyError(error).toReactObject());
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
						if(callback!=null)
						{
							callback.invoke(new RCTSpotifyError(error).toReactObject());
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
	void seekToPosition(final double position, final Callback callback)
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
				player.seekToPosition(new Player.OperationCallback() {
					@Override
					public void onError(Error error)
					{
						if(callback!=null)
						{
							callback.invoke(new RCTSpotifyError(error).toReactObject());
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
						if(callback!=null)
						{
							callback.invoke(new RCTSpotifyError(error).toReactObject());
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
						if(callback!=null)
						{
							callback.invoke(new RCTSpotifyError(error).toReactObject());
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




	void prepareForRequest(final RCTSpotifyCallback<Boolean> completion)
	{
		logBackInIfNeeded(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean loggedIn, RCTSpotifyError error) {
				error = null;
				if(!initialized)
				{
					error = new RCTSpotifyError(RCTSpotifyError.Code.NOT_INITIALIZED, "Spotify has not been initiaized");
				}
				else if(auth.getAccessToken()==null)
				{
					error = new RCTSpotifyError(RCTSpotifyError.Code.NOT_LOGGED_IN, "You are not logged in");
				}
				completion.invoke(loggedIn, error);
			}
		});
	}

	void doAPIRequest(final String endpoint, final String method, final ReadableMap params, final boolean jsonBody, final RCTSpotifyCallback<ReadableMap> completion)
	{
		prepareForRequest(new RCTSpotifyCallback<Boolean>(){
			@Override
			public void invoke(Boolean success, RCTSpotifyError error)
			{
				HashMap<String, String> headers = new HashMap<>();
				String accessToken = auth.getAccessToken();
				if(accessToken != null)
				{
					headers.put("Authorization", "Bearer "+accessToken);
				}
				//TODO add authorization to headers
				Utils.doHTTPRequest("https://api.spotify.com/v1/"+endpoint, method, params, jsonBody, headers, new RCTSpotifyCallback<String>() {
					@Override
					public void invoke(String response, RCTSpotifyError error) {
						if(response==null)
						{
							completion.invoke(null, error);
						}
						else
						{
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
										new RCTSpotifyError(RCTSpotifyError.SPOTIFY_WEB_DOMAIN,
												errorObj.getInt("status"),
												errorObj.getString("message")));
								return;
							}
							catch(JSONException e)
							{
								//do nothing. this means we don't have an error
							}

							completion.invoke(RCTSpotifyConvert.fromJSONObject(responseObj), null);
						}
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

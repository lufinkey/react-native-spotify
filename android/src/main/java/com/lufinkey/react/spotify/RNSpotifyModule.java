package com.lufinkey.react.spotify;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.view.Gravity;

import com.android.volley.NetworkResponse;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import com.lufinkey.react.eventemitter.RNEventConformer;
import com.lufinkey.react.eventemitter.RNEventEmitter;
import com.spotify.sdk.android.player.*;
import com.spotify.sdk.android.player.Error;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

public class RNSpotifyModule extends ReactContextBaseJavaModule implements Player.NotificationCallback, ConnectionStateCallback, RNEventConformer
{
	private final ReactApplicationContext reactContext;

	private boolean initialized;
	private boolean loggingOutPlayer;

	private BroadcastReceiver networkStateReceiver;
	private Connectivity currentConnectivity = Connectivity.OFFLINE;

	private Auth auth;
	private SpotifyPlayer player;
	private final ArrayList<Completion<Void>> playerInitResponses;
	private final ArrayList<Completion<Void>> playerLoginResponses;
	private final ArrayList<Completion<Void>> playerLogoutResponses;

	private ReadableMap options;

	private String loginLoadingText = "Loading...";

	RNSpotifyModule(ReactApplicationContext reactContext)
	{
		super(reactContext);

		this.reactContext = reactContext;
		Utils.reactContext = reactContext;

		initialized = false;
		loggingOutPlayer = false;

		networkStateReceiver = null;

		auth = null;
		player = null;
		playerInitResponses = new ArrayList<>();
		playerLoginResponses = new ArrayList<>();
		playerLogoutResponses = new ArrayList<>();

		options = null;
	}

	@Override
	public String getName()
	{
		return "RNSpotify";
	}

	@Override
	public void onCatalystInstanceDestroy()
	{
		if(player != null)
		{
			Spotify.destroyPlayer(this);
			player = null;
		}
	}

	private Object nullobj()
	{
		return null;
	}

	private void sendEvent(String event, Object... args)
	{
		RNEventEmitter.emitEvent(this.reactContext, this, event, args);
	}

	@ReactMethod
	//test()
	public void test()
	{
		System.out.println("ayy lmao");
	}

	@ReactMethod
	//initialize(options)
	public void initialize(ReadableMap options, final Promise promise)
	{
		// ensure module is not already initialized
		if(initialized)
		{
			SpotifyError.Code.AlreadyInitialized.reject(promise);
			return;
		}

		// ensure options is not null or missing fields
		if(options==null)
		{
			SpotifyError.getNullParameterError("options").reject(promise);
			return;
		}
		else if(!options.hasKey("clientID"))
		{
			SpotifyError.getMissingOptionError("clientId").reject(promise);
			return;
		}

		this.options = options;

		// load auth options
		auth = new Auth();
		auth.reactContext = reactContext;
		auth.clientID = options.getString("clientID");
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

		// load android-specific options
		ReadableMap androidOptions = Arguments.createMap();
		if(options.hasKey("android"))
		{
			androidOptions = options.getMap("android");
		}
		if(androidOptions.hasKey("loginLoadingText"))
		{
			loginLoadingText = androidOptions.getString("loginLoadingText");
		}

		// add connectivity state listener
		currentConnectivity = Utils.getNetworkConnectivity();
		networkStateReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Connectivity prevConnectivity = currentConnectivity;
				Connectivity connectivity = Utils.getNetworkConnectivity();
				currentConnectivity = connectivity;
				if (player != null && player.isInitialized())
				{
					// update the player with the connection state, because Android makes no sense
					player.setConnectivityStatus(null, connectivity);

					// call events
					if(prevConnectivity==Connectivity.OFFLINE && connectivity!=Connectivity.OFFLINE)
					{
						sendEvent("reconnect");
					}
					else if(prevConnectivity!=Connectivity.OFFLINE && connectivity==Connectivity.OFFLINE)
					{
						sendEvent("disconnect");
					}
				}
			}
		};
		IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
		reactContext.getApplicationContext().registerReceiver(networkStateReceiver, filter);

		// done initializing
		initialized = true;

		// call promise
		boolean loggedIn = isLoggedIn();
		promise.resolve(loggedIn);
		if(loggedIn)
		{
			sendEvent("login");
		}

		// try to log back in if necessary
		logBackInIfNeeded(new Completion<Boolean>() {
			@Override
			public void onReject(SpotifyError error)
			{
				// failure
			}

			@Override
			public void onResolve(Boolean loggedIn)
			{
				if(loggedIn)
				{
					// initialize player
					initializePlayerIfNeeded(new Completion<Void>() {
						@Override
						public void onComplete(Void unused, SpotifyError unusedError) {
							// done
						}
					});
				}
			}
		}, true);
	}

	@ReactMethod(isBlockingSynchronousMethod = true)
	//isInitialized()
	public Boolean isInitialized()
	{
		return initialized;
	}

	@ReactMethod
	//isInitializedAsync()
	public void isInitializedAsync(final Promise promise)
	{
		promise.resolve(isInitialized());
	}

	private void logBackInIfNeeded(final Completion<Boolean> completion, final boolean waitForDefinitiveResponse)
	{
		// ensure auth is actually logged in
		if(!auth.isLoggedIn())
		{
			// auth is not logged in, so there's nothing we need to renew
			if(completion != null)
			{
				completion.resolve(false);
			}
			return;
		}
		// attempt to renew auth session
		auth.renewSessionIfNeeded(new Completion<Boolean>() {
			@Override
			public void onReject(SpotifyError error)
			{
				// session renewal failed (we should log out)
				if(isLoggedIn())
				{
					// session renewal returned a failure, but we're still logged in
					// log out player
					logoutPlayer(new Completion<Void>() {
						@Override
						public void onComplete(Void unused, SpotifyError error)
						{
							// clear session
							boolean loggedIn = isLoggedIn();
							if(loggedIn)
							{
								auth.clearSession();
							}
							// call completion
							if(completion != null)
							{
								completion.resolve(false);
							}
							// send logout event
							if(loggedIn)
							{
								sendEvent("logout");
							}
						}
					});
				}
				else
				{
					// auth wasn't logged in during the renewal failure, so just fail
					if(completion != null)
					{
						if (waitForDefinitiveResponse)
						{
							completion.resolve(false);
						}
						else
						{
							completion.reject(error);
						}
					}
				}
			}

			@Override
			public void onResolve(Boolean renewed)
			{
				completion.resolve(true);
			}
		}, waitForDefinitiveResponse);
	}

	private void initializePlayerIfNeeded(final Completion<Void> completion)
	{
		// make sure we have the player scope
		if(!auth.hasPlayerScope())
		{
			completion.resolve(null);
			return;
		}

		// check if player already exists and is initialized
		if(player != null && player.isInitialized())
		{
			loginPlayer(completion);
			return;
		}

		// ensure only one thread actually invokes the initialization, and the others just wait
		boolean firstInitAttempt = false;
		synchronized (playerInitResponses)
		{
			if(playerInitResponses.size() == 0)
			{
				firstInitAttempt = true;
			}
			playerInitResponses.add(completion);
		}

		// if this is the first thread that has tried to initialize the player, then actually do it
		if(firstInitAttempt)
		{
			//initialize player
			final Object reference = this;
			Config playerConfig = new Config(reactContext.getApplicationContext(), auth.getAccessToken(), auth.clientID);
			player = Spotify.getPlayer(playerConfig, reference, new SpotifyPlayer.InitializationObserver(){
				@Override
				public void onError(Throwable error)
				{
					// error initializing the player
					if(player != null)
					{
						// destroy the player
						Spotify.destroyPlayer(reference);
						player = null;
					}

					// call init responses
					ArrayList<Completion<Void>> initResponses = null;
					synchronized (playerInitResponses)
					{
						initResponses = new ArrayList<>(playerInitResponses);
						playerInitResponses.clear();
					}
					for(Completion<Void> completion : initResponses)
					{
						completion.reject(new SpotifyError(Error.kSpErrorInitFailed, error.getLocalizedMessage()));
					}
				}

				@Override
				public void onInitialized(SpotifyPlayer newPlayer)
				{
					// player successfully initialized
					player = newPlayer;

					// setup player
					currentConnectivity = Utils.getNetworkConnectivity();
					player.setConnectivityStatus(null, currentConnectivity);
					player.addNotificationCallback(RNSpotifyModule.this);
					player.addConnectionStateCallback(RNSpotifyModule.this);

					// attempt to log in the player
					loginPlayer(new Completion<Void>() {
						@Override
						public void onComplete(Void unused, SpotifyError error)
						{
							// call init responses
							ArrayList<Completion<Void>> initResponses = null;
							synchronized (playerInitResponses)
							{
								initResponses = new ArrayList<>(playerInitResponses);
								playerInitResponses.clear();
							}
							for(Completion<Void> completion : initResponses)
							{
								if(error == null)
								{
									completion.resolve(null);
								}
								else
								{
									completion.reject(error);
								}
							}
						}
					});
				}
			});
		}
	}

	private void loginPlayer(final Completion<Void> completion)
	{
		boolean loggedIn = false;
		boolean firstLoginAttempt = false;

		// add completion to a list to be called when the login succeeds or fails
		synchronized(playerLoginResponses)
		{
			// ensure we're not already logged in
			if(player.isLoggedIn())
			{
				loggedIn = true;
			}
			else
			{
				if(playerLoginResponses.size()==0)
				{
					firstLoginAttempt = true;
				}
				//wait for RNSpotifyModule.onLoggedIn
				// or RNSpotifyModule.onLoginFailed
				// or RNSpotifyModule.onLoggedOut
				playerLoginResponses.add(completion);
			}
		}

		if(loggedIn)
		{
			// we're already logged in, so finish
			completion.resolve(null);
		}
		else if(firstLoginAttempt)
		{
			// only the first thread to call loginPlayer should actually attempt to log the player in
			player.login(auth.getAccessToken());
		}
	}

	private void logoutPlayer(final Completion<Void> completion)
	{
		if(player == null)
		{
			completion.resolve(null);
			return;
		}

		boolean loggedOut = false;

		synchronized(playerLogoutResponses)
		{
			if(!player.isLoggedIn())
			{
				loggedOut = true;
			}
			else
			{
				// wait for RNSpotifyModule.onLoggedOut
				playerLogoutResponses.add(completion);
			}
		}

		if(loggedOut)
		{
			Spotify.destroyPlayer(this);
			player = null;
			completion.resolve(null);
		}
		else if(!loggingOutPlayer)
		{
			loggingOutPlayer = true;
			player.logout();
		}
	}

	@ReactMethod
	//login()
	public void login(final Promise promise)
	{
		// ensure we're initialized
		if(!initialized)
		{
			SpotifyError.Code.NotInitialized.reject(promise);
			return;
		}
		// perform login flow
		AuthActivity.performAuthFlow(reactContext.getCurrentActivity(), auth, new AuthActivityListener() {
			@Override
			public void onAuthActivityCancel(AuthActivity activity)
			{
				// dismiss activity
				activity.finish(new Completion<Void>() {
					@Override
					public void onComplete(Void unused, SpotifyError unusedError)
					{
						promise.resolve(false);
					}
				});
			}

			@Override
			public void onAuthActivityFailure(AuthActivity activity, final SpotifyError error)
			{
				if(activity == null)
				{
					error.reject(promise);
					return;
				}
				// dismiss activity
				activity.finish(new Completion<Void>() {
					@Override
					public void onComplete(Void unused, SpotifyError unusedError)
					{
						error.reject(promise);
					}
				});
			}

			@Override
			public void onAuthActivityReceivedCode(final AuthActivity activity, String code)
			{
				final ProgressDialog dialog = new ProgressDialog(activity, android.R.style.Theme_Dialog);
				dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
				dialog.getWindow().setGravity(Gravity.CENTER);
				dialog.setMessage(loginLoadingText);
				dialog.setCancelable(false);
				dialog.setIndeterminate(true);
				dialog.show();

				// perform token swap
				auth.swapCodeForToken(code, new Completion<String>() {
					@Override
					public void onReject(final SpotifyError error)
					{
						// failed to get valid auth token
						dialog.dismiss();
						// dismiss activity
						activity.finish(new Completion<Void>() {
							@Override
							public void onComplete(Void unused, SpotifyError unusedError)
							{
								error.reject(promise);
							}
						});
					}

					@Override
					public void onResolve(String accessToken)
					{
						// initialize player
						initializePlayerIfNeeded(new Completion<Void>() {
							@Override
							public void onComplete(Void unused, final SpotifyError unusedError)
							{
								dialog.dismiss();
								// dismiss activity
								activity.finish(new Completion<Void>() {
									@Override
									public void onComplete(Void unused, SpotifyError unusedError)
									{
										boolean loggedIn = isLoggedIn();
										promise.resolve(loggedIn);
										if(loggedIn)
										{
											sendEvent("login");
										}
									}
								});
							}
						});
					}
				});
			}

			@Override
			public void onAuthActivityReceivedToken(final AuthActivity activity, String accessToken, int expiresIn)
			{
				final ProgressDialog dialog = new ProgressDialog(activity, android.R.style.Theme_Black);
				dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
				dialog.getWindow().setGravity(Gravity.CENTER);
				dialog.setMessage(loginLoadingText);
				dialog.setCancelable(false);
				dialog.setIndeterminate(true);
				dialog.show();

				// apply access token
				auth.applyAuthAccessToken(accessToken, expiresIn);

				// initialize player
				initializePlayerIfNeeded(new Completion<Void>() {
					@Override
					public void onComplete(Void unused, SpotifyError unusedError)
					{
						dialog.dismiss();
						// dismiss activity
						activity.finish(new Completion<Void>() {
							@Override
							public void onComplete(Void unused, SpotifyError unusedError)
							{
								boolean loggedIn = isLoggedIn();
								promise.resolve(loggedIn);
								if(loggedIn)
								{
									sendEvent("login");
								}
							}
						});
					}
				});
			}
		});
	}

	@ReactMethod
	//logout()
	public void logout(final Promise promise)
	{
		// ensure we've been initialized
		if(!initialized)
		{
			SpotifyError.Code.NotInitialized.reject(promise);
			return;
		}
		// make sure we're not already logged out
		if(!isLoggedIn())
		{
			promise.resolve(null);
			return;
		}
		// log out and destroy the player
		logoutPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error)
			{
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused)
			{
				boolean loggedIn = isLoggedIn();
				if(loggedIn)
				{
					auth.clearSession();
				}
				promise.resolve(null);
				if(loggedIn)
				{
					sendEvent("logout");
				}
			}
		});
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
		return true;
	}

	@ReactMethod
	//isLoggedInAsync()
	public void isLoggedInAsync(final Promise promise)
	{
		promise.resolve(isLoggedIn());
	}

	@ReactMethod(isBlockingSynchronousMethod = true)
	//getAuth()
	public WritableMap getAuth()
	{
		return Convert.fromAuth(auth);
	}

	@ReactMethod
	//getAuthAsync()
	public void getAuthAsync(final Promise promise)
	{
		promise.resolve(getAuth());
	}




	private void prepareForPlayer(final Completion<Void> completion)
	{
		if(!initialized)
		{
			completion.reject(new SpotifyError(SpotifyError.Code.NotInitialized));
			return;
		}
		logBackInIfNeeded(new Completion<Boolean>() {
			@Override
			public void onReject(SpotifyError error)
			{
				if(player == null && auth.hasPlayerScope())
				{
					completion.reject(error);
				}
				else
				{
					completion.resolve(null);
				}
			}

			@Override
			public void onResolve(Boolean loggedIn)
			{
				if(isLoggedIn())
				{
					initializePlayerIfNeeded(new Completion<Void>() {
						@Override
						public void onReject(SpotifyError error)
						{
							if(player == null && auth.hasPlayerScope())
							{
								completion.reject(error);
							}
							else
							{
								completion.resolve(null);
							}
						}

						@Override
						public void onResolve(Void unused)
						{
							if(player == null && auth.hasPlayerScope())
							{
								completion.reject(new SpotifyError(SpotifyError.Code.PlayerNotReady));
							}
							else
							{
								completion.resolve(null);
							}
						}
					});
				}
				else
				{
					completion.reject(new SpotifyError(SpotifyError.Code.NotLoggedIn));
				}
			}
		}, false);
	}

	@ReactMethod
	//playURI(spotifyURI, startIndex, startPosition)
	public void playURI(final String spotifyURI, final int startIndex, final double startPosition, final Promise promise)
	{
		if(spotifyURI==null)
		{
			SpotifyError.getNullParameterError("spotifyURI").reject(promise);
			return;
		}
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error)
			{
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused)
			{
				player.playUri( new Player.OperationCallback() {
					@Override
					public void onError(com.spotify.sdk.android.player.Error error)
					{
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess()
					{
						promise.resolve(null);
					}
				}, spotifyURI, startIndex, (int)(startPosition*1000));
			}
		});
	}

	@ReactMethod
	//queueURI(spotifyURI)
	public void queueURI(final String spotifyURI, final Promise promise)
	{
		if(spotifyURI==null)
		{
			SpotifyError.getNullParameterError("spotifyURI").reject(promise);
			return;
		}
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error)
			{
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused)
			{
				player.queue(new Player.OperationCallback() {
					@Override
					public void onError(com.spotify.sdk.android.player.Error error)
					{
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess()
					{
						promise.resolve(null);
					}
				}, spotifyURI);
			}
		});
	}

	@ReactMethod
	//setVolume(volume)
	public void setVolume(double volume, final Promise promise)
	{
		//TODO implement this with a custom AudioController
		new SpotifyError(SpotifyError.Code.NotImplemented, "setVolume does not work on android").reject(promise);
	}

	@ReactMethod(isBlockingSynchronousMethod = true)
	//getVolume()
	public Double getVolume()
	{
		//TODO implement this with a custom AudioController
		return 1.0;
	}

	@ReactMethod
	//getVolumeAsync()
	public void getVolumeAsync(final Promise promise)
	{
		promise.resolve(getVolume());
	}

	@ReactMethod
	//setPlaying(playing)
	public void setPlaying(final boolean playing, final Promise promise)
	{
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error)
			{
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused)
			{
				if(playing)
				{
					player.resume(new Player.OperationCallback(){
						@Override
						public void onError(com.spotify.sdk.android.player.Error error)
						{
							new SpotifyError(error).reject(promise);
						}

						@Override
						public void onSuccess()
						{
							promise.resolve(null);
						}
					});
				}
				else
				{
					player.pause(new Player.OperationCallback(){
						@Override
						public void onError(com.spotify.sdk.android.player.Error error)
						{
							new SpotifyError(error).reject(promise);
						}

						@Override
						public void onSuccess()
						{
							promise.resolve(null);
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
	//getPlaybackStateAsync()
	public void getPlaybackStateAsync(final Promise promise)
	{
		promise.resolve(getPlaybackState());
	}

	@ReactMethod(isBlockingSynchronousMethod = true)
	//getPlaybackMetadata()
	public WritableMap getPlaybackMetadata()
	{
		if(player == null)
		{
			return null;
		}
		return Convert.fromPlaybackMetadata(player.getMetadata());
	}

	@ReactMethod
	//getPlaybackMetadataAsync()
	public void getPlaybackMetadataAsync(final Promise promise)
	{
		promise.resolve(getPlaybackMetadata());
	}

	@ReactMethod
	//skipToNext()
	public void skipToNext(final Promise promise)
	{
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error)
			{
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused)
			{
				player.skipToNext(new Player.OperationCallback() {
					@Override
					public void onError(Error error)
					{
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess()
					{
						promise.resolve(null);
					}
				});
			}
		});
	}

	@ReactMethod
	//skipToPrevious()
	public void skipToPrevious(final Promise promise)
	{
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error)
			{
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused)
			{
				player.skipToPrevious(new Player.OperationCallback() {
					@Override
					public void onError(Error error)
					{
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess()
					{
						promise.resolve(null);
					}
				});
			}
		});
	}

	@ReactMethod
	//seek(position)
	public void seek(final double position, final Promise promise)
	{
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error)
			{
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused)
			{
				player.seekToPosition(new Player.OperationCallback() {
					@Override
					public void onError(Error error)
					{
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess()
					{
						promise.resolve(null);
					}
				}, (int)(position*1000));
			}
		});
	}

	@ReactMethod
	//setShuffling(shuffling)
	public void setShuffling(final boolean shuffling, final Promise promise)
	{
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error)
			{
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused)
			{
				player.setShuffle(new Player.OperationCallback() {
					@Override
					public void onError(Error error)
					{
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess()
					{
						promise.resolve(null);
					}
				}, shuffling);
			}
		});
	}

	@ReactMethod
	//setRepeating(repeating)
	public void setRepeating(final boolean repeating, final Promise promise)
	{
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error)
			{
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused)
			{
				player.setRepeat(new Player.OperationCallback() {
					@Override
					public void onError(Error error)
					{
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess()
					{
						promise.resolve(null);
					}
				}, repeating);
			}
		});
	}




	private void prepareForRequest(final Completion<Void> completion)
	{
		if(!initialized)
		{
			completion.reject(new SpotifyError(SpotifyError.Code.NotInitialized));
			return;
		}
		logBackInIfNeeded(new Completion<Boolean>() {
			@Override
			public void onComplete(Boolean loggedIn, SpotifyError error) {
				completion.resolve(null);
			}
		}, false);
	}

	private void doAPIRequest(final String endpoint, final String method, final ReadableMap params, final boolean jsonBody, final Completion<Object> completion)
	{
		prepareForRequest(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error)
			{
				completion.reject(error);
			}

			@Override
			public void onResolve(Void unused)
			{
				// build headers
				HashMap<String, String> headers = new HashMap<>();
				String accessToken = auth.getAccessToken();
				if(accessToken != null)
				{
					headers.put("Authorization", "Bearer "+accessToken);
				}

				// build url
				String url = "https://api.spotify.com/"+endpoint;
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
					headers.put("Content-Type", "application/json; charset=utf-8");
				}

				Utils.doHTTPRequest(url, method, headers, body, new Completion<NetworkResponse>() {
					@Override
					public void onReject(SpotifyError error)
					{
						completion.reject(error);
					}

					@Override
					public void onResolve(NetworkResponse response)
					{
						String responseStr = Utils.getResponseString(response);
						JSONObject resultObj = null;
						String contentType = response.headers.get("Content-Type");
						if(contentType!=null)
						{
							contentType = contentType.split(";")[0].trim();
						}
						if(contentType!=null && contentType.equalsIgnoreCase("application/json") && response.statusCode!=204)
						{
							try
							{
								resultObj = new JSONObject(responseStr);
								if(resultObj.has("error"))
								{
									if(resultObj.has("error_description"))
									{
										String errorCode = resultObj.getString("error");
										String errorDescription = resultObj.getString("error_description");
										completion.reject(new SpotifyError(errorCode, errorDescription));
									}
									else
									{
										JSONObject errorObj = resultObj.getJSONObject("error");
										int statusCode = errorObj.getInt("status");
										String errorMessage = resultObj.getString("message");
										completion.reject(SpotifyError.getHTTPError(statusCode, errorMessage));
									}
									return;
								}
							}
							catch (JSONException e)
							{
								completion.reject(new SpotifyError(SpotifyError.Code.BadResponse));
								return;
							}
						}

						Object result = null;
						if(resultObj != null)
						{
							result = Convert.fromJSONObject(resultObj);
						}
						else
						{
							if(responseStr.length() > 0)
							{
								result = responseStr;
							}
						}
						completion.resolve(result);
					}
				});
			}
		});
	}

	@ReactMethod
	//sendRequest(endpoint, method, params, isJSONBody)
	public void sendRequest(String endpoint, String method, ReadableMap params, boolean jsonBody, final Promise promise)
	{
		doAPIRequest(endpoint, method, params, jsonBody, new Completion<Object>() {
			@Override
			public void onReject(SpotifyError error)
			{
				error.reject(promise);
			}

			@Override
			public void onResolve(Object result)
			{
				promise.resolve(result);
			}
		});
	}



	//ConnectionStateCallback

	@Override
	public void onLoggedIn()
	{
		// handle loginPlayer callbacks
		ArrayList<Completion<Void>> loginResponses;
		synchronized(playerLoginResponses)
		{
			loginResponses = new ArrayList<>(playerLoginResponses);
			playerLoginResponses.clear();
		}
		for(Completion<Void> response : loginResponses)
		{
			response.resolve(null);
		}
	}

	@Override
	public void onLoggedOut()
	{
		boolean wasLoggingOutPlayer = loggingOutPlayer;
		loggingOutPlayer = false;

		// handle loginPlayer callbacks
		ArrayList<Completion<Void>> loginResponses;
		synchronized(playerLoginResponses)
		{
			loginResponses = new ArrayList<>(playerLoginResponses);
			playerLoginResponses.clear();
		}
		for(Completion<Void> response : loginResponses)
		{
			response.reject(new SpotifyError(SpotifyError.Code.NotLoggedIn, "You have been logged out"));
		}

		// if we didn't explicitly log out, try to renew the session
		if(!wasLoggingOutPlayer)
		{
			if(auth.tokenRefreshURL != null && auth.getRefreshToken() != null)
			{
				final Object reference = this;
				auth.renewSession(new Completion<Boolean>() {
					@Override
					public void onReject(SpotifyError error)
					{
						// we couldn't renew the session
						if(isLoggedIn())
						{
							// clear session and destroy player
							auth.clearSession();
							Spotify.destroyPlayer(reference);
							player = null;
							// send logout event
							sendEvent("logout");
						}
					}

					@Override
					public void onResolve(Boolean renewed)
					{
						initializePlayerIfNeeded(new Completion<Void>() {
							@Override
							public void onComplete(Void result, SpotifyError error)
							{
								// we've logged back in
							}
						});
					}
				}, true);
				return;
			}
		}

		// clear session and destroy player
		auth.clearSession();
		Spotify.destroyPlayer(this);
		player = null;

		// handle logoutPlayer callbacks
		ArrayList<Completion<Void>> logoutResponses;
		synchronized(playerLogoutResponses)
		{
			logoutResponses = new ArrayList<>(playerLogoutResponses);
			playerLogoutResponses.clear();
		}
		for(Completion<Void> response : logoutResponses)
		{
			response.resolve(null);
		}
		
		// send logout event
		sendEvent("logout");
	}

	@Override
	public void onLoginFailed(Error error)
	{
		boolean sendLogoutEvent = false;
		if(isLoggedIn())
		{
			// if the error is one that requires logging out, log out
			if(error==Error.kSpErrorApplicationBanned || error==Error.kSpErrorLoginBadCredentials
				|| error==Error.kSpErrorNeedsPremium || error==Error.kSpErrorGeneralLoginError)
			{
				// clear session and destroy player
				auth.clearSession();
				Spotify.destroyPlayer(this);
				player = null;
				sendLogoutEvent = true;
			}
		}

		// handle loginPlayer callbacks
		ArrayList<Completion<Void>> loginResponses;
		synchronized(playerLoginResponses)
		{
			loginResponses = new ArrayList<>(playerLoginResponses);
			playerLoginResponses.clear();
		}
		for(Completion<Void> response : loginResponses)
		{
			response.reject(new SpotifyError(error));
		}

		// send logout event if necessary
		if(sendLogoutEvent)
		{
			sendEvent("logout");
		}
	}

	@Override
	public void onTemporaryError()
	{
		sendEvent("temporaryPlayerError");
	}

	@Override
	public void onConnectionMessage(String message)
	{
		sendEvent("playerMessage", message);
	}



	//Player.NotificationCallback

	private WritableMap createPlaybackEvent()
	{
		WritableMap metadata = getPlaybackMetadata();
		WritableMap state = getPlaybackState();

		WritableMap event = Arguments.createMap();
		if(state != null)
		{
			event.putMap("state", state);
		}
		else
		{
			event.putNull("state");
		}
		if(metadata != null)
		{
			event.putMap("metadata", metadata);
		}
		else
		{
			event.putNull("metadata");
		}
		event.putNull("error");
		return event;
	}

	@Override
	public void onPlaybackEvent(PlayerEvent playerEvent)
	{
		switch(playerEvent)
		{
			case kSpPlaybackNotifyPlay:
				this.sendEvent("play", createPlaybackEvent());
				break;

			case kSpPlaybackNotifyPause:
				this.sendEvent("pause", createPlaybackEvent());
				break;

			case kSpPlaybackNotifyTrackChanged:
				this.sendEvent("trackChange", createPlaybackEvent());
				break;

			case kSpPlaybackNotifyMetadataChanged:
				this.sendEvent("metadataChange", createPlaybackEvent());
				break;

			case kSpPlaybackNotifyContextChanged:
				this.sendEvent("contextChange", createPlaybackEvent());
				break;

			case kSpPlaybackNotifyShuffleOn:
			case kSpPlaybackNotifyShuffleOff:
				this.sendEvent("shuffleStatusChange", createPlaybackEvent());
				break;

			case kSpPlaybackNotifyRepeatOn:
			case kSpPlaybackNotifyRepeatOff:
				this.sendEvent("repeatStatusChange", createPlaybackEvent());
				break;

			case kSpPlaybackNotifyBecameActive:
				this.sendEvent("active", createPlaybackEvent());
				break;

			case kSpPlaybackNotifyBecameInactive:
				this.sendEvent("inactive", createPlaybackEvent());
				break;

			case kSpPlaybackNotifyLostPermission:
				this.sendEvent("permissionLost", createPlaybackEvent());
				break;

			case kSpPlaybackEventAudioFlush:
				this.sendEvent("audioFlush", createPlaybackEvent());
				break;

			case kSpPlaybackNotifyAudioDeliveryDone:
				this.sendEvent("audioDeliveryDone", createPlaybackEvent());
				break;

			case kSpPlaybackNotifyTrackDelivered:
				this.sendEvent("trackDelivered", createPlaybackEvent());
				break;

			case kSpPlaybackNotifyNext:
			case kSpPlaybackNotifyPrev:
				// deprecated
				break;
		}
	}

	@Override
	public void onPlaybackError(Error error)
	{
		//
	}



	//eventemitter.RNEventConformer

	@Override
	@ReactMethod
	public void __registerAsJSEventEmitter(int moduleId)
	{
		RNEventEmitter.registerEventEmitterModule(reactContext, moduleId, this);
	}

	@Override
	public void onNativeEvent(String eventName, Object... args)
	{
		//
	}

	@Override
	public void onJSEvent(String eventName, Object... args)
	{
		//
	}

	@Override
	public void onEvent(String eventName, Object... args)
	{
		//
	}
}

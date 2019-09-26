package com.lufinkey.react.spotify;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;

import com.android.volley.NetworkResponse;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;

import com.lufinkey.react.eventemitter.RNEventConformer;
import com.lufinkey.react.eventemitter.RNEventEmitter;
import com.spotify.sdk.android.player.*;
import com.spotify.sdk.android.player.Error;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class RNSpotifyModule extends ReactContextBaseJavaModule implements Player.NotificationCallback, ConnectionStateCallback, RNEventConformer
{
	private final ReactApplicationContext reactContext;

	private boolean initialized;
	private boolean loggedIn;
	private boolean loggingOutPlayer;

	private BroadcastReceiver networkStateReceiver;
	private Connectivity currentConnectivity = Connectivity.OFFLINE;

	private Auth auth;
	private Handler authRenewalTimer;
	private SpotifyPlayer player;
	private TrackController trackController = null;
	private final ArrayList<Completion<Void>> playerInitResponses;
	private final ArrayList<Completion<Void>> playerLoginResponses;
	private final ArrayList<Completion<Void>> playerLogoutResponses;

	private ReadableMap options;

	private String loginLoadingText = "Loading...";

	RNSpotifyModule(ReactApplicationContext reactContext) {
		super(reactContext);

		this.reactContext = reactContext;
		Utils.reactContext = reactContext;

		initialized = false;
		loggedIn = false;
		loggingOutPlayer = false;

		networkStateReceiver = null;

		auth = null;
		authRenewalTimer = null;
		player = null;
		playerInitResponses = new ArrayList<>();
		playerLoginResponses = new ArrayList<>();
		playerLogoutResponses = new ArrayList<>();

		options = null;
	}

	@Override
	public String getName() {
		return "RNSpotify";
	}

	@Override
	public void onCatalystInstanceDestroy() {
		stopAuthRenewalTimer();
		if(player != null) {
			player.removeNotificationCallback(RNSpotifyModule.this);
			player.removeConnectionStateCallback(RNSpotifyModule.this);
			Spotify.destroyPlayer(this);
			player = null;
		}
	}

	private Object nullobj() {
		return null;
	}

	private void sendEvent(String event, Object... args) {
		Log.d("rn-spotify-sdk", "emitting event "+event);
		RNEventEmitter.emitEvent(this.reactContext, this, event, args);
	}

	@ReactMethod
	//test()
	public void test() {
		System.out.println("ayy lmao");
	}



	@ReactMethod
	//initialize(options)
	public void initialize(ReadableMap options, final Promise promise) {
		// ensure module is not already initialized
		if(initialized) {
			SpotifyError.Code.AlreadyInitialized.reject(promise);
			return;
		}

		// ensure options is not null or missing fields
		if(options==null) {
			SpotifyError.getNullParameterError("options").reject(promise);
			return;
		}
		else if(!options.hasKey("clientID")) {
			SpotifyError.getMissingOptionError("clientID").reject(promise);
			return;
		}

		this.options = options;

		// load auth options
		auth = new Auth();
		auth.reactContext = reactContext;
		if(options.hasKey("sessionUserDefaultsKey")) {
			auth.sessionUserDefaultsKey = options.getString("sessionUserDefaultsKey");
		}
		ReadableArray scopes = null;
		if(options.hasKey("scopes")) {
			scopes = options.getArray("scopes");
		}
		LoginOptions loginOptions = null;
		try {
			loginOptions = LoginOptions.from(options, null);
		}
		catch(SpotifyError error) {
			error.reject(promise);
			return;
		}
		auth.load(loginOptions);

		// load android-specific options
		ReadableMap androidOptions = Arguments.createMap();
		if(options.hasKey("android")) {
			androidOptions = options.getMap("android");
		}
		if(androidOptions.hasKey("loginLoadingText")) {
			loginLoadingText = androidOptions.getString("loginLoadingText");
		}
		if(androidOptions.hasKey("useCustomTrackController")) {
			boolean useCustomTrackController = androidOptions.getBoolean("useCustomTrackController");
			if(useCustomTrackController) {
				trackController = new TrackController();
			}
		}

		// add connectivity state listener
		currentConnectivity = Utils.getNetworkConnectivity();
		networkStateReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Connectivity prevConnectivity = currentConnectivity;
				Connectivity connectivity = Utils.getNetworkConnectivity();
				currentConnectivity = connectivity;
				if (player != null && player.isInitialized()) {
					// update the player with the connection state, because Android makes no sense
					player.setConnectivityStatus(null, connectivity);

					// call events
					if(prevConnectivity==Connectivity.OFFLINE && connectivity!=Connectivity.OFFLINE) {
						sendEvent("reconnect");
					}
					else if(prevConnectivity!=Connectivity.OFFLINE && connectivity==Connectivity.OFFLINE) {
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
		boolean authLoggedIn = auth.isLoggedIn();
		if(authLoggedIn) {
			loggedIn = true;
		}
		promise.resolve(loggedIn);
		if(loggedIn) {
			sendEvent("login", Convert.fromSessionData(auth.getSession()));
		}

		// try to log back in if necessary
		logBackInIfNeeded(new Completion<Boolean>() {
			@Override
			public void onComplete(Boolean loggedIn, SpotifyError error) {
				if(loggedIn != null && loggedIn) {
					startAuthRenewalTimer();
				}
			}
		}, true);
	}



	@ReactMethod(isBlockingSynchronousMethod = true)
	//isInitialized()
	public Boolean isInitialized() {
		return initialized;
	}

	@ReactMethod
	//isInitializedAsync()
	public void isInitializedAsync(final Promise promise) {
		promise.resolve(isInitialized());
	}



	private void logBackInIfNeeded(final Completion<Boolean> completion, final boolean waitForDefinitiveResponse) {
		// ensure auth is actually logged in
		if(!auth.isLoggedIn()) {
			// auth is not logged in, so there's nothing we need to renew
			if(completion != null) {
				completion.resolve(false);
			}
			return;
		}
		// attempt to renew auth session
		renewSessionIfNeeded(new Completion<Boolean>() {
			@Override
			public void onReject(SpotifyError error) {
				// session renewal failed (we should log out)
				if(isLoggedIn()) {
					// session renewal returned a failure, but we're still logged in
					// log out player
					logoutPlayer(new Completion<Void>() {
						@Override
						public void onComplete(Void unused, SpotifyError error) {
							// clear session
							boolean wasLoggedIn = clearSession();
							// call completion
							if(completion != null) {
								completion.resolve(false);
							}
							// send logout event
							if(wasLoggedIn) {
								sendEvent("logout");
							}
						}
					});
				}
				else {
					// auth wasn't logged in during the renewal failure, so just fail
					if(completion != null) {
						if(waitForDefinitiveResponse) {
							completion.resolve(false);
						}
						else {
							completion.reject(error);
						}
					}
				}
			}

			@Override
			public void onResolve(Boolean renewed) {
				completion.resolve(true);
			}
		}, waitForDefinitiveResponse);
	}



	private void renewSessionIfNeeded(final Completion<Boolean> completion, boolean waitForDefinitiveResponse) {
		if(auth.isLoggedIn() || auth.isSessionValid()) {
			// not logged in or session does not need renewal
			completion.resolve(false);
		}
		else if(auth.getSession().refreshToken == null) {
			// no refresh token to renew session with, so the session has expired
			completion.reject(new SpotifyError(SpotifyError.Code.SessionExpired));
		}
		else {
			renewSession(completion, waitForDefinitiveResponse);
		}
	}

	private void renewSession(final Completion<Boolean> completion, boolean waitForDefinitiveResponse) {
		auth.renewSession(new Completion<Boolean>() {
			@Override
			public void onResolve(final Boolean renewed) {
				if(renewed.booleanValue()) {
					if(player == null || !player.isLoggedIn()) {
						initializePlayerIfNeeded(new Completion<Void>() {
							@Override
							public void onResolve(Void unused) {
								completion.resolve(renewed);
							}

							@Override
							public void onReject(SpotifyError error) {
								completion.reject(error);
							}
						});
						return;
					}
					else {
						player.login(auth.getSession().accessToken);
					}
					sendEvent("sessionRenewed", Convert.fromSessionData(auth.getSession()));
				}
				completion.resolve(renewed);
			}

			@Override
			public void onReject(SpotifyError error) {
				completion.reject(error);
			}
		}, waitForDefinitiveResponse);
	}

	@ReactMethod
	public void renewSession(final Promise promise) {
		renewSession(new Completion<Boolean>() {
			@Override
			public void onResolve(Boolean renewed) {
				// ensure the timer has not been stopped
				if(authRenewalTimer != null && renewed) {
					// reschedule the timer
					scheduleAuthRenewalTimer();
				}
				promise.resolve(renewed);
			}

			@Override
			public void onReject(SpotifyError error) {
				error.reject(promise);
			}
		}, false);
	}



	private void initializePlayerIfNeeded(final Completion<Void> completion) {
		// make sure we have the player scope
		if(auth.getSession() == null || !auth.hasStreamingScope()) {
			completion.resolve(null);
			return;
		}

		// check if player already exists and is initialized
		if(player != null && player.isInitialized()) {
			loginPlayer(completion);
			return;
		}

		// ensure only one thread actually invokes the initialization, and the others just wait
		boolean firstInitAttempt = false;
		synchronized (playerInitResponses) {
			if(playerInitResponses.size() == 0) {
				firstInitAttempt = true;
			}
			playerInitResponses.add(completion);
		}

		// if this is the first thread that has tried to initialize the player, then actually do it
		if(firstInitAttempt) {
			//initialize player
			final Object reference = this;
			Config playerConfig = new Config(reactContext.getApplicationContext(), auth.getSession().accessToken, auth.getClientID());
			SpotifyPlayer.Builder builder = new SpotifyPlayer.Builder(playerConfig);
			if(trackController != null) {
				builder.setAudioController(trackController);
			}
			player = Spotify.getPlayer(builder, reference, new SpotifyPlayer.InitializationObserver() {
				@Override
				public void onError(Throwable error) {
					// error initializing the player
					if(player != null) {
						// destroy the player
						Spotify.destroyPlayer(reference);
						player = null;
					}
					// call init responses
					ArrayList<Completion<Void>> initResponses = null;
					synchronized (playerInitResponses) {
						initResponses = new ArrayList<>(playerInitResponses);
						playerInitResponses.clear();
					}
					for(Completion<Void> completion : initResponses) {
						completion.reject(new SpotifyError(Error.kSpErrorInitFailed, error.getLocalizedMessage()));
					}
				}

				@Override
				public void onInitialized(SpotifyPlayer newPlayer) {
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
						public void onComplete(Void unused, SpotifyError error) {
							// call init responses
							ArrayList<Completion<Void>> initResponses = null;
							synchronized (playerInitResponses) {
								initResponses = new ArrayList<>(playerInitResponses);
								playerInitResponses.clear();
							}
							for(Completion<Void> completion : initResponses) {
								if(error == null) {
									completion.resolve(null);
								}
								else {
									completion.reject(error);
								}
							}
						}
					});
				}
			});
		}
	}



	private void loginPlayer(final Completion<Void> completion) {
		boolean playerLoggedIn = false;
		boolean firstLoginAttempt = false;

		// add completion to a list to be called when the login succeeds or fails
		synchronized(playerLoginResponses) {
			// ensure we're not already logged in
			if(player.isLoggedIn()) {
				playerLoggedIn = true;
			}
			else {
				if(playerLoginResponses.size()==0) {
					firstLoginAttempt = true;
				}
				//wait for RNSpotifyModule.onLoggedIn
				// or RNSpotifyModule.onLoginFailed
				// or RNSpotifyModule.onLoggedOut
				playerLoginResponses.add(completion);
			}
		}

		if(playerLoggedIn) {
			// we're already logged in, so finish
			completion.resolve(null);
		}
		else if(firstLoginAttempt) {
			// only the first thread to call loginPlayer should actually attempt to log the player in
			player.login(auth.getSession().accessToken);
		}
	}



	private void logoutPlayer(final Completion<Void> completion) {
		if(player == null) {
			completion.resolve(null);
			return;
		}

		boolean loggedOut = false;

		synchronized(playerLogoutResponses) {
			if(!player.isLoggedIn()) {
				loggedOut = true;
			}
			else {
				// wait for RNSpotifyModule.onLoggedOut
				playerLogoutResponses.add(completion);
			}
		}

		if(loggedOut) {
			Spotify.destroyPlayer(this);
			player = null;
			completion.resolve(null);
		}
		else if(!loggingOutPlayer) {
			loggingOutPlayer = true;
			player.logout();
		}
	}



	@ReactMethod
	//authenticate(options)
	public void authenticate(ReadableMap options, final Promise promise) {
		LoginOptions tmpLoginOptions = null;
		try {
			tmpLoginOptions = LoginOptions.from(options, this.options);
		}
		catch(SpotifyError error) {
			error.reject(promise);
			return;
		}
		final LoginOptions loginOptions = tmpLoginOptions;

		// perform login flow
		AuthActivity.performAuthFlow(reactContext.getCurrentActivity(), loginOptions, new AuthActivityListener() {
			@Override
			public void onAuthActivityCancel(AuthActivity activity) {
				// dismiss activity
				activity.finish(new Completion<Void>() {
					@Override
					public void onComplete(Void unused, SpotifyError unusedError) {
						promise.resolve(false);
					}
				});
			}

			@Override
			public void onAuthActivityFailure(AuthActivity activity, final SpotifyError error) {
				if(activity == null) {
					error.reject(promise);
					return;
				}
				// dismiss activity
				activity.finish(new Completion<Void>() {
					@Override
					public void onComplete(Void unused, SpotifyError unusedError) {
						error.reject(promise);
					}
				});
			}

			@Override
			public void onAuthActivityReceiveSession(final AuthActivity activity, final SessionData session) {
				// dismiss activity
				activity.finish(new Completion<Void>() {
					@Override
					public void onComplete(Void unused, SpotifyError unusedError) {
						promise.resolve(Convert.fromSessionData(session));
					}
				});
			}
		});
	}



	@ReactMethod
	//loginWithSession(options)
	public void loginWithSession(ReadableMap options, final Promise promise) {
		LoginOptions tmpLoginOptions = null;
		try {
			tmpLoginOptions = LoginOptions.from(options, this.options);
		}
		catch(SpotifyError error) {
			error.reject(promise);
			return;
		}
		final LoginOptions loginOptions = tmpLoginOptions;
		SessionData session = null;
		try {
			session = SessionData.from(options);
		}
		catch(SpotifyError error) {
			error.reject(promise);
			return;
		}
		auth.startSession(session, loginOptions);
		final RNSpotifyModule module = this;
		initializePlayerIfNeeded(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error) {
				auth.clearSession();
				error.reject(promise);
			}

			@Override
			public void onResolve(Void result) {
				boolean wasLoggedIn = module.loggedIn;
				boolean loggedIn = auth.isLoggedIn();
				if(!wasLoggedIn) {
					if(!loggedIn) {
						(new SpotifyError(SpotifyError.Code.NotLoggedIn, "module was logged out")).reject(promise);
						return;
					}
					module.loggedIn = true;
				}
				promise.resolve(null);
				if(!wasLoggedIn) {
					sendEvent("login", Convert.fromSessionData(auth.getSession()));
				}
			}
		});
	}



	@ReactMethod
	//login(options)
	public void login(ReadableMap options, final Promise promise) {
		// ensure we're initialized
		if(!initialized) {
			SpotifyError.Code.NotInitialized.reject(promise);
			return;
		}
		else if(isLoggedIn()) {
			promise.resolve(true);
			return;
		}

		LoginOptions tmpLoginOptions = null;
		try {
			tmpLoginOptions = LoginOptions.from(options, this.options);
		}
		catch(SpotifyError error) {
			error.reject(promise);
			return;
		}
		final LoginOptions loginOptions = tmpLoginOptions;

		// perform login flow
		AuthActivity.performAuthFlow(reactContext.getCurrentActivity(), loginOptions, new AuthActivityListener() {
			@Override
			public void onAuthActivityCancel(AuthActivity activity) {
				// dismiss activity
				activity.finish(new Completion<Void>() {
					@Override
					public void onComplete(Void unused, SpotifyError unusedError) {
						promise.resolve(false);
					}
				});
			}

			@Override
			public void onAuthActivityFailure(AuthActivity activity, final SpotifyError error) {
				if(activity == null) {
					error.reject(promise);
					return;
				}
				// dismiss activity
				activity.finish(new Completion<Void>() {
					@Override
					public void onComplete(Void unused, SpotifyError unusedError) {
						error.reject(promise);
					}
				});
			}

			@Override
			public void onAuthActivityReceiveSession(final AuthActivity activity, final SessionData session) {
				final ProgressDialog dialog = new ProgressDialog(activity, android.R.style.Theme_Black);
				dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
				dialog.getWindow().setGravity(Gravity.CENTER);
				dialog.setMessage(loginLoadingText);
				dialog.setCancelable(false);
				dialog.setIndeterminate(true);
				dialog.show();

				// apply session
				auth.startSession(session, loginOptions);

				// initialize player
				initializePlayerIfNeeded(new Completion<Void>() {
					@Override
					public void onComplete(Void unused, final SpotifyError error) {
						dialog.dismiss();
						// dismiss activity
						activity.finish(new Completion<Void>() {
							@Override
							public void onComplete(Void unused, SpotifyError unusedError) {
								if (error != null) {
									auth.clearSession();
									error.reject(promise);
								}
								else {
									boolean authLoggedIn = auth.isLoggedIn();
									if (authLoggedIn) {
										loggedIn = true;
									}
									promise.resolve(loggedIn);
									if (loggedIn) {
										sendEvent("login", Convert.fromSessionData(auth.getSession()));
									}
									startAuthRenewalTimer();
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
	public void logout(final Promise promise) {
		// ensure we've been initialized
		if(!initialized) {
			SpotifyError.Code.NotInitialized.reject(promise);
			return;
		}
		// make sure we're not already logged out
		if(!isLoggedIn()) {
			promise.resolve(null);
			return;
		}
		// log out and destroy the player
		logoutPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error) {
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused) {
				boolean wasLoggedIn = clearSession();
				promise.resolve(null);
				if(wasLoggedIn) {
					sendEvent("logout");
				}
			}
		});
	}



	private void startAuthRenewalTimer() {
		if(authRenewalTimer != null) {
			// auth renewal timer has already been started, don't bother starting again
			return;
		}
		scheduleAuthRenewalTimer();
	}

	private double getTokenRefreshEarliness() {
		String key = "tokenRefreshEarliness";
		if(!options.hasKey(key) || options.getType(key) != ReadableType.Number) {
			return 300.0;
		}
		return options.getDouble(key);
	}

	private void scheduleAuthRenewalTimer() {
		if(!auth.canRefreshSession()) {
			// we can't perform token refresh, so don't bother scheduling the timer
			return;
		}
		long now = (new Date()).getTime();
		long expirationTime = auth.getSession().expireDate.getTime();
		long timeDiff = expirationTime - now;
		long tokenRefreshEarliness = (long)(getTokenRefreshEarliness() * 1000);
		final long renewalTimeDiff = (expirationTime - tokenRefreshEarliness) - now;
		if(timeDiff <= 30.0 || timeDiff <= (tokenRefreshEarliness + 30.0) || renewalTimeDiff <= 0.0) {
			onAuthRenewalTimerFire();
		}
		else {
			if(authRenewalTimer == null) {
				authRenewalTimer = new Handler();
			}
			else {
				authRenewalTimer.removeCallbacksAndMessages(null);
			}
			authRenewalTimer.postDelayed(new Runnable() {
				@Override
				public void run() {
					onAuthRenewalTimerFire();
				}
			}, renewalTimeDiff);
		}
	}

	private void onAuthRenewalTimerFire() {
		renewSession(new Completion<Boolean>() {
			@Override
			public void onComplete(Boolean renewed, SpotifyError error) {
				// ensure the timer has not been stopped
				if(authRenewalTimer != null) {
					// reschedule the timer
					scheduleAuthRenewalTimer();
				}
			}
		}, true);
	}

	private void stopAuthRenewalTimer() {
		if(authRenewalTimer != null) {
			authRenewalTimer.removeCallbacksAndMessages(null);
			authRenewalTimer = null;
		}
	}

	private boolean clearSession() {
		boolean wasLoggedIn = isLoggedIn();
		stopAuthRenewalTimer();
		auth.clearSession();
		loggedIn = false;
		return wasLoggedIn;
	}



	@ReactMethod(isBlockingSynchronousMethod = true)
	//isLoggedIn()
	public Boolean isLoggedIn() {
		if(initialized && loggedIn && auth.isLoggedIn()) {
			return true;
		}
		return false;
	}

	@ReactMethod
	//isLoggedInAsync()
	public void isLoggedInAsync(final Promise promise) {
		promise.resolve(isLoggedIn());
	}



	@ReactMethod(isBlockingSynchronousMethod = true)
	//getSession()
	public WritableMap getSession() {
		return Convert.fromSessionData(auth.getSession());
	}

	@ReactMethod
	//getSessionAsync()
	public void getSessionAsync(final Promise promise) {
		promise.resolve(getSession());
	}




	private void prepareForPlayer(final Completion<Void> completion) {
		if(!initialized) {
			completion.reject(new SpotifyError(SpotifyError.Code.NotInitialized));
			return;
		}
		logBackInIfNeeded(new Completion<Boolean>() {
			@Override
			public void onReject(SpotifyError error) {
				if(player == null && auth.hasStreamingScope()) {
					completion.reject(error);
				}
				else {
					completion.resolve(null);
				}
			}

			@Override
			public void onResolve(Boolean loggedIn) {
				if(isLoggedIn()) {
					initializePlayerIfNeeded(new Completion<Void>() {
						@Override
						public void onReject(SpotifyError error) {
							if(player == null && auth.hasStreamingScope()) {
								completion.reject(error);
							}
							else {
								completion.resolve(null);
							}
						}

						@Override
						public void onResolve(Void unused) {
							if(!auth.hasStreamingScope()) {
								completion.reject(new SpotifyError(SpotifyError.Code.PlayerNotReady, "Missing streaming scope"));
							}
							else if(player == null || !player.isInitialized() || !player.isLoggedIn()) {
								completion.reject(new SpotifyError(SpotifyError.Code.PlayerNotReady));
							}
							else {
								completion.resolve(null);
							}
						}
					});
				}
				else {
					completion.reject(new SpotifyError(SpotifyError.Code.NotLoggedIn));
				}
			}
		}, false);
	}

	@ReactMethod
	//playURI(spotifyURI, startIndex, startPosition)
	public void playURI(final String spotifyURI, final int startIndex, final double startPosition, final Promise promise) {
		if(spotifyURI==null) {
			SpotifyError.getNullParameterError("spotifyURI").reject(promise);
			return;
		}
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error) {
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused) {
				player.playUri( new Player.OperationCallback() {
					@Override
					public void onError(com.spotify.sdk.android.player.Error error) {
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess() {
						promise.resolve(null);
					}
				}, spotifyURI, startIndex, (int)(startPosition*1000));
			}
		});
	}

	@ReactMethod
	//queueURI(spotifyURI)
	public void queueURI(final String spotifyURI, final Promise promise) {
		if(spotifyURI==null) {
			SpotifyError.getNullParameterError("spotifyURI").reject(promise);
			return;
		}
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error) {
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused) {
				player.queue(new Player.OperationCallback() {
					@Override
					public void onError(com.spotify.sdk.android.player.Error error) {
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess() {
						promise.resolve(null);
					}
				}, spotifyURI);
			}
		});
	}

	@ReactMethod
	//setVolume(volume)
	public void setVolume(double volume, final Promise promise) {
		if(trackController != null) {
			trackController.setVolume((float) volume);
		}
		promise.resolve(null);
	}

	@ReactMethod(isBlockingSynchronousMethod = true)
	//getVolume()
	public Double getVolume() {
		if(trackController != null) {
			return Double.valueOf(trackController.getVolume());
		}
		return 1.0;
	}

	@ReactMethod
	//getVolumeAsync()
	public void getVolumeAsync(final Promise promise) {
		promise.resolve(getVolume());
	}

	@ReactMethod
	//setPlaying(playing)
	public void setPlaying(final boolean playing, final Promise promise) {
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error) {
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused) {
				if(playing) {
					player.resume(new Player.OperationCallback(){
						@Override
						public void onError(com.spotify.sdk.android.player.Error error) {
							new SpotifyError(error).reject(promise);
						}

						@Override
						public void onSuccess() {
							promise.resolve(null);
						}
					});
				}
				else {
					player.pause(new Player.OperationCallback(){
						@Override
						public void onError(com.spotify.sdk.android.player.Error error) {
							new SpotifyError(error).reject(promise);
						}

						@Override
						public void onSuccess() {
							promise.resolve(null);
						}
					});
				}
			}
		});
	}

	@ReactMethod(isBlockingSynchronousMethod = true)
	//getPlaybackState()
	public WritableMap getPlaybackState() {
		if(player==null) {
			return null;
		}
		return Convert.fromPlaybackState(player.getPlaybackState());
	}

	@ReactMethod
	//getPlaybackStateAsync()
	public void getPlaybackStateAsync(final Promise promise) {
		promise.resolve(getPlaybackState());
	}

	@ReactMethod(isBlockingSynchronousMethod = true)
	//getPlaybackMetadata()
	public WritableMap getPlaybackMetadata() {
		if(player == null) {
			return null;
		}
		return Convert.fromPlaybackMetadata(player.getMetadata());
	}

	@ReactMethod
	//getPlaybackMetadataAsync()
	public void getPlaybackMetadataAsync(final Promise promise) {
		promise.resolve(getPlaybackMetadata());
	}

	@ReactMethod
	//skipToNext()
	public void skipToNext(final Promise promise) {
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error) {
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused) {
				player.skipToNext(new Player.OperationCallback() {
					@Override
					public void onError(Error error) {
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess() {
						promise.resolve(null);
					}
				});
			}
		});
	}

	@ReactMethod
	//skipToPrevious()
	public void skipToPrevious(final Promise promise) {
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error) {
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused) {
				player.skipToPrevious(new Player.OperationCallback() {
					@Override
					public void onError(Error error) {
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess() {
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
			public void onReject(SpotifyError error) {
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused) {
				player.seekToPosition(new Player.OperationCallback() {
					@Override
					public void onError(Error error) {
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess() {
						promise.resolve(null);
					}
				}, (int)(position*1000));
			}
		});
	}

	@ReactMethod
	//setShuffling(shuffling)
	public void setShuffling(final boolean shuffling, final Promise promise) {
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error) {
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused) {
				player.setShuffle(new Player.OperationCallback() {
					@Override
					public void onError(Error error) {
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess() {
						promise.resolve(null);
					}
				}, shuffling);
			}
		});
	}

	@ReactMethod
	//setRepeating(repeating)
	public void setRepeating(final boolean repeating, final Promise promise) {
		prepareForPlayer(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error) {
				error.reject(promise);
			}

			@Override
			public void onResolve(Void unused) {
				player.setRepeat(new Player.OperationCallback() {
					@Override
					public void onError(Error error) {
						new SpotifyError(error).reject(promise);
					}

					@Override
					public void onSuccess() {
						promise.resolve(null);
					}
				}, repeating);
			}
		});
	}




	private void prepareForRequest(final Completion<Void> completion) {
		if(!initialized) {
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

	private void doAPIRequest(final String endpoint, final String method, final ReadableMap params, final boolean jsonBody, final Completion<Object> completion) {
		prepareForRequest(new Completion<Void>() {
			@Override
			public void onReject(SpotifyError error)
			{
				completion.reject(error);
			}

			@Override
			public void onResolve(Void unused) {
				// build headers
				HashMap<String, String> headers = new HashMap<>();
				String accessToken = null;
				SessionData session = auth.getSession();
				if(session != null) {
					accessToken = session.accessToken;
				}
				if(accessToken != null) {
					headers.put("Authorization", "Bearer "+accessToken);
				}

				// build url
				String url = "https://api.spotify.com/"+endpoint;
				if(!jsonBody && params!=null && method.equalsIgnoreCase("GET")) {
					url += "?"+Utils.makeQueryString(params);
				}

				//create request body
				byte[] body = null;
				if(params!=null) {
					if(jsonBody) {
						JSONObject obj = Convert.toJSONObject(params);
						if (obj != null) {
							body = obj.toString().getBytes();
						}
					}
					else if(!method.equalsIgnoreCase("GET")) {
						body = Utils.makeQueryString(params).getBytes();
					}
				}

				if(jsonBody) {
					headers.put("Content-Type", "application/json; charset=utf-8");
				}

				Utils.doHTTPRequest(url, method, headers, body, new Completion<NetworkResponse>() {
					@Override
					public void onReject(SpotifyError error) {
						completion.reject(error);
					}

					@Override
					public void onResolve(NetworkResponse response) {
						String responseStr = Utils.getResponseString(response);
						JSONObject resultObj = null;
						String contentType = response.headers.get("Content-Type");
						if(contentType!=null) {
							contentType = contentType.split(";")[0].trim();
						}
						if(contentType!=null && contentType.equalsIgnoreCase("application/json") && response.statusCode!=204) {
							try {
								resultObj = new JSONObject(responseStr);
								if(resultObj.has("error")) {
									if(resultObj.has("error_description")) {
										String errorCode = resultObj.getString("error");
										String errorDescription = resultObj.getString("error_description");
										completion.reject(new SpotifyError(errorCode, errorDescription));
									}
									else {
										JSONObject errorObj = resultObj.getJSONObject("error");
										int statusCode = errorObj.getInt("status");
										String errorMessage = errorObj.getString("message");
										if(response.statusCode == 429) {
											String retryAfter = response.headers.get("Retry-After");
											if(retryAfter != null) {
												errorMessage += ". Retry after "+retryAfter+" seconds";
											}

										}
										completion.reject(SpotifyError.getHTTPError(statusCode, errorMessage));
									}
									return;
								}
							}
							catch (JSONException e) {
								completion.reject(new SpotifyError(SpotifyError.Code.BadResponse));
								return;
							}
						}

						Object result = null;
						if(resultObj != null) {
							result = Convert.fromJSONObject(resultObj);
						}
						else {
							if(responseStr.length() > 0) {
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
	public void sendRequest(String endpoint, String method, ReadableMap params, boolean jsonBody, final Promise promise) {
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
	public void onLoggedIn() {
		// handle loginPlayer callbacks
		ArrayList<Completion<Void>> loginResponses;
		synchronized(playerLoginResponses) {
			loginResponses = new ArrayList<>(playerLoginResponses);
			playerLoginResponses.clear();
		}
		for(Completion<Void> response : loginResponses) {
			response.resolve(null);
		}
	}

	@Override
	public void onLoggedOut() {
		boolean wasLoggingOutPlayer = loggingOutPlayer;
		loggingOutPlayer = false;

		// handle loginPlayer callbacks
		ArrayList<Completion<Void>> loginResponses;
		synchronized(playerLoginResponses) {
			loginResponses = new ArrayList<>(playerLoginResponses);
			playerLoginResponses.clear();
		}
		for(Completion<Void> response : loginResponses) {
			response.reject(new SpotifyError(SpotifyError.Code.NotLoggedIn, "You have been logged out"));
		}

		// if we didn't explicitly log out, and we can renew the session, then try to renew the session
		if(!wasLoggingOutPlayer && auth.canRefreshSession()) {
			final Object reference = this;
			renewSession(new Completion<Boolean>() {
				@Override
				public void onComplete(Boolean renewed, SpotifyError error) {
					if(error != null || !renewed.booleanValue()) {
						// we couldn't renew the session
						if(isLoggedIn()) {
							// clear session and destroy player
							clearSession();
							Spotify.destroyPlayer(reference);
							player = null;
							// send logout event
							sendEvent("logout");
						}
					}
					else {
						// we renewed the auth token, so we're good here
					}
				}
			}, true);
		}
		else {
			// clear session and destroy player
			clearSession();
			Spotify.destroyPlayer(this);
			player = null;

			// handle logoutPlayer callbacks
			ArrayList<Completion<Void>> logoutResponses;
			synchronized(playerLogoutResponses) {
				logoutResponses = new ArrayList<>(playerLogoutResponses);
				playerLogoutResponses.clear();
			}
			for(Completion<Void> response : logoutResponses) {
				response.resolve(null);
			}

			// send logout event
			sendEvent("logout");
		}
	}

	@Override
	public void onLoginFailed(Error error) {
		boolean sendLogoutEvent = false;
		if(isLoggedIn()) {
			// clear session and destroy player
			clearSession();
			Spotify.destroyPlayer(this);
			player = null;
			sendLogoutEvent = true;
		}

		// handle loginPlayer callbacks
		ArrayList<Completion<Void>> loginResponses;
		synchronized(playerLoginResponses) {
			loginResponses = new ArrayList<>(playerLoginResponses);
			playerLoginResponses.clear();
		}
		for(Completion<Void> response : loginResponses) {
			response.reject(new SpotifyError(error));
		}

		// send logout event if necessary
		if(sendLogoutEvent) {
			sendEvent("logout");
		}
	}

	@Override
	public void onTemporaryError() {
		sendEvent("temporaryPlayerError");
	}

	@Override
	public void onConnectionMessage(String message) {
		sendEvent("playerMessage", message);
	}



	//Player.NotificationCallback

	private WritableMap createPlaybackEvent() {
		WritableMap metadata = getPlaybackMetadata();
		WritableMap state = getPlaybackState();

		WritableMap event = Arguments.createMap();
		if(state != null) {
			event.putMap("state", state);
		}
		else {
			event.putNull("state");
		}
		if(metadata != null) {
			event.putMap("metadata", metadata);
		}
		else {
			event.putNull("metadata");
		}
		event.putNull("error");
		return event;
	}

	@Override
	public void onPlaybackEvent(PlayerEvent playerEvent) {
		switch(playerEvent) {
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
	public void onPlaybackError(Error error) {
		//
	}



	//eventemitter.RNEventConformer

	@Override
	@ReactMethod
	public void __registerAsJSEventEmitter(int moduleId) {
		RNEventEmitter.registerEventEmitterModule(reactContext, moduleId, this);
	}

	@Override
	public void onNativeEvent(String eventName, Object... args) {
		//
	}

	@Override
	public void onJSEvent(String eventName, Object... args) {
		//
	}

	@Override
	public void onEvent(String eventName, Object... args) {
		//
	}
}

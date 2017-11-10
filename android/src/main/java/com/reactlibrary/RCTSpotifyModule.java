
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

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import com.spotify.sdk.android.authentication.*;
import com.spotify.sdk.android.player.*;
import com.spotify.sdk.android.player.Error;

import java.net.CookieManager;
import java.util.ArrayList;

import okhttp3.Cookie;

public class RCTSpotifyModule extends ReactContextBaseJavaModule implements Player.NotificationCallback, ConnectionStateCallback
{
	private final ReactApplicationContext reactContext;

	private boolean initialized;

	private BroadcastReceiver networkStateReceiver;

	private SpotifyPlayer player;
	private ReadableMap options;

	private RCTSpotifyCallback<Boolean> playerLoginCompletion;

	public RCTSpotifyModule(ReactApplicationContext reactContext)
	{
		super(reactContext);
		this.reactContext = reactContext;

		initialized = false;
		networkStateReceiver = null;
		player = null;
		playerLoginCompletion = null;
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
	void test()
	{
		System.out.println("ayy lmao");
	}

	@ReactMethod
	//initialize(options, (loggedIn, error?))
	void initialize(ReadableMap options, final Callback callback)
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

		this.options = options;

		//check for accessToken
		String sessionUserDefaultsKey = options.getString("sessionUserDefaultsKey");
		String accessToken = null;
		if(sessionUserDefaultsKey!=null)
		{
			SharedPreferences prefs = getMainActivity().getSharedPreferences(sessionUserDefaultsKey, Context.MODE_PRIVATE);
			accessToken = prefs.getString("accessToken", null);
		}
		if(accessToken == null)
		{
			System.out.println("access token is null. Finishing initialization");
			callback.invoke(false, nullobj());
			return;
		}

		//try to log back in
		initializePlayer(accessToken, new RCTSpotifyCallback<Boolean>() {
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

		//check for already initializing player
		if(player != null)
		{
			System.out.println("player already initialized");
			completion.invoke(
					false,
					new RCTSpotifyError(
							RCTSpotifyError.Code.CONFLICTING_CALLBACKS,
							"cannot initialize player when player has already been or is being initialized"
					)
			);
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

				//save accessToken
				String sessionUserDefaultsKey = options.getString("sessionUserDefaultsKey");
				if(sessionUserDefaultsKey != null)
				{
					SharedPreferences prefs = getMainActivity().getSharedPreferences(sessionUserDefaultsKey, Context.MODE_PRIVATE);
					SharedPreferences.Editor prefsEditor = prefs.edit();
					prefsEditor.putString("accessToken", accessToken);
					prefsEditor.commit();
				}

				//ensure no conflicting callbacks
				if(playerLoginCompletion != null)
				{
					System.out.println("playerLoginCompletion is not null");
					Spotify.destroyPlayer(player);
					player = null;
					completion.invoke(
							false,
							new RCTSpotifyError(
									RCTSpotifyError.Code.CONFLICTING_CALLBACKS,
									"cannot call initializePlayer method while initializePlayer is already being called")
					);
					return;
				}

				//wait for RCTSpotifyModule.onLoggedIn
				// or RCTSpotifyModule.onLoginFailed
				playerLoginCompletion = new RCTSpotifyCallback<Boolean>() {
					@Override
					public void invoke(Boolean loggedIn, RCTSpotifyError error)
					{
						if(!loggedIn.booleanValue())
						{
							Spotify.destroyPlayer(player);
							player = null;
						}
						completion.invoke(loggedIn.booleanValue(), error);
					}
				};

				//setup player
				player.setConnectivityStatus(playerOperationCallback, getNetworkConnectivity(getMainActivity()));
				player.addNotificationCallback(RCTSpotifyModule.this);
				player.addConnectionStateCallback(RCTSpotifyModule.this);

				//if player is already logged in, just call the callback
				if(player.isLoggedIn())
				{
					RCTSpotifyCallback<Boolean> completionTmp = playerLoginCompletion;
					playerLoginCompletion = null;
					completionTmp.invoke(true, null);
				}
			}
		});
	}

	@ReactMethod
	//login((loggedIn, error?))
	void login(final Callback callback)
	{
		//get required options
		String clientID = options.getString("clientID");
		String redirectURL = options.getString("redirectURL");
		String missingOption = null;
		if(clientID == null)
		{
			missingOption = "clientID";
		}
		else if(redirectURL == null)
		{
			missingOption = "redirectURL";
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
				.setScopes(new String[]{"user-read-private", "playlist-read", "playlist-read-private", "streaming"})
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
	void logout(final Callback callback)
	{
		if(!isLoggedIn())
		{
			callback.invoke(nullobj());
			return;
		}

		//destroy the player
		Spotify.destroyPlayer(player);
		player = null;

		//remove the saved access token
		String sessionUserDefaultsKey = options.getString("sessionUserDefaultsKey");
		if(sessionUserDefaultsKey != null)
		{
			SharedPreferences prefs = getMainActivity().getSharedPreferences(sessionUserDefaultsKey, Context.MODE_PRIVATE);
			SharedPreferences.Editor prefsEditor = prefs.edit();
			prefsEditor.remove("accessToken");
			prefsEditor.commit();
		}

		clearCookies("https://accounts.spotify.com");

		callback.invoke(nullobj());
	}

	@ReactMethod
	//isLoggedIn()
	boolean isLoggedIn()
	{
		if(player != null && player.isLoggedIn())
		{
			return true;
		}
		return false;
	}

	@ReactMethod
	//handleAuthURL(url)
	boolean handleAuthURL(String url)
	{
		//TODO for some reason we don't use this on Android, despite having to give a redirectURL
		return false;
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
		//handle initializePlayer callback
		if(playerLoginCompletion != null)
		{
			RCTSpotifyCallback<Boolean> completionTmp = playerLoginCompletion;
			playerLoginCompletion = null;
			completionTmp.invoke(true, null);
		}
	}

	@Override
	public void onLoggedOut()
	{
		System.out.println("onLoggedOut");
		//handle initializePlayer callback
		if(playerLoginCompletion != null)
		{
			RCTSpotifyCallback<Boolean> completionTmp = playerLoginCompletion;
			playerLoginCompletion = null;
			completionTmp.invoke(false, null);
		}
	}

	@Override
	public void onLoginFailed(com.spotify.sdk.android.player.Error error)
	{
		System.out.println("onLoginFailed");
		//handle initializePlayer callback
		if(playerLoginCompletion != null)
		{
			RCTSpotifyCallback<Boolean> completionTmp = playerLoginCompletion;
			playerLoginCompletion = null;
			completionTmp.invoke(false, new RCTSpotifyError(RCTSpotifyError.Code.AUTHORIZATION_FAILED, "login failed: "+error));
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

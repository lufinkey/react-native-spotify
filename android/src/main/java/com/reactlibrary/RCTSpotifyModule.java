
package com.reactlibrary;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;

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

public class RCTSpotifyModule extends ReactContextBaseJavaModule implements Player.NotificationCallback, ConnectionStateCallback
{
	private final ReactApplicationContext reactContext;

	private boolean initialized;

	private BroadcastReceiver networkStateReceiver;

	private SpotifyPlayer player;
	private ReadableMap options;

	public RCTSpotifyModule(ReactApplicationContext reactContext)
	{
		super(reactContext);
		this.reactContext = reactContext;

		initialized = false;
		networkStateReceiver = null;
		player = null;
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
	void test()
	{
		System.out.println("ayy lmao");
	}

	@ReactMethod
	//initialize(options, (
	void initialize(ReadableMap options, final Callback completion)
	{
		if(initialized)
		{
			completion.invoke(
					false,
					new RCTSpotifyError(
							RCTSpotifyError.Code.ALREADY_INITIALIZED,
							"cannot initialize Spotify times").toReactObject()
			);
			return;
		}

		this.options = options;

		//check for missing parameters
		String missingParameter = null;
		if(options.getString("clientID") == null)
		{
			missingParameter = "clientID";
		}
		else if(options.getString("redirectURL") == null)
		{
			missingParameter = "redirectURL";
		}
		if(missingParameter!=null)
		{
			completion.invoke(
					false,
					new RCTSpotifyError(
							RCTSpotifyError.Code.MISSING_PARAMETERS,
							"missing parameter "+missingParameter)
			);
			return;
		}

		logBackInIfNeeded(new RCTSpotifyCallback<Boolean>() {
			@Override
			public void invoke(Boolean obj, RCTSpotifyError error) {
				ReadableMap errorObj = null;
				if(error!=null)
				{
					errorObj = error.toReactObject();
				}
				completion.invoke(
						obj.booleanValue(),
						errorObj
				);
			}
		});
	}

	private void logBackInIfNeeded(final RCTSpotifyCallback<Boolean> completion)
	{
		//get access token
		String clientID = options.getString("clientID");
		String sessionUserDefaultsKey = options.getString("sessionUserDefaultsKey");
		String accessToken = null;
		if(sessionUserDefaultsKey!=null)
		{
			SharedPreferences spotifyPrefs = getMainActivity().getSharedPreferences(sessionUserDefaultsKey, Context.MODE_PRIVATE);
			accessToken = spotifyPrefs.getString("accessToken", null);
		}

		if(accessToken == null)
		{
			completion.invoke(false, null);
			return;
		}

		Config playerConfig = new Config(getMainActivity().getApplicationContext(), accessToken, clientID);
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
				player.setConnectivityStatus(playerOperationCallback, getNetworkConnectivity(getMainActivity()));
				player.addNotificationCallback(RCTSpotifyModule.this);
				player.addConnectionStateCallback(RCTSpotifyModule.this);
				completion.invoke(true, null);
			}
		});
	}

	@ReactMethod
	boolean isLoggedIn()
	{
		if(player != null && player.isLoggedIn())
		{
			return true;
		}
		return false;
	}

	private final Player.OperationCallback playerOperationCallback = new Player.OperationCallback() {
		@Override
		public void onSuccess()
		{
			//TODO handle success
		}

		@Override
		public void onError(com.spotify.sdk.android.player.Error error)
		{
			//TODO handle error
		}
	};



	//ConnectionStateCallback

	@Override
	public void onLoggedIn()
	{
		System.out.println("onLoggedIn");
	}

	@Override
	public void onLoggedOut()
	{
		//
	}

	@Override
	public void onLoginFailed(Error error)
	{
		System.out.println("onLoginFailed");
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

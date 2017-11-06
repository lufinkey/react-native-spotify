
package com.reactlibrary;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

public class RCTSpotifyModule extends ReactContextBaseJavaModule
{
	private final ReactApplicationContext reactContext;

	private boolean initialized;

	private BroadcastReceiver networkStateReceiver;

	public RCTSpotifyModule(ReactApplicationContext reactContext)
	{
		super(reactContext);
		this.reactContext = reactContext;

		initialized = false;
		player = null;
		networkStateReceiver = null;
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
	void initialize(ReadableMap options, Callback completion)
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
		//TODO do the thing
	}
}

package com.reactlibrary;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

public class SpotifyAuthActivity extends Activity
{
	private static final int REQUEST_CODE = 6969;

	static AuthenticationRequest request;
	static RCTSpotifyCallback<AuthenticationResponse> completion;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
		super.onActivityResult(requestCode, resultCode, intent);

		if(requestCode == REQUEST_CODE)
		{
			AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
			RCTSpotifyCallback<AuthenticationResponse> completionTmp = completion;
			request = null;
			completion = null;
			finish();
			completionTmp.invoke(response, null);
		}
	}
}

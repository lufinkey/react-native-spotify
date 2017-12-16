package com.lufinkey.react.spotify;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

public class AuthActivity extends Activity
{
	private static final int REQUEST_CODE = 6969;

	static AuthActivity currentActivity;
	static AuthenticationRequest request;
	static CompletionBlock<AuthenticationResponse> completion;

	CompletionBlock<Void> onFinishCompletion;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		currentActivity = this;
		AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
		super.onActivityResult(requestCode, resultCode, intent);

		if(requestCode == REQUEST_CODE && completion != null)
		{
			AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
			CompletionBlock<AuthenticationResponse> completionTmp = completion;
			request = null;
			completion = null;
			completionTmp.invoke(response, null);
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		if(isFinishing())
		{
			if(onFinishCompletion!=null)
			{
				CompletionBlock<Void> completionTmp = onFinishCompletion;
				onFinishCompletion = null;
				completionTmp.invoke(null, null);
			}
		}
	}
}

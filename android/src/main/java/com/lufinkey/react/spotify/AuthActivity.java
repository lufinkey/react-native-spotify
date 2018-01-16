package com.lufinkey.react.spotify;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

public class AuthActivity extends Activity
{
	private static final int REQUEST_CODE = 6969;

	private static Auth authFlow_auth;
	private static AuthActivityListener authFlow_listener;

	private Auth auth;
	private AuthActivityListener listener;
	private CompletionBlock<Void> finishCompletion;

	public static void performAuthFlow(Activity context, Auth auth, AuthActivityListener listener)
	{
		// check for missing options
		if(auth.clientID == null)
		{
			SpotifyError error = new SpotifyError(SpotifyError.Code.MISSING_PARAMETERS, "missing option clientID");
			listener.onAuthActivityFailure(null, error);
			return;
		}

		// ensure no conflicting callbacks
		if(authFlow_auth != null || authFlow_listener != null)
		{
			System.out.println("AuthActivity is already being shown");
			SpotifyError error = new SpotifyError(SpotifyError.Code.CONFLICTING_CALLBACKS, "Cannot show another AuthActivity while one is already being shown");
			listener.onAuthActivityFailure(null, error);
			return;
		}

		// store temporary static variables
		authFlow_auth = auth;
		authFlow_listener = listener;

		// start activity
		context.startActivity(new Intent(context, AuthActivity.class));
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setFinishOnTouchOutside(false);

		auth = authFlow_auth;
		listener = authFlow_listener;

		authFlow_auth = null;
		authFlow_listener = null;

		// decide response type
		AuthenticationResponse.Type responseType = AuthenticationResponse.Type.TOKEN;
		if(auth.tokenSwapURL!=null)
		{
			responseType = AuthenticationResponse.Type.CODE;
		}

		// create auth request
		AuthenticationRequest.Builder requestBuilder = new AuthenticationRequest.Builder(auth.clientID, responseType, auth.redirectURL);
		requestBuilder.setScopes(auth.requestedScopes);
		requestBuilder.setShowDialog(true);
		AuthenticationRequest request = requestBuilder.build();

		// show auth activity
		AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
		super.onActivityResult(requestCode, resultCode, intent);

		if(requestCode == REQUEST_CODE)
		{
			AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);

			switch(response.getType())
			{
				default:
					listener.onAuthActivityCancel(this);
					break;

				case ERROR:
					if(response.getError().equals("access_denied"))
					{
						listener.onAuthActivityCancel(this);
					}
					else
					{
						listener.onAuthActivityFailure(this, new SpotifyError(SpotifyError.Code.AUTHORIZATION_FAILED, response.getError()));
					}
					break;

				case CODE:
					listener.onAuthActivityReceivedCode(this, response.getCode());
					break;

				case TOKEN:
					listener.onAuthActivityReceivedToken(this, response.getAccessToken(), response.getExpiresIn());
					break;
			}
		}
	}

	public void finish(CompletionBlock<Void> completion)
	{
		finishCompletion = completion;
		this.finish();
	}

	@Override
	public void finish()
	{
		AuthenticationClient.stopLoginActivity(this, REQUEST_CODE);
		super.finish();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		if(isFinishing())
		{
			if(finishCompletion != null)
			{
				CompletionBlock<Void> completionTmp = finishCompletion;
				finishCompletion = null;
				completionTmp.invoke(null, null);
			}
		}
	}
}

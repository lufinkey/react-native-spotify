package com.lufinkey.react.spotify;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import java.util.UUID;

public class AuthActivity extends Activity
{
	private static final int REQUEST_CODE = 6969;

	private static LoginOptions authFlow_options;
	private static AuthActivityListener authFlow_listener;
	private static AuthActivity currentAuthActivity;

	private LoginOptions options;
	private String xssState;
	private AuthActivityListener listener;
	private Completion<Void> finishCompletion;

	public static void performAuthFlow(Activity context, LoginOptions options, AuthActivityListener listener) {
		// ensure no conflicting callbacks
		if(authFlow_options != null || authFlow_listener != null || currentAuthActivity != null) {
			SpotifyError error = new SpotifyError(SpotifyError.Code.ConflictingCallbacks, "Cannot call login or authenticate multiple times before completing");
			listener.onAuthActivityFailure(null, error);
			return;
		}

		// store temporary static variables
		authFlow_options = options;
		authFlow_listener = listener;

		// start activity
		Intent intent = new Intent(context, AuthActivity.class);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setFinishOnTouchOutside(false);

		options = authFlow_options;
		xssState = UUID.randomUUID().toString();
		listener = authFlow_listener;
		currentAuthActivity = this;

		authFlow_options = null;
		authFlow_listener = null;

		AuthenticationRequest request = options.getAuthenticationRequest(xssState);

		// show auth activity
		AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		if(requestCode == REQUEST_CODE) {
			AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);

			switch(response.getType()) {
				default:
					listener.onAuthActivityCancel(this);
					break;

				case ERROR:
					if(response.getError().equals("access_denied")) {
						listener.onAuthActivityCancel(this);
					}
					else {
						listener.onAuthActivityFailure(this, new SpotifyError(response.getError(), response.getError()));
					}
					break;

				case TOKEN:
					/*if(xssState != null && !xssState.equals(response.getState())) {
						listener.onAuthActivityFailure(this, new SpotifyError("state_mismatch", "state mismatch"));
						return;
					}*/
					SessionData sessionData = new SessionData();
					sessionData.accessToken = response.getAccessToken();
					sessionData.expireDate = SessionData.getExpireDate(response.getExpiresIn());
					sessionData.refreshToken = null;
					sessionData.scopes = options.scopes;
					listener.onAuthActivityReceiveSession(this, sessionData);
					break;

				case CODE:
					/*if(xssState != null && !xssState.equals(response.getState())) {
						listener.onAuthActivityFailure(this, new SpotifyError("state_mismatch", "state mismatch"));
						return;
					}*/
					if(options.tokenSwapURL == null) {
						listener.onAuthActivityFailure(this, SpotifyError.getMissingOptionError("tokenSwapURL"));
						return;
					}
					final AuthActivity authActivity = this;
					Auth.swapCodeForToken(response.getCode(), options.tokenSwapURL, new Completion<SessionData>() {
						@Override
						public void onReject(SpotifyError error) {
							listener.onAuthActivityFailure(authActivity, error);
						}

						@Override
						public void onResolve(SessionData session) {
							if(session.scopes == null) {
								session.scopes = options.scopes;
							}
							listener.onAuthActivityReceiveSession(authActivity, session);
						}
					});
					break;
			}
		}
	}

	public void finish(Completion<Void> completion) {
		finishCompletion = completion;
		this.finish();
	}

	@Override
	public void finish() {
		AuthenticationClient.stopLoginActivity(this, REQUEST_CODE);
		super.finish();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(isFinishing()) {
			currentAuthActivity = null;
			if(finishCompletion != null) {
				Completion<Void> completionTmp = finishCompletion;
				finishCompletion = null;
				completionTmp.resolve(null);
			}
		}
	}
}

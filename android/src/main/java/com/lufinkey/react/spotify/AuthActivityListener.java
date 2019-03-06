package com.lufinkey.react.spotify;

import com.spotify.sdk.android.authentication.AuthenticationResponse;

public interface AuthActivityListener
{
	void onAuthActivityCancel(AuthActivity activity);
	void onAuthActivityFailure(AuthActivity activity, SpotifyError error);
	void onAuthActivityReceiveSession(AuthActivity activity, SessionData session);
}

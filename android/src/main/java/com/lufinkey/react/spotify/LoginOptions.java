package com.lufinkey.react.spotify;

import com.facebook.react.bridge.Dynamic;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import java.util.ArrayList;
import java.util.HashMap;

public class LoginOptions {
	public String clientID = null;
	public String redirectURL = null;
	public String[] scopes = null;
	public String tokenSwapURL = null;
	public String tokenRefreshURL = null;
	public HashMap<String,String> params = null;


	AuthenticationRequest getAuthenticationRequest(String state) {
		AuthenticationResponse.Type responseType = AuthenticationResponse.Type.TOKEN;
		if(tokenSwapURL != null) {
			responseType = AuthenticationResponse.Type.CODE;
		}
		AuthenticationRequest.Builder requestBuilder = new AuthenticationRequest.Builder(clientID, responseType, redirectURL);
		if(scopes != null) {
			requestBuilder.setScopes(scopes);
		}
		/*if(state != null) {
			requestBuilder.setState(state);
		}*/
		if(params != null) {
			String showDialog = params.get("show_dialog");
			if(showDialog != null) {
				requestBuilder.setShowDialog(showDialog.equals("true") ? true : false);
			}
		}
		return requestBuilder.build();
	}

	public static LoginOptions from(ReadableMap dict, ReadableMap fallback) throws SpotifyError {
		return from(dict, fallback, new ArrayList<String>());
	}

	public static LoginOptions from(ReadableMap dict, ReadableMap fallback, ArrayList<String> ignore) throws SpotifyError {
		LoginOptions options = new LoginOptions();
		// clientID
		Dynamic clientID = Utils.getOption("clientID", dict, fallback);
		options.clientID = (clientID != null) ? clientID.asString() : null;
		if(options.clientID == null && !ignore.contains("clientID")) {
			throw SpotifyError.getMissingOptionError("clientID");
		}
		// redirectURL
		Dynamic redirectURL = Utils.getOption("redirectURL", dict, fallback);
		options.redirectURL = (redirectURL != null) ? redirectURL.asString() : null;
		if(options.redirectURL == null && !ignore.contains("redirectURL")) {
			throw SpotifyError.getMissingOptionError("redirectURL");
		}
		// scopes
		Dynamic scope = Utils.getOption("scopes", dict, fallback);
		ReadableArray scopeArray = (scope != null) ? scope.asArray() : null;
		String[] scopes = new String[scopeArray.size()];
		for(int i=0; i<scopeArray.size(); i++) {
			scopes[i] = scopeArray.getString(i);
		}
		options.scopes = scopes;
		// tokenSwapURL
		Dynamic tokenSwapURL = Utils.getOption("tokenSwapURL", dict, fallback);
		options.tokenSwapURL = (tokenSwapURL != null) ? tokenSwapURL.asString() : null;
		// tokenRefreshURL
		Dynamic tokenRefreshURL = Utils.getOption("tokenRefreshURL", dict, fallback);
		options.tokenRefreshURL = (tokenRefreshURL != null) ? tokenRefreshURL.asString() : null;
		// params
		HashMap<String,String> params = new HashMap<>();
		Dynamic showDialog = Utils.getOption("showDialog", dict, fallback);
		if(showDialog != null) {
			params.put("show_dialog", showDialog.asBoolean() ? "true" : "false");
		}
		options.params = params;
		return options;
	}
}

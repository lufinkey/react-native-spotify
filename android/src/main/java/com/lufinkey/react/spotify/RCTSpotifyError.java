package com.lufinkey.react.spotify;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

public class RCTSpotifyError
{
	public static final String DOMAIN = "RCTSpotifyErrorDomain";

	public enum Code
	{
		ALREADY_INITIALIZED(90),
		INITIALIZATION_FAILED(91),
		AUTHORIZATION_FAILED(92),
		SPOTIFY_ERROR(93),
		NOT_IMPLEMENTED(94),
		PLAYER_NOT_INITIALIZED(95),
		CONFLICTING_CALLBACKS(100),
		MISSING_PARAMETERS(101),
		BAD_PARAMETERS(102),
		NOT_INITIALIZED(103),
		NOT_LOGGED_IN(104),
		REQUEST_ERROR(105);

		public final int value;
		private Code(int value)
		{
			this.value = value;
		}
	};

	private Code code;
	private String description;

	public RCTSpotifyError(com.spotify.sdk.android.player.Error error)
	{
		//TODO add a switch for nativeCode
		this.code = Code.SPOTIFY_ERROR;
		this.description = error.toString();
	}

	public RCTSpotifyError(Code code, String description)
	{
		this.code = code;
		this.description = description;
	}

	public Code getCode()
	{
		return code;
	}

	public String getDescription()
	{
		return description;
	}

	public ReadableMap toReactObject()
	{
		WritableMap map = Arguments.createMap();
		map.putString("domain", DOMAIN);
		map.putInt("code", code.value);
		map.putString("description", description);
		return map;
	}

	public static RCTSpotifyError getNullParameterError(String parameterName)
	{
		return new RCTSpotifyError(Code.BAD_PARAMETERS, parameterName+" parameter cannot be null");
	}
}

package com.lufinkey.react.spotify;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import com.spotify.sdk.android.player.Error;

import java.lang.reflect.Field;

public class SpotifyError
{
	public static final String DOMAIN = "RCTSpotifyErrorDomain";
	public static final String SPOTIFY_SDK_DOMAIN = "com.spotify.ios-sdk.playback";
	public static final String SPOTIFY_AUTH_DOMAIN = "com.spotify.auth";
	public static final String SPOTIFY_WEB_DOMAIN = "com.spotify.web-api";

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

	private String domain;
	private int code;
	private String description;

	public static int getNativeCode(Error error)
	{
		try
		{
			Field nativeCodeField = com.spotify.sdk.android.player.Error.class.getField("nativeCode");
			nativeCodeField.setAccessible(true);
			int nativeCode = (int)nativeCodeField.get(error);
			return nativeCode;
		}
		catch (Exception e)
		{
			return -1;
		}
	}

	public static SpotifyError fromSDKError(int nativeCode)
	{
		String description = null;
		if(nativeCode==getNativeCode(Error.kSpErrorOk))
		{
			description = "The operation was successful. I don't know why this is an error either.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorFailed))
		{
			description = "The operation failed due to an unspecified issue.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorInitFailed))
		{
			description = "Audio streaming could not be initialised.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorWrongAPIVersion))
		{
			description = "Audio streaming could not be initialized because of an incompatible API version.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorNullArgument))
		{
			description = "An unexpected NULL pointer was passed as an argument to a function.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorInvalidArgument))
		{
			description = "An unexpected argument value was passed to a function.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorUninitialized))
		{
			description = "Audio streaming has not yet been initialised for this application.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorAlreadyInitialized))
		{
			description = "Audio streaming has already been initialised for this application.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorLoginBadCredentials))
		{
			description = "Login to Spotify failed because of invalid credentials.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorNeedsPremium))
		{
			description = "This operation requires a Spotify Premium account.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorTravelRestriction))
		{
			description = "Spotify user is not allowed to log in from this country.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorApplicationBanned))
		{
			description = "This application has been banned by Spotify.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorGeneralLoginError))
		{
			description = "An unspecified login error occurred.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorUnsupported))
		{
			description = "The operation is not supported.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorNotActiveDevice))
		{
			description = "The operation is not supported if the device is not the active playback device.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorAPIRateLimited))
		{
			description = "This application has made too many API requests at a time, so it is now rate-limited.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorPlaybackErrorStart))
		{
			description = "Unable to start playback.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorGeneralPlaybackError))
		{
			description = "An unspecified playback error occurred.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorPlaybackRateLimited))
		{
			description = "This application has requested track playback too many times, so it is now rate-limited.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorPlaybackCappingLimitReached))
		{
			description = "This application's playback limit has been reached.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorAdIsPlaying))
		{
			description = "An ad is playing.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorCorruptTrack))
		{
			description = "The track is corrupted.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorContextFailed))
		{
			description = "The operation failed.";
		}
		else if(nativeCode==getNativeCode(Error.kSpErrorPrefetchItemUnavailable))
		{
			description = "Item is unavailable for pre-fetch.";
		}
		else if(nativeCode==getNativeCode(Error.kSpAlreadyPrefetching))
		{
			description = "Item is already pre-fetching.";
		}
		else if(nativeCode==getNativeCode(Error.kSpStorageReadError))
		{
			description = "Storage read failed.";
		}
		else if(nativeCode==getNativeCode(Error.kSpStorageWriteError))
		{
			description = "Storage write failed.";
		}
		else if(nativeCode==getNativeCode(Error.kSpPrefetchDownloadFailed))
		{
			description = "Download failed.";
		}
		if(description==null)
		{
			return null;
		}
		return new SpotifyError(SPOTIFY_SDK_DOMAIN, nativeCode, description);
	}

	public SpotifyError(Error error)
	{
		int nativeCode = getNativeCode(error);
		if(nativeCode!=getNativeCode(Error.UNKNOWN))
		{
			domain = SPOTIFY_SDK_DOMAIN;
			code = nativeCode;
			SpotifyError spotifyError = fromSDKError(nativeCode);
			if(spotifyError==null)
			{
				description = error.toString();
			}
			else
			{
				description = spotifyError.description;
			}
		}
		else
		{
			this.domain = DOMAIN;
			this.code = Code.SPOTIFY_ERROR.value;
			this.description = error.toString();
		}
	}

	public SpotifyError(Code code, String description)
	{
		this.domain = DOMAIN;
		this.code = code.value;
		this.description = description;
	}

	public SpotifyError(String domain, int code, String description)
	{
		this.domain = domain;
		this.code = code;
		this.description = description;
	}

	public int getCode()
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
		map.putInt("code", code);
		map.putString("description", description);
		return map;
	}

	public static SpotifyError getNullParameterError(String parameterName)
	{
		return new SpotifyError(Code.BAD_PARAMETERS, parameterName+" parameter cannot be null");
	}
}

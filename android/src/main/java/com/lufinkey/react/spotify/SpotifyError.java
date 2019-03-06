package com.lufinkey.react.spotify;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import com.spotify.sdk.android.player.Error;

public class SpotifyError extends Throwable
{
	public enum Code {
		AlreadyInitialized("Spotify has already been initialized"),
		NotInitialized("Spotify has not been initialized"),
		NotImplemented("This feature has not been implemented"),
		NotLoggedIn("You are not logged in"),
		MissingOption("Missing required option"),
		NullParameter("Null parameter"),
		ConflictingCallbacks("You cannot call this function while it is already executing"),
		BadResponse("Invalid response format"),
		PlayerNotReady("Player is not ready"),
		SessionExpired("Your login session has expired");

		public final String description;

		private Code(String description) {
			this.description = description;
		}

		public String getCodeName() {
			return "RNS"+name();
		}

		public void reject(Promise promise) {
			promise.reject(getCodeName(), description);
		}
	};

	private String code;

	public static String getSDKErrorCode(Error error) {
		String code = error.name();
		if(code.startsWith("kSp")) {
			return "SP"+code.substring(3);
		}
		return code;
	}

	public static String getSDKErrorMessage(Error error) {
		switch(error) {
			case kSpErrorOk:
				return "The operation was successful. I don't know why this is an error...";
			case kSpErrorFailed:
				return "The operation failed due to an unspecified issue.";
			case kSpErrorInitFailed:
				return "Audio streaming could not be initialised.";
			case kSpErrorWrongAPIVersion:
				return "Audio streaming could not be initialized because of an incompatible API version.";
			case kSpErrorNullArgument:
				return "An unexpected NULL pointer was passed as an argument to a function.";
			case kSpErrorInvalidArgument:
				return "An unexpected argument value was passed to a function.";
			case kSpErrorUninitialized:
				return "Audio streaming has not yet been initialised for this application.";
			case kSpErrorAlreadyInitialized:
				return "Audio streaming has already been initialised for this application.";
			case kSpErrorLoginBadCredentials:
				return "Login to Spotify failed because of invalid credentials.";
			case kSpErrorNeedsPremium:
				return "This operation requires a Spotify Premium account.";
			case kSpErrorTravelRestriction:
				return "Spotify user is not allowed to log in from this country.";
			case kSpErrorApplicationBanned:
				return "This application has been banned by Spotify.";
			case kSpErrorGeneralLoginError:
				return "An unspecified login error occurred.";
			case kSpErrorUnsupported:
				return "The operation is not supported.";
			case kSpErrorNotActiveDevice:
				return "The operation is not supported if the device is not the active playback device.";
			case kSpErrorAPIRateLimited:
				return "This application has made too many API requests at a time, so it is now rate-limited.";
			case kSpErrorPlaybackErrorStart:
				return "Unable to start playback.";
			case kSpErrorGeneralPlaybackError:
				return "An unspecified playback error occurred.";
			case kSpErrorPlaybackRateLimited:
				return "This application has requested track playback too many times, so it is now rate-limited.";
			case kSpErrorPlaybackCappingLimitReached:
				return "This application's playback limit has been reached.";
			case kSpErrorAdIsPlaying:
				return "An ad is playing.";
			case kSpErrorCorruptTrack:
				return "The track is corrupted.";
			case kSpErrorContextFailed:
				return "The operation failed.";
			case kSpErrorPrefetchItemUnavailable:
				return "Item is unavailable for pre-fetch.";
			case kSpAlreadyPrefetching:
				return "Item is already pre-fetching.";
			case kSpStorageReadError:
				return "Storage read failed.";
			case kSpStorageWriteError:
				return "Storage write failed.";
			case kSpPrefetchDownloadFailed:
				return "Download failed.";
			default:
				return "Unknown Error";
		}
	}

	public SpotifyError(Error error) {
		super(getSDKErrorMessage(error));
		this.code = getSDKErrorCode(error);
	}

	public SpotifyError(Error error, String message) {
		super((message != null && message.length() > 0) ? message : getSDKErrorMessage(error));
		this.code = getSDKErrorCode(error);
	}

	public SpotifyError(Code code) {
		super(code.description);
		this.code = code.getCodeName();
	}

	public SpotifyError(Code code, String message) {
		super(message);
		this.code = code.getCodeName();
	}

	public SpotifyError(String code, String message) {
		super(message);
		this.code = code;

	}

	public String getCode() {
		return code;
	}

	public ReadableMap toReactObject() {
		WritableMap map = Arguments.createMap();
		map.putString("code", code);
		map.putString("message", getMessage());
		return map;
	}

	public void reject(Promise promise) {
		promise.reject(code, getMessage());
	}

	public static SpotifyError getNullParameterError(String parameterName) {
		return new SpotifyError(Code.NullParameter, parameterName+" cannot be null");
	}

	public static SpotifyError getMissingOptionError(String optionName) {
		return new SpotifyError(Code.MissingOption, "Missing required option "+optionName);
	}

	public static SpotifyError getHTTPError(int statusCode) {
		if(statusCode <= 0) {
			return new SpotifyError("HTTPRequestFailed", "Unable to send request");
		}
		return getHTTPError(statusCode, "Request failed with status "+statusCode);
	}

	public static SpotifyError getHTTPError(int statusCode, String message) {
		String code = "HTTP"+statusCode;
		if(statusCode <= 0) {
			code = "HTTPRequestFailed";
		}
		return new SpotifyError(code, message);
	}
}

package com.reactlibrary;

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
		MISSING_PARAMETERS(92),
		CONFLICTING_CALLBACKS(100);

		public final int value;
		private Code(int value)
		{
			this.value = value;
		}
	};

	private Code code;
	private String description;

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
}

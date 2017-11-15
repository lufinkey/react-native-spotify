package com.lufinkey.react.spotify;

public interface RCTSpotifyCallback<T>
{
	public void invoke(T obj, RCTSpotifyError error);
}

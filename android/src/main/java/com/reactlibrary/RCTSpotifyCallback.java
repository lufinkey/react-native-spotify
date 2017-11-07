package com.reactlibrary;

public interface RCTSpotifyCallback<T>
{
	public void invoke(T obj, RCTSpotifyError error);
}

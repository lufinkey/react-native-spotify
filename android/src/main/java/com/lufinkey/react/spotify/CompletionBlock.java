package com.lufinkey.react.spotify;

public interface CompletionBlock<T>
{
	public void invoke(T obj, SpotifyError error);
}

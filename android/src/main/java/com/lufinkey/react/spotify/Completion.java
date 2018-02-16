package com.lufinkey.react.spotify;

public abstract class Completion<T>
{
	public void resolve(T result)
	{
		onResolve(result);
		onComplete(result, null);
	}

	public void reject(SpotifyError error)
	{
		onReject(error);
		onComplete(null, error);
	}

	public void onResolve(T result)
	{
		//
	}

	public void onReject(SpotifyError error)
	{
		//
	}

	public void onComplete(T result, SpotifyError error)
	{
		//
	}
}

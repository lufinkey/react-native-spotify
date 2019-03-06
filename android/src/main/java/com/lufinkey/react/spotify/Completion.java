package com.lufinkey.react.spotify;

public abstract class Completion<T>
{
	private boolean responded = false;

	public final void resolve(T result) {
		if(responded) {
			throw new IllegalStateException("cannot call resolve or reject multiple times on a Completion object");
		}
		responded = true;
		onResolve(result);
		onComplete(result, null);
	}

	public final void reject(SpotifyError error) {
		if(responded) {
			throw new IllegalStateException("cannot call resolve or reject multiple times on a Completion object");
		}
		responded = true;
		onReject(error);
		onComplete(null, error);
	}

	public void onResolve(T result) {
		//
	}

	public void onReject(SpotifyError error) {
		//
	}

	public void onComplete(T result, SpotifyError error) {
		//
	}
}

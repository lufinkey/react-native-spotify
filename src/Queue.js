
import EventEmitter from 'events';
import Spotify from './Spotify';


const Queue = new EventEmitter();

let currentURI = null;
let uris = [];
let connected = null;



// handle connection

Spotify.on('login', () => {
	if(connected === null)
	{
		connected = true;
	}
});

Spotify.on('logout', () => {
	connected = null;
	Queue.clear();
});

Spotify.on('disconnect', () => {
	connected = false;
});

Spotify.on('reconnect', () => {
	connected = true;
});



// handle track queueing

let tryQueuePlayback = null;
tryQueuePlayback = () => {
	if(currentURI == null)
	{
		return;
	}
	Spotify.playURI(currentURI, 0, 0).then(() => {
		// done
	}).catch((error) => {
		// error
		// ensure we're logged in and we have uris
		if(connected !== null && uris.length > 0)
		{
			if(!connected)
			{
				// we must have failed because we weren't connected, so wait until we are
				Spotify.once('reconnect', tryQueuePlayback);
			}
			else
			{
				// unknown error
				Queue.emit('queueError', error);
			}
		}
	});
};

Spotify.on("trackDelivered", (event) => {
	if(uris.length > 0)
	{
		// play the next song in the queue
		Queue.nextTrack();
	}
	else if(currentURI != null)
	{
		// the queue has finished
		currentURI = null;
		Queue.emit('queueFinish');
	}
});



// functions

Queue.nextTrack = function()
{
	var nextURI = uris[0];
	uris.splice(0, 1);
	if(nextURI == null)
	{
		currentURI = null;
		return;
	}
	currentURI = nextURI;
	tryQueuePlayback();
}

Queue.skipToTrack = function(index)
{
	uris.splice(0, index);
	Queue.nextTrack();
}

Queue.addTrack = function(uri)
{
	uris.push(uri);
}

Queue.removeTrack = function(index)
{
	if(!Number.isInteger(index))
	{
		throw new Error("invalid index");
	}
	uris.splice(index, 1);
}

Queue.moveTrack = function(sourceIndex, destIndex)
{
	if(!Number.isInteger(sourceIndex) || !Number.isInteger(destIndex)
		|| sourceIndex < 0 || sourceIndex >= uris.length
		|| destIndex < 0 || destIndex >= uris.length)
	{
		throw new Error("invalid index");
	}
	var uri = uris[sourceIndex];
	uris.splice(sourceIndex, 1);
	uris.splice(destIndex, 0, uri);
}

Queue.getURIs = function()
{
	return uris.slice(0);
}

Queue.clear = function()
{
	currentURI = null;
	uris = [];
}

export default Queue;

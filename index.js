
import { NativeModules } from 'react-native';

var SpotifyManager = NativeModules.SpotifyManager || NativeModules.SpotifyManager;

export default class Spotify
{
	constructor()
	{
		this.instanceID = null;
		this.error = null;
	}

	initialize(options)
	{
		if(this.instanceID != null)
		{
			throw "Instance is already initialized";
		}
		var result = SpotifyManager.createSpotifyInstance(options);
		if(!result.success)
		{
			this.error = result.error;
			return false;
		}
		this.instanceID = result.instanceID;
		return true;
	}

	destroy()
	{
		if(this.instanceID == null)
		{
			return;
		}
		SpotifyManager.destroySpotifyInstance(this.instanceID);
	}

	testTheThing()
	{
		console.log("ayyy we doin the thing");
		SpotifyManager.test();
	}
}

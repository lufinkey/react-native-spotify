
import { NativeModules } from 'react-native';

var SpotifyManager = NativeModules.SpotifyManager || NativeModules.SpotifyManager;

export default class Spotify
{
	constructor()
	{
		this.instanceID = null;
	}

	initialize(options)
	{
		if(this.instanceID != null)
		{
			throw "Instance is already initialized";
		}
		var result = SpotifyManager.createSpotifyInstance(options);
		this.instanceID = result["instanceID"];
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

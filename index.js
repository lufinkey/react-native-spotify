
import { NativeModules } from 'react-native';

var SpotifyManager = NativeModules.SpotifyManager || NativeModules.SpotifyManager;

export default class Spotify
{
	constructor(options)
	{
		var result = SpotifyManager.createSpotify(options);

		this.instanceID = result["instanceID"];
		console.log("created Spotify instance with options:");
		console.log(options);
	}

	testTheThing()
	{
		console.log("ayyy we doin the thing");
		SpotifyManager.test();
	}
}

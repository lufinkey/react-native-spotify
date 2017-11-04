
import { NativeModules } from 'react-native';

var SpotifyManager = NativeModules.SpotifyManager || NativeModules.SpotifyManager;

export default class Spotify
{
	constructor(client_id)
	{
		console.log("client_id: "+client_id);
	}

	testTheThing()
	{
		console.log("ayyy we doin the thing");
		SpotifyManager.testSpotify();
	} 
}


import React, { Component } from 'react';
import {
	ActivityIndicator,
	Alert,
	StyleSheet,
	Text,
	TouchableHighlight,
	View
} from 'react-native';
import { NavigationActions } from 'react-navigation';
import Spotify from 'rn-spotify-sdk';

export class InitialScreen extends Component
{
	static navigationOptions = {
		header: null
	};

	constructor()
	{
		super();

		this.state = {spotifyInitialized: false};
		this.spotifyLoginButtonWasPressed = this.spotifyLoginButtonWasPressed.bind(this);
	}

	goToPlayer()
	{
		const navAction = NavigationActions.reset({
			index: 0,
			actions: [
			  NavigationActions.navigate({ routeName: 'player'})
			]
		});
		this.props.navigation.dispatch(navAction);
	}

	componentDidMount()
	{
		// initialize Spotify if it hasn't been initialized yet
		if(!Spotify.isInitialized())
		{
			// initialize spotify
			var spotifyOptions = {
				"clientID":"<INSERT-YOUR-CLIENT-ID-HERE>",
				"sessionUserDefaultsKey":"SpotifySession",
				"redirectURL":"examplespotifyapp://auth",
				"scopes":["user-read-private", "playlist-read", "playlist-read-private", "streaming"],
			};
			Spotify.initialize(spotifyOptions).then((loggedIn) => {
				// update UI state
				this.setState({spotifyInitialized: true});
				// handle initialization
				if(loggedIn)
				{
					this.goToPlayer();
				}
			}).catch((error) => {
				Alert.alert("Error", error.message);
			});
		}
		else
		{
			// update UI state
			this.setState((state) => {
				state.spotifyInitialized = true;
				return state;
			});
			// handle logged in
			if(Spotify.isLoggedIn())
			{
				this.goToPlayer();
			}
		}
	}

	spotifyLoginButtonWasPressed()
	{
		// log into Spotify
		Spotify.login().then((loggedIn) => {
			if(loggedIn)
			{
				// logged in
				this.goToPlayer();
			}
			else
			{
				// cancelled
			}
		}).catch((error) => {
			// error
			Alert.alert("Error", error.message);
		});
	}

	render()
	{
		if(!this.state.spotifyInitialized)
		{
			return (
				<View style={styles.container}>
					<ActivityIndicator animating={true} style={styles.loadIndicator}>
					</ActivityIndicator>
					<Text style={styles.loadMessage}>
						Loading...
					</Text>
				</View>
			);
		}
		else
		{
			return (
				<View style={styles.container}>
					<Text style={styles.greeting}>
						Hey! You! Log into your spotify
					</Text>
					<TouchableHighlight onPress={this.spotifyLoginButtonWasPressed} style={styles.spotifyLoginButton}>
						<Text style={styles.spotifyLoginButtonText}>Log into Spotify</Text>
					</TouchableHighlight>
				</View>
			);
		}
	}
}

const styles = StyleSheet.create({
	container: {
		flex: 1,
		justifyContent: 'center',
		alignItems: 'center',
		backgroundColor: '#F5FCFF',
	},

	loadIndicator: {
		//
	},
	loadMessage: {
		fontSize: 20,
		textAlign: 'center',
		margin: 10,
	},

	spotifyLoginButton: {
		justifyContent: 'center',
		borderRadius: 18,
		backgroundColor: 'green',
		overflow: 'hidden',
		width: 200,
		height: 40,
		margin: 20,
	},
	spotifyLoginButtonText: {
		fontSize: 20,
		textAlign: 'center',
		color: 'white',
	},

	greeting: {
		fontSize: 20,
		textAlign: 'center',
		margin: 10,
	},
});

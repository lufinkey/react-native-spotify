
import React, { Component } from 'react';
import {
	Alert,
	StyleSheet,
	Text,
	TouchableHighlight,
	View
} from 'react-native';
import { NavigationActions } from 'react-navigation';
import Spotify from 'react-native-spotify';

export class PlayerScreen extends Component
{
	static navigationOptions = {
		title: 'Player',
	};

	constructor()
	{
		super();

		this.state = { spotifyUserName:null };

		this.spotifyLogoutButtonWasPressed = this.spotifyLogoutButtonWasPressed.bind(this);
	}

	componentDidMount()
	{
		console.log("sending getMe request");
		Spotify.getMe((result, error) => {
			console.log("got getMe result");
			if(error)
			{
				Alert.alert("Error Sending getMe request", error.message);
			}
			else
			{
				this.setState((state) => {
					state.spotifyUserName = result.display_name;
					return state;
				});
			}
		});
	}

	goToInitialScreen()
	{
		const navAction = NavigationActions.reset({
			index: 0,
			actions: [
			  NavigationActions.navigate({ routeName: 'initial'})
			]
		});
		this.props.navigation.dispatch(navAction);
	}

	spotifyLogoutButtonWasPressed()
	{
		Spotify.logout((error) => {
			if(error)
			{
				Alert.alert("Error", error.message);
			}
			else
			{
				this.goToInitialScreen();
			}
		});
	}

	render()
	{
		return (
			<View style={styles.container}>
				{ this.state.spotifyUserName!=null ? (
					<Text style={styles.greeting}>
						You are logged in as {this.state.spotifyUserName}
					</Text>
				) : (
					<Text style={styles.greeting}>
						Getting user info...
					</Text>
				)}
				<TouchableHighlight onPress={this.spotifyLogoutButtonWasPressed}>
					<Text>Logout</Text>
				</TouchableHighlight>
			</View>
		);
	}
}

const styles = StyleSheet.create({
	container: {
		flex: 1,
		justifyContent: 'center',
		alignItems: 'center',
		backgroundColor: '#F5FCFF',
	},
	greeting: {
		fontSize: 20,
		textAlign: 'center',
		margin: 10,
	},
});

package com.lufinkey.react.spotify;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class Utils
{
	public static ReactApplicationContext reactContext = null;

	private static RequestQueue requestQueue = null;

	public static String makeQueryString(ReadableMap params)
	{
		String query = "";
		HashMap<String, Object> map = params.toHashMap();
		boolean firstArg = true;
		for(String key : map.keySet())
		{
			if(firstArg)
			{
				firstArg = false;
			}
			else
			{
				query += "&";
			}
			String value = map.get(key).toString();
			try
			{
				query += URLEncoder.encode(key, "UTF-8")+"="+URLEncoder.encode(value, "UTF-8");
			}
			catch (UnsupportedEncodingException e)
			{
				e.printStackTrace();
				break;
			}
		}
		return query;
	}

	public static void doHTTPRequest(String url, final String method, final ReadableMap params, final boolean jsonBody, final HashMap<String,String> headers, final CompletionBlock<String> completion)
	{
		if(requestQueue == null)
		{
			requestQueue = Volley.newRequestQueue(reactContext.getCurrentActivity());
		}

		//parse request method
		int requestMethod;
		if(method.equals("GET"))
		{
			requestMethod = Request.Method.GET;
		}
		else if(method.equals("POST"))
		{
			requestMethod = Request.Method.POST;
		}
		else if(method.equals("DELETE"))
		{
			requestMethod = Request.Method.DELETE;
		}
		else if(method.equals("PUT"))
		{
			requestMethod = Request.Method.PUT;
		}
		else if(method.equals("HEAD"))
		{
			requestMethod = Request.Method.HEAD;
		}
		else if(method.equals("PATCH"))
		{
			requestMethod = Request.Method.PATCH;
		}
		else
		{
			completion.invoke(null, new SpotifyError(SpotifyError.Code.BAD_PARAMETERS, "invalid request method "+method));
			return;
		}

		//append query string to url if necessary
		if(!jsonBody && params!=null && method.equals("GET"))
		{
			url += "?"+makeQueryString(params);
		}

		//make request
		StringRequest request = new StringRequest(requestMethod, url, new Response.Listener<String>() {
			@Override
			public void onResponse(String response)
			{
				completion.invoke(response, null);
			}
		}, new Response.ErrorListener() {
			@Override
			public void onErrorResponse(VolleyError volleyError)
			{
				String response = null;
				SpotifyError error = null;
				if(volleyError.networkResponse!=null)
				{
					if(volleyError.networkResponse.data!=null)
					{
						response = new String(volleyError.networkResponse.data);
					}
					error = new SpotifyError("VolleyErrorDomain", volleyError.networkResponse.statusCode, "Request Error "+volleyError.networkResponse.statusCode);
				}
				else
				{
					error = new SpotifyError(SpotifyError.Code.REQUEST_ERROR, "Could not communicate with server");
				}
				completion.invoke(response, error);
			}
		}) {
			@Override
			public Map<String, String> getHeaders() throws AuthFailureError
			{
				if(headers==null)
				{
					return super.getHeaders();
				}
				return headers;
			}

			@Override
			public String getBodyContentType()
			{
				if(jsonBody)
				{
					return "application/json; charset=utf-8";
				}
				else
				{
					return "application/x-www-form-urlencoded";
				}
			}

			@Override
			public byte[] getBody() throws AuthFailureError
			{
				if(params!=null)
				{
					if(jsonBody)
					{
						JSONObject obj = Convert.toJSONObject(params);
						if (obj == null)
						{
							return null;
						}
						return obj.toString().getBytes();
					}
					else if(!method.equals("GET"))
					{
						return makeQueryString(params).getBytes();
					}
				}
				return null;
			}
		};

		//do request
		requestQueue.add(request);
	}
}

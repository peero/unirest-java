package com.mashape.unirest.http.utils;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

import com.mashape.unirest.http.options.Option;
import com.mashape.unirest.http.options.Options;

public class ClientFactory {

	public static CloseableHttpClient getHttpClient(String requestTimeouts) {
		return Options.getCloseableHttpClient(requestTimeouts);
	}

	public static CloseableHttpAsyncClient getAsyncHttpClient(String requestTimeouts) {
		return Options.getCloseableHttpAsyncClient(requestTimeouts);
	}

}

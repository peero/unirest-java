package com.mashape.unirest.http.options;

import com.mashape.unirest.http.async.utils.AsyncIdleConnectionMonitorThread;
import com.mashape.unirest.http.utils.SyncIdleConnectionMonitorThread;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
	* Created by webgate on 15/12/16.
	*/
public class Options {

	public static final int CONNECTION_TIMEOUT = 10000;
	public static final int SOCKET_TIMEOUT = 60000;
	public static final int MAX_TOTAL = 200;
	public static final int MAX_PER_ROUTE = 20;
	private static final PoolingHttpClientConnectionManager syncConnectionManager;
	private static final PoolingNHttpClientConnectionManager asyncConnectionManager;

	private static Map<Option, Object> options = new HashMap<Option, Object>();
	// hold all httpclient instance for specific connection/socket timeout composition
	private static Map<String, CloseableHttpClient> HTTP_CLIENT_INSTANCES = new HashMap<String, CloseableHttpClient>();
	// hold all async httpclient instance for specific connection/socket timeout composition
	private static Map<String, CloseableHttpAsyncClient> HTTP_ASYNC_CLIENT_INSTANCES = new HashMap<String, CloseableHttpAsyncClient>();


	private static boolean customClientSet = false;

	static {
		syncConnectionManager = new PoolingHttpClientConnectionManager();
		syncConnectionManager.setMaxTotal(MAX_TOTAL);
		syncConnectionManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);

		SyncIdleConnectionMonitorThread syncIdleConnectionMonitorThread = new SyncIdleConnectionMonitorThread(syncConnectionManager);
		options.put(Option.SYNC_MONITOR, syncIdleConnectionMonitorThread);
		syncIdleConnectionMonitorThread.start();

		// Create common default configuration and clients
		RequestConfig clientConfig = RequestConfig.custom().setConnectTimeout(CONNECTION_TIMEOUT).setSocketTimeout(SOCKET_TIMEOUT).setConnectionRequestTimeout(SOCKET_TIMEOUT).build();
		setOption(Option.HTTPCLIENT, HttpClientBuilder.create().setDefaultRequestConfig(clientConfig).setConnectionManager(syncConnectionManager).build());

		DefaultConnectingIOReactor ioReactor;
		try {
			ioReactor = new DefaultConnectingIOReactor();
			asyncConnectionManager = new PoolingNHttpClientConnectionManager(ioReactor);
			asyncConnectionManager.setMaxTotal(MAX_TOTAL);
			asyncConnectionManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);
		} catch (IOReactorException e) {
			throw new RuntimeException(e);
		}
		options.put(Option.ASYNC_MONITOR, new AsyncIdleConnectionMonitorThread(asyncConnectionManager));

		setOption(Option.ASYNCHTTPCLIENT, HttpAsyncClientBuilder.create().setDefaultRequestConfig(clientConfig).setConnectionManager(asyncConnectionManager).build());
	}

	public static void customClientSet() {
		customClientSet = true;
	}

	public static void setOption(Option option, Object value) {
		if ((option == Option.CONNECTION_TIMEOUT || option == Option.SOCKET_TIMEOUT) && customClientSet) {
			throw new RuntimeException("You can't set custom timeouts when providing custom client implementations. Set the timeouts directly in your custom client configuration instead.");
		}

		if (option == Option.MAX_TOTAL) {
			syncConnectionManager.setMaxTotal(((Integer) value).intValue());
			asyncConnectionManager.setMaxTotal(((Integer) value).intValue());
		} else if (option == Option.MAX_PER_ROUTE) {
			syncConnectionManager.setDefaultMaxPerRoute(((Integer) value).intValue());
			asyncConnectionManager.setDefaultMaxPerRoute(((Integer) value).intValue());
		}

		options.put(option, value);
	}

	public static Object getOption(Option option) {
		return options.get(option);
	}


	public static void refresh() {
		// Load timeouts
		int connectionTimeout = CONNECTION_TIMEOUT;
		if (Options.getOption(Option.CONNECTION_TIMEOUT) != null) {
			connectionTimeout = ((Long) Options.getOption(Option.CONNECTION_TIMEOUT)).intValue();
		}
		int socketTimeout = SOCKET_TIMEOUT;
		if (Options.getOption(Option.SOCKET_TIMEOUT) != null) {
			socketTimeout = ((Long) Options.getOption(Option.SOCKET_TIMEOUT)).intValue();
		}

		// Load proxy if set
		HttpHost proxy = (HttpHost) Options.getOption(Option.PROXY);

		RequestConfig clientConfig = RequestConfig.custom().setConnectTimeout(connectionTimeout).setSocketTimeout(socketTimeout).setConnectionRequestTimeout(socketTimeout).setProxy(proxy).build();

		setOption(Option.HTTPCLIENT, HttpClientBuilder.create().setDefaultRequestConfig(clientConfig).setConnectionManager(syncConnectionManager).build());
		setOption(Option.ASYNCHTTPCLIENT, HttpAsyncClientBuilder.create().setDefaultRequestConfig(clientConfig).setConnectionManager(asyncConnectionManager).build());
	}

	public static CloseableHttpClient getCloseableHttpClient(String requestTimeouts) {

		if (requestTimeouts == null) {
			return (CloseableHttpClient) Options.getOption(Option.HTTPCLIENT);
		}

		if (HTTP_CLIENT_INSTANCES.containsKey(requestTimeouts)) {
			return HTTP_CLIENT_INSTANCES.get(requestTimeouts);
		} else {
			String[] timeouts = requestTimeouts.split("-");
			int connectionTimeout = Integer.parseInt(timeouts[0]);
			int socketTimeout = Integer.parseInt(timeouts[1]);

			// Load proxy if set
			HttpHost proxy = (HttpHost) Options.getOption(Option.PROXY);

			//create a new HttpClient based on timeouts
			RequestConfig clientConfig = RequestConfig.custom().setConnectTimeout(connectionTimeout).setSocketTimeout(socketTimeout).setConnectionRequestTimeout(socketTimeout).setProxy(proxy).build();
			CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(clientConfig).setConnectionManager(syncConnectionManager).build();
			HTTP_CLIENT_INSTANCES.put(requestTimeouts, httpClient);

			return httpClient;
		}
	}

	public static CloseableHttpAsyncClient getCloseableHttpAsyncClient(String requestTimeouts) {

		if (requestTimeouts == null) {
			return (CloseableHttpAsyncClient) Options.getOption(Option.ASYNCHTTPCLIENT);
		}

		if (HTTP_CLIENT_INSTANCES.containsKey(requestTimeouts)) {
			return HTTP_ASYNC_CLIENT_INSTANCES.get(requestTimeouts);
		} else {
			String[] timeouts = requestTimeouts.split("-");
			int connectionTimeout = Integer.parseInt(timeouts[0]);
			int socketTimeout = Integer.parseInt(timeouts[1]);

			// Load proxy if set
			HttpHost proxy = (HttpHost) Options.getOption(Option.PROXY);

			//create a new HttpClient based on timeouts
			RequestConfig clientConfig = RequestConfig.custom().setConnectTimeout(connectionTimeout).setSocketTimeout(socketTimeout).setConnectionRequestTimeout(socketTimeout).setProxy(proxy).build();
			CloseableHttpAsyncClient httpAsyncClient = HttpAsyncClientBuilder.create().setDefaultRequestConfig(clientConfig).setConnectionManager(asyncConnectionManager).build();
			HTTP_ASYNC_CLIENT_INSTANCES.put(requestTimeouts, httpAsyncClient);

			return httpAsyncClient;
		}
	}

	public static void shutdown() throws IOException {
		// Closing the Sync HTTP client
		CloseableHttpClient syncClient = (CloseableHttpClient) Options.getOption(Option.HTTPCLIENT);
		if (syncClient != null) {
			syncClient.close();
		}

		for (Map.Entry<String,CloseableHttpClient> entry : HTTP_CLIENT_INSTANCES.entrySet()){
			entry.getValue().close();
		}

		SyncIdleConnectionMonitorThread syncIdleConnectionMonitorThread = (SyncIdleConnectionMonitorThread) Options.getOption(Option.SYNC_MONITOR);
		if (syncIdleConnectionMonitorThread != null) {
			syncIdleConnectionMonitorThread.interrupt();
		}

		// Closing the Async HTTP client (if running)
		CloseableHttpAsyncClient asyncClient = (CloseableHttpAsyncClient) Options.getOption(Option.ASYNCHTTPCLIENT);
		if (asyncClient != null && asyncClient.isRunning()) {
			asyncClient.close();
		}

		for (Map.Entry<String,CloseableHttpAsyncClient> entry : HTTP_ASYNC_CLIENT_INSTANCES.entrySet()){
			entry.getValue().close();
		}

		AsyncIdleConnectionMonitorThread asyncMonitorThread = (AsyncIdleConnectionMonitorThread) Options.getOption(Option.ASYNC_MONITOR);
		if (asyncMonitorThread != null) {
			asyncMonitorThread.interrupt();
		}
	}
}

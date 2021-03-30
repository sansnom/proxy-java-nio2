package fr.proxy;

import java.nio.channels.AsynchronousSocketChannel;

public class SocketInfo {

	private final String clientIP;
	private AsynchronousSocketChannel client;
	private volatile AsynchronousSocketChannel remote;
	private String hostname;
	private int port;
	private volatile boolean remoteReadDone;

	public SocketInfo(String clientIP) {
		this.clientIP = clientIP;
	}

	public AsynchronousSocketChannel getClient() {
		return client;
	}

	public void setClient(AsynchronousSocketChannel client) {
		this.client = client;
	}

	public String getClientIP() {
		return clientIP;
	}

	public AsynchronousSocketChannel getRemote() {
		return remote;
	}

	public void setRemote(AsynchronousSocketChannel remote) {
		this.remote = remote;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	@Override
	public String toString() {
		return "SocketInfo{" +
			"clientIP='" + clientIP + '\'' +
			", hostname='" + hostname + '\'' +
			", port=" + port +
			'}';
	}

	public void remoteReadDone() {
		this.remoteReadDone = true;
	}

	public boolean isRemoteReadDone() {
		return remoteReadDone;
	}
}

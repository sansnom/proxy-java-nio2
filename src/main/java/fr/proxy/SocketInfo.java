package fr.proxy;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;

public class SocketInfo {

	private InetSocketAddress clientAddress;
	private AsynchronousSocketChannel client;
	private volatile AsynchronousSocketChannel remote;
	private String hostname;
	private int port;
	private volatile boolean remoteReadDone;
	private boolean tunnel;

	public InetSocketAddress getClientAddress() {
		return clientAddress;
	}

	public void setClientAddress(InetSocketAddress clientAddress) {
		this.clientAddress = clientAddress;
	}

	public AsynchronousSocketChannel getClient() {
		return client;
	}

	public void setClient(AsynchronousSocketChannel client) {
		this.client = client;
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

	public void remoteReadDone() {
		this.remoteReadDone = true;
	}

	public boolean isRemoteReadDone() {
		return remoteReadDone;
	}

	public void setTunnel(boolean tunnel) {
		this.tunnel = tunnel;
	}

	public boolean isTunnel() {
		return tunnel;
	}

	public void setDestination(Destination destination) {
		this.hostname = destination.getHostName();
		this.port = destination.getPort();
	}

	@Override
	public String toString() {
		return "SocketInfo{" +
			"clientAddress='" + clientAddress + '\'' +
			", hostname='" + hostname + '\'' +
			", port=" + port +
			'}';
	}
}

package fr.proxy;

/**
 * Since 01/04/2021
 *
 * @author sbrocard
 */
public class Destination {

	private String hostName;

	private int port;

	public Destination(String hostName, int port) {
		this.hostName = hostName;
		this.port = port;
	}

	public String getHostName() {
		return hostName;
	}

	public int getPort() {
		return port;
	}

	@Override
	public String toString() {
		return "Destination{" +
			"hostName='" + hostName + '\'' +
			", port=" + port +
			'}';
	}
}

package sir.sukhov.smovefile.targets;

public abstract class Target {
    private String host;
    private int port;
    private String user;
    private String identity;
    private String knownHosts;
    private String path;

    public Target(String host, int port, String user, String identity, String knownHosts, String path) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.identity = identity;
        this.knownHosts = knownHosts;
        this.path = path;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUser() {
        return user;
    }

    public String getIdentity() {
        return identity;
    }

    public String getKnownHosts() {
        return knownHosts;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "Target{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", user='" + user + '\'' +
                ", identity='" + identity + '\'' +
                ", knownHosts='" + knownHosts + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}

package sir.sukhov.smovefile.targets;

public class Destination extends Target {
    public Destination(String host, int port, String user, String identity, String knownHosts, String path) {
        super(host, port, user, identity, knownHosts, path);
    }

    @Override
    public String toString() {
        return "Destination={" +
                super.toString() +
                '}';
    }
}

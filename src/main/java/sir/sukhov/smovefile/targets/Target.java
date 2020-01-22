/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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

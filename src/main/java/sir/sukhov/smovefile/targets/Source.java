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

import java.util.List;

public class Source extends Target {

    private List<String> templates;

    public Source(String host, int port, String user, String identity, String knownHosts, String path, List<String> templates) {
        super(host, port, user, identity, knownHosts, path);
        this.templates = templates;
    }

    public List<String> getTemplates() {
        return templates;
    }

    @Override
    public String toString() {
        return "Source={" +
                super.toString() + ", " +
                "templates=" + templates +
                '}';
    }
}

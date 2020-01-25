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

package sir.sukhov.smovefile;

import com.jcraft.jsch.*;
import sir.sukhov.smovefile.targets.Destination;
import sir.sukhov.smovefile.targets.Source;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class Flow implements Runnable {
    private Source source;
    private Destination destination;
    private int executorNumber;
    private Map<String, Future> tasks = new HashMap<>();
    private Map<String, Candidate> candidates = new HashMap<>();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Flow.class);
    private int bandwidth;
    private final AtomicLong bytesPerSecondLimit;
    private ExecutorService moveService;

    public Flow(Source source, Destination destination, int executorNumber, int bandwidth, ExecutorService moveService) {
        this.source = source;
        this.destination = destination;
        this.executorNumber =  executorNumber;
        this.bandwidth = bandwidth;
        this.bytesPerSecondLimit = new AtomicLong(bandwidth * 125000);
        this.moveService = moveService;
    }

    @Override
    public void run() {

        Session session     = null;
        Channel channel     = null;

        try {

            JSch jsch = new JSch();
            jsch.addIdentity(source.getIdentity());
            jsch.setKnownHosts(source.getKnownHosts());
            session = jsch.getSession(source.getUser(), source.getHost(), source.getPort());
            session.connect();

            channel = session.openChannel("sftp");
            channel.connect();
            ChannelSftp channelSftp = (ChannelSftp) channel;
            Vector fileList;
            while (!Thread.currentThread().isInterrupted()) {
                Candidate candidate;
                fileList = channelSftp.ls(source.getPath());
                for (Object o : fileList) {
                    ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) o;
                    if (!((entry.getFilename()).endsWith(".part")) && !(entry.getFilename().startsWith(".")) && (entry.getAttrs().isReg())) {
                        for (String template : source.getTemplates()) {
                            if ((entry.getFilename()).matches(template)) {
                                if (candidates.containsKey(entry.getFilename()) && (!tasks.containsKey(entry.getFilename()))) {
                                    candidate = candidates.get(entry.getFilename());
                                    if (candidate.getScore() >= 3) {
                                        // Adding new task
                                        log.info(String.format("Adding new move task for %s:%d:%s/%s",
                                                source.getHost(), source.getPort(), source.getPath(), entry.getFilename()));
                                        bytesPerSecondLimit.set(bandwidth * 125000 / Integer.min(executorNumber,tasks.size() + 1));
                                        tasks.put(entry.getFilename(), moveService.submit(new Move(source, destination, entry.getFilename(), bytesPerSecondLimit)
                                        ));

                                    } else {
                                        if ((candidate.getSize() == entry.getAttrs().getSize()) && (candidate.getMtime() == entry.getAttrs().getMTime())) {
                                            log.debug(String.format("No changes during one poll in size and mtime of %s:%d:%s/%s",
                                                    source.getHost(), source.getPort(), source.getPath(), entry.getFilename()));
                                            candidate.incScore();
                                        } else {
                                            log.debug(String.format("mtime/size changes is observed, move is delayed for %s:%d:%s/%s",
                                                    source.getHost(), source.getPort(), source.getPath(), entry.getFilename()));
                                            candidate.clearScore(entry.getAttrs().getSize(), entry.getAttrs().getMTime());
                                        }
                                    }
                                } else if (!candidates.containsKey(entry.getFilename())) {
                                    log.debug(String.format("Adding new move candidate for %s:%d:%s/%s",
                                            source.getHost(), source.getPort(), source.getPath(), entry.getFilename()));
                                    candidates.put(entry.getFilename(), new Candidate(entry.getAttrs().getSize(), entry.getAttrs().getMTime()));
                                }
                                break;
                            }
                        }
                    }
                }
                Thread.sleep(1000);
                for (Iterator<Map.Entry<String, Future>> it = tasks.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, Future> entry = it.next();
                    if (entry.getValue().isDone()) {
                        log.info("Move task status for file " +  entry.getKey() + " isDone");
                        bytesPerSecondLimit.set(bandwidth * 125000 / Integer.min(executorNumber,Integer.max(tasks.size() - 1,1)));
                        it.remove();
                        candidates.remove(entry.getKey());
                    }
                }
            }
        } catch (JSchException | SftpException e) {
            log.error("Error" , e);
        } catch (InterruptedException e) {
            log.debug("Flow thread is interrupted", e);
        } catch (RuntimeException e) {
            log.error("Runtime exception", e);
        }
        finally {
            if(session != null) session.disconnect();
            if(channel != null) channel.disconnect();
        }
    }

}

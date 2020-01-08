package sir.sukhov.smovefile;

import com.jcraft.jsch.*;
import sir.sukhov.smovefile.targets.Destination;
import sir.sukhov.smovefile.targets.Source;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Flow implements Runnable {
    private Source source;
    private Destination destination;
    private int executorNumber;
    private Map<String, Future> tasks = new HashMap<>();
    private static final Logger logger = Logger.getLogger(Flow.class.getName());

    public Flow(Source source, Destination destination, int executorNumber) {
        this.source = source;
        this.destination = destination;
        this.executorNumber =  executorNumber;
    }

    @Override
    public void run() {

        Session session     = null;
        Channel channel     = null;

        try {
            ExecutorService moveService = Executors.newFixedThreadPool(executorNumber);

            JSch jsch = new JSch();
            jsch.addIdentity(source.getIdentity());
            jsch.setKnownHosts(source.getKnownHosts());
            session = jsch.getSession(source.getUser(), source.getHost(), source.getPort());
            session.connect();

            channel = session.openChannel("sftp");
            channel.connect();
            ChannelSftp channelSftp = (ChannelSftp) channel;
            StringBuilder sb = new StringBuilder();
            Vector fileList = null;
            while (!Thread.currentThread().isInterrupted()) {
                sb.setLength(0);
                fileList = channelSftp.ls(source.getPath());
                sb.append("Directory listing for host ").
                        append(source.getHost()).
                        append(" path ").
                        append(source.getPath()).
                        append("\n\t");
                for (int i = 0; i < fileList.size(); i++) {
                    ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) fileList.get(i);
                    sb.append(entry.getFilename());
                    if (!((entry.getFilename()).endsWith(".part")) && !(entry.getFilename().startsWith(".")) && (entry.getAttrs().isReg())) {
                        for (String template : source.getTemplates()) {
                            if ((entry.getFilename()).matches(template)) {
                                if (tasks.containsKey(entry.getFilename())) {
                                    // Checking move file status
                                    if (tasks.get(entry.getFilename()).isDone()) {
                                        sb.append(" <-- file movement is DONE");
                                    }
                                    else {
                                        sb.append(" <-- file movement status is not done");
                                    }
                                }
                                else {
                                    // Adding new task
                                    logger.log(Level.INFO, "Adding new move task for " + entry.getFilename());
                                    tasks.put(entry.getFilename(), moveService.submit(
                                            new Move(source, destination, entry.getFilename())
                                    ));
                                    sb.append(" <-- file movement task is added");
                                }
                                break;
                            }
                        }
                    }
                    sb.append("\n\t");
                }
                logger.log(Level.FINER, sb.toString());
                Thread.sleep(1000);
                for (Iterator<Map.Entry<String, Future>> it = tasks.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, Future> entry = it.next();
                    if (entry.getValue().isDone()) {
                        logger.log(Level.INFO, "Task status for file " +  entry.getKey() + " isDone");
                        it.remove();
                    }
                }
            }
        } catch (JSchException | SftpException e) {
            logger.log(Level.SEVERE, "Error" , e);
        } catch (InterruptedException e) {
            logger.log(Level.INFO, "Thread is interrupted", e);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Runtime exception", e);
        }
        finally {
            System.out.println("Closing all resources");
            if(session != null) session.disconnect();
            if(channel != null) channel.disconnect();
        }
    }

}

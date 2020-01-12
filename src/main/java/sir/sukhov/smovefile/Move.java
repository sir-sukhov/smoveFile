package sir.sukhov.smovefile;

import com.jcraft.jsch.*;
import sir.sukhov.smovefile.targets.Destination;
import sir.sukhov.smovefile.targets.Source;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Move implements Runnable {

    private static Logger logger = Logger.getLogger(Move.class.getName());
    private Source source;
    private Destination destination;
    private String fileName;
    private final AtomicLong bytesPerSecondLimit;

    Move(Source source, Destination destination, String fileName, AtomicLong bytesPerSecondLimit) {
        this.source = source;
        this.destination = destination;
        this.fileName = fileName;
        this.bytesPerSecondLimit = bytesPerSecondLimit;
    }

    @Override
    public void run() {
        JSch sourceJSch = new JSch();
        JSch destinationJSch = new JSch();
        Session sourceSession = null;
        Session destinationSession = null;
        Channel sourceChannel = null;
        Channel destinationChannel = null;
        InputStream sourceIS;
        OutputStream sourceOS;
        InputStream destIS;
        OutputStream destOS;
        String sourceFilePath = source.getPath() + "/" + fileName;
        String destFilePath = destination.getPath() + "/" + fileName;

        try {
            sourceJSch.addIdentity(source.getIdentity());
            sourceJSch.setKnownHosts(source.getKnownHosts());
            sourceSession = sourceJSch.getSession(source.getUser(), source.getHost(), source.getPort());
            destinationJSch.addIdentity(destination.getIdentity());
            destinationJSch.setKnownHosts(destination.getKnownHosts());
            destinationSession = destinationJSch.getSession(destination.getUser(), destination.getHost(), destination.getPort());

            sourceSession.connect();
            destinationSession.connect();

            // exec 'scp -f sourceFilePath' remotely
            sourceChannel = sourceSession.openChannel("exec");
            ((ChannelExec)sourceChannel).setCommand("scp -f '" + sourceFilePath.replace("'", "'\"'\"'") + "'");
            // exec 'scp -t destFilePath' remotely
            destinationChannel = destinationSession.openChannel("exec");
            ((ChannelExec)destinationChannel).setCommand("scp -t '" + destFilePath.replace("'", "'\"'\"'") + ".part'");

            // get I/O streams
            sourceIS = sourceChannel.getInputStream();
            sourceOS = sourceChannel.getOutputStream();
            destIS = destinationChannel.getInputStream();
            destOS = destinationChannel.getOutputStream();

            sourceChannel.connect();
            destinationChannel.connect();
            byte[] buf=new byte[1024];

            // send '\0' to source Output Stream
            buf[0]=0; sourceOS.write(buf, 0, 1); sourceOS.flush();
            // get '\0' from dest Input Stream
            if(checkAck(destIS)!=0){
                logger.log(Level.SEVERE, "Destination Input Stream ACK is not 0");
                throw new RuntimeException("Destination Input Stream ACK is not 0");
            }


            // forward 'C0644 filesize filename'
            long fileSize;
            for(int i = 0;; i++){
                sourceIS.read(buf, i, 1);
                if(buf[i]==(byte)0x0a){
                    destOS.write(buf,0,i+1); destOS.flush();
                    byte[] attrs = new byte[i+1];
                    System.arraycopy(buf,0,attrs,0,i+1);
                    fileSize = Long.parseLong((new String(attrs)).split(" ")[1]);
                    break;
                }
            }
            // send '\0' to source Output Stream
            buf[0]=0; sourceOS.write(buf, 0, 1); sourceOS.flush();
            // get '\0' from dest Output Stream
            if(checkAck(destIS)!=0){
                logger.log(Level.SEVERE, "Destination Input Stream ACK is not 0");
                throw new RuntimeException("Destination Input Stream ACK is not 0");
            }

            // forward content
            int foo;
            long counter = 0;
            long timestamp = System.currentTimeMillis();
            long now;
            while(true){
                if(buf.length < fileSize) foo=buf.length;
                else foo=(int) fileSize;
                foo = sourceIS.read(buf, 0, foo);
                if(foo<0){
                    // error
                    logger.log(Level.SEVERE, "Got lt 0 from reading source input stream");
                    throw new RuntimeException("Got lt 0 from reading source input stream");
                }
                destOS.write(buf,0, foo);
                counter += foo;
                fileSize -=foo;
                if (fileSize ==0L) break;
                if (counter > bytesPerSecondLimit.get()) {
                    now = System.currentTimeMillis();
                    if (timestamp + 1000 > now) {
                        Thread.sleep(timestamp + 1000 - now);
                    }
                    timestamp = System.currentTimeMillis();
                    counter = 0;
                }
            }
            if(checkAck(sourceIS)!=0){
                logger.log(Level.SEVERE, "Source Input Stream ACK is not 0");
                throw new RuntimeException("Source Input Stream ACK is not 0");
            }

            // send '\0'
            buf[0]=0;
            sourceOS.write(buf, 0, 1); sourceOS.flush();
            destOS.write(buf, 0, 1); destOS.flush();
            if(checkAck(destIS)!=0){
                logger.log(Level.SEVERE, "Destination Input Stream ACK is not 0");
                throw new RuntimeException("Destination Input Stream ACK is not 0");
            }

            destinationChannel.disconnect();
            sourceChannel.disconnect();

            destinationChannel = destinationSession.openChannel("sftp");
            destinationChannel.connect();
            ChannelSftp destChannelSftp = (ChannelSftp) destinationChannel;
            destChannelSftp.rename(destFilePath + ".part", destFilePath);

            sourceChannel = sourceSession.openChannel("sftp");
            sourceChannel.connect();
            ChannelSftp sourceChannelSftp = (ChannelSftp) sourceChannel;
            sourceChannelSftp.rm(sourceFilePath);

        } catch (JSchException e) {
            logger.log(Level.SEVERE, "JSch Exception:", e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "IO Exception:", e);
        } catch (SftpException e) {
            logger.log(Level.SEVERE, "JSch SFTP Exception:", e);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Interrupted:", e);
        } finally {
            if (sourceChannel != null) sourceChannel.disconnect();
            if (destinationChannel != null) destinationChannel.disconnect();
            if (sourceSession != null) sourceSession.disconnect();
            if (destinationSession !=  null) destinationSession.disconnect();
        }

    }

    private static int checkAck(InputStream in) throws IOException {
        int b=in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if(b==0) return b;
        if(b==-1) return b;

        if(b==1 || b==2){
            StringBuilder sb=new StringBuilder();
            int c;
            do {
                c=in.read();
                sb.append((char)c);
            }
            while(c!='\n');
            if(b==1){ // error
                logger.log(Level.SEVERE, sb.toString());
            }
            if(b==2){ // fatal error
                logger.log(Level.SEVERE, sb.toString());
            }
        }
        return b;
    }

}

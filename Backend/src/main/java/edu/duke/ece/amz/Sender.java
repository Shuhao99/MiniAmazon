package edu.duke.ece.amz;

import com.google.protobuf.Message;
import edu.duke.ece.amz.proto.WorldAmazon;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public abstract class Sender {
    protected final Map<Long, Timer> schedulerMap;
    protected final OutputStream out;

    public Sender(Socket socket, Map<Long, Timer> schedulerMap) throws IOException {
        this.out = socket.getOutputStream();
        this.schedulerMap = schedulerMap;
    }

    // One command only contains one command with one seqNum
    public void sendCmd(Message.Builder msg, long seqNum) throws IOException {

        if (msg.getDescriptorForType() == WorldAmazon.ACommands.getDescriptor()) {
            WorldAmazon.ACommands.Builder commands = (WorldAmazon.ACommands.Builder) msg;
            commands.setSimspeed(100);
            System.out.println("amazon sending (to world): " + seqNum);
        }
        else {
            System.out.println("amazon sending (to ups): " + seqNum);
        }
        msg.build().writeDelimitedTo(out);

        // Use timer to schedule the resend
        // Retry until acked
        Timer timer = new Timer();
        long RESEND_TIMEOUT = 3000;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    System.out.println("amazon resending: " + seqNum);
                    msg.build().writeDelimitedTo(out);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }, 0, RESEND_TIMEOUT);

        schedulerMap.put(seqNum, timer);
    }

    public void sendACK(Message.Builder commands) throws IOException {

        if (commands.getDescriptorForType() == WorldAmazon.ACommands.getDescriptor()) {
            WorldAmazon.ACommands.Builder msg = (WorldAmazon.ACommands.Builder) commands;
            msg.setSimspeed(100);
            System.out.println("amazon tell world acked.");
        }
        else {
            System.out.println("Ups tell world acked.");
        }
        commands.build().writeDelimitedTo(out);

    }
}

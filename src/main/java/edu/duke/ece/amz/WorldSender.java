package edu.duke.ece.amz;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import edu.duke.ece.amz.proto.WorldAmazon.*;

public class WorldSender {
    //TODO:seqNum
    //TODO:Schedule_list
    private final Map<Long, Timer> schedulerMap;
    private final OutputStream worldOut;

    // TODO: ADD a hashmap to maintain all the unacked msg


    public WorldSender (Socket socket, Map<Long, Timer> schedulerMap) throws IOException {
        this.worldOut = socket.getOutputStream();
        this.schedulerMap = schedulerMap;
    }

    public OutputStream getWorldOut() {
        return worldOut;
    }

    // One Acommands only contains one command with one seqNum
    void sendToWorld(ACommands.Builder commands, long seqNum) throws IOException {

        commands.setSimspeed(100);
        System.out.println("amazon sending(to world): " + seqNum);
        commands.build().writeDelimitedTo(worldOut);

        // Use timer to schedule the resend
        // Retry until acked
        Timer timer = new Timer();
        long RESEND_TIMEOUT = 1000;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    System.out.println("amazon sending(to world): " + seqNum);
                    commands.build().writeDelimitedTo(worldOut);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }, 0, RESEND_TIMEOUT);

        schedulerMap.put(seqNum, timer);
    }
}
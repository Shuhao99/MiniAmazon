package edu.duke.ece.amz;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class WorldSender extends Sender{

    // TODO: ADD a hashmap to maintain all the unacked msg
    public WorldSender (Socket socket, Map<Long, Timer> schedulerMap) throws IOException {
        super(socket, schedulerMap);
    }

    public OutputStream getWorldOut() {
        return out;
    }

}
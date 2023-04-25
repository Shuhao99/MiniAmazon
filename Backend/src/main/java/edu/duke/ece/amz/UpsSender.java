package edu.duke.ece.amz;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Timer;

public class UpsSender extends Sender{

    public UpsSender (Socket socket, Map<Long, Timer> schedulerMap) throws IOException {
        super(socket, schedulerMap);
    }

}

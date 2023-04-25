package edu.duke.ece.amz;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import edu.duke.ece.amz.proto.WorldAmazon.*;

//Make it as daemon thread
public class BackendListener extends Listener {

    private final WorldSender worldSender;
    private final Map<Long, Package> packageMap;
    private final Database mydb;
    private final AtomicLong seqNum;

    BackendListener(int portNum, WorldSender worldSender,
                    Map<Long, Package> packageMap, Database mydb,
                    AtomicLong seqNum
    ) {
        super(portNum);
        this.setDaemon(true);
        this.worldSender = worldSender;
        this.packageMap = packageMap;
        this.mydb = mydb;
        this.seqNum = seqNum;
    }

    @Override
    public void run(){
        while (!Thread.currentThread().isInterrupted()){
            Socket fe_socket = this.accept();
            if (fe_socket != null){
                executorService.submit(()->{
                    try {
                        handlePurchase(fe_socket);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    void handlePurchase(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter writer = new PrintWriter(socket.getOutputStream());
        String req = reader.readLine();
        System.out.println("new buying request: " + req);

        long pkId = Long.parseLong(req);

        // send ack
        writer.write(String.format("ack:%d", pkId));
        writer.flush();
        socket.close();

        Package pkg = mydb.getPackage(pkId);
        //build purchase more command
        long curSeqNum = this.seqNum.getAndIncrement();
        APurchaseMore newPurchase = APurchaseMore.newBuilder().
                setWhnum(pkg.getWhID()).
                addAllThings(pkg.getProducts()).
                setSeqnum(curSeqNum).build();

        // Build new command
        ACommands.Builder newCommand = ACommands.newBuilder().
                addBuy(newPurchase);

        //sendToWorld
        worldSender.sendCmd(newCommand, curSeqNum);
        System.out.println("Tell warehouse to purchase: " + pkId);

        // Add package to tracking map
        packageMap.put(pkId, pkg);
    }
}

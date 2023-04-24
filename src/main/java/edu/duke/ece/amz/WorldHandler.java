package edu.duke.ece.amz;
import edu.duke.ece.amz.proto.WorldAmazon.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicLong;

// client side only use one socket, require socket in, socket out, package ID to build a thread
public class WorldHandler implements Runnable {
    private final AResponses msg;
    private final Map<Long, Timer> schedulerMap;
    private final Map<Long, Package> packageMap;
    private final AtomicLong seqNum;
    private final WorldSender worldSender;
    private final Database mydb;


    public WorldHandler(
            AResponses msg, Map<Long, Timer> schedulerMap,
            Map<Long, Package> packageMap,
            AtomicLong seqNum,
            WorldSender worldSender,
            Database mydb
    ){
        this.schedulerMap = schedulerMap;
        this.packageMap = packageMap;
        this.msg = msg;
        this.seqNum = seqNum;
        this.worldSender = worldSender;
        this.mydb = mydb;
    }

    @Override
    public void run(){

        ArrayList<Long> seqNumList = new ArrayList<>();

        for (APurchaseMore purchaseMore : msg.getArrivedList()){
            seqNumList.add(purchaseMore.getSeqnum());
            for(Package p : packageMap.values()){
                if(p.getWhID() != purchaseMore.getWhnum()){
                    continue;
                }
                if (p.getProducts() != purchaseMore.getThingsList()){
                    continue;
                }
                // If this response is already processed, skip
                if (p.getStatus().equals(Package.PACKING)){
                    break;
                }

                System.out.println("Package arrived: " + p.getId());
                // Tell Warehouse to Pack
                try {
                    pack(p.getId());
                } catch (IOException e) {
                    System.err.println(e.toString());
                }

                // Tell UPS to Pick
                break;
            }
        }
        // packed package ---> to load
        for (APacked p : msg.getReadyList()){
            System.out.println(p.getShipid() + ": Packed");

        }
        // loaded package ---> to delivery
        for (ALoaded l : msg.getLoadedList()){
            System.out.println(l.getShipid() + ": Loaded");
        }
        // error message
        for (AErr err : msg.getErrorList()){
            System.err.println(err.getErr());
        }

        for (long ack : msg.getAcksList()){
            // Remove acked requests from tracker
            System.out.println("World acked: " + msg.getAcksList());
            if (schedulerMap.containsKey(ack)){
                schedulerMap.get(ack).cancel();
                schedulerMap.remove(ack);
            }
        }

        if(seqNumList.size() > 0){
            try {
                sendACK(seqNumList);
            } catch (IOException e) {
                System.err.println(e.toString());
            }
        }

    }

    public void pack(long pkId) throws IOException {
        // send to world
        Package pkg = packageMap.get(pkId);
        long curSeqNum = seqNum.getAndIncrement();
        APack packCmd = APack.newBuilder().
                setWhnum(pkg.getWhID()).
                setSeqnum(curSeqNum).
                addAllThings(pkg.getProducts()).
                setShipid(pkId).build();
        ACommands.Builder cmd = ACommands.newBuilder().addTopack(packCmd);
        worldSender.sendToWorld(cmd, curSeqNum);
        System.out.println("Tell warehouse to pack: " + pkId);
        //TODO: tell ups to pick up

        // update package status in list
        packageMap.get(pkId).setStatus(Package.PACKING);
        // update package status in db
        mydb.updateStatus(pkId, Package.PACKING);

    }

    public void sendACK(ArrayList<Long> seqNumList) throws IOException {
        ACommands.Builder cmd = ACommands.newBuilder().addAllAcks(seqNumList);
        worldSender.sendToWorld(cmd, seqNum.getAndIncrement());
    }
}

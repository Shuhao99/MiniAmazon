package edu.duke.ece.amz;

import edu.duke.ece.amz.proto.AmzUps.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicLong;

public class UpsHandler extends Handler implements Runnable {
    private final UACommand msg;

    public UpsHandler (
            UACommand msg, Map<Long, Timer> schedulerMap,
            Map<Long, Package> packageMap,
            AtomicLong seqNum,
            Database mydb,
            UpsSender upsSender,
            WorldSender worldSender
    ) throws IOException
    {
        super(schedulerMap, packageMap, seqNum, mydb, upsSender, worldSender);
        this.msg = msg;
    }

    @Override
    public void run(){
        ArrayList<Long> seqNumList = new ArrayList<>();

        // Truck arrived, tell warehouse to load
        for (UALoadRequest req : msg.getLoadRequestsList()){
            seqNumList.add(req.getSeqNum());
            if (! packageMap.containsKey(req.getShipId())){
                continue;
            }
            Package pkg = packageMap.get(req.getShipId());

            // Skip duplicate package
            if (pkg.getTruckID() != -1){
                continue;
            }
            System.out.println(req.getShipId() + ": Truck Arrived." + req.getSeqNum());
            pkg.setTruckID(req.getTruckId());

            // Check if package is packed
            if(pkg.getStatus().equals(Package.PACKED)){
                // tell world to load
                try {
                    load(req.getShipId());
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }

        }

        // Package Delivered update package status, remove package from package list
        for (UADelivered d : msg.getDeliveredList()){
            seqNumList.add(d.getSeqNum());
            if (packageMap.containsKey(d.getShipId())){
                System.out.println(d.getShipId() + ": Delivered");
                mydb.updateStatus(d.getShipId(), Package.DELIVERED);
                packageMap.remove(d.getShipId());
            }
        }

        // Tell UPS acked
        System.out.println("UPS acked: " + msg.getAcksList());
        for (long ack : msg.getAcksList()){
            // Remove acked requests from tracker
            if (schedulerMap.containsKey(ack)){
                schedulerMap.get(ack).cancel();
                schedulerMap.remove(ack);
            }
        }
        if(seqNumList.size() > 0){
            try {
                AUCommand.Builder cmd = AUCommand.newBuilder().addAllAcks(seqNumList);
                upsSender.sendACK(cmd);
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }

    }
}

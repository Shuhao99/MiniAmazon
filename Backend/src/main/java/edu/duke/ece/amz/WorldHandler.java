package edu.duke.ece.amz;
import edu.duke.ece.amz.proto.AmzUps.*;
import edu.duke.ece.amz.proto.WorldAmazon.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicLong;

// client side only use one socket, require socket in, socket out, package ID to build a thread
public class WorldHandler extends Handler implements Runnable {
    private final AResponses msg;
    private final boolean mockUPS;
    private final MockUPS ups;


    public WorldHandler(
            AResponses msg, Map<Long, Timer> schedulerMap,
            Map<Long, Package> packageMap,
            AtomicLong seqNum,
            WorldSender worldSender,
            Database mydb,
            UpsSender upsSender,
            boolean mockUPS,
            MockUPS ups
    ){
        super(schedulerMap, packageMap, seqNum, mydb, upsSender, worldSender);
        this.msg = msg;
        this.mockUPS = mockUPS;
        this.ups = ups;
    }

    @Override
    public void run(){

        ArrayList<Long> seqNumList = new ArrayList<>();

        // Order purchased tell World to pack and tell Ups to pick
        for (APurchaseMore purchaseMore : msg.getArrivedList()){
            seqNumList.add(purchaseMore.getSeqnum());
            for(Package p : packageMap.values()){
                if(p.getWhID() != purchaseMore.getWhnum()){
                    continue;
                }
                if (! p.getProducts().equals(purchaseMore.getThingsList())){
                    continue;
                }
                // If this response is already processed, skip
                if (!p.getStatus().equals(Package.PROCESSING)){
                    break;
                }

                System.out.println("Package arrived warehouse: " + p.getId());

                try {
                    // Tell Warehouse to Pack
                    pack(p.getId());
                    // Tell UPS to Pick
                    upsPickUp(p.getId());
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }

                break;
            }
        }

        // Package packed, Tell world to load
        for (APacked p : msg.getReadyList()){
            System.out.println(p.getShipid() + ": Packed");
            seqNumList.add(p.getSeqnum());
            if (!packageMap.containsKey(p.getShipid())){
                continue;
            }
            Package pkg = packageMap.get(p.getShipid());

            // Check if it is duplicated
            if (!pkg.getStatus().equals(Package.PACKING)){
                continue;
            }

            // Update package status
            pkg.setStatus(Package.PACKED);
            mydb.updateStatus(p.getShipid(), Package.PACKED);
            // Check if Truck arrived
            if (pkg.getTruckID() != -1){
                try {
                    load(p.getShipid());
                } catch (IOException e) {
                    System.err.println("Load Error: " + e.getMessage());
                }
            }
        }

        // Package Loaded, tell Ups to deliver
        for (ALoaded l : msg.getLoadedList()){

            seqNumList.add(l.getSeqnum());
            if (!packageMap.containsKey(l.getShipid())){
                continue;
            }
            Package pkg = packageMap.get(l.getShipid());
            if (!pkg.getStatus().equals(Package.LOADING)){
                continue;
            }
            System.out.println(l.getShipid() + ": Loaded");
            try {
                upsDeliver(l.getShipid());
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }

        // error message
        for (AErr err : msg.getErrorList()){
            System.err.println(err.getErr());
        }

        // Tell world acked
        System.out.println("World acked: " + msg.getAcksList());
        for (long ack : msg.getAcksList()){
            // Remove acked requests from tracker
            if (schedulerMap.containsKey(ack)){
                schedulerMap.get(ack).cancel();
                schedulerMap.remove(ack);
            }
        }

        if(seqNumList.size() > 0){
            try {
                ACommands.Builder cmd = ACommands.newBuilder().addAllAcks(seqNumList);
                worldSender.sendACK(cmd);
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }

    }

    public void pack(long pkId) throws IOException {
        // send to world
        // update package status in list
        packageMap.get(pkId).setStatus(Package.PACKING);
        // update package status in db
        mydb.updateStatus(pkId, Package.PACKING);

        Package pkg = packageMap.get(pkId);
        long curSeqNum = seqNum.getAndIncrement();
        APack packCmd = APack.newBuilder().
                setWhnum(pkg.getWhID()).
                setSeqnum(curSeqNum).
                addAllThings(pkg.getProducts()).
                setShipid(pkId).build();
        ACommands.Builder cmd = ACommands.newBuilder().addTopack(packCmd);
        worldSender.sendCmd(cmd, curSeqNum);
        System.out.println("Tell warehouse to pack: " + pkId);

    }

    public void upsPickUp(long pkId) throws IOException {
        Package pkg = packageMap.get(pkId);

        long curSeqNum = seqNum.getAndIncrement();

        if (mockUPS){
            this.ups.pick(pkg.getWhID(), pkId);
        }

        String upsName = mydb.getUpsName(pkId);

        String word = "Cao ni ma ya";

        AUPickupRequest pickCmd = AUPickupRequest.newBuilder().
                setSeqNum(curSeqNum).
                setShipId(pkId).
                setWarehouseId(pkg.getWhID()).
                setX(pkg.getWhX()).
                setY(pkg.getWhY()).
                setDestinationX(pkg.getDesX()).
                setDestinationY(pkg.getDesY()).
                setUpsName(upsName).
                setItems(word).
                build();
        AUCommand.Builder cmd = AUCommand.newBuilder().addPickupRequests(pickCmd);
        System.out.println("Tell Ups to pick: " + cmd);
        upsSender.sendCmd(cmd, curSeqNum);

    }

    public void upsDeliver(long pkId) throws IOException {
        System.out.println("Tell Ups to deliver: " + pkId);
        Package pkg = packageMap.get(pkId);

        long curSeqNum = seqNum.getAndIncrement();
        pkg.setStatus(Package.DELIVERING);
        mydb.updateStatus(pkg.getId(), Package.DELIVERING);

        // If mock ups
        if (mockUPS){
            try {
                System.out.println("In try: " + pkId);
                ups.delivery(pkg.getDesX(), pkg.getDesY(), pkg.getId());
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
        else {
            AUDeliverRequest deliverCmd = AUDeliverRequest.newBuilder().
                    setSeqNum(curSeqNum).
                    setShipId(pkId).
                    build();

            AUCommand.Builder cmd = AUCommand.newBuilder().addDeliverRequests(deliverCmd);
            upsSender.sendCmd(cmd, curSeqNum);
        }
    }
}

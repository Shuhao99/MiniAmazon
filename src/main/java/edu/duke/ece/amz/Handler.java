package edu.duke.ece.amz;

import edu.duke.ece.amz.proto.WorldAmazon;

import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Handler {

    protected final Map<Long, Timer> schedulerMap;
    protected final Map<Long, Package> packageMap;
    protected final AtomicLong seqNum;
    protected final UpsSender upsSender;
    protected final WorldSender worldSender;
    protected final Database mydb;

    public Handler(
            Map<Long, Timer> schedulerMap,
            Map<Long, Package> packageMap,
            AtomicLong seqNum,
            Database mydb,
            UpsSender upsSender,
            WorldSender worldSender
    ){
        this.schedulerMap = schedulerMap;
        this.packageMap = packageMap;
        this.seqNum = seqNum;
        this.mydb = mydb;
        this.upsSender = upsSender;
        this.worldSender = worldSender;
    }

    public void load(long pkId) throws IOException {
        // send to world
        Package pkg = packageMap.get(pkId);
        long curSeqNum = seqNum.getAndIncrement();

        WorldAmazon.APutOnTruck loadCmd = WorldAmazon.APutOnTruck.newBuilder().
                setWhnum(pkg.getWhID()).
                setSeqnum(curSeqNum).
                setTruckid(pkg.getTruckID()).
                setShipid(pkId).build();

        WorldAmazon.ACommands.Builder cmd = WorldAmazon.ACommands.newBuilder().addLoad(loadCmd);
        worldSender.sendCmd(cmd, curSeqNum);
        System.out.println("Tell warehouse to load: " + pkId);

        // update package status in list
        packageMap.get(pkId).setStatus(Package.LOADING);
        // update package status in db
        mydb.updateStatus(pkId, Package.LOADING);
    }

}

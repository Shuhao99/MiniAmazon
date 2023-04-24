package edu.duke.ece.amz;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import edu.duke.ece.amz.proto.WorldAmazon.*;
import edu.duke.ece.amz.proto.AmzUps.*;


public class Server {
    private static final String WORLD_HOST = "vcm-32288.vm.duke.edu";
    private static final int WORLD_PORT = 23456;

    private static final String UPS_HOST = "vcm-32288.vm.duke.edu";
    private static final int LSN_UPS_ON = 7777;
    private static final int SEND_UPS_ON = 6666;

    private static final int BACKEND_PORT = 8888;


    private final AtomicLong seqNum;
    private final ExecutorService executorService;
    private final WorldSender worldSender;
    private final InputStream worldIn;

    private final Map<Long, Package> packageMap;
    // mapping between sequence number and request(the timer handle the re-send task)
    private final Map<Long, Timer> schedulerMap;
    // all warehouses
//    private final List<AInitWarehouse> warehouseList;

    private final Database mydb;



    public Server() throws IOException{
        this.seqNum = new AtomicLong(0);

        this.packageMap = new ConcurrentHashMap<>();
        this.schedulerMap = new ConcurrentHashMap<>();
        //this.warehouseList = new Database().getWhs();

        executorService = Executors.newFixedThreadPool(50);
        Socket worldSocket = new Socket(WORLD_HOST, WORLD_PORT);
        worldSender = new WorldSender(worldSocket, this.schedulerMap);
        worldIn = worldSocket.getInputStream();
        mydb = new Database();

        connectWorld();


        //TODO: Create thread to purchase all items we have
    }

    // Connect to world
    public void connectWorld() throws IOException {
        System.out.println("Connecting to world...");

        // TODO: receive worldId from UPS

        AConnect connect = AConnect.newBuilder()
                .setIsAmazon(true)
                .addAllInitwh(mydb.getWhList())
                .build();

        connect.writeDelimitedTo(worldSender.getWorldOut());

        AConnected resp = AConnected.parser().parseDelimitedFrom(worldIn);

        System.out.println("world id: " + resp.getWorldid());
        System.out.println("result: " + resp.getResult());
    }

    public void run() {
        // Start listening response from the world
        executorService.submit(()->{
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    // Keep listen response from world in.
                    AResponses msg = AResponses.parser().parseDelimitedFrom(worldIn);
                    System.out.println("New response from world: ");
                    executorService.submit(new WorldHandler(msg, schedulerMap, packageMap, seqNum, worldSender, mydb));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // Start listening response from the Frontend
        BackendDaemon beThread = new BackendDaemon(BACKEND_PORT, worldSender, packageMap, mydb, seqNum);
        beThread.start();


        // Start listening response from the UPS

    }

    public static void main(String[] args) throws IOException {
        // try to connect to world
        Server server = new Server();
        server.run();
    }
}

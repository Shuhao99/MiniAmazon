package edu.duke.ece.amz;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import edu.duke.ece.amz.proto.AmzUps;
import edu.duke.ece.amz.proto.WorldAmazon.*;
import edu.duke.ece.amz.proto.AmzUps.*;

import static java.lang.System.exit;


public class Server {
    private static final String WORLD_HOST = "vcm-32288.vm.duke.edu";
    private static final int WORLD_PORT = 23456;

    private static final int LSN_UPS_ON = 7777;
//    private static final int SEND_UPS_ON = 6666;
    private static final int BACKEND_PORT = 8888;

    private final AtomicLong seqNum;
    private final ExecutorService executorService;

    private final WorldSender worldSender;
    private final InputStream worldIn;

    private final UpsSender upsSender;
    private final InputStream upsIn;

    private final Map<Long, Package> packageMap;
    // mapping between sequence number and request(the timer handle the re-send task)
    private final Map<Long, Timer> schedulerMap;
    private final MockUPS UPS;
    // all warehouses
//    private final List<AInitWarehouse> warehouseList;

    private final Database mydb;
    private final boolean mockUps = true;



    public Server() throws IOException{
        this.seqNum = new AtomicLong(0);

        this.packageMap = new ConcurrentHashMap<>();
        this.schedulerMap = new ConcurrentHashMap<>();
        //this.warehouseList = new Database().getWhs();

        executorService = Executors.newFixedThreadPool(50);

        Socket worldSocket = new Socket(WORLD_HOST, WORLD_PORT);
        worldSender = new WorldSender(worldSocket, this.schedulerMap);
        worldIn = worldSocket.getInputStream();
        Listener upsListener = new Listener(LSN_UPS_ON);

        UPS = new MockUPS();
        mydb = new Database();

        if(mockUps){
            UPS.init();
        }

        Socket upsSocket = upsListener.accept();
        upsIn = upsSocket.getInputStream();
        upsSender = new UpsSender(upsSocket, schedulerMap);

        try{
            connectWorld();
        }
        catch (Exception e){
            System.err.println("Run world simulator first");
            exit(1);
        }
    }

    // Connect to world
    public void connectWorld() throws IOException {
        System.out.println("Connecting to world...");

        long worldId;
        AmzUps.UAstart start = AmzUps.UAstart.parser().parseDelimitedFrom(upsIn);
        worldId = start.getWorldid();

        AConnect connect = AConnect.newBuilder()
                .setIsAmazon(true)
                .setWorldid(worldId)
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
                    System.out.println("New response from world: " + msg.toString());
                    executorService.submit(new WorldHandler(msg, schedulerMap, packageMap, seqNum, worldSender, mydb,upsSender, mockUps, UPS));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // Start listening response from the Frontend
        BackendListener beThread = new BackendListener(BACKEND_PORT, worldSender, packageMap, mydb, seqNum);
        beThread.start();


        // Start listening response from the UPS
        // Start listening response from the world
        executorService.submit(()->{
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    // Keep listen response from world in.
                    UACommand msg = UACommand.parser().parseDelimitedFrom(upsIn);
                    System.out.println("New response from UPS: " + msg.toString());
                    executorService.submit(new UpsHandler(msg, schedulerMap, packageMap, seqNum, mydb, upsSender, worldSender));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    public static void main(String[] args) throws IOException {
        // try to connect to world
        Server server = new Server();
        server.run();
    }
}

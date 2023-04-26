package edu.duke.ece.amz;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import edu.duke.ece.amz.proto.AmzUps;
import edu.duke.ece.amz.proto.WorldUps.*;

/**
 * This class is mainly for testing purpose, mock a simple UPS.
 */
public class MockUPS {

    private static final String HOST = "vcm-32288.vm.duke.edu";
    private static final int PORT = 12345;
    private static final int UPS_SERVER_PORT = 7777;

    public long worldID;
    public int truckID;

    private final InputStream in;
    private final OutputStream out;
    private long seqNum;
    private final Socket ups_socket;
    private final InputStream ups_in;
    private final OutputStream ups_out;

    public MockUPS() throws IOException {
        worldID = 0;
        seqNum = 0;
        truckID = 1;
        Socket socket = new Socket(HOST, PORT);
        in = socket.getInputStream();
        out = socket.getOutputStream();

        ups_socket = new Socket("vcm-32288.vm.duke.edu", UPS_SERVER_PORT);
        ups_in = ups_socket.getInputStream();
        ups_out = ups_socket.getOutputStream();
    }

    public void init(){
        new Thread(() -> {
            // 1. create a new world
            try {
                connectToWorld();
            } catch (IOException e) {
                System.err.println("connect to world: " + e.toString());
            }
            // 2. connect to amazon and tell it the result
            try {

                AmzUps.UAstart.newBuilder().setWorldid((int) worldID).setSeqnum(seqNum).build().writeDelimitedTo(ups_socket.getOutputStream());

            }catch (Exception e){
                System.err.println("ups init: " + e.toString());
            }
        }).start();
    }

    public void connectToWorld() throws IOException {
        // init two trucks
        int a  = 1;
        UInitTruck.Builder builder = UInitTruck.newBuilder();
        builder.setId(a);
        builder.setX(a);
        builder.setY(a);

        int b = 2;
        UInitTruck.Builder builder1 = UInitTruck.newBuilder();
        builder1.setId(b);
        builder1.setX(b);
        builder1.setY(b);

        int c = 3;
        UInitTruck.Builder builder2 = UInitTruck.newBuilder();
        builder2.setId(c);
        builder2.setX(c);
        builder2.setY(c);

        UConnect.Builder connect =  UConnect.newBuilder();
        connect.setIsAmazon(false);
        connect.addTrucks(builder.build());
        connect.addTrucks(builder1.build());
        connect.addTrucks(builder2.build());
//        if (worldID >= 0){
//            connect.setWorldid(worldID);
//        }
        System.out.println("UPS try to connect to World...");
        connect.build().writeDelimitedTo(out);

        seqNum++;
        UConnected connected = UConnected.parser().parseDelimitedFrom(in);

        this.worldID = (int) connected.getWorldid();
        System.out.println("world id: " + connected.getWorldid());
        System.out.println("result: " + connected.getResult());

    }

    public void pick(int whID, long packageID) throws IOException {
        UCommands.Builder command = UCommands.newBuilder();

        UGoPickup.Builder pick = UGoPickup.newBuilder();
        pick.setWhid(whID);
        pick.setTruckid(packageID % 2 == 0 ? 1 : 2);
        pick.setSeqnum(seqNum);
        command.addPickups(pick);

        command.build().writeDelimitedTo(out);
        seqNum++;
        UResponses responses = UResponses.parser().parseDelimitedFrom(in);

        System.out.println(responses.toString());

        if (responses.getCompletionsCount() == 0){

            synchronized (in) {
                responses = UResponses.parser().parseDelimitedFrom(in);
            }
            System.out.println(responses.toString());
        }

        List<Long> seqs = new ArrayList<>();
        for (UFinished finished : responses.getCompletionsList()){
            seqs.add(finished.getSeqnum());
        }
        sendAck(seqs);

        try{
            // Socket socket = new Socket("localhost", UPS_SERVER_PORT);
            AmzUps.UALoadRequest.Builder picked = AmzUps.UALoadRequest.newBuilder();

            picked.setSeqNum(seqNum);
            picked.setShipId(packageID);
            picked.setTruckId(packageID % 2 == 0 ? 1 : 2);

            AmzUps.UACommand.Builder c = AmzUps.UACommand.newBuilder();
            c.addLoadRequests(picked);


            c.build().writeDelimitedTo(ups_out);

            AmzUps.UACommand r = AmzUps.UACommand.parser().parseDelimitedFrom(ups_in);

            if(r.getAcks(0) == seqNum){
                seqNum++;
                System.out.println("ups pick receive correct ack");
            }
        }catch (IOException e){
            System.err.println("ups pick:" + e.toString());
        }
    }

    public void delivery(int destX, int destY, long packageID) throws IOException {

        UCommands.Builder command = UCommands.newBuilder();

        UGoDeliver.Builder delivery = UGoDeliver.newBuilder();
        delivery.setTruckid(packageID % 2 == 0 ? 1 : 2);
        delivery.addPackages(UDeliveryLocation.newBuilder().setPackageid(packageID).setX(destX).setY(destY));
        delivery.setSeqnum(seqNum);
        command.addDeliveries(delivery);
        command.setSimspeed(100);
        System.out.println("UPS tell world to deliver");
        command.build().writeDelimitedTo(out);
        seqNum++;
        UResponses responses = UResponses.parser().parseDelimitedFrom(in);
        System.out.println("UPS deliver get new response: ");
        System.out.println(responses.toString());

        if (responses.getDeliveredCount() == 0){

            responses = UResponses.parser().parseDelimitedFrom(in);
            System.out.println(responses.toString());
        }

        List<Long> seqs = new ArrayList<>();
        for (UDeliveryMade d : responses.getDeliveredList()){
            seqs.add(d.getSeqnum());
        }
        sendAck(seqs);

        try{

            AmzUps.UADelivered.Builder delivered = AmzUps.UADelivered.newBuilder();

            delivered.setSeqNum(seqNum);
            delivered.setShipId(packageID);

            AmzUps.UACommand.Builder c = AmzUps.UACommand.newBuilder();
            c.addDelivered(delivered);
            c.build().writeDelimitedTo(ups_out);


            AmzUps.AUCommand r = AmzUps.AUCommand.parser().parseDelimitedFrom(ups_in);

            if(r.getAcks(0) == seqNum){
                seqNum++;
                System.out.println("ups delivery receive correct ack");
            }
        }catch (IOException e){
            System.err.println("ups delivery:" + e.toString());
        }
    }

    void sendAck(List<Long> seqs) throws IOException {
        UCommands.Builder commands = UCommands.newBuilder();
        for (long seq : seqs){
            commands.addAcks(seq);
        }
        commands.build().writeDelimitedTo(out);
    }
}

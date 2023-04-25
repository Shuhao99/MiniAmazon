package edu.duke.ece.amz;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Listener extends Thread{
    ServerSocket serverSocket;
    protected final ExecutorService executorService;

    public Listener(int portNum){
        try {
            this.serverSocket = new ServerSocket(portNum);
        } catch (IOException e){
            e.printStackTrace();
        }
        executorService = Executors.newFixedThreadPool(10);
    }

    public Socket accept(){
        try {
            return serverSocket.accept();
        } catch (IOException e){
            return  null;
        }
    }

}

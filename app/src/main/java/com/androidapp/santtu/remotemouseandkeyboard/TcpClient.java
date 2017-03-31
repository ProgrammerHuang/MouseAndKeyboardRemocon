package com.androidapp.santtu.remotemouseandkeyboard;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Handles the TCP connection between the app and the server.
 */

public class TcpClient implements Runnable{
    private String serverIp;
    //private InetAddress serverAddr;
    private Socket socket;
    private PrintWriter out;
    private Context context;
    private Handler myHandler;
    private ProgressBar progressBar;
    private MenuItem connectAction;
    private volatile String outGoingMessage;

    private volatile boolean mRun;   //while this is true, server continues to run

    final Lock lock = new ReentrantLock();
    final Condition notEmpty = lock.newCondition();

    /**
     * Constructor for TcpClient.
     * @param ip the server's ip
     * @param con the context of activity which starts this object
     * @param handler handles messaging between threads
     * @param bar progress bar that "spins" while attempting to start connection
     */
    public TcpClient(String ip, Context con, Handler handler, ProgressBar bar)
    {
        serverIp=ip;
        context = con;
        myHandler = handler;
        outGoingMessage = "";
        progressBar = bar;
    }

    /**
     * Saves an out going message which will be sent to the server
     * @param message is the message to be sent
     */
    public void SendMessage(String message)
    {
        lock.lock();
        try
        {
            outGoingMessage = message;
            notEmpty.signalAll();
        }finally{lock.unlock();}

    }

    /**
     * Handles stopping the connection thread.
     */
    public void StopClient()
    {
        lock.lock();
        try
        {
            mRun = false;
            SendMessage("exit");

            notEmpty.signalAll();
        }finally{lock.unlock();}
    }

    @Override
    public void run()
    {
        mRun = true;

        //init connection
        try {
            InetAddress serverAddr = InetAddress.getByName(serverIp);
            socket = new Socket(serverAddr, Constants.SERVER_PORT); //open socket on server IP and port

            //connection was made -> let the user know
            myHandler.post(new Runnable(){
                public void run() {
                    progressBar.setVisibility(View.GONE);
                    SingleToast.show(context, "Connected!", Toast.LENGTH_LONG);
                    connectAction.setIcon(R.drawable.ic_cast_connected_white_24dp);
                }
            });

            try {

                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket
                        .getOutputStream())), true); //create output stream to send data to server

                while (mRun)    // the message loop that handles sending messages to the server
                {
                    lock.lock();
                    try {
                        while(outGoingMessage == "")
                        {
                            try {
                                notEmpty.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        if (out != null && outGoingMessage != null) {
                            out.println(outGoingMessage);
                            if (out.checkError())
                            {
                                //System.out.println("ERROR writing data to socket !!!");
                                myHandler.post(new Runnable(){
                                    public void run() {
                                        SingleToast.show(context, "Connection lost!", Toast.LENGTH_LONG);
                                        connectAction.setIcon(R.drawable.ic_cast_white_24dp);
                                    }
                                });
                                mRun = false;
                            }
                            outGoingMessage = "";
                        }
                    }finally{lock.unlock();}
                }

                //out.println("exit"); //tell server to close this connection
                socket.close();
                out = null;
                serverIp = null;

            } catch (IOException e) {
                Log.e("remotedroid", "Error while creating OutWriter", e);
                myHandler.post(new Runnable(){
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                        SingleToast.show(context, "Connection failed.", Toast.LENGTH_LONG);
                        connectAction.setIcon(R.drawable.ic_cast_white_24dp);
                    }
                });
            }

        } catch (IOException e) {
            Log.e("remotedroid", "Error while connecting", e);
            myHandler.post(new Runnable(){
                public void run() {
                    progressBar.setVisibility(View.GONE);
                    SingleToast.show(context, "Connection failed.", Toast.LENGTH_LONG);
                    connectAction.setIcon(R.drawable.ic_cast_white_24dp);
                }
            });
        }
    }

    public void SetConnectAction(MenuItem item)
    {
     connectAction = item;
    }
}

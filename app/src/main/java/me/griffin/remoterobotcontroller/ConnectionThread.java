package me.griffin.remoterobotcontroller;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import me.griffinbeck.server.BackLoadedCommandPacket;
import me.griffinbeck.server.ClientConnectionManager;
import me.griffinbeck.server.ClientSocketConnector;
import me.griffinbeck.server.CommandPacket;
import me.griffinbeck.server.cmdresponses.CommandArguments;
import me.griffinbeck.server.cmdresponses.Commands;

/**
 * Created by griffin on 2/22/2018.
 */

public class ConnectionThread extends Thread {
    private String ip;
    private int port;
    private Socket socket;
    private ClientSocketConnector clientSocketConnector;
    private ClientConnectionManager clientConnectionManager;
    private boolean doRun = false;
    private MainActivity mainActivity;
    private DataOutputStream outputStream;
    private DataInputStream inputStream;

    public ConnectionThread(String ip, int port, MainActivity mainActivity) {
        this.mainActivity = mainActivity;
        this.ip = ip;
        this.port = port;
    }


    public boolean connect() {
        if (ip == null || port < 1024) {
            return false;
        } else {
            this.start();
            return true;
        }
    }

    public void run() {
        try {
            clientSocketConnector = new ClientSocketConnector(ip, port, false);
            socket = clientSocketConnector.connectToServer();
            if (socket == null) {
                throw new Exception("Failed to Connect to Server");
            }
            doRun = true;
            CurrentCommandHolder.setConnected(true);
            clientConnectionManager = new ClientConnectionManager(socket, clientSocketConnector);
            handleLinkingNegotiation();
            CurrentCommandHolder.setAllowControls(true);
            mainActivity.runOnUiThread(new Runnable() {
                public void run() {
                    mainActivity.connectionSucess();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            mainActivity.runOnUiThread(new Runnable() {
                public void run() {
                    mainActivity.notConnected();
                }
            });
            CurrentCommandHolder.setConnected(false);
        }
        CommandPacket toSendPacket = null;
        CommandPacket toReadPacket = null;
        while (doRun) {
            try {
                //TODO add input stream for handling incoming messages
                //Log.i("Connection", "Sending");
                //Writer out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF8"));
                //outputStream = new DataOutputStream(socket.getOutputStream());
                Log.i("Connection", "Hold Loop ran");
                toSendPacket = CurrentCommandHolder.takeCommand();
                if (toSendPacket != null) {
                    String toSend = toSendPacket.getPacket();
                    if (toSend != null) {
                        Log.i("ConnectionThread", "sent " + toSend);
                        clientConnectionManager.sendPacket(toSendPacket);
                        //outputStream.writeUTF(toSend);
                        //outputStream.flush();
                        //socket.getOutputStream().flush();
                    }
                }
                Log.i("Connection", "Hold Loop ran 2");
                toReadPacket = clientConnectionManager.getPacket(false);
                if (toReadPacket != null) {
                    Log.i("Connection", "Received Packet: " + toReadPacket.getPacket());
                    handleCommandIn(toReadPacket);
                }
                Log.i("Connection", "Hold Loop ran 3 END");
            } catch (IOException e) {
                Log.i("Connection", e.toString());
                mainActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        mainActivity.hideNetworkConnectedIcon();
                    }
                });
                Log.i("Connection", "Attempting to Reconnect");
                if (tryRecconnect()) {
                    try {
                        Log.i("Connection", "Recconnection Sucessful, attempting to link");
                        handleLinkingNegotiation();
                        mainActivity.runOnUiThread(new Runnable() {
                            public void run() {
                                mainActivity.showNetworkConnectedIcon();
                            }
                        });
                    } catch (IOException e1) {
                        doRun = false;
                        CurrentCommandHolder.setConnected(false);
                        mainActivity.runOnUiThread(new Runnable() {
                            public void run() {
                                mainActivity.notConnected();
                            }
                        });
                    }
                } else {
                    Log.i("Connection", "Recconnection Failed");
                    doRun = false;
                    CurrentCommandHolder.setConnected(false);
                    clientConnectionManager.closeConnection();
                    mainActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            mainActivity.notConnected();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                doRun = false;
                CurrentCommandHolder.setConnected(false);
                mainActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        mainActivity.notConnected();
                    }
                });
            }
        }
    }

    private boolean tryRecconnect() {
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mainActivity, "Recconnecting To Server", Toast.LENGTH_LONG).show();
            }
        });

        long time = System.currentTimeMillis();
        ConnectivityManager cm =
                (ConnectivityManager) mainActivity.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork;
        boolean isConnected = false;
        while ((System.currentTimeMillis() - 60000) < time) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (cm != null) {
                activeNetwork = cm.getActiveNetworkInfo();
                isConnected = activeNetwork != null && activeNetwork.isConnected();
            }
            if (isConnected) {
                if (clientConnectionManager.tryRecconnect())
                    return true;
            }
        }

        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mainActivity, "Reconnect Failed", Toast.LENGTH_LONG).show();
            }
        });
        return false;

    }

    private void handleCommandIn(CommandPacket packet) throws IOException {
        if (Commands.REQUEST.equalTo(packet.getCmd())) {

        } else if (Commands.PAUSE.equalTo(packet.getCmd())) {
            if (CommandArguments.PAUSE_PAUSECONNECTION.equals(packet.getArg(0))) {
                CurrentCommandHolder.setAllowControls(false);
                holdPause();
                CurrentCommandHolder.setAllowControls(true);
            }
        } else if (Commands.BACKLOADED_PACKET.equalTo(packet.getCmd())) {
            if (CommandArguments.BACKLOADED_PACKET_IMG.equals(packet.getArg(1))) {
                CurrentCommandHolder.sendIMGFrame(((BackLoadedCommandPacket) packet).getBackLoad());
            }
        }

    }

    /**
     * Will handle incoming packets until the unpause packet is recieved at which time it will end the method
     */
    public void holdPause() throws IOException {
        CommandPacket inPacket = null;
        boolean pause = true;
        while (clientConnectionManager.isConnected() && pause) {
            inPacket = clientConnectionManager.getPacket(true);
            if (Commands.PAUSE.equalTo(inPacket.getCmd())) {
                if (CommandArguments.PAUSE_UNPAUSECONNECTION.equals(inPacket.getArg(0))) {
                    handleLinkingNegotiationPostPause();
                    /*if (CommandArguments.PAUSE_ESTABLISHLINK.equals(inPacket.getArg(1))) {
                        pause = false;
                    } else {
                        pause = false;
                    }*/
                    pause = false;
                }
            } else if (Commands.HEARTBEAT.equalTo(inPacket.getCmd())) {
                clientConnectionManager.sendPacket(new CommandPacket(Commands.HEARTBEAT));
            }
        }
    }

    private void handleLinkingNegotiation() throws IOException {
        boolean hold = true;
        CommandPacket receiving = clientConnectionManager.getPacket(true);
        Log.i("Connection", "received " + receiving.getPacket() + "");
        if (Commands.PAUSE.equalTo(receiving.getCmd())) {
            if (CommandArguments.PAUSE_PAUSECONNECTION.equals(receiving.getArgs()[0])) {
                while (hold) {
                    receiving = clientConnectionManager.getPacket(true);
                    if (Commands.PAUSE.equalTo(receiving.getCmd())) {
                        if (CommandArguments.PAUSE_UNPAUSECONNECTION.equals(receiving.getArgs()[0])) {
                            hold = false;
                            clientConnectionManager.sendPacket(new CommandPacket(Commands.LINK, CommandArguments.LINK_REQUESTLINK));
                            Log.i("Connection", "Sent Link Request Packet");
                        }
                    }
                }
                hold = true;
                while (hold) {
                    receiving = clientConnectionManager.getPacket(true);
                    if (Commands.LINK.equalTo(receiving.getCmd())) {
                        if (CommandArguments.LINK_LINKOPENED.equals(receiving.getArgs()[0])) {
                            Log.i("Connection", "Received Link Open");
                            hold = false;
                        }
                    } else if (Commands.PAUSE.equalTo(receiving.getCmd())) {
                        if (CommandArguments.PAUSE_PAUSECONNECTION.equals(receiving.getArg(0))) {
                            //TODO HANDLE PAUSE
                        }
                    }
                }
            }
        } else if (Commands.LINK.equalTo(receiving.getCmd())) {
            if (CommandArguments.LINK_PREPARE.equals(receiving.getArg(0))) {
                Log.i("Connection", "Link Prepare Started");
                while (hold) {
                    receiving = clientConnectionManager.getPacket(true);
                    if (Commands.LINK.equalTo(receiving.getCmd())) {
                        if (CommandArguments.LINK_REQUESTLINK.equals(receiving.getArgs()[0])) {
                            clientConnectionManager.sendPacket(new CommandPacket(Commands.LINK, CommandArguments.LINK_LINKOPENED));
                            hold = false;
                        }
                    } else if (Commands.PAUSE.equalTo(receiving.getCmd())) {
                        if (receiving.getArg(0) != null && CommandArguments.PAUSE_PAUSECONNECTION.equals(receiving.getArg(0))) {
                            //TODO HANDLE PAUSE
                        }
                    }
                }
            }
        }
        if (hold)
            handleLinkingNegotiation();
        Log.i("Connection", "Linking Negotiation finished, now exiting linking negotiation");
    }

    public void handleLinkingNegotiationPostPause() throws IOException {
        boolean hold = true;
        clientConnectionManager.sendPacket(new CommandPacket(Commands.LINK, CommandArguments.LINK_REQUESTLINK));
        CommandPacket receiving;
        hold = true;
        while (hold) {
            receiving = clientConnectionManager.getPacket(true);
            if (Commands.LINK.equalTo(receiving.getCmd())) {
                if (CommandArguments.LINK_LINKOPENED.equals(receiving.getArgs()[0])) {
                    hold = false;
                }
            } else if (Commands.HEARTBEAT.equalTo(receiving.getCmd())) {
                clientConnectionManager.sendPacket(new CommandPacket(Commands.HEARTBEAT));
            } else if (Commands.PAUSE.equalTo(receiving.getCmd())) {
                if (CommandArguments.PAUSE_PAUSECONNECTION.equals(receiving.getArg(0))) {
                    hold = false;
                    holdPause();
                }
            }
        }

        if (hold)
            handleLinkingNegotiationPostPause();
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isRunning() {
        return doRun;
    }

    public void end() {
        doRun = false;
    }
}

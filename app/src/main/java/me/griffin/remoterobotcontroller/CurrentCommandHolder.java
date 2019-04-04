package me.griffin.remoterobotcontroller;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.griffinbeck.server.CommandPacket;

/**
 * Created by griffin on 2/20/2018.
 */

public class CurrentCommandHolder {
    public static boolean connectionStatus;
    public static boolean connectionAcceptingControls = false;
    /**
     * TODO: Make getting of data thread safe
     */
    private static double speed;
    private static double turn;
    private static int speedInt;
    private static int turnInt;
    private static String ip;
    private static String port;
    private static List<CommandPacket> cmds;
    private static List<String> commandResponse;
    private static Handler cameraViewHandler;
    private static BitmapFactory.Options bitmap_options = new BitmapFactory.Options();

    static {
        cmds = new ArrayList<>();
        commandResponse = new ArrayList<>();
        connectionStatus = false;
        bitmap_options.inPreferredConfig = Bitmap.Config.RGB_565;
    }

    public static void addCommand(CommandPacket packet) {
        cmds.add(packet);
    }

    public static void addCommand(String cmd, double... args) {
        if (connectionStatus && connectionAcceptingControls) {
            String toSend = cmd;
            if (Objects.equals(cmd, "d")) {
                if (args.length > 1) {
                    speed = args[1];
                    turn = args[0];
                    if (speed > 1)
                        speed = 1;
                    if (speed < -1)
                        speed = -1;
                    if (turn > 1)
                        turn = 1;
                    if (turn < -1)
                        turn = -1;
                    speedInt = (int) (speed * 127);
                    turnInt = (int) ((turn / Math.abs(turn)) * Math.pow(turn, 2) * 127);
                    if (speedInt > 127)
                        speedInt = 127;
                    if (speedInt < -127)
                        speedInt = -127;
                    if (turnInt > 127)
                        turnInt = 127;
                    if (turnInt < -127)
                        turnInt = -127;
                    turnInt = turnInt / 2;

                    if (cmds.size() > 30) {
                        cmds.clear();
                    }
                    cmds.add(new CommandPacket("d", speedInt + "", turnInt + ""));

                } else {
                    Log.e("Command Data Holder", "Invalid arguments for drive command");
                }
            } else {
                String[] argString = new String[args.length];
                for (int i = 0; i < args.length; i++) {
                    argString[i] = "" + args[i];
                }
                cmds.add(new CommandPacket(cmd, argString));
            }
        }
        /*for (double i : args) {
            cmd += "," + i;
        }*/


    }

    public static void addResponse(String response) {
        commandResponse.add(response);
    }

    public static boolean hasCmd() {
        return !cmds.isEmpty();
    }

    public static boolean hasResponse() {
        return !commandResponse.isEmpty();
    }

    public static CommandPacket takeCommand() {
        if (!cmds.isEmpty() && connectionAcceptingControls)
            return cmds.remove(0);
        return null;
    }

    public static String getResponse() {
        if (!commandResponse.isEmpty())
            return commandResponse.remove(0);
        return null;
    }

    public static String getIp() {
        return ip;
    }

    public static void setIp(String ip) {
        CurrentCommandHolder.ip = ip;
    }

    public static void setConnected(boolean isConnected) {
        connectionStatus = isConnected;
    }

    public static void setAllowControls(boolean allowControls) {
        connectionAcceptingControls = allowControls;
    }

    public static boolean isConnectionStatus() {
        return connectionStatus;
    }

    public static String getPort() {
        return port;
    }

    public static void setPort(String port) {
        CurrentCommandHolder.port = port;
    }

    public static Handler getCameraViewHandler() {
        return cameraViewHandler;
    }

    public static void setCameraViewHandler(Handler cameraViewHandler) {
        CurrentCommandHolder.cameraViewHandler = cameraViewHandler;
    }

    public static void sendIMGFrame(byte[] buffer) {
        Message m = cameraViewHandler.obtainMessage();
        m.obj = BitmapFactory.decodeByteArray(buffer, 0, buffer.length);
        if (m.obj != null) {
            cameraViewHandler.sendMessage(m);
        } else {
            System.out.println("Decode Failed");
        }
    }
}

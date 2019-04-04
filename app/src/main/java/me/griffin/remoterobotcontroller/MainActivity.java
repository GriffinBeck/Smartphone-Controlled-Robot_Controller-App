package me.griffin.remoterobotcontroller;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import me.griffin.remoterobotcontroller.Camera.CameraViewHandler;
import me.griffinbeck.server.CommandPacket;
import me.griffinbeck.server.cmdresponses.CommandArguments;
import me.griffinbeck.server.cmdresponses.Commands;

public class MainActivity extends AppCompatActivity implements JoystickView.JoystickListener {
    final private Handler handler = new Handler();
    private final Handler cameraHandler = new CameraViewHandler(this);
    public Bitmap mLastFrame;
    private Button connectButton;
    private ConnectionThread connectionThread;
    private float xPercent;
    private float yPercent;
    // Define the code block to be executed
    final private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            // Do something here on the main thread
            //Log.d("Handlers", "Called on main thread");
            // Repeat this the same runnable code block again another 2 seconds
            CurrentCommandHolder.addCommand("d", -xPercent, yPercent);
            //Log.d("Main Method", "X percent: " + xPercent + " Y percent: " + yPercent);

            handler.postDelayed(runnableCode, 300);
        }
    };
    private Menu menu;
    //Camera View Code
    private ImageView mCameraView;
    private Runnable cameraViewChecker = new Runnable() {
        @Override
        public void run() {
            try {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mLastFrame != null) {

                            //Bitmap mutableBitmap = mLastFrame.copy(Bitmap.Config.RGB_565, true);

                            mCameraView.setImageBitmap(mLastFrame);
                        }

                    }
                }); //this function can change value of mInterval.
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                handler.postDelayed(cameraViewChecker, 1000 / 15);
            }
        }
    };

    public static Bitmap rotateImage(Bitmap source, float angle) {
        if (source != null) {
            Bitmap retVal;

            Matrix matrix = new Matrix();
            matrix.postRotate(angle);
            retVal = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            source.recycle();
            return retVal;
        }
        return null;
    }
    //End Camera View Code

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Context context = this.getBaseContext();

        JoystickView joystick = new JoystickView(context);
        if (context == null) {
            Log.e("Joystick", "Context is null 2");
        }
        //connectButton = findViewById(R.id.connectButton);
        /*connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                handleConnectButton();
            }
        });*/
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.INTERNET}, 111);
        }
        //Camera View Code
        CurrentCommandHolder.setCameraViewHandler(cameraHandler);
        mCameraView = findViewById(R.id.camera_preview);
        cameraViewChecker.run();
        //End Camera View Code

// Start the initial runnable task by posting through the handler
        handler.post(runnableCode);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menubar, menu);
        this.menu = menu;
        hideNetworkConnectedIcon();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.connect_to_server:
                openConnectionDialog();
                return true;
            case R.id.open_camera:
                CurrentCommandHolder.addCommand(new CommandPacket(Commands.REQUEST, CommandArguments.REQUEST_IMG));
                return true;
            case R.id.autonmous_toggle:
                CurrentCommandHolder.addCommand(new CommandPacket(Commands.REQUEST, CommandArguments.REQUEST_AUTONOMOUS));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onJoystickMoved(float xPercent, float yPercent, int id) {
        //CurrentCommandHolder.addCommand("d", -xPercent,yPercent);
        this.xPercent = xPercent;
        this.yPercent = yPercent;
        //Log.d("Main Method", "X percent: " + xPercent + " Y percent: " + yPercent);
    }

    private void openConnectionDialog() {
        //Log.e("Alert", "Creating alert");
        final AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
        final AlertDialog dialog;
        alert.setTitle("Enter Connection Details for the Server");
        //LayoutInflater inflater = this.getLayoutInflater();
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setDividerPadding(10);
        //View dialogLayout = inflater.inflate(R.layout.connect_to_server, null);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        final EditText ipInput = new EditText(this);
        ipInput.setHint("IP Address Ex:(127.0.0.0)");
        ipInput.setFitsSystemWindows(true);
        final EditText portInput = new EditText(this);
        portInput.setHint("Port Ex:(3068)");
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setRawInputType(Configuration.KEYBOARD_12KEY);
        dialogLayout.addView(ipInput);
        dialogLayout.addView(portInput);
        alert.setView(dialogLayout);

        alert.setPositiveButton("Connect", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                final String ip = ipInput.getText().toString().trim();
                final String portString = portInput.getText().toString().trim();
                if (ip.length() == 0 || portString.length() == 0) {
                    alert.show();
                } else {
                    final int port = Integer.parseInt(portString);
                    connectionThread = new ConnectionThread(ip, port, MainActivity.this);
                    boolean didRun = connectionThread.connect();

                    if (didRun) ;
                    else
                        ((TextView) findViewById(R.id.connectionError)).setText("Failed To Connect");
                }
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //Put actions for CANCEL button here, or leave in blank
            }
        });
        dialog = alert.show();
        dialog.show();
        Log.i("Alert", "showing dialog");
    }

    public void notConnected() {
        ((TextView) findViewById(R.id.connectionError)).setText("Failed To Connect");
        hideNetworkConnectedIcon();
    }

    public void connectionSucess() {
        ((TextView) findViewById(R.id.connectionError)).setText("Connected");
        showNetworkConnectedIcon();
    }

    public void showNetworkConnectedIcon() {
        menu.findItem(R.id.network_connected_icon).setVisible(true);
    }

    public void hideNetworkConnectedIcon() {
        menu.findItem(R.id.network_connected_icon).setVisible(false);
    }

}

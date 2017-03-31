package com.androidapp.santtu.remotemouseandkeyboard;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.AdView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    Context context;            //this activity's context
    Button leftButton;          //left mouse button
    Button rightButton;         //right mouse button
    Button keyboardButton;      //button for opening keyboard
    Button scrollUpButton;      //mouse scroll up
    Button scrollDownButton;    //mouse scroll down
    Button backButton;          //back button for browsers etc
    Button forwardButton;       //forward for browsers etc
    TextView mousePad;          //the area which works as a mouse pad
    ProgressBar progressBar;    //spinning progress bar for connection waiting
    MenuItem connectAction;     //the button which handles starting connection
    Switch leftSwitch, rightSwitch; //switches which handle hiding left and right side buttons

    private boolean mouseMoved=false;

    private String serverIp;
    private TcpClient client;
    private Thread networkThread;

    private float initX =0;
    private float initY =0;
    private float disX =0;
    private float disY =0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //check if connection setup has been done and if not, start setup activity
        SharedPreferences settings = getSharedPreferences(Constants.PREFS_NAME, 0);
        boolean firstTimeSetup = settings.getBoolean("firstTimeSetupDone", false);
        boolean firstTimeConnectPopup = settings.getBoolean("ConnectPopupDone", false);
        serverIp = settings.getString("serverIp", null);
        if(!firstTimeSetup || serverIp == null)
        {
            //settings = getSharedPreferences(PREFS_NAME, 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("firstTimeSetupDone", false);

            editor.commit();
            Intent i = new Intent(MainActivity.this, SetupActivity.class);
            startActivity(i);
            finish();
        }

        //show how to connect popup if it hasn't been shown before
        if(!firstTimeConnectPopup)
            ShowConnectPopup();

        //init ads
        boolean test = true;
        InitAds(test);

        context = this; //save the context to show Toast messages

        progressBar = (ProgressBar)findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        //setup tcp object and the thread that handles it
        Handler handler = new Handler();
        client = new TcpClient(serverIp, this, handler, progressBar);
        networkThread = new Thread(client);

        //getting references of all buttons
        leftButton = (Button)findViewById(R.id.leftButton);
        rightButton = (Button)findViewById(R.id.rightButton);
        keyboardButton = (Button)findViewById(R.id.keyboardButton);
        scrollDownButton = (Button)findViewById(R.id.scrollDownButton);
        scrollUpButton = (Button)findViewById(R.id.scrollUpButton);
        backButton = (Button)findViewById(R.id.backButton);
        forwardButton = (Button)findViewById(R.id.forwardButton);

        //using a separate method to handle left and right switch init. Other components could be initialized this way as well.
        InitSwitches();

        //this activity extends View.OnClickListener, set this as onClickListener for buttons below
        leftButton.setOnClickListener(this);
        rightButton.setOnClickListener(this);
        keyboardButton.setOnClickListener(this);
        scrollDownButton.setOnClickListener(this);
        scrollUpButton.setOnClickListener(this);
        backButton.setOnClickListener(this);
        forwardButton.setOnClickListener(this);

        mousePad = (TextView)findViewById(R.id.mousePad);

        //capture finger taps and movement on the mousepad textview
        mousePad.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(networkThread.isAlive() && client!=null){
                    switch(event.getAction()){
                        case MotionEvent.ACTION_DOWN:
                            //save X and Y positions when user touches the TextView
                            initX =event.getX();
                            initY =event.getY();
                            mouseMoved=false;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            disX = event.getX()- initX; //mouse movement in x direction
                            disY = event.getY()- initY; //mouse movement in y direction
                            //set init to new position so that continuous mouse movement is captured
                            initX = event.getX();
                            initY = event.getY();
                            if(disX !=0|| disY !=0){
                                client.SendMessage(disX +","+ disY); //send mouse movement to server
                            }
                            mouseMoved=true;
                            break;
                        case MotionEvent.ACTION_UP:
                            //consider a tap only if user did not move mouse after ACTION_DOWN
                            if(!mouseMoved){
                                client.SendMessage(Constants.MOUSE_LEFT_CLICK); //send mouse left click to server
                            }
                    }
                }
                return true;
            }
        });

        //all the components have been initialized and the app is ready to be used -> let the user know
        if(serverIp != null)
            SingleToast.show(this, "Ready to connect!", Toast.LENGTH_LONG);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        connectAction = menu.findItem(R.id.action_connect);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        //if the user clicks settings, start settings activity and flush this activity
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SetupActivity.class);
            startActivity(intent);
            finish();
            return true;
        }

        //noinspection SimplifiableIfStatement
        //if user clicks connect button
        if(id == R.id.action_connect) {

            if(networkThread.isAlive()) //if connection has already been made, let the user know instead of starting new connection
            {
                SingleToast.show(context, "Connection underway!", Toast.LENGTH_LONG);
            }
            else    //if connection hasn't been made, start connection to the server
            {
                client.SetConnectAction(connectAction);
                networkThread = new Thread(client);
                networkThread.start();
                progressBar.setVisibility(View.VISIBLE);
                SingleToast.show(context, "Connecting...", Toast.LENGTH_LONG);
                //isConnecting = true;
            }

                return true;
            }

        return super.onOptionsItemSelected(item);
    }

    //OnClick method is called when any of the (non menu) buttons are pressed
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.leftButton:
                if (networkThread.isAlive() && client!=null) {
                    client.SendMessage(Constants.MOUSE_LEFT_CLICK);//send "left_click" to server
                }
                else
                    SingleToast.show(context, "Connect first!", Toast.LENGTH_LONG);
                break;
            case R.id.rightButton:
                if (networkThread.isAlive() && client!=null) {
                    client.SendMessage(Constants.MOUSE_RIGHT_CLICK); //send "right_click" to server
                }
                else
                    SingleToast.show(context, "Connect first!", Toast.LENGTH_LONG);
                break;
            case R.id.keyboardButton:
                if (networkThread.isAlive() && client!=null) {
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0); //open keyboard
                }
                else
                    SingleToast.show(context, "Connect first!", Toast.LENGTH_LONG);
                break;
            case R.id.scrollDownButton:
                if (networkThread.isAlive() && client!=null) {
                    client.SendMessage(Constants.SCROLL_DOWN); //send "scroll_down" to server
                }
                else
                    SingleToast.show(context, "Connect first!", Toast.LENGTH_LONG);
                break;
            case R.id.scrollUpButton:
                if (networkThread.isAlive() && client!=null) {
                    client.SendMessage(Constants.SCROLL_UP); //send "scroll_up" to server
                }
                else
                    SingleToast.show(context, "Connect first!", Toast.LENGTH_LONG);
                break;
            case R.id.backButton:
                if (networkThread.isAlive() && client!=null) {
                    client.SendMessage(Constants.BACK); //send "back" to server
                }
                else
                    SingleToast.show(context, "Connect first!", Toast.LENGTH_LONG);
                break;
            case R.id.forwardButton:
                if (networkThread.isAlive() && client!=null) {
                    client.SendMessage(Constants.FORWARD); //send "forward" to server
                }
                else
                    SingleToast.show(context, "Connect first!", Toast.LENGTH_LONG); //connection hasn't been made yet, let the user know
                break;
        }

    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent KEvent)    //handles keyboard events
    {
        String nordicLetter = KEvent.getCharacters();
        int keyaction = KEvent.getAction();
        if(keyaction == KeyEvent.ACTION_UP)
        {
            int keyCode = KEvent.getKeyCode();
            int keyunicode = KEvent.getUnicodeChar(KEvent.getMetaState() );
            char character = (char) keyunicode;

            if(keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT
                    || keyCode == KeyEvent.KEYCODE_CAPS_LOCK || keyCode == KeyEvent.KEYCODE_CTRL_LEFT
                    || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT || keyCode == KeyEvent.KEYCODE_SHIFT_LEFT
                    || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT)
            {
                System.out.println("Not printing special keys");
            }
            else if(keyCode == KeyEvent.KEYCODE_DEL)
                client.SendMessage(Constants.BACKSPACE); //send backspace pressed keyboard button to server
            else if(keyCode == KeyEvent.KEYCODE_ENTER)
                client.SendMessage(Constants.ENTER); //send enter pressed keyboard button to server
            else
                client.SendMessage(character+""); //send pressed keyboard button to server

        }
        else if(nordicLetter != null)   //handle nordic letters here, because they don't work with keycode method
        {
            if(nordicLetter.contains("ä") || nordicLetter.contains("ö")
                    || nordicLetter.contains("Ö") || nordicLetter.contains("Ä"))    //include other special characters here if needed
                client.SendMessage(nordicLetter); //send pressed keyboard button to server
        }


        return super.dispatchKeyEvent(KEvent);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        //if connection is still active, tell the network thread to stop
        if(networkThread.isAlive() && client!=null) {
            client.StopClient();
        }

    }

    /**
     * Init the ads used in this activity.
     * @param test are these ads meant to be for testing
     */
    private void InitAds(boolean test)
    {
        AdView mAdView = (AdView) findViewById(R.id.adView);
        if(test)
        {
            //for testing (below)
            MobileAds.initialize(getApplicationContext(), "ca-app-pub-3940256099942544~3347511713");
            AdRequest adRequest = new AdRequest.Builder()   //for testing
                    .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)        // All emulators
                    .addTestDevice("")  // my device ID
                    .build();
            mAdView.loadAd(adRequest);
        }
        else
        {
            //for non testing
            MobileAds.initialize(getApplicationContext(), ""); //my app id
            AdRequest adRequest = new AdRequest.Builder().build(); //Use this for release version
            mAdView.loadAd(adRequest);
        }
    }

    /**
     * Creates a popup window to tell the user how to connect to the pc app
     */
    private void ShowConnectPopup() {
        LayoutInflater inflater = this.getLayoutInflater();
        final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(inflater.inflate(R.layout.connect_popup, null));
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                // save so that message isn't shown again
                SharedPreferences settings = getSharedPreferences(Constants.PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean("ConnectPopupDone", true);

                editor.commit();
            }
        });

        final AlertDialog dialog = builder.create();
        dialog.show();

        //center the OK button
        final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        LinearLayout.LayoutParams positiveButtonLL = (LinearLayout.LayoutParams) positiveButton.getLayoutParams();
        positiveButtonLL.width = ViewGroup.LayoutParams.MATCH_PARENT;
        positiveButton.setLayoutParams(positiveButtonLL);

    }

    /**
     * Inits the left and right switches which hide/show left and right buttons
     */
    private void InitSwitches()
    {
        leftSwitch = (Switch)findViewById(R.id.leftSwitch);
        leftSwitch.setChecked(true);
        leftSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {

                if(isChecked){
                    backButton.setVisibility(View.VISIBLE);
                    forwardButton.setVisibility(View.VISIBLE);
                }else{
                    backButton.setVisibility(View.GONE);
                    forwardButton.setVisibility(View.GONE);
                }

            }
        });
        rightSwitch = (Switch)findViewById(R.id.rightSwitch);
        rightSwitch.setChecked(true);
        rightSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {

                if(isChecked){
                    scrollUpButton.setVisibility(View.VISIBLE);
                    scrollDownButton.setVisibility(View.VISIBLE);
                }else{
                    scrollUpButton.setVisibility(View.GONE);
                    scrollDownButton.setVisibility(View.GONE);
                }

            }
        });
    }

}

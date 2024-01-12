package de.kai_morich.simple_bluetooth_terminal;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd");
    Date now = new Date();
    String fileName = formatter.format(now)+"num"+ UUID.randomUUID().toString() + ".txt";//like 2016_01_12.txt

    //private Button send_location;

    private static final int REQUEST_LOCATION = 1;
    private int EXTERNAL_STORAGE_PERMISSION_CODE = 23;
    /*Button btnGetLocation;
    TextView showLocation;*/
    LocationManager locationManager;
    String latitude, longitude;

    //private TextView receiveText;
    //private TextView sendText;
    //private TextUtil.HexWatcher hexWatcher;

    private TextView helmet_worn_true;
    private TextView helmet_worn_false;
    private TextView user_safe;
    private TextView user_falling;
    private TextView accident;
    private AlertDialog dialog;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;
    //private int helmetWorn=0;
    private double accelerationX=0;
    private double accelerationY=0;
    private double accelerationZ=0;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("myApp","Terminal Fragment");
        /*requestPermissions(new String[]{getActivity().WRITE_EXTERNAL_STORAGE,READ_EXTERNAL_STORAGE}, 1);*/
        ActivityCompat.requestPermissions( getActivity(), new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1122);

        ActivityCompat.requestPermissions( getActivity(), new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS}, PackageManager.PERMISSION_GRANTED);
        if ((ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_SMS) +
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.SEND_SMS))
                != PackageManager.PERMISSION_GRANTED) {

// Permission is not granted
// Should we show an explanation?

            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),"Manifest.permission.READ_SMS") ||
                    ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),"Manifest.permission.READ_SMS")) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{"Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS"},
                        121);

                // REQUEST_CODE is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }

        /*else {
            // Permission has already been granted
        }*/

        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        /*receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());*/
        helmet_worn_true=view.findViewById(R.id.helmet_worn_true);
        helmet_worn_false=view.findViewById(R.id.helmet_worn_false);
        user_safe=view.findViewById(R.id.user_safe);
        user_falling=view.findViewById(R.id.user_falling);
        accident=view.findViewById(R.id.accident);




        /*send_location.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });*/

        dialog = new AlertDialog.Builder(getContext())
                .setTitle("Accident Alert")
                .setMessage("Kindly close off this dialogue to confirm their wasn't any accident.")
                .setPositiveButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO: Add positive button action code here
                        user_safe.setBackgroundColor(getResources().getColor(R.color.green));
                        user_falling.setBackgroundColor(getResources().getColor(R.color.transparent));
                        accident.setBackgroundColor(getResources().getColor(R.color.transparent));

                    }
                })

                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            private static final int AUTO_DISMISS_MILLIS = 10000;
            @Override
            public void onShow(final DialogInterface dialog) {
                final Button defaultButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                final CharSequence negativeButtonText = defaultButton.getText();
                new CountDownTimer(AUTO_DISMISS_MILLIS, 100) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        /*defaultButton.setText(String.format(
                                Locale.getDefault(), "%s (%d)",
                                negativeButtonText,
                                TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) + 1 //add one so it never displays zero
                        ));*/
                    }
                    @Override
                    public void onFinish() {
                        if (((AlertDialog) dialog).isShowing()) {
                            dialog.dismiss();
                            locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
                            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                                OnGPS();
                            } else {
                                getLocation();
                            }
                            Toast.makeText(getContext(),"Message Alert Send",Toast.LENGTH_SHORT).show();
                        }
                    }
                }.start();
            }
        });

        //sendText = view.findViewById(R.id.send_text);
        /*hexWatcher = new TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX mode" : "");*/

        //View sendBtn = view.findViewById(R.id.send_btn);
        //sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        return view;
    }

    private void OnGPS() {
        final androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(getContext());
        builder.setMessage("Enable GPS").setCancelable(false).setPositiveButton("Yes", new  DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        }).setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        final androidx.appcompat.app.AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(
                getContext(),Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        } else {
            Location locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (locationGPS != null) {
                double lat = locationGPS.getLatitude();
                double longi = locationGPS.getLongitude();
                latitude = String.valueOf(lat);
                longitude = String.valueOf(longi);
                String message = "Your Location: " + "\n" + "Latitude: " + latitude + "\n" + "Longitude: " + longitude;
                String number = "03235192192";

                SmsManager mySmsManager = SmsManager.getDefault();
                mySmsManager.sendTextMessage(number,null, message, null, null);
                //showLocation.setText();
            } else {
                Toast.makeText(getContext(), "Unable to find location.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            //receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            /*sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");*/
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            //receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        if(hexEnabled) {
            //receiveText.append(TextUtil.toHexString(data) + '\n');
        } else {
            String msg = new String(data);
            if(newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                // don't show CR as ^M if directly before LF
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    //Editable edt = receiveText.getEditableText();
                    /*if (edt != null && edt.length() > 1)
                        edt.replace(edt.length() - 2, edt.length(), "");*/
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            //receiveText.append(TextUtil.toCaretString(msg, newline.length() != 0));
            String fullText=TextUtil.toCaretString(msg, newline.length() != 0).toString();
            //String fullText="impact";

            //Log.d("myApp",fullText);

            if(fullText.toLowerCase().trim().equals("helmet not wearing")){
                helmet_worn_true.setBackgroundColor(getResources().getColor(R.color.transparent));
                helmet_worn_false.setBackgroundColor(getResources().getColor(R.color.red));
            }
            if(fullText.toLowerCase().trim().equals("helmet wearing")){
                helmet_worn_true.setBackgroundColor(getResources().getColor(R.color.green));
                helmet_worn_false.setBackgroundColor(getResources().getColor(R.color.transparent));
            }

            if(fullText.toLowerCase().trim().equals("user is safe")){
                user_safe.setBackgroundColor(getResources().getColor(R.color.green));
                user_falling.setBackgroundColor(getResources().getColor(R.color.transparent));
                accident.setBackgroundColor(getResources().getColor(R.color.transparent));

            }
            if(fullText.toLowerCase().trim().equals("user is falling")){
                user_safe.setBackgroundColor(getResources().getColor(R.color.transparent));
                user_falling.setBackgroundColor(getResources().getColor(R.color.yellow));
                accident.setBackgroundColor(getResources().getColor(R.color.transparent));
            }
            if(fullText.toLowerCase().trim().equals("impact")){
                user_safe.setBackgroundColor(getResources().getColor(R.color.transparent));
                user_falling.setBackgroundColor(getResources().getColor(R.color.transparent));
                accident.setBackgroundColor(getResources().getColor(R.color.red));
                dialog.show();
            }
            generateNoteOnSD(getContext(),"secure",fullText);
            }

            /*if(fullText.matches("-?(0|[1-9]\\d*)") && !fullText.equals("") ){
                helmetWorn=Integer.valueOf(fullText);
                //Log.d("myApp", String.valueOf(helmetWorn));
            }
            //Log.d("myApp", String.valueOf(Double.parseDouble(fullText.replaceAll("[\\s+a-zA-Z :]",""))));
            if(fullText.contains("Acc X")&& !fullText.equals("") && !fullText.isEmpty()){
                Log.d("myApp","x: "+fullText.substring(7,12));
                double substring=Double.parseDouble(fullText.substring(7,12).replaceAll("[\\s+a-zA-Z :]",""));
                Log.d("myApp", String.valueOf(substring));
                //accelerationX=Double.valueOf(fullText.substring(8,13));
            }
            if(fullText.contains("Acc Y")&& !fullText.equals("")) {
                Log.d("myApp", "y: " + fullText.substring(19, 25));
            }*/


        }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        //Log.d("myApp",spn.toString());
        //receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }
    public void generateNoteOnSD(Context context, String sFileName, String sBody) {

        try
        {
            File root = new File(Environment.getExternalStorageDirectory()+File.separator+"Hamza Bhai", "Report Files");
            Log.d("myApp",Environment.getExternalStorageDirectory()+File.separator+"Hamza Bhai");
            //File root = new File(Environment.getExternalStorageDirectory(), "Notes");
            if (!root.exists())
            {
                root.mkdirs();
            }
            File gpxfile = new File(root, fileName);


            FileWriter writer = new FileWriter(gpxfile,true);
            writer.append(sBody+"\n\n");
            writer.flush();
            writer.close();
            Toast.makeText(getContext(), "Data has been written to Report File", Toast.LENGTH_SHORT).show();
        }
        catch(IOException e)
        {
            e.printStackTrace();

        }
    }
}

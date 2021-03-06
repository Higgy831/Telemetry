package com.einstein_automotive.httpwp.telemetry;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.einstein_automotive.httpwp.telemetry.DataStorage.Data;
import com.einstein_automotive.httpwp.telemetry.DataStorage.Unit;
import com.einstein_automotive.httpwp.telemetry.DataStorage.ValueName;
import com.einstein_automotive.httpwp.telemetry.Fragments.Communicators.Communicator;
import com.einstein_automotive.httpwp.telemetry.Fragments.Communicators.CommunicatorStop;
import com.einstein_automotive.httpwp.telemetry.Fragments.Monitoring_frag;
import com.einstein_automotive.httpwp.telemetry.Fragments.Racing_frag;
import com.einstein_automotive.httpwp.telemetry.Fragments.Settings_frag;
import com.einstein_automotive.httpwp.telemetry.Fragments.Telemetry_frag;
import com.einstein_automotive.httpwp.telemetry.Fragments.TopBarBarViewed_frag;
import com.einstein_automotive.httpwp.telemetry.customViews.Point;
import com.einstein_automotive.httpwp.telemetry.tabs.SlidingTabLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HttpsURLConnection;


public class MainActivity extends FragmentActivity implements Communicator, CommunicatorStop {

    private ViewPager vPager;
    private FragmentManager fragManager;
    private Racing_frag racingFrag;
    private Monitoring_frag moniFrag;
    private Telemetry_frag telemetryFrag;
    private Settings_frag settingsFrag;

    private ImageView ivBattery, ivTemperature, ivPressure;

    URL url;
    TextView tv_Connectivity;

    private Point point;

    TopBarBarViewed_frag tbFrag;

    private ConcurrentHashMap<ValueName,Data>  concurrentHashMap;
    private int port;
    private byte[] recievedDataPacket;
    private String udpReceived;
    private byte[] sendData;

    byte[] data = new byte[130];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fragManager = getSupportFragmentManager();

        vPager = (ViewPager) findViewById(R.id.pager);


        vPager.setAdapter(new PagerAdapter(fragManager));
        SlidingTabLayout mTabs = (SlidingTabLayout) findViewById(R.id.tabs);
        mTabs.setDistributeEvenly(true);
        mTabs.setCustomTabView(R.layout.custom_tab, R.id.tabtext);
        mTabs.setViewPager(vPager);

        LinearLayout mainLL = (LinearLayout) findViewById(R.id.mainLL);
        mainLL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideShowBarFragment(fragManager.findFragmentById(R.id.topBarFragment));
            }
        });
        tv_Connectivity = (TextView) findViewById(R.id.tv_Connectivity);




        sendData = new byte[20];

        PagerAdapter pagerAdaptervPager = (PagerAdapter) vPager.getAdapter();
        racingFrag = (Racing_frag) pagerAdaptervPager.getItem(0);
        moniFrag = (Monitoring_frag) pagerAdaptervPager.getItem(1);
        settingsFrag = (Settings_frag) pagerAdaptervPager.getItem(2);
        telemetryFrag = (Telemetry_frag) pagerAdaptervPager.getItem(3);;
        tbFrag = (TopBarBarViewed_frag) fragManager.findFragmentById(R.id.topBarFragment);
        concurrentHashMap = new ConcurrentHashMap<>();

        port = 9999;

        ivBattery = (ImageView) findViewById(R.id.iv_battery);
        ivTemperature = (ImageView) findViewById(R.id.iv_temperature);
        ivPressure = (ImageView) findViewById(R.id.iv_pressure);

        MainTask mainTask = new MainTask();
        mainTask.start();
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder closeAlert = new AlertDialog.Builder(this);
        closeAlert.setTitle("Closing App? Unsafed data will be losed!");
        closeAlert.setPositiveButton("YES", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
                System.exit(0);
            }
        });
        closeAlert.setNegativeButton("NO", null);
        closeAlert.create();
        closeAlert.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void hideShowBarFragment(Fragment fragment) {
        FragmentTransaction transaction = fragManager.beginTransaction();
        if (fragment.isHidden()) {
            transaction.show(fragment);
        } else {
            transaction.hide(fragment);
        }
        transaction.commit();
        racingFrag.sizeChanged(vPager.getHeight());

    }

    @Override
    public void respond(String data, int tab) {

    }

    @Override
    public void emergencyStop(boolean stopNow) {
        sendData[0]= 3;
        sendData[1]= 1;
        sendData[10]=3;
        new Thread(new TaskSendData()).start();
    }

    class MainTask extends Thread {
        @Override
        public void run() {
            while (!isInterrupted()) {


                        einsteinData();

            }
        }
    }

    class TaskSendData extends Thread {
        @Override
        public void run() {
            try {
                DatagramSocket socket = new DatagramSocket();
                InetAddress serverAddr = InetAddress.getByName("192.168.1.255");
                DatagramPacket dp;
                dp = new DatagramPacket(sendData, 20, serverAddr, 12354);
                socket.send(dp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    DatagramSocket socket;

    // Alle 10 ms neue Daten Performance opti?
    private void einsteinData() {
        try {
            if (socket == null){
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.setBroadcast(true);
                socket.bind(new InetSocketAddress(port));
                socket.setSoTimeout(5000);
            }

            DatagramPacket packet = new DatagramPacket(new byte[130],130);
            socket.receive(packet);
            data = packet.getData();
            udpReceived = String.valueOf(packet.getLength());

            readByteData();

        }catch (SocketTimeoutException e) {
            e.printStackTrace();
            Log.e("Socket","TimeoutException");
            updateConnectivity();
        } catch (SocketException e) {
            e.printStackTrace();
            Log.e("Socket",e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Socket",e.getMessage());
        }
    }

    private void readByteData(){

        //  public Data(float value, Unit unit, float critical, float min, float max) {
        concurrentHashMap.put(ValueName.RPM, new Data(data[0]+128f+((data[1]+128f)*256f), Unit.RPM, 16000, 0, 20000));
        concurrentHashMap.put(ValueName.Speed, new Data(data[2]+128f, Unit.Speed, 0, 0, 160));
        concurrentHashMap.put(ValueName.MotorTemp, new Data(data[3]+88f, Unit.Temperature, 80, 10, 200));
        concurrentHashMap.put(ValueName.AirTemp, new Data(data[4]+88f, Unit.Temperature, 80, 10, 200));
        concurrentHashMap.put(ValueName.OilTemp, new Data(data[5]+88f, Unit.Temperature, 80, 10, 200));
        concurrentHashMap.put(ValueName.FuelTemp, new Data(data[6]+88f, Unit.Temperature, 80, 10, 200));
        concurrentHashMap.put(ValueName.LambdaTemp, new Data(data[7]+88f, Unit.Temperature, 80, 10, 200));
        concurrentHashMap.put(ValueName.OilPressure, new Data((data[8]+128f)/20, Unit.Pressure, 2, 2, 10));
        concurrentHashMap.put(ValueName.WaterPressure, new Data((data[9]+128f)/20, Unit.Pressure, 2, 2, 10));
        concurrentHashMap.put(ValueName.FuelPressure, new Data((data[10]+128f)/20, Unit.Pressure, 2, 2, 10));
        concurrentHashMap.put(ValueName.Lambda, new Data(data[11]+128f+data[12], Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.BatteryVoltage, new Data((data[13]+128f+(data[14]+128f)*256f)/1000, Unit.Voltage, 0, 0, 0));
        concurrentHashMap.put(ValueName.Gear, new Data(data[16]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.TCswitch, new Data(data[17]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.TCstate, new Data(data[18]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.Ath, new Data(data[19]+128f+data[20]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.SpeedFL, new Data(data[26]+128f, Unit.Speed, 0, 0, 0));
        concurrentHashMap.put(ValueName.SpeedFR, new Data(data[27]+128f, Unit.Speed, 0, 0, 0));
        concurrentHashMap.put(ValueName.SpeedRL, new Data(data[28]+128f, Unit.Speed, 0, 0, 0));
        concurrentHashMap.put(ValueName.SpeedRR, new Data(data[29]+128f, Unit.Speed, 0, 0, 0));

        concurrentHashMap.put(ValueName.SuspensionFL, new Data(data[35]+128f, Unit.Distance, 0, 0, 0));
        concurrentHashMap.put(ValueName.SuspensionFR, new Data(data[36]+128f, Unit.Distance, 0, 0, 0));
        concurrentHashMap.put(ValueName.SuspensionRL, new Data(data[37]+128f, Unit.Distance, 0, 0, 0));
        concurrentHashMap.put(ValueName.SuspensionRR, new Data(data[38]+128f, Unit.Distance, 0, 0, 0));

        concurrentHashMap.put(ValueName.GForceX, new Data(data[44]+128f+data[45]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.GForceY, new Data(data[46]+128f+data[47]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.GForce, new Data(data[49]+128f, Unit.GForce, 0, 0, 0));

        concurrentHashMap.put(ValueName.CanState, new Data(data[55]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.AliveCounter, new Data(data[56]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.SGCAN, new Data(data[57]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.BrakePressureFront, new Data(data[58]+128f, Unit.Pressure, 0, 0, 0));
        concurrentHashMap.put(ValueName.BrakePressureRear, new Data(data[59]+128f, Unit.Pressure, 0, 0, 0));
        concurrentHashMap.put(ValueName.Steering, new Data(data[60]+128f, Unit.Angle, 0, 0, 0));
        concurrentHashMap.put(ValueName.GearDetVoltage, new Data(data[61]+128f, Unit.Voltage, 0, 0, 0));
        concurrentHashMap.put(ValueName.SensorVoltage, new Data(data[62]+128f, Unit.Voltage, 0, 0, 0));
        concurrentHashMap.put(ValueName.DIPSwitch, new Data(data[63]+128f, Unit.Count, 0, 0, 0));

        concurrentHashMap.put(ValueName.Temp1, new Data(data[69]+128f+data[70]+128f, Unit.Temperature, 0, 0, 0));
        concurrentHashMap.put(ValueName.Temp2, new Data(data[71]+128f+data[72]+128f, Unit.Temperature, 0, 0, 0));
        concurrentHashMap.put(ValueName.Temp3, new Data(data[73]+128f+data[74]+128f, Unit.Temperature, 0, 0, 0));
        concurrentHashMap.put(ValueName.Temp4, new Data(data[75]+128f+data[76]+128f, Unit.Temperature, 0, 0, 0));
        concurrentHashMap.put(ValueName.Temp5, new Data(data[77]+128f+data[78]+128f, Unit.Temperature, 0, 0, 0));
        concurrentHashMap.put(ValueName.Temp6, new Data(data[79]+128f+data[80]+128f, Unit.Temperature, 0, 0, 0));
        concurrentHashMap.put(ValueName.Temp7, new Data(data[81]+128f+data[82]+128f, Unit.Temperature, 0, 0, 0));
        concurrentHashMap.put(ValueName.Temp8, new Data(data[83]+128f+data[84]+128f, Unit.Temperature, 0, 0, 0));

        concurrentHashMap.put(ValueName.DashboardState, new Data(data[90]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.UpShift, new Data(data[91]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.DownShift, new Data(data[92]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.DashboardTemp, new Data(data[93]+128f, Unit.Temperature, 0, 0, 0));

        concurrentHashMap.put(ValueName.SCUstate, new Data(data[99]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.GearSensorVoltage, new Data(data[100]+128f, Unit.Voltage, 0, 0, 0));
        //concurrentHashMap.put(ValueName.Gear, new Data(data[101]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.SCUtemp, new Data(data[102]+128f, Unit.Temperature, 0, 0, 0));
        concurrentHashMap.put(ValueName.TimeUpShift, new Data(data[103]+128f+data[104]+128f, Unit.Time, 0, 0, 0));
        concurrentHashMap.put(ValueName.TimeDownShift, new Data(data[105]+128f+data[106]+128f, Unit.Time, 0, 0, 0));
        concurrentHashMap.put(ValueName.TimeClutchOpen, new Data(data[107]+128f+data[108]+128f, Unit.Time, 0, 0, 0));
        concurrentHashMap.put(ValueName.TimeGearcutActive, new Data(data[109]+128f, Unit.Time, 0, 0, 0));
        concurrentHashMap.put(ValueName.WaitDownShift, new Data(data[110]+128f, Unit.Time, 0, 0, 0));

        concurrentHashMap.put(ValueName.CurrentMaxDashboard, new Data(data[116]+128f, Unit.Ampere, 0, 0, 0));
        concurrentHashMap.put(ValueName.CurrentMaxTelemetrie, new Data(data[117]+128f, Unit.Ampere, 0, 0, 0));
        concurrentHashMap.put(ValueName.CurrentMaxDDSC, new Data(data[118]+128f, Unit.Ampere, 0, 0, 0));
        concurrentHashMap.put(ValueName.CurrentMaxMainRelay, new Data(data[119]+128f, Unit.Ampere, 0, 0, 0));
        concurrentHashMap.put(ValueName.CurrentMaxFuelPump, new Data(data[120]+128f, Unit.Ampere, 0, 0, 0));
        concurrentHashMap.put(ValueName.CurrentMaxFAN, new Data(data[121]+128f, Unit.Ampere, 0, 0, 0));
        concurrentHashMap.put(ValueName.CurrentMaxShifter, new Data(data[122]+128f, Unit.Ampere, 0, 0, 0));
        concurrentHashMap.put(ValueName.Multiplex, new Data(data[123]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.StateSG, new Data(data[124]+128f, Unit.Count, 0, 0, 0));
        concurrentHashMap.put(ValueName.CurrentAverageSG, new Data(data[125]+128f+data[126]+128f, Unit.Ampere, 0, 0, 0));
        concurrentHashMap.put(ValueName.VoltageAverageSG, new Data(data[127]+128f+data[128]+128f, Unit.Voltage, 0, 0, 0));
        concurrentHashMap.put(ValueName.PowerAverageSG, new Data(data[129]+128f+data[120]+128f, Unit.Power, 0, 0, 0));


        racingFrag.updateData(concurrentHashMap);
        //tbFrag.setFuelPressure(((data[30] + 128) * 5 / 255.f) * 2f);
        //telemetryFrag.updateData(concurrentHashMap);
        tbFrag.updateData(concurrentHashMap);
       // moniFrag.updateData(concurrentHashMap);
        updateMainActivityLayout();
    }

    private void updateMainActivityLayout(){

        if (concurrentHashMap.get(ValueName.PowerAverageSG).getValue() >= 150){
            ivBattery.post(new Runnable() {
                @Override
                public void run() {
                    ivBattery.setBackgroundColor(Color.RED);
                }
            });
        }else{
            ivBattery.post(new Runnable() {
                @Override
                public void run() {
                    ivBattery.setBackgroundColor(Color.TRANSPARENT);
                }
            });
        }

        if (concurrentHashMap.get(ValueName.MotorTemp).getValue() >= 80 || concurrentHashMap.get(ValueName.LambdaTemp).getValue() >= 80 || concurrentHashMap.get(ValueName.OilTemp).getValue() >= 80 || concurrentHashMap.get(ValueName.FuelTemp).getValue() >= 80 || concurrentHashMap.get(ValueName.DashboardTemp).getValue() >= 80 || concurrentHashMap.get(ValueName.SCUtemp).getValue() >= 80){
            ivTemperature.post(new Runnable() {
                @Override
                public void run() {
                    ivTemperature.setBackgroundColor(Color.RED);
                }
            });
        }else{
            ivTemperature.post(new Runnable() {
                @Override
                public void run() {
                    ivTemperature.setBackgroundColor(Color.TRANSPARENT);
                }
            });
        }

        if (concurrentHashMap.get(ValueName.FuelPressure).getValue() >= 8 || concurrentHashMap.get(ValueName.OilPressure).getValue() >= 8 || concurrentHashMap.get(ValueName.WaterPressure).getValue() >= 8){
            ivPressure.post(new Runnable() {
                @Override
                public void run() {
                    ivPressure.setBackgroundColor(Color.RED);
                }
            });
        }else{
            ivPressure.post(new Runnable() {
                @Override
                public void run() {
                    ivPressure.setBackgroundColor(Color.TRANSPARENT);
                }
            });
        }

        tv_Connectivity.post(new Runnable() {
            @Override
            public void run() {
                tv_Connectivity.setText(concurrentHashMap.get(ValueName.AliveCounter).getValue()+"");
            }
        });
    }

    private void updateConnectivity(){

        tv_Connectivity.post(new Runnable() {
            @Override
            public void run() {
                tv_Connectivity.setText("Data Receive Timeout!");
            }
        });
    }

    //region Project Cars
    private void projectCarsData() {

        String responseString = null;
        try {

            url = new URL("http://10.0.2.2:8080/crest/v1/api");
            Thread.sleep(100);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-length", "0");
            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);
            conn.setReadTimeout(300);
            conn.setConnectTimeout(400);
            if (conn.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                InputStreamReader in = new InputStreamReader(conn.getInputStream());
                BufferedReader br = new BufferedReader(in);
                String line;
                while ((line = br.readLine()) != null) {
                    responseString += line;
                }
                br.close();
                in.close();
            }

        } catch (Exception ignored) {

        }
        if (responseString != null) {
            if (responseString.startsWith("null")) {
                responseString = responseString.substring(4, responseString.length());
                try {
                    JSONObject jObject = new JSONObject(responseString);
                    JSONObject carStates = jObject.getJSONObject("carState");
                    float speed = Float.parseFloat(carStates.getString("mSpeed"));
                    speed *= 3.609344f;
                    float rpm = (int) (Float.parseFloat(carStates.getString("mRpm")));
                    float steering = Float.parseFloat(carStates.getString("mSteering"));
                    steering *= 90;
                    float gear = (int) (Float.parseFloat(carStates.getString("mGear")));
                    JSONObject Velocity = jObject.getJSONObject("motionAndDeviceRelated");
                    JSONArray mLocalVelocity = Velocity.getJSONArray("mLocalVelocity");
                    float gforceX =Float.valueOf(mLocalVelocity.getString(0)) * -1.0f;
                    float gforceY = Float.valueOf(mLocalVelocity.getString(1)) * -1.0f;
                    point = new Point(gforceX,gforceY);
//                    concurrentHashMap.put(ValueName.Speed, speed);
//                    concurrentHashMap.put(ValueName.RPM, rpm);
//                    concurrentHashMap.put(ValueName.Gear, gear);
//                    concurrentHashMap.put(ValueName.Steering, steering);
//                    concurrentHashMap.put(ValueName.GForceX, gforceX);
//                    concurrentHashMap.put(ValueName.GForceY, gforceY);
//                    racingFrag.updateData(concurrentHashMap);

//                    moniFrag.update((int) speed, (int)gear, (int)rpm, steering);
//                    racingFrag.update((int) speed, (int) gear, (int)rpm, steering, point);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    //endregion

    class PagerAdapter extends FragmentPagerAdapter {

        String[] tabs;
        int[] icons = {R.drawable.racing, R.drawable.monitoring, R.drawable.settings,R.drawable.telemetry};
        Fragment[] fragments = new Fragment[4];

        public PagerAdapter(FragmentManager fm) {

            super(fm);
            tabs = getResources().getStringArray(R.array.tabs);
            fragments[0] = new Racing_frag();
            fragments[1] = new Monitoring_frag();
            fragments[2] = new Settings_frag();
            fragments[3] = new Telemetry_frag();

        }

        @Override
        public CharSequence getPageTitle(int position) {
            Drawable drawable = getResources().getDrawable(icons[position]);
            drawable.setBounds(0, 0, 32, 32);
            ImageSpan iSpan = new ImageSpan((drawable));
            SpannableString spannableString = new SpannableString(tabs[position] + "     ");
            spannableString.setSpan(iSpan, tabs[position].length() + 3, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            return spannableString;
        }

        @Override
        public Fragment getItem(int position) {
            return fragments[position];
        }

        @Override
        public int getCount() {
            return 3;
        }
    }
}

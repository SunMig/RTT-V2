package com.example.rtt;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.text.NumberFormat;
import android.icu.text.SimpleDateFormat;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.jama.Matrix;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private final String TAG = "MainActivity";
    private String[] need_permission;
    private ScanResult scanResult;
    private WifiRttManager wifiRttManager;
    private WifiManager wifiManager;
    private RangingResultCallback rangingResultCallback;
    private List<ScanResult> accesspointsupport802mc;
    private List<ScanResult> accesspoint;
    private List<RangingResult> rttresult=new ArrayList<>();
    private List<RangingResult> rssiresult=new ArrayList<>();
    private ArrayList<Integer> mStatisticRangeHistory;
    final Handler mRangeRequestDelayHandler = new Handler();
    private List<RttResultClass> RttResults=new ArrayList<>();
    private LinkedHashMap<String,Double> rttHashMap=new LinkedHashMap<>();
    private LinkedHashMap<String,Double> RSSIHashMap=new LinkedHashMap<>();
    private RttResultClass rttResultClass;
    private TextView textView,textView2,textView3,textView4,textView10,textView5,textView6;
    private String FilePath=LocalPath.wifi_mac_txt;
    private List<String> RttMac=new ArrayList<>();
    private HashMap<String,Point> RttPointMap=new HashMap<>();
    private FileReadClass fileReadClass=new FileReadClass();
    private int IterTimes=0,count=1;
    private double sumdistance=0d;
    private String fileName="rttdata";
    private String fileMemsName="memsdata";
    private String sdPath;
    private boolean writeFile=false;
    private EditText editText;
    private TimeCount timeCount;
    private int SampleTime;
    Calendar calendar=Calendar.getInstance();
    int year=calendar.get(Calendar.YEAR);
    int month=calendar.get(Calendar.MONTH);
    int day=calendar.get(Calendar.DAY_OF_MONTH);
    int hour=calendar.get(Calendar.HOUR_OF_DAY);
    int minute=calendar.get(Calendar.MINUTE);
    private SensorManager sensorManager;
    private Sensor accsensor,magsensor,gsensor,grsensor,presensor;
    float[] Rorate=new float[9];
    float[] OriVal=new float[3];
    float[] accVal=new float[3];
    float[] magVal=new float[3];
    float[] gVal=new float[3];
    float[] grVal=new float[3];
//    double[][] rttrefer={{18.4,0},{11.3,5.36},{0.85,5.36},{9.1,0}};//e0,9c,b7,98,要实现自适应变化
    boolean isGRa=false,isGYR=false,isMAg=false;
    private StepDectFsm stepDectFsm=new StepDectFsm();
    private float stepLength=0f;
    private int stepcount=0;
    float Azimuth=0f,Pitch=0f,Roll=0f;
    private double[][] pdrcoor={{0},{0}};
    private Point point=new Point();
    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestApplicationPermission();

        accesspointsupport802mc = new ArrayList<ScanResult>();
        mStatisticRangeHistory=new ArrayList<>();
        RttMac=fileReadClass.ReadFileFromStorge(FilePath);
        RttPointMap=fileReadClass.ReadCoordFileFromStorge(LocalPath.wifi_ftm_position);//0706
        fileName=fileName+"_"+year+"_"+month+"_"+day+"_"+hour+"_"+minute;
        fileMemsName=fileMemsName+"_"+year+"_"+month+"_"+day+"_"+hour+"_"+minute;
        textView=(TextView)findViewById(R.id.text1);
        textView2=findViewById(R.id.text2);
        textView10=findViewById(R.id.text10);
        textView3=findViewById(R.id.text3);textView4=findViewById(R.id.text4);
        textView5=findViewById(R.id.text5);
        textView6=findViewById(R.id.text6);textView6.setText(""+1);
        editText=findViewById(R.id.edit_text);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiRttManager=(WifiRttManager)getApplicationContext().getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        //开启wifi
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            Log.i(TAG, "WIFI 打开 ");
        }
        rangingResultCallback = new RTTrangingResultCallBack();

    }
    //注册传感器
    @Override
    protected void onResume() {
        super.onResume();
        sensorManager=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
        accsensor=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magsensor=sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gsensor=sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        grsensor=sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        presensor=sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sensorManager.registerListener(this,accsensor,CollectTime.COLLECT_NORMAL);
        sensorManager.registerListener(this,magsensor,CollectTime.COLLECT_NORMAL);
        sensorManager.registerListener(this,gsensor,CollectTime.COLLECT_NORMAL);
        sensorManager.registerListener(this,grsensor,CollectTime.COLLECT_NORMAL);
        sensorManager.registerListener(this,presensor,CollectTime.COLLECT_NORMAL);
    }

    //wifi扫描
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void wifiStartscan(View view) {
        wifiManager.startScan();
        Log.d(TAG,"start scaning...");
        Toast.makeText(this,"Start Scanning...",Toast.LENGTH_LONG).show();
    }
    //获取满足条件的AP
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void getRttScan(View view) {
        accesspoint = wifiManager.getScanResults();
        accesspointsupport802mc = find80211mcSupportedAccessPoints(accesspoint);
        Toast.makeText(this,accesspointsupport802mc.size()+"RTT device was found!"
                ,Toast.LENGTH_LONG).show();
    }
    //RTT
    @TargetApi(Build.VERSION_CODES.P)
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void wifiRttScan(View view) {
        for(int i=0;i<RttMac.size();i++){
            rttHashMap.put(RttMac.get(i),0d);
            RSSIHashMap.put(RttMac.get(i),0d);
        }
        StartRanging();
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void StartRanging(){
        RangingRequest.Builder builder = new RangingRequest.Builder();
        if(accesspointsupport802mc.size()==0){
            Toast.makeText(this,accesspointsupport802mc.size()+"RTT device was found! Ranging will not start!",
                    Toast.LENGTH_LONG).show();
        }else{
            builder.addAccessPoints(accesspointsupport802mc);
            RangingRequest request = builder.build();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            wifiRttManager.startRanging(request, getApplication().getMainExecutor(), rangingResultCallback);
        }
    }

    //寻找满足条件AP的方法
    @TargetApi(Build.VERSION_CODES.P)
    @RequiresApi(api = Build.VERSION_CODES.M)
    private List<ScanResult> find80211mcSupportedAccessPoints(
            @NonNull List<ScanResult> originalList) {
        List<ScanResult> newList = new ArrayList<>();

        for (ScanResult scanResult : originalList) {

            if (scanResult.is80211mcResponder()) {
                newList.add(scanResult);
            }

            if (newList.size() >= RangingRequest.getMaxPeers()) {
                break;
            }
        }
        return newList;
    }
    //start collect
    public void collectdata(View view) {
        SampleTime=Integer.valueOf(String.valueOf(editText.getText().toString()));
        timeCount=new TimeCount(SampleTime*1000,1000);
        timeCount.start();
        fileName=fileName+"_"+count;
        fileMemsName=fileMemsName+"_"+count;
        writeFile=true;
        Toast.makeText(this,"Start...",Toast.LENGTH_LONG).show();

}
    //stop/reset collect
    public void stopcollectdata(View view) {
        fileName="rttdata"+"_"+year+"_"+month+"_"+day+"_"+hour+"_"+minute;
        fileMemsName="memsdata"+"_"+year+"_"+month+"_"+day+"_"+hour+"_"+minute;
        count=count+1;
        editText.setText(SampleTime+"");
        textView6.setText(count+"");
//        writeFile=false;
    }

    //传感器响应事件
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onSensorChanged(SensorEvent event) {
        String strings="";
        switch (event.sensor.getType()){
            case Sensor.TYPE_ACCELEROMETER:
                accVal=event.values.clone();
                //判断步态的方法
                if(stepDectFsm.StepDect(accVal)){
                    stepLength=stepDectFsm.getStepLength();
                    stepcount++;
                }
                break;
            case Sensor.TYPE_GRAVITY:
                gVal=event.values.clone();
                isGRa=true;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magVal=event.values.clone();
                isMAg=true;
                break;
            case Sensor.TYPE_GYROSCOPE:
                grVal=event.values.clone();
                isGYR=true;
        }
//        float Azimuth=0f,Pitch=0f,Roll=0f;
        if(isGYR&&isMAg&&isGRa){
            SensorManager.getRotationMatrix(Rorate,null,gVal,magVal);
            SensorManager.getOrientation(Rorate,OriVal);
            //重置判断条件
            isGRa=false;
            isGYR=false;
            isMAg=false;
            //写入内存
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            strings=sdf.format(new Date());
            strings=strings+" "+magVal[0]+" "+magVal[1]+" "+magVal[2]+" "+accVal[0]+" "
            +accVal[1]+" "+accVal[2]+" "+grVal[0]+" "+grVal[1]+" "+grVal[2];
            Azimuth= (float) Math.toDegrees(OriVal[0]);
            Pitch= -(float) Math.toDegrees(OriVal[1]);
            Roll= (float) Math.toDegrees(OriVal[2]);
//            Log.d(TAG,"Pitch is "+Pitch);
//            Log.d(TAG,"Roll is "+Roll);
            if(Azimuth<0){
                Azimuth=Azimuth+360;
            }
//            Azimuth=Azimuth-5.9f;
//            Log.d(TAG,"azimuth is "+Azimuth);
            strings=strings+stepLength+" "+Azimuth+" "+Pitch+" "+Roll+" "+"\n";
            if(writeFile){
                WritememsFileSdcard(strings);
            }
            //Log.d(TAG,strings);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //WiFi测距请求类
    @RequiresApi(api = Build.VERSION_CODES.P)
    private class RTTrangingResultCallBack extends RangingResultCallback{
        //测距频率
        private void queneNextRange(){
            mRangeRequestDelayHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    StartRanging();
                }
            },100);
        }
        @Override
        public void onRangingFailure(int code) {
            Log.d(TAG,"Range Failed!");
            queneNextRange();
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> results) {
            //重写此方法，获得到每一个AP的平均距离
            if(results.get(0).getStatus()==RangingResult.STATUS_SUCCESS){
                rttresult=results;
                rssiresult=results;
                IterTimes=IterTimes+1;
                /*
                * 大于等于4个AP的时候，怎么实时实现探测到的AP的测距？
                * */
                if(IterTimes<5){
                    //循环迭代
                    for(int i=0;i<rttresult.size();i++){
                        Log.d(TAG,"I:"+IterTimes);
                        String sigmac=rttresult.get(i).getMacAddress().toString();
                        RangingResult rangingResult=rttresult.get(i);
                        for (Map.Entry<String, Double> entry : rttHashMap.entrySet()) {
                            if (entry.getKey().equals(sigmac)&&
                                    (rangingResult.getStatus()==RangingResult.STATUS_SUCCESS)) {
                                entry.setValue(entry.getValue() + rttresult.get(i).getDistanceMm() / 1000f);
                            }
                        }
                    }
                    //信号均值
                    for(int i=0;i<rssiresult.size();i++){
                        String sigmac=rssiresult.get(i).getMacAddress().toString();
                        RangingResult rangingResults=rttresult.get(i);
                        for (Map.Entry<String, Double> entry : RSSIHashMap.entrySet()) {
                            if (entry.getKey().equals(sigmac)&&
                                    (rangingResults.getStatus()==RangingResult.STATUS_SUCCESS)) {
                                entry.setValue(entry.getValue() + rssiresult.get(i).getRssi());
                            }
                        }
                    }
                    //测距请求
                    queneNextRange();
                }
                //求五次成功测距的均值，（后续可以再改），2019/12/17，已有八个AP
                if(IterTimes>=5){
                    String string="",str="";
                    double[] rttrange=new double[8];
                    String[] rttMac=new String[8];
                    int index=0;
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
                    str=sdf.format(new Date())+" ";
                    for(Map.Entry<String,Double> entry:rttHashMap.entrySet()){
//                        double distance=entry.getValue()/5;
                        NumberFormat nf = NumberFormat.getInstance();
                        nf.setMaximumFractionDigits(5);
                        double distance= Double.parseDouble(nf.format(entry.getValue()/5));
                        if(entry.getKey().equals("dc:8b:28:54:d8:e1")){
                            distance=distance+5;
                            distance= Double.parseDouble(nf.format(distance));
                        }
                        rttrange[index]=distance;
                        rttMac[index]=entry.getKey();//MAC
                        Log.d(TAG,entry.getKey()+" "+distance);
                        string=string+entry.getKey()+" "+distance+'\n';
                        str=str+entry.getKey()+" "+distance+" ";
                        entry.setValue(0d);
                        Log.d(TAG,"String "+string);
                        index++;
                    }
                    Log.d(TAG,"String "+string);
                    for(Map.Entry<String,Double> entry:RSSIHashMap.entrySet()){
                        double rssi=entry.getValue()/5;
                        Log.d(TAG,entry.getKey()+" "+rssi);
                        str=str+" "+rssi+" ";
                        entry.setValue(0d);
                    }
                    textView3.setText(string);
                    //满足条件，开始写入内存
                    if(writeFile){
                        str=str+'\n';
                        WriteFileSdcard(str);
                    }
                    IterTimes=0;
                    queneNextRange();
                    //定位方法实现，三角定位算法
//                    LeastSquareMethod(rttrange,rttMac);//0525
                    //考虑添加PDR计算方法
                    //1126，后续添加滤波融合
                    double aa=0d;
                    double bb=1;
                }
            }else {
                Log.d(TAG, "RangingResult failed.");
                queneNextRange();
            }
        }
    }
    //测距定位算法（WLS算法）,目前程序支持4个AP的定位解算
    /*
    * 4个AP，l是3*1，A是3*2，最终结果X是2*1
    * 3个AP，l是2*1，A是2*2,最终结果X是2*1
    * 初始化可以和rttrange联系起来，但是具体那些AP被扫到不确定，需要一个循环把相应的坐标拿出来
    *
    * */
    private void LeastSquareMethod(double[] rttrange,String[] rttMac) {
        int len=rttrange.length;
        double[][] l=initialarray(len-1,1);//0706
        double[][] A=initialarray(len-1,2);
//        double[][] rttrefer=new double[len][2];
//        double[][] l={{0},{0},{0}};
//        double[][] A={{0,0},{0,0},{0,0}};
        double[][] x={{0},{0}};
        //需要增加实时测距数据补偿，主要是LS拟合系数0.0035，0.0648，0.4402
        for(int i=0;i<rttrange.length;i++){
            if(rttrange[i]!=0){
                rttrange[i]=rttrange[i]+0.0039*rttrange[i]*rttrange[i]+0.1231*rttrange[i]+0.18;
            }
//            rttrange[i]=rttrange[i]+0.0039*rttrange[i]*rttrange[i]+0.1231*rttrange[i]+0.18;
        }
        double[][] w=calcluateDistanceweight(rttrange);
        double[][] rttrefer=selectrttposition(RttPointMap,rttrange,rttMac);//参考点坐标
        //需要把rttrefer挑选出来
        for(int i=1;i<rttrange.length;i++){
            l[i-1][0]=(rttrange[i]*rttrange[i]-rttrange[0]*rttrange[0]+rttrefer[0][0]*rttrefer[0][0]+
                    rttrefer[0][1]*rttrefer[0][1]-rttrefer[i][0]*rttrefer[i][0]-rttrefer[i][1]*rttrefer[i][1])/2;
            A[i-1][0]=rttrefer[0][0]-rttrefer[i][0];
            A[i-1][1]=rttrefer[0][1]-rttrefer[i][1];
        }
        MatrixClass matrixClass_a=new MatrixClass(A);
        Matrix matrix_a=new Matrix(A);
        Matrix matrix_l=new Matrix(l);
        Matrix matrix_w=new Matrix(w);
        Matrix X=new Matrix(x);
        X=((((matrix_a.transpose().times(matrix_w).times(matrix_a).inverse()))
                .times(matrix_a.transpose())).times(matrix_w)).times(matrix_l);
//        X=((matrix_a.transpose().times(matrix_a).inverse()).times(matrix_a.transpose())).times(matrix_l);
        Log.d(TAG,"x...."+X.getMatrix(0,1,0,0));
        Matrix matrix=X.getMatrix(0,1,0,0);
        textView4.setText("X is: "+matrix.get(0,0)+"\n"+"Y is: "+matrix.get(1,0));
        Log.d(TAG,"Azimuth is "+Azimuth+" "+stepLength);
//        //判断条件
//        if(stepDectFsm.StepDect(accVal)&&stepLength>0.5){
//            double[][] pdrim_coor=pdrcalcualte();
//            Matrix matrix_pdr=new Matrix(pdrim_coor);
//            X=X.plus(matrix_pdr).times(0.5);
//            Log.d(TAG,"x...."+X.getMatrix(0,1,0,0));
//            textView10.setText("coordinate is: "+X.getMatrix(0,1,0,0));
//        }else{
//            Log.d(TAG,"x...."+X.getMatrix(0,1,0,0));
//            textView10.setText("coordinate is: "+X.getMatrix(0,1,0,0));
//        }

    }
    //循环拿到选择到的AP的坐标
    private double[][] selectrttposition(HashMap<String,Point> hashMap,double[] rttrange,String[] rttMac){
        int len=rttrange.length;
//        double[][] position=new double[len][2];
        double[][] position=initialarray(len,2);
        //找距离不是0的键0707
        int index=0;
        for(int i=0;i<rttrange.length;i++){
            double d=rttrange[i];
//            Point point=new Point();
            if(d>0){
                String str=rttMac[i];
                for(Map.Entry<String,Point> entry:hashMap.entrySet()){
                    String s=entry.getKey();
                    if(s.equals(str)){
                        position[index][0]=entry.getValue().getX();
                        position[index][1]=entry.getValue().getY();
                        }
                }
////                point=hashMap.get(str);
//                position[index][0]=point.getX();
//                position[index][1]=point.getY();
            }
            index=index+1;
        }
        return position;
    }
    //计算距离的权重
    private double[][] calcluateDistanceweight(double[] rttrange) {
        int len=rttrange.length;
        double[][] weight=initialarray(len-1,len-1);
//        double[][] weight={{0,0,0},{0,0,0},{0,0,0}};
        double sum_distance=0d;
        for(int i=0;i<rttrange.length;i++){
            sum_distance=sum_distance+(rttrange[i]-rttrange[0]);
        }
        //权重的计算0707
        for(int i=0;i<len-1;i++){
            weight[i][i]=1/((rttrange[i+1]-rttrange[0])/sum_distance);
        }
        return weight;
    }
    //初始化矩阵
    private double[][] initialarray(int m,int n){
        double[][] a=new double[m][n];
        for(int i=0;i<m;i++){
            for(int j=0;j<n;j++){
                a[i][j]=0;
            }
        }
        return a;
    }
    //写入MEMS数据
    private void WritememsFileSdcard(String message) {
        try{
            //创建文件夹
            sdPath = Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator;
            File file = new File(sdPath+FileName.strmems+File.separator);
            if(!file.exists()){
                file.mkdir();
            }
            //创建文件并写入
            File file1=new File(sdPath+FileName.strmems+File.separator+fileMemsName+".txt");
            if (!file1.exists()) {
                file1.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(file1,true);
            fos.write(message.getBytes());
            fos.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    //写入测距数据
    private void WriteFileSdcard(String message) {
        try{
            //创建文件夹
            sdPath = Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator;
            File file = new File(sdPath+FileName.str+File.separator);
            if(!file.exists()){
                file.mkdir();
            }
            //创建文件并写入
            File file1=new File(sdPath+FileName.str+File.separator+fileName+".txt");
            if (!file1.exists()) {
                file1.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(file1,true);
            fos.write(message.getBytes());
            fos.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    //PDR方法推算坐标
    private double[][] pdrcalcualte(){
       double x=16.7d,y=3.2d;
       x=x+stepLength*Math.cos(Azimuth);
       y=y+stepLength*Math.sin(Azimuth);
       pdrcoor[0][0]=x;
       pdrcoor[1][0]=y;
       return pdrcoor;
    }

    //权限申请方法
    private void requestApplicationPermission() {
        need_permission = new String[]{
                Manifest.permission.CHANGE_NETWORK_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.READ_LOGS,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
        boolean permission_ok = true;
        for (String permission : need_permission) {
            if (ContextCompat.checkSelfPermission(this,
                    permission) != PackageManager.PERMISSION_GRANTED) {
                permission_ok = false;
//                mTextView.append(String.valueOf(permission_ok)+"\n");
            }
        }
        if (!permission_ok) {
            ActivityCompat.requestPermissions(this, need_permission, 1);
        }
    }

    //倒计时
    private class TimeCount extends CountDownTimer{

        /**
         * @param millisInFuture    The number of millis in the future from the call
         *                          to {@link #start()} until the countdown is done and {@link #onFinish()}
         *                          is called.
         * @param countDownInterval The interval along the way to receive
         *                          {@link #onTick(long)} callbacks.
         */
        public TimeCount(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            editText.setText(millisUntilFinished/1000+"");
        }

        @Override
        public void onFinish() {
            writeFile=false;
        }
    }
}
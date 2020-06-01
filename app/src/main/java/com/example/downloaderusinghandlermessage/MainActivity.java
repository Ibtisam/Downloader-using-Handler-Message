package com.example.downloaderusinghandlermessage;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    private Button b_dwl;
    private Button b_opf;
    private EditText et_url;
    private EditText et_des;
    private ProgressBar pb_file;
    private String filePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pb_file = findViewById(R.id.pb_file);
        //setting progress bar progress to 0
        pb_file.setProgress(0);
        et_url = findViewById(R.id.et_url);
        et_des = findViewById(R.id.et_des);
        b_dwl = findViewById(R.id.b_dwl);
        b_opf = findViewById(R.id.b_opf);

        //download button click listener
        b_dwl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //checking permissions and requesting if required
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                } else {
                    startDownload();
                }
            }
        });

        b_opf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //getting Uri path from FileProvider of this app
                Uri path = FileProvider.getUriForFile(getApplicationContext(), BuildConfig.APPLICATION_ID + ".provider", new File(filePath));
                //Creating intent for action ACTION_VIEW
                Intent intent = new Intent(Intent.ACTION_VIEW);
                //Setting the data for intent i.e. the file path
                intent.setData(path);
                //To grant permission to other apps for reading this apps provided files
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                //If no default program is set to open the file open the chooser dialog
                Intent chooser = Intent.createChooser(intent, "Choose an app to open with: ");
                startActivity(chooser);
            }
        });
    }

    public void startDownload() {
        b_opf.setEnabled(false);
        b_dwl.setEnabled(false);
        pb_file.setProgress(0);
        URL url_dwl = null;
        try {
            url_dwl = new URL(et_url.getText().toString());

            //Creating a handler for UI Thread
            Handler uiThreadHandler = new UIHandler(Looper.getMainLooper());

            FileDownloader dwldr = new FileDownloader(url_dwl, uiThreadHandler);
            //Creating a worker thread for our Runnable FileDownloader
            Thread th_dwl = new Thread(dwldr);
            //Starting the thread
            th_dwl.start();

        } catch (MalformedURLException e) {
            Toast.makeText(getApplicationContext(), "URL not valid", Toast.LENGTH_SHORT).show();
            b_dwl.setEnabled(true);
        }
    }

    //Custom Handler for UI Thread to handle messages from worker thread
    class UIHandler extends Handler{
        public UIHandler(@NonNull Looper looper) {
            super(looper);
        }

        //For handling messages from the worker thread
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 0:
                    Toast.makeText(MainActivity.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case 1:
                    et_des.setText((String) msg.obj);
                    break;
                case 2:
                    pb_file.setProgress((int) msg.obj);
                    break;
                case 3:
                    b_opf.setEnabled((boolean) msg.obj);
                    b_dwl.setEnabled((boolean) msg.obj);
                    break;
            }
        }
    }


    class FileDownloader implements Runnable {
        private URL url_dwl;
        private Handler uiThreadHandler;
        private Message msg;
        private String reply;
        private int fileLength;
        private long total;

        public FileDownloader(URL url_dwl, Handler uiThreadHandler) {
            this.url_dwl = url_dwl;
            this.uiThreadHandler = uiThreadHandler;
        }

        //this method will be invoked when the Thread is started
        @Override
        public void run() {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) url_dwl.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    //using UI Thread handler to send Message
                    //creating the message
                    msg = uiThreadHandler.obtainMessage(0, "Connection Error");
                    //sending the message
                    uiThreadHandler.sendMessage(msg);

                    Thread.currentThread().interrupt();
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                fileLength = connection.getContentLength();
                String link = url_dwl.toString();
                String f_name = link.substring(link.lastIndexOf("/") + 1, link.length());
                reply = "";
                reply += "File Name: " + f_name + "\nFile Length: " + fileLength / 1048576 + "Mb";
                //using UI Thread handler to send Message
                //creating the message
                msg = uiThreadHandler.obtainMessage(1, reply);
                //sending the message
                uiThreadHandler.sendMessage(msg);

                // download the file
                input = connection.getInputStream();
                String path = "";
                //checking is an external storage is available
                if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                    //getting the path of external downloads directory
                    path = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getPath();
                    reply += "\nDownloaded at: " + path + "/" + f_name;
                    //using UI Thread handler to send Message
                    //creating the message
                    msg = uiThreadHandler.obtainMessage(1, reply);
                    //sending the message
                    uiThreadHandler.sendMessage(msg);

                    filePath = path + "/" + f_name;
                    output = new FileOutputStream(path + "/" + f_name);

                    byte data[] = new byte[4096];
                    total = 0;
                    int count;
                    //reading the file from internet and writing it to the path
                    while ((count = input.read(data)) != -1) {
                        // allow canceling with back button
                        total += count;
                        // publishing the progress....
                        if (fileLength > 0) {// only if total length is known
                            //setting the progress of progress bar
                            //using UI Thread handler to send Message
                            //creating the message
                            msg = uiThreadHandler.obtainMessage(2, (int) (total * 100 / fileLength));
                            //sending the message
                            uiThreadHandler.sendMessage(msg);
                        }
                        output.write(data, 0, count);
                    }
                    //using UI Thread handler to send Message
                    //creating the message
                    msg = uiThreadHandler.obtainMessage(3, true);
                    //sending the message
                    uiThreadHandler.sendMessage(msg);
                } else {
                    //using UI Thread handler to send Message
                    //creating the message
                    msg = uiThreadHandler.obtainMessage(0, "Media not present");
                    //sending the message
                    uiThreadHandler.sendMessage(msg);
                }

            } catch (final Exception e) {
                //using UI Thread handler to send Message
                //creating the message
                msg = uiThreadHandler.obtainMessage(0, "Some error occurred");
                //sending the message
                uiThreadHandler.sendMessage(msg);
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    startDownload();
                } else {
                    // permission denied, boo!
                    Toast.makeText(this, "Please grant permissions", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }
}

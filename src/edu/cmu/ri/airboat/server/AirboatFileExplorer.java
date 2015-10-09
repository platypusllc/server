package edu.cmu.ri.airboat.server;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
/**
 * Created by hnx on 7/10/14.
 */

public class AirboatFileExplorer extends Dialog implements android.view.View.OnClickListener{

    private ListView list;
    ArrayAdapter<String> Adapter;
    ArrayList<String> arr=new ArrayList<String>();

    Context context;
    private String path;

    private TextView title;
    private EditText et;
    private Button home,back,cancel;

    private String[] fileType = null;

    public AirboatListener mListener;

    /**
     * @param context
     * @param fileType fileType filter
     * @param myListener an listener that change the father activity
     */
    public AirboatFileExplorer(Context context,String[]fileType, AirboatListener myListener) {
        super(context);
        this.context = context;
        this.fileType = fileType;
        this.mListener = myListener;
    }
    /* (non-Javadoc)
     * @see android.app.Dialog#dismiss()
     */
    @Override
    public void dismiss() {
        super.dismiss();
    }
    /* (non-Javadoc)
     * @see android.app.Dialog#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.filexplorer);

        path = getRootDir();
        arr = (ArrayList<String>) getDirs(path);
        Adapter = new ArrayAdapter<String>(context,android.R.layout.simple_list_item_1, arr);

        list = (ListView)findViewById(R.id.list_dir);
        list.setAdapter(Adapter);

        list.setOnItemClickListener(lvLis);

        home = (Button) findViewById(R.id.btn_home);
        home.setOnClickListener(this);

        back = (Button) findViewById(R.id.btn_back);
        back.setOnClickListener(this);

        cancel = (Button) findViewById(R.id.btn_cancel);
        cancel.setOnClickListener(this);

        et = new EditText(context);
        et.setWidth(240);
        et.setHeight(70);
        et.setGravity(Gravity.CENTER);
        et.setPadding(0, 2, 0, 0);
        et.setText("wfFileName");
//		title = (TextView) findViewById(R.id.dir_str);
//		title.setText(path);

    }
    //updating ListView
    Runnable add=new Runnable(){

        @Override
        public void run() {
            arr.clear();
            List<String> temp = getDirs(path);
            for(int i = 0;i < temp.size();i++)
                arr.add(temp.get(i));
            Adapter.notifyDataSetChanged();
        }
    };

    private OnItemClickListener lvLis=new OnItemClickListener(){
        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                long arg3) {
            String temp = (String) arg0.getItemAtPosition(arg2);
//System.out.println("OnItemClick path1:"+path);
            if(temp.equals(".."))
                path = getSubDir(path);
            else if(path.equals("/"))
                path = path+temp;
            else
                path = path+"/"+temp;

//System.out.println("OnItemClick path2"+path)
            File file=new File(path);
            //if click a file, dismiss and return its path
            if(file.isFile()){
                dismiss();
                //Toast.makeText(context, path, Toast.LENGTH_SHORT).show();
                mListener.refreshActivity(path);
            }
            Handler handler=new Handler();
            handler.post(add);
        }
    };

    private List<String> getDirs(String ipath){
        List<String> file = new ArrayList<String>();
//System.out.println("GetDirs path:"+ipath);
        File[] myFile = new File(ipath).listFiles();
        if(myFile == null){
            file.add("..");

        }else
            for(File f: myFile){
                //apply filters
                if(f.isDirectory()){
                    String tempf = f.toString();
                    int pos = tempf.lastIndexOf("/");
                    String subTemp = tempf.substring(pos+1, tempf.length());
//					String subTemp = tempf.substring(path.length(),tempf.length());
                    file.add(subTemp);
//System.out.println("files in dir:"+subTemp);
                }
                //过滤知道类型的文件
                if(f.isFile() && fileType != null){
                    for(int i = 0;i< fileType.length;i++){
                        int typeStrLen = fileType[i].length();

                        String fileName = f.getPath().substring(f.getPath().length()- typeStrLen);
                        if (fileName.toLowerCase().equals(fileType[i])) {
                            file.add(f.toString().substring(path.length()+1,f.toString().length()));
                        }
                    }
                }
            }

        if(file.size()==0)
            file.add("..");

//		System.out.println("file[0]:"+file.get(0)+" File size:"+file.size());
        return file;
    }
    /* (non-Javadoc)
     * @see android.view.View.OnClickListener#onClick(android.view.View)
     */
    @Override
    public void onClick(View v) {
        if(v.getId() == home.getId()){
            path = getRootDir();
            Handler handler=new Handler();
            handler.post(add);
        }else if(v.getId() == back.getId()){
            path = getSubDir(path);
            Handler handler=new Handler();
            handler.post(add);
        }else if(v.getId() == cancel.getId()){
            dismiss();
        }


    }

    private String getSDPath(){
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(android.os.Environment.MEDIA_MOUNTED);   //detect whether a SD card exist
        if(sdCardExist)
        {
            sdDir = Environment.getExternalStorageDirectory();//get the root path
        }
        if(sdDir == null){
//Toast.makeText(context, "No SDCard inside!",Toast.LENGTH_SHORT).show();
            return null;
        }
        return sdDir.toString();

    }

    private String getRootDir(){
        String root = "/";

        path = getSDPath();
        if (path == null)
            path="/";

        return root;
    }

    private String getSubDir(String path){
        String subpath = null;

        int pos = path.lastIndexOf("/");

        if(pos == path.length()){
            path = path.substring(0,path.length()-1);
            pos = path.lastIndexOf("/");
        }

        subpath = path.substring(0,pos);

        if(pos == 0)
            subpath = "/";

        return subpath;
    }
}

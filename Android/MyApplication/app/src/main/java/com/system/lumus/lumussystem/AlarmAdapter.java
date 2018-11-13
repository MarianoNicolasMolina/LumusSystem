package com.system.lumus.lumussystem;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class AlarmAdapter extends ArrayAdapter<Calendar>{
    private AlarmaInterface alarmaInterface;

    public interface AlarmaInterface{
        void sendAlarmaOn(int pos);
        void sendAlarmaOff(int pos);
    }

    public void setAlarmaInterface(AlarmaInterface alarmaInterface) {
        this.alarmaInterface = alarmaInterface;
    }

    public AlarmAdapter(Context context, ArrayList<Calendar> records) {
        super(context,0, records);
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent){
        Calendar item = getItem(position);

        if(convertView == null){
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.listview_alarma,parent,false);
        }

        TextView list_item = convertView.findViewById(R.id.list_txt);
        Switch list_switch = convertView.findViewById(R.id.list_switch);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");

        list_item.setText(sdf.format(item.getTime()));

        list_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
               if(isChecked){
                   alarmaInterface.sendAlarmaOn(position);
               }else{
                   alarmaInterface.sendAlarmaOff(position);
               }
            }
        });

        return convertView;
    }

}

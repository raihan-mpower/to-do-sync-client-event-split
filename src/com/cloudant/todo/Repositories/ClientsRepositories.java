package com.cloudant.todo.Repositories;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.cloudant.todo.model.Client;
import com.cloudant.todo.model.Events;
import com.google.gson.Gson;

import java.util.Map;

/**
 * Created by raihan on 3/15/16.
 */
public class ClientsRepositories extends SQLiteOpenHelper {
    private String common_SQL = "CREATE TABLE common(_id INTEGER PRIMARY KEY AUTOINCREMENT,details VARCHAR)";
    public static final String ID_COLUMN = "_id";
    public static final String Relational_ID = "baseEntityId";
    public static final String propertyDETAILS_COLUMN = "propertydetails";
    public static final String attributeDETAILS_COLUMN = "attributedetails";
    public String TABLE_NAME = "Clients";
    public String [] additionalcolumns;
    public ClientsRepositories(Context context, String[] columns) {
        super(context, "test_convert", null, 1);
        additionalcolumns = columns;
        common_SQL = "CREATE TABLE IF NOT EXISTS "+ TABLE_NAME + "(_id INTEGER PRIMARY KEY AUTOINCREMENT,baseEntityId VARCHAR,";
        for(int i = 0;i<columns.length;i++){
            common_SQL = common_SQL+ columns[i] + " VARCHAR,";
        }
        common_SQL = common_SQL +"attributedetails VARCHAR, propertydetails VARCHAR)";
    }
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        onCreate(db);
    }


    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(common_SQL);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }

    public ContentValues createValuesFor(Client common) {
        ContentValues values = new ContentValues();
        values.put(Relational_ID, common.getBaseEntityID());
        values.put(propertyDETAILS_COLUMN, new Gson().toJson(common.getPropertyDetailsMap()));
        values.put(attributeDETAILS_COLUMN, new Gson().toJson(common.getAttributesDetailsMap()));
        for (Map.Entry<String,String> entry : common.getAttributesColumnsMap().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            values.put(key,value);
            // do stuff
        }
        for (Map.Entry<String,String> entry : common.getPropertyColumnMap().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            values.put(key,value);
            // do stuff
        }
        return values;
    }
    public void insertValues(ContentValues values){
        getWritableDatabase().insert(TABLE_NAME,null,values);
    }



}


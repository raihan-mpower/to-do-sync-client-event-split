package com.cloudant.todo.Repositories;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.cloudant.todo.model.Events;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Raihan Ahmed on 4/15/15.
 */
public class EventsRepositories extends SQLiteOpenHelper {
    private String common_SQL = "CREATE TABLE common(_id INTEGER PRIMARY KEY AUTOINCREMENT,details VARCHAR)";
    public static final String ID_COLUMN = "_id";
    public static final String Relational_ID = "baseEntityId";
    public static final String obsDETAILS_COLUMN = "obsdetails";
    public static final String attributeDETAILS_COLUMN = "attributedetails";
    public String TABLE_NAME = "common";
    public String [] additionalcolumns;
    public EventsRepositories(Context context,String tablename, String[] columns) {
        super(context, "test_convert", null, 1);
        additionalcolumns = columns;
        TABLE_NAME = tablename;
        common_SQL = "CREATE TABLE IF NOT EXISTS "+ TABLE_NAME + "(_id INTEGER PRIMARY KEY AUTOINCREMENT,baseEntityId VARCHAR,";
        for(int i = 0;i<columns.length;i++){
            common_SQL = common_SQL+ columns[i] + " VARCHAR,";
        }
        common_SQL = common_SQL +"attributedetails VARCHAR, obsdetails VARCHAR)";
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

    public ContentValues createValuesFor(Events common) {
        ContentValues values = new ContentValues();
        values.put(Relational_ID, common.getBaseEntityID());
        values.put(obsDETAILS_COLUMN, new Gson().toJson(common.getObsDetailsMap()));
        values.put(attributeDETAILS_COLUMN, new Gson().toJson(common.getAttributesDetailsMap()));
        for (Map.Entry<String,String> entry : common.getAttributesColumnsMap().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            values.put(key,value);
            // do stuff
        }
        for (Map.Entry<String,String> entry : common.getObsColumnsMap().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            values.put(key,value);
            // do stuff
        }
        return values;
    }
    public void insertValues(ContentValues values){
        getWritableDatabase().insert("Events",null,values);
    }



}

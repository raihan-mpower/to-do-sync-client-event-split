package com.cloudant.todo.Jsonprocessor;

import com.cloudant.todo.model.Events;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by raihan on 3/15/16.
 */
public class EventsProcessor {
    String baseEntityID;
    Map<String,String> attributesDetailsMap = new HashMap<String, String>();
    Map<String,String> attributesColumnsMap = new HashMap<String, String>();
    Map<String,String> ObsColumnsMap = new HashMap<String, String>();
    Map<String,String> ObsDetailsMap = new HashMap<String, String>();

    public EventsProcessor (JSONObject EventMapConfig,JSONObject EventJson){
        try {
            JSONArray attributes = EventMapConfig.getJSONArray("attributes");
            JSONArray obs = EventMapConfig.getJSONArray("obs");


            Iterator<?> keys = EventJson.keys();

            while( keys.hasNext() ) {
                String key = (String)keys.next();
                if(key.equalsIgnoreCase("baseEntityID")){
                    baseEntityID = EventJson.getString(key);
                }else {
                    if (key.equalsIgnoreCase("obs")) {
                        ProcessObservations(obs, EventJson.getJSONArray("obs"));
//                    baseEntityID = EventJson.getString(key);
                    }else {
                        if (isAttributeColumn(key, attributes)) {
                            attributesColumnsMap.put(key, EventJson.getString(key));
                        } else {
                            attributesDetailsMap.put(key, EventJson.getString(key));
                        }
                    }
                }
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    public Events createEventObject(){
        Events event = new Events(baseEntityID,attributesDetailsMap,attributesColumnsMap,ObsColumnsMap,ObsDetailsMap);
        return event;
    }
    private void ProcessObservations(JSONArray obsColumns, JSONArray observationsInForm) {
        try {
            for (int i = 0; i < observationsInForm.length(); i++) {
                JSONObject observationObject = observationsInForm.getJSONObject(i);
                if(isInObservationColumn(observationObject.getString("formSubmissionField"),obsColumns)){
                ObsColumnsMap.put(observationObject.getString("formSubmissionField"),observationObject.getString("values"));
                }else{
                    ObsDetailsMap.put(observationObject.getString("formSubmissionField"),observationObject.getString("values"));

                }
            }
        }catch (Exception e){

        }
    }

    private boolean isInObservationColumn(String formSubmissionField, JSONArray obsColumns) {
        boolean returnboolean = false;
        if(obsColumns.length() == 0 ){
            return returnboolean;
        }
        for(int i = 0;i<obsColumns.length();i++){
            try {
                JSONObject obscolumnsObject = obsColumns.getJSONObject(i);
                if(obscolumnsObject.getString("name").equalsIgnoreCase(formSubmissionField)){
                    returnboolean = true;
                    i = obsColumns.length();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
        return returnboolean;
    }


    private boolean isAttributeColumn(String key, JSONArray attributes) {
        boolean returnboolean = false;
        if(attributes.length() == 0 ){
            return returnboolean;
        }
        for(int i = 0;i<attributes.length();i++){
            try {
                JSONObject attributecolumns = attributes.getJSONObject(i);
               if(attributecolumns.getString("name").equalsIgnoreCase(key)){
                   returnboolean = true;
                   i = attributecolumns.length();
               }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
        return returnboolean;
    }
}

package com.se491.app.two2er.HelperObjects;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by eoliv on 2/28/2017.
 */

public class UserObject {
    public String id = "";
    public String fname = "";
    public String lname = "";
    public String age = "";
    public String email = "";
    public String userImage = "";
    public double dLong = 0.0;
    public double dLat = 0.0;

    public UserObject(){}

    public UserObject(String id, String fname, String lname, String age, String email, String userImage){
        this.id = id;
        this.fname = fname;
        this.lname = lname;
        this.age = age;
        this.email = email;
        this.userImage = userImage;
    }

    public UserObject(JSONObject user) throws JSONException {
        this.id = user.getString("_id");
        String[] fullName = user.getString("name").split(" ");
        Log.e("Inside UserObject", "My user fullName: " + fullName.length);
        Log.e("Inside UserObject", "My user fullName: " + user.getString("name"));

        if(fullName.length >= 1) {
            this.fname = fullName[0];
        }
        if(fullName.length == 2) {
            this.lname = fullName[1];
        }

        this.age = user.getString("age");
        this.email = user.getString("email");
        this.userImage = user.getString("image_url");
        //this.userImage = "https://i.stack.imgur.com/cEdDG.png";


        // GeoJSON node in JSON Object
        JSONObject location = user.getJSONObject("location");
        String tempCoords = location.getString("coordinates");

        String[] coords = tempCoords.replace("[", "").replace("]", "").split(",");
        //Split the array into the proper double varaibales:
        this.dLong = Double.parseDouble(coords[0]);
        this.dLat = Double.parseDouble(coords[1]);
    }
}
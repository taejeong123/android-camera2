package com.example.bitflow;

import android.content.res.AssetManager;
import android.content.res.Resources;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class ItemList {

    public static String[] typeList = new String[]{"Coin", "Paper", "MIX", "Coin", "Paper", "MIX"};
    public static String[] fbList = new String[]{"Front", "Back"};
    public static String[] distanceCoinList = new String[]{"7cm", "10cm"};
    public static String[] distanceOtherList = new String[]{"20cm", "23cm"};

    public static String[] getDegreeList() {
        ArrayList<String> degreeList = new ArrayList<>();

        for (int i = 0; i <= 350; i += 10) {
            degreeList.add(i + "°");
        }

        String[] newDegreeList = new String[degreeList.size()];
        newDegreeList = degreeList.toArray(newDegreeList);
        return newDegreeList;
    }

    public static JSONObject getNatCodeNUnitJson(Resources resources) {
        JSONObject jsonObject = null;

        AssetManager assetManager = resources.getAssets();

        try {
            InputStream inputStream = assetManager.open("jsons/natCode_unit.json");
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader reader = new BufferedReader(inputStreamReader);

            StringBuffer buffer = new StringBuffer();
            String line = reader.readLine();
            while (line != null) {
                buffer.append(line + "\n");
                line = reader.readLine();
            }

            jsonObject = new JSONObject(buffer.toString());

        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        return jsonObject;
    }
}

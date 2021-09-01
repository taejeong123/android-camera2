package com.example.bitflow;

import android.content.Context;
import android.widget.NumberPicker;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class Utils {

    public static void displayMessage(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    public static void setDegreePicker(NumberPicker numberPicker, JSONObject jsonObject) {

        ArrayList<String> codeList = new ArrayList<>();

        try {
            JSONArray arr = jsonObject.getJSONArray("info");

            for (int i = 0; i < arr.length(); i++) {
                JSONObject jsonItem = arr.getJSONObject(i);

                String code = jsonItem.getString("code");

                codeList.add(code);
            }

            JSONArray unit = arr.getJSONObject(0).getJSONArray("unit");

            switch (numberPicker.getId()) {
                case R.id.nat_code_picker: {
                    String[] newCodeList = new String[codeList.size()];
                    newCodeList = codeList.toArray(newCodeList);
                    setDegreePicker(numberPicker, newCodeList);
                    break;
                }
                case R.id.unit_picker: {
                    String[] newUnitList = new String[unit.length()];
                    for (int i = 0; i < unit.length(); i++) {
                        newUnitList[i] = unit.getString(i);
                    }
                    setDegreePicker(numberPicker, newUnitList);
                    break;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void setDegreePicker(NumberPicker numberPicker, String[] list) {
        numberPicker.setMinValue(0);
        numberPicker.setMaxValue(list.length - 1);
        numberPicker.setDisplayedValues(list);
    }

    public static String getFileName(MoneyVO moneyVO) {
        String idx = moneyVO.getIdx();
        String code = moneyVO.getNatCode();
        String unit = moneyVO.getUnit();
        String fb = moneyVO.getFb();
        String distance = moneyVO.getDistance();
        String degree = moneyVO.getDegree();

        if (idx.equals("")) {
            return null;
        }

        idx = String.format("%05d", Integer.parseInt(idx));
        unit = unit.replaceAll("\\.", "");
        distance = distance.replace("cm", "");
        degree = degree.replace("°", "");

        if (fb.equals("Front")) { fb = "F"; }
        else if (fb.equals("Back")) { fb = "B"; }

        if (unit.contains("(구)")) {
            unit = unit.replaceAll("\\(구\\)", "o");
        } else if (unit.contains("(신)")) {
            unit = unit.replaceAll("\\(신\\)", "n");
        } else if (unit.contains("(동전)")) {
            unit = unit.replaceAll("\\(동전\\)", "c");
        }

        String fileName = idx + "_" + code + "_" + unit + "_" + fb + "_" + distance + "_" + degree + ".jpg";
        return fileName;
    }
}
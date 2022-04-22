package com.example.bleApp;

import java.util.UUID;

public class Constants {
    public static String SERVICE_STRING = "066a76ed-35b9-41de-a2b5-8021fb832dbb";
    public static UUID SERVICE_UUID = UUID.fromString(SERVICE_STRING);

    public static String CHARACTERISTIC_UUID_STRING = "066a76ed-35b9-41de-a2b5-8021fb832dbb";
    public static UUID CHARACTERISTIC_UUID = UUID.fromString(CHARACTERISTIC_UUID_STRING);

    public static String CHARACTERISTIC_ECHO_STRING = "066a76ed-35b9-41de-a2b5-8021fb832dbb";
    public static UUID CHARACTERISTIC_ECHO_UUID = UUID.fromString(CHARACTERISTIC_ECHO_STRING);

    public static String CHARACTERISTIC_TIME_STRING = "066a76ed-35b9-41de-a2b5-8021fb832dbb";
    public static UUID CHARACTERISTIC_TIME_UUID = UUID.fromString(CHARACTERISTIC_TIME_STRING);

    public static String CLIENT_CONFIGURATION_DESCRIPTOR_STRING = "00002902-0000-1000-8000-00805f9b34fb";
    public static UUID CLIENT_CONFIGURATION_DESCRIPTOR_UUID = UUID.fromString(CLIENT_CONFIGURATION_DESCRIPTOR_STRING);

    public static final String CLIENT_CONFIGURATION_DESCRIPTOR_SHORT_ID = "2902";

    public static final long SCAN_PERIOD = 5000;

    public static final String XEE_KIDS_MATCH_CONTROL = "XEE_KIDS_SMART_WATCH";
    public static final String XEE_KIDS_FRIEND = "XEE_KIDS_FRIEND";

    public static boolean MATCHED = false;
}

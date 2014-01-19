/*
 * CODENVY CONFIDENTIAL
 * __________________
 *
 *  [2012] - [2013] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.cli;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;

import java.util.Map;
import java.util.HashMap;

/**
 * Helper methods for interacting with Codenvy REST APIs
 *
 */ 
public class RESTAPIHelper {

    public static Map<String, Map<String,String>> API_NAME_PROPERTY_MAP = new HashMap<>();

    public static final String REST_API_AUTH_LOGIN_JSON = "1";
    public static final String REST_API_FACTORY_JSON = "2";
    public static final String REST_API_FACTORY_MULTI_PART = "3";

    private static final String MULTI_PART_CRLF = "\r\n";
    private static final String MULTI_PART_TWO_HYPHENS = "--";
    private static final String MULTI_PART_BOUNDARY =  "*****";

    // Initialize a HashMap to contain the mapping of each REST URL with the appropriate parameters that are required.
    static {
        API_NAME_PROPERTY_MAP.put(REST_API_AUTH_LOGIN_JSON, new HashMap<String, String>() {{
            put("RestURL", "/api/auth/login");
            put("RequestMethod", "POST");
            put("Content-Type", "application/json");
            put("TokenRequired", "false");
        }});

        API_NAME_PROPERTY_MAP.put(REST_API_FACTORY_MULTI_PART, new HashMap<String, String>() {{
            put("RestURL", "/api/factory");
            put("RequestMethod", "POST");
            put("Content-Type", "multipart/form-data;boundary="+MULTI_PART_BOUNDARY);
            put("Content-Disposition", "Content-Disposition: form-data; name=\"factoryUrl\"" + MULTI_PART_CRLF + MULTI_PART_CRLF);
            put("TokenRequired", "true");
        }});
    }

    public static JSONObject callRESTAPIAndRetrieveResponse(CLICredentials cred,
                                                            JSONObject input_data,
                                                            String rest_resource) {
        HttpURLConnection conn = null;
        JSONObject output_data = null;
        DataOutputStream wr = null;
        InputStream errorStream = null;
        InputStreamReader in = null;

        try {

            StringBuffer rest_url = new StringBuffer();
            rest_url.append(cred.getProvider() + API_NAME_PROPERTY_MAP.get(rest_resource).get("RestURL"));

            if (API_NAME_PROPERTY_MAP.get(rest_resource).get("TokenRequired") == "true") {
                rest_url.append("?token=" + cred.getToken());
            }

            // Set up the connection.
            conn = (HttpURLConnection) new URL(rest_url.toString()).openConnection();
  
            conn.setRequestMethod(API_NAME_PROPERTY_MAP.get(rest_resource).get("RequestMethod"));
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", API_NAME_PROPERTY_MAP.get(rest_resource).get("Content-Type")); 
            conn.setRequestProperty("charset", "utf-8");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(false);
            
            wr = new DataOutputStream(conn.getOutputStream());

            if (API_NAME_PROPERTY_MAP.get(rest_resource).get("Content-Disposition") != null) {
                wr.writeBytes(MULTI_PART_TWO_HYPHENS + MULTI_PART_BOUNDARY + MULTI_PART_CRLF);
                wr.writeBytes(API_NAME_PROPERTY_MAP.get(rest_resource).get("Content-Disposition"));
                wr.writeBytes(MULTI_PART_CRLF);
            }
            
            wr.writeBytes(input_data.toString());

            if (API_NAME_PROPERTY_MAP.get(rest_resource).get("Content-Disposition") != null) {
                wr.writeBytes(MULTI_PART_CRLF);
                wr.writeBytes(MULTI_PART_TWO_HYPHENS + MULTI_PART_BOUNDARY + MULTI_PART_TWO_HYPHENS + MULTI_PART_CRLF);
            }

            wr.flush();
            wr.close();

            int responseCode = conn.getResponseCode();

            if (responseCode / 100 != 2) {
                System.out.println("#######################################################");
                System.out.println("### Unexpected REST API response code received: " + responseCode + " ###");
                System.out.println("#######################################################");
                System.out.println("\n");
                System.out.println("Visit http://docs.codenvy.com/api/ for mapping of response codes.");

                errorStream = conn.getErrorStream();
                String message = errorStream != null ? readAndCloseQuietly(errorStream) : "";
                System.out.println(message);
                System.out.println("\n");
            } else {
                in = new InputStreamReader((InputStream) conn.getInputStream());
                JSONParser parser = new JSONParser();
                output_data = (JSONObject) parser.parse(in);
            }

        } catch (UnknownHostException | javax.net.ssl.SSLHandshakeException | java.net.SocketTimeoutException e ) {

            System.out.println("####################################################################");
            System.out.println("### Network issues.  We cannot reach the remote Codenvy host.    ###");
            System.out.println("### Issues can be SSL handshake, Socket Timeout, or uknown host. ###");
            System.out.println("### Use 'codenvy auth -d' to see the profile configuration used. ###");
            System.out.println("####################################################################");

        } catch (IOException | ParseException e ) {
            System.out.println(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }

            if (wr != null) {
                try {
                    wr.close();
                } catch (IOException e) {}
            }

            if (errorStream != null) {
                try {
                    errorStream.close();
                } catch (IOException e) {}
            }

            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {}
            }
        }

        return output_data;

    }


    public static String readStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return null;
        }
    
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
    
        while ((r = inputStream.read(buf)) != -1) {
            bout.write(buf, 0, r);
        }
    
        return bout.toString();
    }

    public static String readAndCloseQuietly(InputStream inputStream) throws IOException {
        try {
            return readStream(inputStream);
        } catch (IOException e) {
            throw e;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
        }
    }
}
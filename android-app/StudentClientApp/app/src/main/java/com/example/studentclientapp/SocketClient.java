package com.example.studentclientapp;

import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.SocketTimeoutException;


public class SocketClient {
    private static final String TAG = "SocketClient";
    private static SocketClient instance;
    private static final String ENCRYPTION_KEY = "0123456789abcdef0123456789abcdef"; // 32 bytes for AES-256
    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";

    // Use your computer's IP - VERY IMPORTANT!
    private String serverIp = "192.168.29.126";
    private int serverPort = 12345;

    // Add these fields
    private String currentUsername = "";
    private int currentUserId = -1;

    public void setUserInfo(String username, int userId) {
        this.currentUsername = username;
        this.currentUserId = userId;
        Log.d(TAG, "User info set: " + username + " (ID: " + userId + ")");
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public int getCurrentUserId() {
        return currentUserId;
    }

    public interface SocketCallback {
        void onResponse(String response);
        void onError(String error);
    }

    private SocketClient() {}

    public static synchronized SocketClient getInstance() {
        if (instance == null) {
            instance = new SocketClient();
        }
        return instance;
    }

    // AES Encryption method
    private String encryptAES(String plainText) {
        try {
            byte[] keyBytes = ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] ivBytes = new byte[16];
            SecureRandom random = new SecureRandom();
            random.nextBytes(ivBytes);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combine IV + encrypted data
            byte[] combined = new byte[ivBytes.length + encrypted.length];
            System.arraycopy(ivBytes, 0, combined, 0, ivBytes.length);
            System.arraycopy(encrypted, 0, combined, ivBytes.length, encrypted.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "AES Encryption error: " + e.getMessage());
            return null;
        }
    }

    // AES Decryption method
    private String decryptAES(String encryptedBase64) {
        try {
            byte[] combined = Base64.decode(encryptedBase64, Base64.NO_WRAP);

            // Extract IV (first 16 bytes)
            byte[] ivBytes = new byte[16];
            System.arraycopy(combined, 0, ivBytes, 0, ivBytes.length);

            // Extract encrypted data
            byte[] encryptedBytes = new byte[combined.length - 16];
            System.arraycopy(combined, 16, encryptedBytes, 0, encryptedBytes.length);

            byte[] keyBytes = ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] decrypted = cipher.doFinal(encryptedBytes);
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.e(TAG, "AES Decryption error: " + e.getMessage());
            return null;
        }
    }

    // Password hashing method
    String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            Log.e(TAG, "Password hashing error: " + e.getMessage());
            return password; // Fallback
        }
    }

    // Replace the testConnection method with this version:
    public void testConnection(final SocketCallback callback) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                Socket socket = null;
                PrintWriter out = null;
                BufferedReader in = null;

                try {
                    Log.d(TAG, "Testing connection to " + serverIp + ":" + serverPort);

                    // Create socket with timeout
                    socket = new Socket();
                    InetSocketAddress address = new InetSocketAddress(serverIp, serverPort);
                    socket.connect(address, 5000);
                    socket.setSoTimeout(3000); // Reduced timeout for test

                    out = new PrintWriter(socket.getOutputStream(), true);
                    // Use InputStreamReader directly instead of BufferedReader
                    InputStreamReader streamReader = new InputStreamReader(socket.getInputStream());
                    StringBuilder responseBuilder = new StringBuilder();
                    char[] buffer = new char[1024];

                    // Send plain "TEST" message
                    Log.d(TAG, "Sending test connection message: TEST");
                    out.println("TEST");
                    out.flush();

                    // Read response character by character with timeout
                    int bytesRead;
                    long startTime = System.currentTimeMillis();
                    long timeout = 3000; // 3 seconds

                    while ((bytesRead = streamReader.read(buffer)) != -1) {
                        responseBuilder.append(buffer, 0, bytesRead);

                        // Check if we have a complete response (look for newline or end of stream)
                        String currentResponse = responseBuilder.toString();
                        if (currentResponse.contains("\n") ||
                                currentResponse.length() >= 200 || // Expected max response size
                                (System.currentTimeMillis() - startTime) > timeout) {
                            break;
                        }

                        // Small delay to avoid CPU spinning
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }

                    String response = responseBuilder.toString().trim();

                    if (response.isEmpty()) {
                        Log.w(TAG, "Empty response received");
                        return "ERROR: Empty response from server";
                    }

                    Log.d(TAG, "Raw response received (length: " + response.length() + "): " +
                            response.substring(0, Math.min(50, response.length())) + "...");

                    // Try to decrypt if it's encrypted
                    if (response.length() > 50) { // Likely encrypted
                        String decrypted = decryptAES(response.trim());
                        if (decrypted != null) {
                            Log.d(TAG, "Decrypted response: " + decrypted);
                            try {
                                JSONObject jsonResponse = new JSONObject(decrypted);
                                String status = jsonResponse.getString("status");
                                String message = jsonResponse.getString("message");
                                return "SUCCESS: " + message + " (Encrypted)";
                            } catch (Exception e) {
                                return "SUCCESS: Connection successful - " +
                                        decrypted.substring(0, Math.min(100, decrypted.length()));
                            }
                        }
                    }

                    // If not encrypted or decryption failed
                    return "SUCCESS: " + response.substring(0, Math.min(100, response.length()));

                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "Socket timeout: " + e.getMessage());
                    return "ERROR: Connection timeout - server may be busy";
                } catch (java.net.ConnectException e) {
                    Log.e(TAG, "Connection refused: " + e.getMessage());
                    return "ERROR: Connection refused. Check if server is running";
                } catch (Exception e) {
                    Log.e(TAG, "Connection test failed: " + e.getMessage());
                    e.printStackTrace();
                    return "ERROR: " + e.getMessage();
                } finally {
                    try {
                        if (out != null) out.close();
                        if (in != null) in.close();
                        if (socket != null) socket.close();
                        Log.d(TAG, "Test connection resources cleaned up");
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing resources: " + e.getMessage());
                    }
                }
            }

            @Override
            protected void onPostExecute(String result) {
                // Ensure we're on UI thread
                if (result.startsWith("SUCCESS")) {
                    callback.onResponse(result);
                } else {
                    callback.onError(result);
                }
            }
        }.execute();
    }

    public void sendRequest(final String command, final JSONObject params, final SocketCallback callback) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                Socket socket = null;
                PrintWriter out = null;
                BufferedReader in = null;

                try {
                    Log.d(TAG, "=== NEW REQUEST ===");
                    Log.d(TAG, "Command: " + command);
                    Log.d(TAG, "Original params: " + (params != null ? params.toString() : "null"));

                    // Create new socket for each request
                    socket = new Socket();
                    InetSocketAddress address = new InetSocketAddress(serverIp, serverPort);
                    socket.connect(address, 5000);

                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    // Create request JSON
                    JSONObject request = new JSONObject();
                    request.put("command", command);

                    // Start with provided params
                    JSONObject finalParams = new JSONObject();
                    if (params != null) {
                        // Copy all params
                        java.util.Iterator<String> keys = params.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            // Hash password for both LOGIN and REGISTER commands
                            if (key.equals("password") && (command.equals("LOGIN") || command.equals("REGISTER"))) {
                                // Hash password before sending
                                finalParams.put(key, hashPassword(params.getString(key)));
                                Log.d(TAG, "✓ Password hashed for " + command);
                            } else {
                                finalParams.put(key, params.get(key));
                            }
                        }
                    }

                    // AUTOMATICALLY ADD AUTHENTICATION PARAMETERS FOR COMMANDS THAT NEED THEM
                    if (command.equals("GET_DATA") || command.equals("SUBMIT_REQUEST") || command.equals("GET_REQUESTS")) {
                        // Add username if we have it and not already in params
                        if (currentUsername != null && !currentUsername.isEmpty() && !finalParams.has("username")) {
                            finalParams.put("username", currentUsername);
                            Log.d(TAG, "✓ Added username to " + command + ": " + currentUsername);
                        }

                        // Add user_id if we have it and not already in params
                        if (currentUserId != -1 && !finalParams.has("user_id")) {
                            finalParams.put("user_id", currentUserId);
                            Log.d(TAG, "✓ Added user_id to " + command + ": " + currentUserId);
                        }
                    }

                    // Add authentication for EXIT command too
                    if (command.equals("EXIT")) {
                        if (currentUsername != null && !currentUsername.isEmpty() && !finalParams.has("username")) {
                            finalParams.put("username", currentUsername);
                            Log.d(TAG, "✓ Added username to EXIT: " + currentUsername);
                        }
                    }

                    request.put("params", finalParams);

                    String requestStr = request.toString();
                    Log.d(TAG, "Full request JSON: " + requestStr);
                    Log.d(TAG, "Request size: " + requestStr.length() + " chars");

                    // Encrypt with AES
                    String encryptedRequest = encryptAES(requestStr);
                    if (encryptedRequest == null) {
                        Log.e(TAG, "❌ Encryption failed!");
                        return "{\"status\":\"error\",\"message\":\"Encryption failed\"}";
                    }

                    Log.d(TAG, "Encrypted size: " + encryptedRequest.length() + " chars");

                    // Send encrypted request with newline
                    out.println(encryptedRequest);
                    out.flush();

                    Log.d(TAG, "Request sent, waiting for response...");

                    // Read response with timeout
                    socket.setSoTimeout(10000); // 10 second timeout
                    String response = in.readLine();

                    Log.d(TAG, "Raw response received, length: " + (response != null ? response.length() : 0));

                    if (response == null || response.isEmpty()) {
                        Log.e(TAG, "❌ No response from server");
                        return "{\"status\":\"error\",\"message\":\"No response from server\"}";
                    }

                    // Try to decrypt with AES
                    String decrypted = decryptAES(response);
                    if (decrypted != null) {
                        Log.d(TAG, "✅ Response decrypted successfully");
                        Log.d(TAG, "Decrypted response: " + decrypted);
                        return decrypted;
                    } else {
                        Log.e(TAG, "❌ AES decryption failed, trying as plain text");
                        // Check if it might be JSON already
                        if (response.trim().startsWith("{")) {
                            return response;
                        } else {
                            return "{\"status\":\"error\",\"message\":\"Failed to decrypt response: " + response.substring(0, Math.min(50, response.length())) + "...\"}";
                        }
                    }

                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "❌ Socket timeout: " + e.getMessage());
                    return "{\"status\":\"error\",\"message\":\"Connection timeout\"}";
                } catch (Exception e) {
                    Log.e(TAG, "❌ Request error: " + e.getMessage());
                    e.printStackTrace();
                    return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";

                } finally {
                    try {
                        if (out != null) out.close();
                        if (in != null) in.close();
                        if (socket != null) socket.close();
                        Log.d(TAG, "=== REQUEST COMPLETE ===");
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing resources: " + e.getMessage());
                    }
                }
            }

            @Override
            protected void onPostExecute(String result) {
                callback.onResponse(result);
            }
        }.execute();
    }

    public void setServerAddress(String ip, int port) {
        this.serverIp = ip;
        this.serverPort = port;
        Log.d(TAG, "Server address set to: " + ip + ":" + port);
    }
}
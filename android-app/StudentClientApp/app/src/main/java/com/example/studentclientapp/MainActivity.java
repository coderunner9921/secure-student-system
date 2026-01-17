package com.example.studentclientapp;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private EditText etUsername, etPassword;
    private Button btnLogin, btnRegister, btnTestConnection;
    private TextView tvStatus;
    private SocketClient socketClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnTestConnection = findViewById(R.id.btnTestConnection);
        tvStatus = findViewById(R.id.tvStatus);


        // Get socket client instance
        socketClient = SocketClient.getInstance();

        // Pre-fill for testing
        etUsername.setText("john123");
        etPassword.setText("password123");

        // Set click listeners
        btnTestConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testConnection();
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRegisterDialog();
            }
        });

        // Initial connection test
        testConnection();
    }

    private void testConnection() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvStatus.setText("🔄 Testing connection...");
                tvStatus.setTextColor(Color.BLUE);
                btnTestConnection.setEnabled(false); // Disable button while testing
            }
        });

        socketClient.testConnection(new SocketClient.SocketCallback() {
            @Override
            public void onResponse(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvStatus.setText("✅ " + response.replace("SUCCESS: ", ""));
                        tvStatus.setTextColor(Color.GREEN);
                        btnTestConnection.setEnabled(true);
                        Toast.makeText(MainActivity.this, "Server connection successful!", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvStatus.setText("❌ " + error.replace("ERROR: ", ""));
                        tvStatus.setTextColor(Color.RED);
                        btnTestConnection.setEnabled(true);
                        Toast.makeText(MainActivity.this, "Connection failed: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void loginUser() {
        debugConnectionInfo();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("🔄 Logging in...");
        tvStatus.setTextColor(Color.BLUE);

        try {
            JSONObject params = new JSONObject();
            params.put("username", username);
            params.put("password", password);

            socketClient.sendRequest("LOGIN", params, new SocketClient.SocketCallback() {
                @Override
                public void onResponse(String response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                JSONObject jsonResponse = new JSONObject(response);

                                if (jsonResponse.getString("status").equals("success")) {
                                    // Login successful
                                    JSONObject data = jsonResponse.getJSONObject("data");
                                    int userId = data.getInt("user_id");
                                    String username = data.getString("username");

                                    tvStatus.setText("✅ Login successful!");
                                    tvStatus.setTextColor(Color.GREEN);

                                    // Go to dashboard
                                    Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                                    intent.putExtra("user_id", userId);
                                    intent.putExtra("username", username);
                                    startActivity(intent);

                                    Toast.makeText(MainActivity.this, "Welcome, " + username + "!", Toast.LENGTH_SHORT).show();
                                } else {
                                    tvStatus.setText("❌ " + jsonResponse.getString("message"));
                                    tvStatus.setTextColor(Color.RED);
                                    Toast.makeText(MainActivity.this,
                                            "Login failed: " + jsonResponse.getString("message"),
                                            Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                // Show raw response for debugging
                                tvStatus.setText("⚠️ Raw response: " + response.substring(0, Math.min(100, response.length())));
                                tvStatus.setTextColor(Color.YELLOW);
                                Toast.makeText(MainActivity.this,
                                        "Debug: " + response,
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("❌ Error: " + error);
                            tvStatus.setTextColor(Color.RED);
                            Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });

        } catch (Exception e) {
            tvStatus.setText("❌ Error creating request");
            tvStatus.setTextColor(Color.RED);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showRegisterDialog() {
        // Go to registration activity
        Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
        startActivity(intent);
    }

    private void debugConnectionInfo() {
        String ip = socketClient.getCurrentUsername(); // Just for debug
        Log.d("ConnectionDebug", "Attempting to connect to server...");
        Log.d("ConnectionDebug", "Username entered: " + etUsername.getText().toString());
        Log.d("ConnectionDebug", "Button clicked at: " + System.currentTimeMillis());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Clear password field when returning
        etPassword.setText("");
    }
}
package com.example.studentclientapp;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;

public class RegisterActivity extends AppCompatActivity {
    private EditText etRegUsername, etRegPassword, etRegEmail;
    private EditText etStudentId, etFullName, etDepartment, etSemester, etGPA;
    private Button btnRegister, btnBackToLogin;
    private TextView tvStatus;
    private SocketClient socketClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize views
        etRegUsername = findViewById(R.id.etRegUsername);
        etRegPassword = findViewById(R.id.etRegPassword);
        etRegEmail = findViewById(R.id.etRegEmail);
        etStudentId = findViewById(R.id.etStudentId);
        etFullName = findViewById(R.id.etFullName);
        etDepartment = findViewById(R.id.etDepartment);
        etSemester = findViewById(R.id.etSemester);
        etGPA = findViewById(R.id.etGPA);
        btnRegister = findViewById(R.id.btnRegister);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);
        tvStatus = findViewById(R.id.tvStatus);

        // Get socket client instance
        socketClient = SocketClient.getInstance();

        // Set click listeners
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerUser();
            }
        });

        btnBackToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    private void registerUser() {
        String username = etRegUsername.getText().toString().trim();
        String password = etRegPassword.getText().toString().trim();
        String email = etRegEmail.getText().toString().trim();
        String studentId = etStudentId.getText().toString().trim();
        String fullName = etFullName.getText().toString().trim();
        String department = etDepartment.getText().toString().trim();
        String semesterStr = etSemester.getText().toString().trim();
        String gpaStr = etGPA.getText().toString().trim();

        // Basic validation
        if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("🔄 Registering...");
        tvStatus.setTextColor(Color.BLUE);

        try {
            JSONObject params = new JSONObject();
            params.put("username", username);
            params.put("password", password);
            params.put("email", email);

            // Add optional student data if provided
            if (!studentId.isEmpty()) params.put("student_id", studentId);
            if (!fullName.isEmpty()) params.put("full_name", fullName);
            if (!department.isEmpty()) params.put("department", department);
            if (!semesterStr.isEmpty()) params.put("semester", Integer.parseInt(semesterStr));
            if (!gpaStr.isEmpty()) params.put("gpa", Double.parseDouble(gpaStr));

            socketClient.sendRequest("REGISTER", params, new SocketClient.SocketCallback() {
                @Override
                public void onResponse(String response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                JSONObject jsonResponse = new JSONObject(response);
                                String status = jsonResponse.getString("status");

                                if (status.equals("success")) {
                                    tvStatus.setText("✅ Registration successful!");
                                    tvStatus.setTextColor(Color.GREEN);

                                    // Auto-login after registration
                                    JSONObject loginParams = new JSONObject();
                                    loginParams.put("username", username);
                                    loginParams.put("password", password);

                                    socketClient.sendRequest("LOGIN", loginParams, new SocketClient.SocketCallback() {
                                        @Override
                                        public void onResponse(String loginResponse) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        JSONObject loginJson = new JSONObject(loginResponse);
                                                        if (loginJson.getString("status").equals("success")) {
                                                            JSONObject data = loginJson.getJSONObject("data");
                                                            int userId = data.getInt("user_id");
                                                            String username = data.getString("username");

                                                            // Go to dashboard
                                                            Intent intent = new Intent(RegisterActivity.this, DashboardActivity.class);
                                                            intent.putExtra("user_id", userId);
                                                            intent.putExtra("username", username);
                                                            startActivity(intent);
                                                            finish();
                                                        }
                                                    } catch (Exception e) {
                                                        // Go to login screen anyway
                                                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                                        startActivity(intent);
                                                        finish();
                                                    }
                                                }
                                            });
                                        }

                                        @Override
                                        public void onError(String error) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    // Registration successful but auto-login failed
                                                    Toast.makeText(RegisterActivity.this,
                                                            "Registered! Please login manually",
                                                            Toast.LENGTH_LONG).show();
                                                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                                    startActivity(intent);
                                                    finish();
                                                }
                                            });
                                        }
                                    });
                                } else {
                                    tvStatus.setText("❌ " + jsonResponse.getString("message"));
                                    tvStatus.setTextColor(Color.RED);
                                    Toast.makeText(RegisterActivity.this,
                                            "Registration failed: " + jsonResponse.getString("message"),
                                            Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                tvStatus.setText("⚠️ Error: " + e.getMessage());
                                tvStatus.setTextColor(Color.YELLOW);
                            }
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("❌ Connection error");
                            tvStatus.setTextColor(Color.RED);
                            Toast.makeText(RegisterActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
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
}
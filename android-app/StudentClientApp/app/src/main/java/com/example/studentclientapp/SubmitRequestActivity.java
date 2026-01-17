package com.example.studentclientapp;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;

public class SubmitRequestActivity extends AppCompatActivity {
    private EditText etRequestTitle, etRequestDescription;
    private Spinner spRequestType;
    private Button btnSubmit, btnCancel;
    private TextView tvStatus;
    private SocketClient socketClient;
    private String username;
    private int userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submit_request);

        // Get user info from intent
        Intent intent = getIntent();
        userId = intent.getIntExtra("user_id", -1);
        username = intent.getStringExtra("username");

        // Initialize views
        etRequestTitle = findViewById(R.id.etRequestTitle);
        etRequestDescription = findViewById(R.id.etRequestDescription);
        spRequestType = findViewById(R.id.spRequestType);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnCancel = findViewById(R.id.btnCancel);
        tvStatus = findViewById(R.id.tvStatus);

        // Get socket client instance
        socketClient = SocketClient.getInstance();
        socketClient.setUserInfo(username, userId);

        // Setup spinner for request types
        setupRequestTypeSpinner();

        // Set click listeners
        btnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitRequest();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void setupRequestTypeSpinner() {
        String[] requestTypes = {
                "Select Request Type",
                "Academic Complaint",
                "Administrative Issue",
                "Financial Matter",
                "Technical Support",
                "Facilities Problem",
                "Library Services",
                "Transportation",
                "Hostel/Accommodation",
                "Campus Safety",
                "Other"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                requestTypes
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRequestType.setAdapter(adapter);
    }

    private void submitRequest() {
        String title = etRequestTitle.getText().toString().trim();
        String description = etRequestDescription.getText().toString().trim();
        String requestType = spRequestType.getSelectedItem().toString();

        // Validation
        if (requestType.equals("Select Request Type")) {
            Toast.makeText(this, "Please select a request type", Toast.LENGTH_SHORT).show();
            return;
        }

        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }

        if (description.isEmpty()) {
            Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show();
            return;
        }

        if (description.length() < 10) {
            Toast.makeText(this, "Description should be at least 10 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("🔄 Submitting request...");
        tvStatus.setTextColor(Color.BLUE);
        btnSubmit.setEnabled(false);

        try {
            JSONObject params = new JSONObject();
            params.put("username", username);
            params.put("user_id", userId);
            params.put("request_type", requestType);
            params.put("title", title);
            params.put("description", description);

            socketClient.sendRequest("SUBMIT_REQUEST", params, new SocketClient.SocketCallback() {
                @Override
                public void onResponse(String response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                JSONObject jsonResponse = new JSONObject(response);
                                String status = jsonResponse.getString("status");

                                if (status.equals("success")) {
                                    tvStatus.setText("✅ " + jsonResponse.getString("message"));
                                    tvStatus.setTextColor(Color.GREEN);

                                    // Show success and clear form
                                    Toast.makeText(SubmitRequestActivity.this,
                                            "Request submitted successfully!",
                                            Toast.LENGTH_LONG).show();

                                    // Clear form
                                    etRequestTitle.setText("");
                                    etRequestDescription.setText("");
                                    spRequestType.setSelection(0);

                                    // Return to dashboard after delay
                                    new android.os.Handler().postDelayed(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    finish();
                                                }
                                            },
                                            2000
                                    );

                                } else {
                                    tvStatus.setText("❌ " + jsonResponse.getString("message"));
                                    tvStatus.setTextColor(Color.RED);
                                    btnSubmit.setEnabled(true);
                                }
                            } catch (Exception e) {
                                tvStatus.setText("⚠️ Error: " + e.getMessage());
                                tvStatus.setTextColor(Color.YELLOW);
                                btnSubmit.setEnabled(true);
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
                            btnSubmit.setEnabled(true);
                            Toast.makeText(SubmitRequestActivity.this,
                                    "Connection error: " + error,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });

        } catch (Exception e) {
            tvStatus.setText("❌ Error creating request");
            tvStatus.setTextColor(Color.RED);
            btnSubmit.setEnabled(true);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
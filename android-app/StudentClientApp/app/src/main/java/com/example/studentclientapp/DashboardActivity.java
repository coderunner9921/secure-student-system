package com.example.studentclientapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class DashboardActivity extends AppCompatActivity {
    private TextView tvWelcome, tvResponse, tvGPA, tvAttendance, tvConnectionStatus, tvUserInfo;
    private CardView cardViewData, cardSubmitRequest, cardViewRequests;
    private CardView cardGPA, cardAttendance;
    private View connectionLight;
    private TextView btnLogout;
    private SocketClient socketClient;
    private int userId;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Get user info from intent
        Intent intent = getIntent();
        userId = intent.getIntExtra("user_id", -1);
        username = intent.getStringExtra("username");

        // Initialize views
        tvWelcome = findViewById(R.id.tvWelcome);
        tvResponse = findViewById(R.id.tvResponse);
        tvGPA = findViewById(R.id.tvGPA);
        tvAttendance = findViewById(R.id.tvAttendance);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvUserInfo = findViewById(R.id.tvUserInfo);
        connectionLight = findViewById(R.id.connectionLight);
        btnLogout = findViewById(R.id.btnLogout);

        cardViewData = findViewById(R.id.cardViewData);
        cardSubmitRequest = findViewById(R.id.cardSubmitRequest);
        cardViewRequests = findViewById(R.id.cardViewRequests);
        cardGPA = findViewById(R.id.cardGPA);
        cardAttendance = findViewById(R.id.cardAttendance);

        // Set welcome message
        tvWelcome.setText("👋 Welcome, " + username + "!");

        // Get socket client and set username
        socketClient = SocketClient.getInstance();
        socketClient.setUserInfo(username, userId);

        // Update connection status
        updateConnectionStatus();

        // Set click listeners
        setupCardAnimations();

        // Setup logout button
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logout();
            }
        });

        // Load initial data
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                loadInitialData();
            }
        }, 500);
    }

    private void setupCardAnimations() {
        CardView[] cards = {cardViewData, cardSubmitRequest, cardViewRequests};

        for (final CardView card : cards) {
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Scale animation
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                                    handleCardClick(v.getId());
                                }
                            });
                }
            });
        }
    }

    private void handleCardClick(int cardId) {
        if (cardId == R.id.cardViewData) {
            viewStudentData();
        } else if (cardId == R.id.cardSubmitRequest) {
            submitRequest();
        } else if (cardId == R.id.cardViewRequests) {
            viewRequests();
        }
    }

    private void updateConnectionStatus() {
        socketClient.testConnection(new SocketClient.SocketCallback() {
            @Override
            public void onResponse(final String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            connectionLight.setBackgroundColor(Color.GREEN);
                            tvConnectionStatus.setText("Connected • AES-256 Active");
                            tvConnectionStatus.setTextColor(Color.GREEN);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            @Override
            public void onError(final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            connectionLight.setBackgroundColor(Color.RED);
                            tvConnectionStatus.setText("Disconnected • Click to retry");
                            tvConnectionStatus.setTextColor(Color.RED);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }

    private void loadInitialData() {
        animateCardEntrance();
        viewStudentData();
    }

    private void animateCardEntrance() {
        final View[] cards = {cardGPA, cardAttendance, cardViewData, cardSubmitRequest, cardViewRequests};

        for (int i = 0; i < cards.length; i++) {
            final int index = i;
            cards[i].setAlpha(0f);
            cards[i].setTranslationY(50f);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    cards[index].animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(500)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
            }, i * 100);
        }
    }

    private void viewStudentData() {
        tvResponse.setText("🔄 Fetching data...");
        tvResponse.setTextColor(Color.BLUE);

        try {
            JSONObject params = new JSONObject();
            params.put("username", username);

            socketClient.sendRequest("GET_DATA", params, new SocketClient.SocketCallback() {
                @Override
                public void onResponse(final String response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                JSONObject jsonResponse = new JSONObject(response);
                                if (jsonResponse.getString("status").equals("success")) {
                                    JSONObject data = jsonResponse.getJSONObject("data");

                                    // Animate numbers
                                    animateNumber(tvGPA, data.getDouble("gpa"));
                                    animateNumber(tvAttendance, data.getDouble("attendance_percentage"));

                                    // Update user info
                                    tvUserInfo.setText("Student ID: " + data.getString("student_id"));

                                    // Format response
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("🎓 Student ID: ").append(data.getString("student_id")).append("\n\n");
                                    sb.append("👤 Name: ").append(data.getString("full_name")).append("\n\n");
                                    sb.append("🏫 Department: ").append(data.getString("department")).append("\n\n");
                                    sb.append("📚 Semester: ").append(data.getInt("semester")).append("\n\n");
                                    sb.append("⭐ GPA: ").append(data.getDouble("gpa")).append("\n\n");
                                    sb.append("📊 Attendance: ").append(data.getDouble("attendance_percentage")).append("%");

                                    tvResponse.setText(sb.toString());
                                    tvResponse.setTextColor(Color.GREEN);

                                    Toast.makeText(DashboardActivity.this, "✅ Data loaded successfully!", Toast.LENGTH_SHORT).show();

                                } else {
                                    tvResponse.setText("❌ " + jsonResponse.getString("message"));
                                    tvResponse.setTextColor(Color.RED);
                                }
                            } catch (Exception e) {
                                tvResponse.setText("⚠️ Error: " + e.getMessage() + "\n\nResponse: " + response);
                                tvResponse.setTextColor(Color.YELLOW);
                            }
                        }
                    });
                }

                @Override
                public void onError(final String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvResponse.setText("❌ Error: " + error);
                            tvResponse.setTextColor(Color.RED);
                        }
                    });
                }
            });

        } catch (Exception e) {
            tvResponse.setText("❌ Error: " + e.getMessage());
            tvResponse.setTextColor(Color.RED);
        }
    }

    private void animateNumber(TextView textView, double targetValue) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, (float) targetValue);
        animator.setDuration(1500);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                textView.setText(String.format("%.2f", value));
            }
        });
        animator.start();
    }

    private void submitRequest() {
        // Navigate to the new Submit Request activity
        Intent intent = new Intent(DashboardActivity.this, SubmitRequestActivity.class);
        intent.putExtra("user_id", userId);
        intent.putExtra("username", username);
        startActivity(intent);
    }

    private void viewRequests() {
        tvResponse.setText("📋 Loading requests...");
        tvResponse.setTextColor(Color.BLUE);

        try {
            JSONObject params = new JSONObject();
            params.put("username", username);
            params.put("user_id", userId);

            socketClient.sendRequest("GET_REQUESTS", params, new SocketClient.SocketCallback() {
                @Override
                public void onResponse(final String response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                JSONObject jsonResponse = new JSONObject(response);
                                if (jsonResponse.getString("status").equals("success")) {
                                    String formatted = formatRequests(jsonResponse.getJSONArray("requests"));
                                    tvResponse.setText(formatted);
                                    tvResponse.setTextColor(Color.GREEN);
                                } else {
                                    tvResponse.setText("📭 No requests found");
                                    tvResponse.setTextColor(Color.YELLOW);
                                }
                            } catch (Exception e) {
                                tvResponse.setText("📄 Response: " + response);
                                tvResponse.setTextColor(Color.YELLOW);
                            }
                        }
                    });
                }

                @Override
                public void onError(final String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvResponse.setText("❌ Error: " + error);
                            tvResponse.setTextColor(Color.RED);
                        }
                    });
                }
            });

        } catch (Exception e) {
            tvResponse.setText("❌ Error: " + e.getMessage());
            tvResponse.setTextColor(Color.RED);
        }
    }

    private String formatRequests(JSONArray requests) {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Your Requests:\n\n");

        try {
            for (int i = 0; i < requests.length(); i++) {
                JSONObject request = requests.getJSONObject(i);
                sb.append("🆔 ID: ").append(request.getInt("id")).append("\n");
                sb.append("📝 Type: ").append(request.getString("type")).append("\n");
                sb.append("📌 Title: ").append(request.getString("title")).append("\n");
                sb.append("📄 Description: ").append(request.getString("description")).append("\n");
                sb.append("📊 Status: ").append(request.getString("status")).append("\n");
                sb.append("📅 Created: ").append(request.getString("created_at")).append("\n");
                sb.append("────────────────────\n\n");
            }
        } catch (Exception e) {
            return "📋 Requests loaded successfully!";
        }

        return sb.toString();
    }

    private void logout() {
        try {
            JSONObject params = new JSONObject();
            params.put("username", username);

            socketClient.sendRequest("EXIT", params, new SocketClient.SocketCallback() {
                @Override
                public void onResponse(String response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DashboardActivity.this, "👋 Goodbye!", Toast.LENGTH_SHORT).show();
                            navigateToLogin();
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DashboardActivity.this, "Logging out...", Toast.LENGTH_SHORT).show();
                            navigateToLogin();
                        }
                    });
                }
            });

        } catch (Exception e) {
            navigateToLogin();
        }
    }

    private void navigateToLogin() {
        Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
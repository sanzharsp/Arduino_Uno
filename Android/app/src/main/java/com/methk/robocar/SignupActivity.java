package com.methk.robocar;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


public class SignupActivity extends AppCompatActivity {

    private DatabaseHelper databaseHelper;
    public void goToLoginActivity(View view) {
        Intent intent = new Intent(this, Login.class);
        startActivity(intent);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        databaseHelper = new DatabaseHelper(this);

        final EditText emailEditText = findViewById(R.id.email_edit_text);
        final EditText passwordEditText = findViewById(R.id.password_edit_text);

        Button signupButton = findViewById(R.id.signup_button);
        signupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = emailEditText.getText().toString();
                String password = passwordEditText.getText().toString();

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(SignupActivity.this, "Барлық өрістерді толтырыңыз", Toast.LENGTH_SHORT).show();
                } else {
                    // Добавляем пользователя в базу данных
                    databaseHelper.addUser(email, password);

                    Toast.makeText(SignupActivity.this, "Тіркеу сәтті өтті", Toast.LENGTH_SHORT).show();

                    // Переходим на активность входа
                    Intent intent = new Intent(SignupActivity.this, Login.class);
                    startActivity(intent);
                    finish();
                }
            }
        });
    }
}

package com.methk.robocar;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.support.v7.app.AppCompatActivity;

public class Login extends AppCompatActivity {

    private DatabaseHelper databaseHelper;
    public void goToSignupActivity(View view) {
        Intent intent = new Intent(this, SignupActivity.class);
        startActivity(intent);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        databaseHelper = new DatabaseHelper(this);

        final EditText emailEditText = findViewById(R.id.email_edit_text);
        final EditText passwordEditText = findViewById(R.id.password_edit_text);

        Button loginButton = findViewById(R.id.login_button);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = emailEditText.getText().toString();
                String password = passwordEditText.getText().toString();

                if (isValidUser(email, password)) {
                    // Если данные верны, перейдите на следующую активность
                    Intent intent = new Intent(Login.this, Devices.class);
                    startActivity(intent);
                    finish();
                } else {
                    // Если данные не верны, выведите сообщение об ошибке
                    Toast.makeText(Login.this, "Электрондық пошта мекенжайы немесе құпия сөз дұрыс емес", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean isValidUser(String email, String password) {
        // Проверка в базе данных наличия пользователя с введенными учетными данными
        return databaseHelper.checkUser(email, password);
    }
}

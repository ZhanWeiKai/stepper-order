# QR Code Login Design

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement QR code login on Step 1, allowing users to scan with phone and login to proceed to Step 2

**Architecture:** Spring Boot backend with in-memory storage + Android QR code display + polling mechanism

**Tech Stack:** Spring Boot, Android (ZXing QR library), HTML/CSS (login page)

---

## Overview

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   Android   │         │  Spring     │         │  Mobile     │
│   App       │         │  Boot       │         │  Browser    │
│  (Step 1)   │         │  Backend    │         │  Login Page │
└──────┬──────┘         └──────┬──────┘         └──────┬──────┘
       │                       │                       │
       │ 1. POST /session/create                      │
       │─────────────────────>│                       │
       │<─────────────────────│                       │
       │   {sessionId: "uuid"} │                       │
       │                       │                       │
       │ 2. Display QR Code    │                       │
       │   URL: /login/{id}    │                       │
       │                       │                       │
       │                       │<──────────────────────│
       │                       │ 3. Scan QR, open page │
       │                       │                       │
       │                       │<──────────────────────│
       │                       │ 4. Click Login button │
       │                       │   POST /login/{id}    │
       │                       │                       │
       │ 5. GET /status/{id}   │                       │
       │─────────────────────>│                       │
       │<─────────────────────│                       │
       │   {loggedIn: false}   │                       │
       │                       │                       │
       │ 6. Poll again...      │                       │
       │─────────────────────>│                       │
       │<─────────────────────│                       │
       │   {loggedIn: true}    │                       │
       │                       │                       │
       │ 7. Go to Step 2       │                       │
       │                       │                       │
```

---

## Backend API Design

### Base URL
```
http://119.91.206.195:8889
```

### APIs

#### 1. Create Session
```
POST /api/qr-login/session/create

Response:
{
  "sessionId": "uuid-string",
  "qrCodeUrl": "http://119.91.206.195:8889/login/uuid-string"
}
```

#### 2. Check Login Status
```
GET /api/qr-login/status/{sessionId}

Response:
{
  "sessionId": "uuid-string",
  "loggedIn": true/false
}
```

#### 3. Login (Mobile)
```
POST /api/qr-login/login/{sessionId}

Response:
{
  "success": true,
  "message": "Login successful"
}
```

---

## Backend Implementation

### Data Storage
```java
// In-memory storage, thread-safe
private ConcurrentHashMap<String, Boolean> loginStatus = new ConcurrentHashMap<>();
```

### Controller
```java
@RestController
@RequestMapping("/api/qr-login")
public class QrLoginController {

    private final ConcurrentHashMap<String, Boolean> loginStatus = new ConcurrentHashMap<>();

    @PostMapping("/session/create")
    public ResponseEntity<SessionResponse> createSession() {
        String sessionId = UUID.randomUUID().toString();
        loginStatus.put(sessionId, false);
        return ResponseEntity.ok(new SessionResponse(sessionId));
    }

    @GetMapping("/status/{sessionId}")
    public ResponseEntity<StatusResponse> checkStatus(@PathVariable String sessionId) {
        boolean loggedIn = loginStatus.getOrDefault(sessionId, false);
        return ResponseEntity.ok(new StatusResponse(sessionId, loggedIn));
    }

    @PostMapping("/login/{sessionId}")
    public ResponseEntity<LoginResponse> login(@PathVariable String sessionId) {
        if (loginStatus.containsKey(sessionId)) {
            loginStatus.put(sessionId, true);
            return ResponseEntity.ok(new LoginResponse(true, "Login successful"));
        }
        return ResponseEntity.badRequest().body(new LoginResponse(false, "Invalid session"));
    }
}
```

---

## Mobile Login Page

### URL
```
http://119.91.206.195:8889/login/{sessionId}
```

### Simple HTML Page
```html
<!DOCTYPE html>
<html>
<head>
    <title>Login</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body {
            font-family: Arial, sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
            margin: 0;
            background: #f5f5f5;
        }
        .container {
            text-align: center;
            padding: 40px;
            background: white;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        button {
            background: #2196F3;
            color: white;
            border: none;
            padding: 15px 60px;
            font-size: 18px;
            border-radius: 5px;
            cursor: pointer;
        }
        button:hover {
            background: #1976D2;
        }
    </style>
</head>
<body>
    <div class="container">
        <h2>QR Code Login</h2>
        <p>Click the button below to login</p>
        <button onclick="login()">Login</button>
        <p id="status"></p>
    </div>
    <script>
        const sessionId = window.location.pathname.split('/').pop();

        function login() {
            fetch('/api/qr-login/login/' + sessionId, { method: 'POST' })
                .then(r => r.json())
                .then(data => {
                    if (data.success) {
                        document.getElementById('status').innerHTML =
                            '<span style="color:green">✓ Login successful! You can close this page.</span>';
                    }
                });
        }
    </script>
</body>
</html>
```

---

## Android Implementation

### Dependencies
```gradle
implementation 'com.google.zxing:core:3.5.2'
implementation 'com.journeyapps:zxing-android-embedded:4.3.0'
```

### QR Code Generation
```java
// Generate QR code bitmap from URL
public Bitmap generateQRCode(String url, int size) {
    QRCodeWriter writer = new QRCodeWriter();
    BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size);
    // Convert to Bitmap...
}
```

### Polling Logic
```java
// Poll every 2 seconds
Handler handler = new Handler();
Runnable pollingRunnable = new Runnable() {
    @Override
    public void run() {
        checkLoginStatus(sessionId, new Callback() {
            @Override
            public void onSuccess(boolean loggedIn) {
                if (loggedIn) {
                    // Go to Step 2
                    stepperLayout.proceed();
                } else {
                    // Continue polling
                    handler.postDelayed(this, 2000);
                }
            }
        });
    }
};
handler.post(pollingRunnable);
```

---

## Tasks

### Task 1: Create Spring Boot Backend
- Create project structure
- Implement QrLoginController with 3 APIs
- Add login page HTML
- Configure port 8889

### Task 2: Deploy to Tencent Cloud
- Build JAR file
- Upload to server
- Configure Docker/restart

### Task 3: Update Android App
- Add ZXing dependency
- Create QR code view in Step 1
- Implement polling mechanism
- Handle login success transition

### Task 4: Test End-to-End
- Generate QR code
- Scan with phone
- Click login
- Verify app transitions to Step 2

---

## Server Configuration

| Property | Value |
|----------|-------|
| Server IP | 119.91.206.195 |
| Port | 8889 |
| Context Path | /api/qr-login |

---

## Security Notes

This is a **simulated login** for demo purposes:
- No real authentication
- Session IDs are UUIDs (hard to guess)
- In-memory storage (lost on restart)
- No HTTPS (for simplicity)

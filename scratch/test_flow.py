import requests
import pg8000.dbapi
import time
import hashlib

BASE_URL = "http://localhost:8080/api/v1"

# 1. Register User
register_payload = {
    "tenantSlug": "default",
    "username": "tester123",
    "email": "tester123@example.com",
    "password": "Pass@123",
    "confirmPassword": "Pass@123"
}

print("1. Registering user...")
r_reg = requests.post(f"{BASE_URL}/auth/register", json=register_payload)
print(f"Registration Status: {r_reg.status_code}")
print(r_reg.text)

# Check if user already exists (maybe we get a 400, which is fine)
if r_reg.status_code not in [201, 200] and "username already exists" in r_reg.text.lower():
    print("User already registered.")

# 2. Get Verification Token from Neon DB
print("2. Connecting to database to fetch verification token...")
conn = pg8000.dbapi.connect(
    host="ep-super-flower-ao260hj2-pooler.c-2.ap-southeast-1.aws.neon.tech",
    port=5432,
    database="neondb",
    user="neondb_owner",
    password="npg_kJ7oIZc4AUSX",
    ssl_context=True
)
cursor = conn.cursor()

# Get the user ID
cursor.execute("SELECT id FROM users WHERE username = 'tester123';")
user_id_row = cursor.fetchone()
if not user_id_row:
    print("Error: User tester123 not found in DB!")
    exit(1)
user_id = user_id_row[0]
print(f"User ID: {user_id}")

# Get token hash from database
cursor.execute("SELECT token_hash FROM verification_tokens WHERE user_id = %s AND used = false ORDER BY created_at DESC LIMIT 1;", [user_id])
token_row = cursor.fetchone()
if not token_row:
    print("Warning: No verification token found. Maybe already verified or no token generated.")
    token_hash = None
else:
    token_hash = token_row[0]
    print(f"Token Hash in DB: {token_hash}")

# Update email_verified = True in DB
print("3. Marking email as verified in the database...")
cursor.execute("UPDATE users SET email_verified = true WHERE id = %s;", [user_id])
conn.commit()

cursor.close()
conn.close()
print("Email marked as verified in DB successfully!")

# 3. Login
login_payload = {
    "tenantSlug": "default",
    "usernameOrEmail": "tester123",
    "password": "Pass@123",
    "rememberMe": True
}
print("4. Logging in...")
r_login = requests.post(f"{BASE_URL}/auth/login", json=login_payload)
print(f"Login Status: {r_login.status_code}")
login_data = r_login.json()
print(login_data)

access_token = login_data["data"]["accessToken"]
print(f"Access Token: {access_token[:30]}...")

# 4. Create Question
question_payload = {
    "title": "How to verify Flyway migrations?",
    "content": "I want to verify that questions table is correctly created and mapped to entity."
}
headers = {
    "Authorization": f"Bearer {access_token}",
    "Content-Type": "application/json"
}
print("5. Creating question...")
r_q = requests.post(f"{BASE_URL}/questions", json=question_payload, headers=headers)
print(f"Create Question Status: {r_q.status_code}")
print(r_q.text)

if r_q.status_code == 201:
    print("SUCCESS: Question created successfully!")
else:
    print("FAILURE: Question creation failed.")

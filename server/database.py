# database.py - Enhanced Version
import sqlite3
import hashlib
import secrets
from datetime import datetime

class Database:
    def __init__(self, db_name="student_system.db"):
        self.connection = sqlite3.connect(db_name, check_same_thread=False)
        self.create_tables()
    
    def create_tables(self):
        cursor = self.connection.cursor()
        
        # Users table with password salt
        cursor.execute('''
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            password_salt TEXT NOT NULL,
            email TEXT NOT NULL,
            failed_login_attempts INTEGER DEFAULT 0,
            account_locked_until TIMESTAMP,
            last_login TIMESTAMP,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        ''')
        
        # Student records table
        cursor.execute('''
        CREATE TABLE IF NOT EXISTS student_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            student_id TEXT UNIQUE NOT NULL,
            full_name TEXT NOT NULL,
            department TEXT NOT NULL,
            semester INTEGER,
            gpa REAL,
            attendance_percentage REAL DEFAULT 0.0,
            FOREIGN KEY (user_id) REFERENCES users (id)
        )
        ''')
        
        # Requests/complaints table
        cursor.execute('''
        CREATE TABLE IF NOT EXISTS requests (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            request_type TEXT NOT NULL,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            status TEXT DEFAULT 'pending',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users (id)
        )
        ''')
        
        self.connection.commit()
    
    def register_user(self, username, password, email, student_data=None):
        try:
            cursor = self.connection.cursor()
            
            # Check if username exists
            if self.check_username_exists(username):
                return {"status": "error", "message": "Username already exists"}
            
            # Check if email exists
            cursor.execute("SELECT id FROM users WHERE email = ?", (email,))
            if cursor.fetchone():
                return {"status": "error", "message": "Email already registered"}
            
            # Generate salt and hash password
            salt = secrets.token_hex(16)
            password_hash = hashlib.sha256((password + salt).encode()).hexdigest()
            
            # Insert user with salt
            cursor.execute(
                "INSERT INTO users (username, password_hash, password_salt, email) VALUES (?, ?, ?, ?)",
                (username, password_hash, salt, email)
            )
            user_id = cursor.lastrowid
            
            # Insert student record if provided
            if student_data:
                cursor.execute('''
                INSERT INTO student_records 
                (user_id, student_id, full_name, department, semester, gpa)
                VALUES (?, ?, ?, ?, ?, ?)
                ''', (user_id, student_data['student_id'], student_data['full_name'],
                    student_data['department'], student_data['semester'], student_data['gpa']))
            
            self.connection.commit()
            return {"status": "success", "user_id": user_id}
            
        except Exception as e:
            return {"status": "error", "message": f"Registration error: {e}"}
    
    def authenticate_user(self, username, password, ip_address=None):
        """Authenticate user with password and track failed attempts"""
        cursor = self.connection.cursor()
        
        # Check if account is locked
        cursor.execute('''
        SELECT id, password_hash, password_salt, failed_login_attempts, 
               account_locked_until 
        FROM users WHERE username = ?
        ''', (username,))
        
        user = cursor.fetchone()
        
        if not user:
            return {"status": "error", "message": "Invalid credentials"}
        
        user_id, stored_hash, salt, failed_attempts, locked_until = user
        
        # Check if account is locked
        if locked_until:
            locked_time = datetime.strptime(locked_until, '%Y-%m-%d %H:%M:%S')
            if datetime.now() < locked_time:
                return {"status": "error", "message": "Account is locked. Try again later"}
        
        # Verify password
        password_hash = hashlib.sha256((password + salt).encode()).hexdigest()
        
        if secrets.compare_digest(password_hash, stored_hash):
            # Successful login - reset failed attempts and update last login
            cursor.execute('''
            UPDATE users SET 
                failed_login_attempts = 0,
                account_locked_until = NULL,
                last_login = CURRENT_TIMESTAMP
            WHERE id = ?
            ''', (user_id,))
            self.connection.commit()
            
            return {"status": "success", "user_id": user_id, "username": username}
        else:
            # Failed login - increment failed attempts
            failed_attempts += 1
            if failed_attempts >= 5:
                # Lock account for 15 minutes
                lock_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                cursor.execute('''
                UPDATE users SET 
                    failed_login_attempts = ?,
                    account_locked_until = datetime('now', '+15 minutes')
                WHERE id = ?
                ''', (failed_attempts, user_id))
                message = "Account locked for 15 minutes due to too many failed attempts"
            else:
                cursor.execute('''
                UPDATE users SET failed_login_attempts = ? WHERE id = ?
                ''', (failed_attempts, user_id))
                message = f"Invalid credentials. {5 - failed_attempts} attempts remaining"
            
            self.connection.commit()
            return {"status": "error", "message": message}
    
    def get_student_data(self, user_id):
        cursor = self.connection.cursor()
        
        cursor.execute('''
        SELECT sr.student_id, sr.full_name, sr.department, 
               sr.semester, sr.gpa, sr.attendance_percentage
        FROM student_records sr
        WHERE sr.user_id = ?
        ''', (user_id,))
        
        record = cursor.fetchone()
        
        if record:
            return {
                "status": "success",
                "data": {
                    "student_id": record[0],
                    "full_name": record[1],
                    "department": record[2],
                    "semester": record[3],
                    "gpa": record[4],
                    "attendance_percentage": record[5]
                }
            }
        return {"status": "error", "message": "No student data found"}
    
    def submit_request(self, user_id, request_type, title, description):
        cursor = self.connection.cursor()
        
        cursor.execute('''
        INSERT INTO requests (user_id, request_type, title, description)
        VALUES (?, ?, ?, ?)
        ''', (user_id, request_type, title, description))
        
        self.connection.commit()
        return {"status": "success", "request_id": cursor.lastrowid}
    
    def get_user_requests(self, user_id):
        cursor = self.connection.cursor()
        
        cursor.execute('''
        SELECT id, request_type, title, description, status, created_at
        FROM requests
        WHERE user_id = ?
        ORDER BY created_at DESC
        LIMIT 50
        ''', (user_id,))
        
        requests = cursor.fetchall()
        result = []
        for req in requests:
            result.append({
                "id": req[0],
                "type": req[1],
                "title": req[2],
                "description": req[3],
                "status": req[4],
                "created_at": req[5]
            })
        
        return {"status": "success", "requests": result}

    def check_username_exists(self, username):
        """Check if username already exists"""
        cursor = self.connection.cursor()
        cursor.execute("SELECT id FROM users WHERE username = ?", (username,))
        return cursor.fetchone() is not None
    
    def close(self):
        self.connection.close()
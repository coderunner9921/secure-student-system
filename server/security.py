# security.py - Enhanced Version
import json
import base64
import hashlib
import secrets
import sqlite3
from datetime import datetime, timedelta
from Crypto.Cipher import AES
from Crypto.Random import get_random_bytes
import re

class SecurityHandler:
    def __init__(self):
        self.key = b'0123456789abcdef' * 2  # 32 bytes for AES-256
        self.session_expiry_hours = 24
        self.salt_length = 16
        
    def pad(self, data):
        """Pad data to be multiple of 16 bytes"""
        padding_length = 16 - (len(data) % 16)
        padding = bytes([padding_length] * padding_length)
        return data + padding
    
    def unpad(self, data):
        """Remove padding"""
        padding_length = data[-1]
        return data[:-padding_length]
    
    def encrypt_data(self, data):
        """Encrypt JSON data using AES"""
        try:
            if isinstance(data, dict):
                data_str = json.dumps(data)
            else:
                data_str = data
            
            # Generate random IV for each encryption
            iv = get_random_bytes(16)
            cipher = AES.new(self.key, AES.MODE_CBC, iv)
            
            # Pad and encrypt
            padded_data = self.pad(data_str.encode('utf-8'))
            encrypted = cipher.encrypt(padded_data)
            
            # Combine IV + encrypted data and encode in base64
            combined = iv + encrypted
            result = base64.b64encode(combined).decode('utf-8')
            
            return result
            
        except Exception as e:
            print(f"[SECURITY] Encryption error: {e}")
            return None
    
    def decrypt_data(self, encrypted_data):
        """Decrypt data from Base64/AES"""
        try:
            # Decode base64
            combined = base64.b64decode(encrypted_data)
            
            # Extract IV (first 16 bytes) and encrypted data
            iv = combined[:16]
            encrypted = combined[16:]
            
            # Decrypt
            cipher = AES.new(self.key, AES.MODE_CBC, iv)
            decrypted = cipher.decrypt(encrypted)
            
            # Remove padding and decode
            unpadded = self.unpad(decrypted)
            result_str = unpadded.decode('utf-8')
            
            # Try to parse as JSON
            return json.loads(result_str)
            
        except Exception as e:
            print(f"[SECURITY] Decryption error: {e}")
            return {"status": "error", "message": "Decryption failed"}
    
    def hash_password_with_salt(self, password):
        """Hash password with random salt"""
        salt = secrets.token_hex(self.salt_length)
        password_hash = hashlib.sha256((password + salt).encode()).hexdigest()
        return password_hash, salt
    
    def verify_password(self, password, stored_hash, salt):
        """Verify password against stored hash and salt"""
        password_hash = hashlib.sha256((password + salt).encode()).hexdigest()
        return secrets.compare_digest(password_hash, stored_hash)
    
    def generate_session_token(self):
        """Generate secure session token"""
        return secrets.token_urlsafe(32)
    
    def validate_input(self, params, required_fields):
        """Validate required fields with regex patterns"""
        for field in required_fields:
            if field not in params:
                return False, f"Missing required field: {field}"
            if not params[field] or str(params[field]).strip() == "":
                return False, f"Field cannot be empty: {field}"
        
        # Additional validation patterns
        if 'email' in params:
            email_pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
            if not re.match(email_pattern, params['email']):
                return False, "Invalid email format"
        
        if 'username' in params:
            if len(params['username']) < 3:
                return False, "Username must be at least 3 characters"
            if not re.match(r'^[a-zA-Z0-9_]+$', params['username']):
                return False, "Username can only contain letters, numbers, and underscores"
        
        return True, "Valid"
    
    def sanitize_input(self, input_str):
        """Enhanced input sanitization to prevent SQL injection"""
        if not input_str:
            return ""
        
        # Remove SQL injection patterns
        sql_patterns = [
            r'(?i)\bDROP\b', r'(?i)\bDELETE\b', r'(?i)\bINSERT\b',
            r'(?i)\bUPDATE\b', r'(?i)\bSELECT\b', r'(?i)\bUNION\b',
            r'--', r';', r'/\*', r'\*/', r'@@', r'@'
        ]
        
        sanitized = str(input_str)
        for pattern in sql_patterns:
            sanitized = re.sub(pattern, '', sanitized)
        
        # Escape special characters
        sanitized = re.sub(r'[;\'\"\\\x00]', '', sanitized)
        return sanitized.strip()


class SessionManager:
    def __init__(self, db_connection):
        self.connection = db_connection
        self.create_session_table()
    
    def create_session_table(self):
        cursor = self.connection.cursor()
        cursor.execute('''
        CREATE TABLE IF NOT EXISTS sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            session_token TEXT UNIQUE NOT NULL,
            ip_address TEXT,
            user_agent TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            expires_at TIMESTAMP,
            is_active BOOLEAN DEFAULT 1,
            FOREIGN KEY (user_id) REFERENCES users (id)
        )
        ''')
        self.connection.commit()
    
    def create_session(self, user_id, ip_address=None, user_agent=None):
        """Create new session for user"""
        cursor = self.connection.cursor()
        
        # Generate secure token
        session_token = secrets.token_urlsafe(32)
        
        # Set expiry (24 hours from now)
        expires_at = datetime.now() + timedelta(hours=24)
        
        cursor.execute('''
        INSERT INTO sessions (user_id, session_token, ip_address, user_agent, expires_at)
        VALUES (?, ?, ?, ?, ?)
        ''', (user_id, session_token, ip_address, user_agent, expires_at))
        
        self.connection.commit()
        return session_token
    
    def validate_session(self, session_token):
        """Validate session token and return user_id if valid"""
        cursor = self.connection.cursor()
        
        cursor.execute('''
        SELECT user_id FROM sessions 
        WHERE session_token = ? 
        AND is_active = 1 
        AND expires_at > CURRENT_TIMESTAMP
        ''', (session_token,))
        
        result = cursor.fetchone()
        if result:
            return result[0]
        return None
    
    def invalidate_session(self, session_token):
        """Invalidate session token"""
        cursor = self.connection.cursor()
        cursor.execute('''
        UPDATE sessions SET is_active = 0 
        WHERE session_token = ?
        ''', (session_token,))
        self.connection.commit()
    
    def cleanup_expired_sessions(self):
        """Clean up expired sessions"""
        cursor = self.connection.cursor()
        cursor.execute('''
        DELETE FROM sessions 
        WHERE expires_at < CURRENT_TIMESTAMP
        ''')
        self.connection.commit()


class SecurityAudit:
    def __init__(self, db_connection):
        self.connection = db_connection
        self.create_audit_table()
    
    def create_audit_table(self):
        cursor = self.connection.cursor()
        cursor.execute('''
        CREATE TABLE IF NOT EXISTS security_audit (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            event_type TEXT NOT NULL,
            user_id INTEGER,
            ip_address TEXT,
            details TEXT,
            severity TEXT CHECK(severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
        )
        ''')
        self.connection.commit()
    
    def log_event(self, event_type, user_id=None, ip_address=None, details=None, severity='MEDIUM'):
        """Log security event"""
        cursor = self.connection.cursor()
        cursor.execute('''
        INSERT INTO security_audit (event_type, user_id, ip_address, details, severity)
        VALUES (?, ?, ?, ?, ?)
        ''', (event_type, user_id, ip_address, json.dumps(details) if details else None, severity))
        self.connection.commit()
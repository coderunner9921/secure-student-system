import socket
import threading
import json
import sys
from datetime import datetime
from database import Database
from security import SecurityHandler

class StudentSocketServer:
    def __init__(self, host='0.0.0.0', port=12345):
        self.host = host
        self.port = port
        self.server_socket = None
        self.running = False
        self.clients = []
        self.db = Database()
        self.security = SecurityHandler()
        
        # Command handlers
        self.commands = {
            'REGISTER': self.handle_register,
            'LOGIN': self.handle_login,
            'GET_DATA': self.handle_get_data,
            'SUBMIT_REQUEST': self.handle_submit_request,
            'GET_REQUESTS': self.handle_get_requests,
            'HELP': self.handle_help,
            'EXIT': self.handle_exit
        }
    
    def start(self):
        """Start the socket server"""
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.server_socket.bind((self.host, self.port))
            self.server_socket.listen(5)
            
            self.running = True
            print(f"[*] Server started on {self.host}:{self.port}")
            print("[*] Waiting for connections...")
            
            # Start client handler thread
            while self.running:
                try:
                    client_socket, client_address = self.server_socket.accept()
                    print(f"[+] New connection from {client_address}")
                    
                    client_handler = threading.Thread(
                        target=self.handle_client,
                        args=(client_socket, client_address)
                    )
                    client_handler.daemon = True
                    client_handler.start()
                    self.clients.append((client_socket, client_address))
                    
                except Exception as e:
                    if self.running:
                        print(f"[!] Error accepting connection: {e}")
        
        except Exception as e:
            print(f"[!] Server error: {e}")
        finally:
            self.stop()
    
    def handle_client(self, client_socket, client_address):
        """Handle individual client connection"""
        client_id = f"{client_address[0]}:{client_address[1]}"
        print(f"[*] Handling client {client_id}")
        
        authenticated_user = None
        
        try:
            while True:
                # Receive data
                data = client_socket.recv(4096)
                if not data:
                    break
                
                # Try to decode
                request_str = data.decode('utf-8').strip()
                
                print(f"[{client_id}] Received data (length: {len(request_str)})")
                
                # SPECIAL CASE: If it's "TEST" (from test connection)
                if request_str == "TEST":
                    print(f"[{client_id}] Test connection detected")
                    response = self.create_response("success", "Server is running!")
                    encrypted_response = self.security.encrypt_data(response)
                    if encrypted_response:
                        client_socket.send((encrypted_response + "\n").encode())
                        print(f"[{client_id}] Sent encrypted response with newline")
                    else:
                        # Fallback to plain response if encryption fails
                        client_socket.send(json.dumps(response).encode())
                    continue
                
                # Try to decrypt with AES
                request = self.security.decrypt_data(request_str)
                
                if request and request.get("status") != "error":
                    # AES decryption successful
                    print(f"[{client_id}] AES decryption successful")
                else:
                    # Try old Base64 method for compatibility
                    print(f"[{client_id}] AES failed, trying Base64")
                    try:
                        import base64
                        decoded = base64.b64decode(request_str)
                        request = json.loads(decoded.decode('utf-8'))
                        print(f"[{client_id}] Base64 fallback successful")
                    except:
                        # If it's not JSON either, send error
                        print(f"[{client_id}] Invalid request format")
                        response = self.create_response("error", "Invalid request format")
                        encrypted_response = self.security.encrypt_data(response)
                        if encrypted_response:
                            client_socket.send(encrypted_response.encode())
                        else:
                            client_socket.send(json.dumps(response).encode())
                        continue
                
                command = request.get('command', '').upper()
                params = request.get('params', {})
                
                # Log the request
                print(f"[{client_id}] Command: {command}")
                if command in ['LOGIN', 'REGISTER']:
                    print(f"[{client_id}] Username: {params.get('username', 'N/A')}")
                
                # ENHANCED AUTHENTICATION CHECK - Supports params-based auth
                if command == 'GET_DATA':
                    # GET_DATA can work with username/user_id params
                    print(f"[DEBUG] GET_DATA command received")
                    print(f"[DEBUG] Params keys: {list(params.keys())}")
                    if 'username' in params or 'user_id' in params:
                        print(f"[DEBUG] GET_DATA has authentication params, proceeding")
                    else:
                        print(f"[DEBUG] GET_DATA missing auth params, rejecting")
                        response = self.create_response("error", "Authentication required")
                        encrypted_response = self.security.encrypt_data(response)
                        if encrypted_response:
                            client_socket.send(encrypted_response.encode())
                        else:
                            client_socket.send(json.dumps(response).encode())
                        continue
                        
                elif command == 'SUBMIT_REQUEST':
                    # SUBMIT_REQUEST can work with username/user_id params
                    print(f"[DEBUG] SUBMIT_REQUEST command received")
                    print(f"[DEBUG] Params keys: {list(params.keys())}")
                    if 'username' in params or 'user_id' in params:
                        print(f"[DEBUG] SUBMIT_REQUEST has authentication params, proceeding")
                    else:
                        print(f"[DEBUG] SUBMIT_REQUEST missing auth params, rejecting")
                        response = self.create_response("error", "Authentication required")
                        encrypted_response = self.security.encrypt_data(response)
                        if encrypted_response:
                            client_socket.send(encrypted_response.encode())
                        else:
                            client_socket.send(json.dumps(response).encode())
                        continue
                        
                elif command == 'GET_REQUESTS':
                    # GET_REQUESTS can work with username/user_id params or session
                    print(f"[DEBUG] GET_REQUESTS command received")
                    print(f"[DEBUG] Params keys: {list(params.keys())}")
                    if 'username' in params or 'user_id' in params or authenticated_user:
                        print(f"[DEBUG] GET_REQUESTS has authentication, proceeding")
                    else:
                        print(f"[DEBUG] GET_REQUESTS missing auth, rejecting")
                        response = self.create_response("error", "Authentication required")
                        encrypted_response = self.security.encrypt_data(response)
                        if encrypted_response:
                            client_socket.send(encrypted_response.encode())
                        else:
                            client_socket.send(json.dumps(response).encode())
                        continue
                        
                elif command == 'EXIT':
                    if authenticated_user or 'username' in params or 'user_id' in params:
                        print(f"[DEBUG] EXIT command with authentication, proceeding")
                    else:
                        print(f"[DEBUG] EXIT without authentication, rejecting")
                        response = self.create_response("error", "Authentication required")
                        encrypted_response = self.security.encrypt_data(response)
                        if encrypted_response:
                            client_socket.send(encrypted_response.encode())
                        else:
                            client_socket.send(json.dumps(response).encode())
                        continue

                # Execute command
                if command in self.commands:
                    if command in ['LOGIN', 'REGISTER']:
                        result = self.commands[command](params)
                        if result['status'] == 'success' and command == 'LOGIN':
                            authenticated_user = result.get('data', {}).get('user_id')
                            print(f"[DEBUG] Login successful, authenticated_user set to: {authenticated_user}")
                    elif authenticated_user:
                        # Use session authentication
                        result = self.commands[command](params, authenticated_user)
                    else:
                        # Use params-based authentication (username/user_id in params)
                        result = self.commands[command](params)
                else:
                    result = self.create_response("error", f"Unknown command: {command}")

                # DEBUG: Print result before sending
                print(f"[DEBUG] Command result type: {type(result)}")
                print(f"[DEBUG] Command result: {result}")
                
                # Send response (encrypted)
                try:
                    encrypted_response = self.security.encrypt_data(result)
                    if encrypted_response:
                        print(f"[DEBUG] Encrypted response length: {len(encrypted_response)}")
                        print(f"[DEBUG] First 50 chars of encrypted: {encrypted_response[:50]}")
                        
                        # SEND THE RESPONSE WITH NEWLINE
                        full_response = encrypted_response + "\n"
                        bytes_sent = client_socket.send(full_response.encode())
                        print(f"[DEBUG] Sent {bytes_sent} bytes to client (including newline)")
                    else:
                        # Fallback to plain JSON if encryption fails
                        print(f"[DEBUG] Encryption failed, sending plain response")
                        plain_response = json.dumps(result)
                        full_response = plain_response + "\n"
                        bytes_sent = client_socket.send(full_response.encode())
                        print(f"[DEBUG] Sent {bytes_sent} bytes plain response")
                        
                except Exception as e:
                    print(f"[DEBUG] Error sending response: {e}")
                    import traceback
                    traceback.print_exc()
                    try:
                        # Last resort: try to send error message
                        error_response = json.dumps({"status": "error", "message": "Server error"})
                        client_socket.send((error_response + "\n").encode())
                    except:
                        pass

                print(f"[DEBUG] Response sending complete\n")
                
                if command == 'EXIT':
                    break
            
        except ConnectionResetError:
            print(f"[-] Client {client_id} disconnected abruptly")
        except Exception as e:
            print(f"[!] Error with client {client_id}: {e}")
            import traceback
            traceback.print_exc()
        finally:
            client_socket.close()
            self.clients = [c for c in self.clients if c[0] != client_socket]
            print(f"[-] Client {client_id} disconnected")
    
    def handle_register(self, params):
        """Handle user registration"""
        required = ['username', 'password', 'email']
        valid, message = self.security.validate_input(params, required)
        
        if not valid:
            return self.create_response("error", message)
        
        # Sanitize inputs
        username = self.security.sanitize_input(params['username'])
        email = self.security.sanitize_input(params['email'])
        
        password_hash = params['password']
        
        # Prepare student data if provided
        student_data = None
        if all(k in params for k in ['student_id', 'full_name', 'department']):
            student_data = {
                'student_id': self.security.sanitize_input(params['student_id']),
                'full_name': self.security.sanitize_input(params['full_name']),
                'department': self.security.sanitize_input(params['department']),
                'semester': int(params.get('semester', 1)),
                'gpa': float(params.get('gpa', 0.0))
            }
        
        # Register user with hashed password
        result = self.db.register_user(username, password_hash, email, student_data)
        
        if result['status'] == 'success':
            return self.create_response("success", "Registration successful", {
                "user_id": result['user_id']
            })
        else:
            return self.create_response("error", result['message'])
    
    def handle_login(self, params):
        """Handle user login"""
        try:
            print(f"[DEBUG] Login attempt with params: {params}")
            
            required = ['username', 'password']
            valid, message = self.security.validate_input(params, required)
            
            if not valid:
                print(f"[DEBUG] Validation failed: {message}")
                return self.create_response("error", message)
            
            username = params['username']
            password = params['password']
            
            print(f"[DEBUG] Authenticating user: {username}")
            
            # Authenticate user
            result = self.db.authenticate_user(username, password)
            
            print(f"[DEBUG] Authentication result: {result}")
            
            if result['status'] == 'success':
                response = self.create_response("success", "Login successful", {
                    "user_id": result['user_id'],
                    "username": result['username']
                })
                print(f"[DEBUG] Sending success response: {response}")
                return response
            else:
                response = self.create_response("error", result['message'])
                print(f"[DEBUG] Sending error response: {response}")
                return response
                
        except Exception as e:
            print(f"[DEBUG] Login handler exception: {e}")
            import traceback
            traceback.print_exc()
            return self.create_response("error", f"Server error: {str(e)}")
    
    def handle_get_data(self, params, authenticated_user_id=None):
        """Get student data for authenticated user"""
        print(f"[DEBUG] GET_DATA called with params: {params}")
        print(f"[DEBUG] authenticated_user_id parameter: {authenticated_user_id}")
        
        user_id = None
        
        # Priority 1: Use authenticated_user_id from session (if available)
        if authenticated_user_id:
            user_id = authenticated_user_id
            print(f"[DEBUG] Using authenticated_user_id from session: {user_id}")
        
        # Priority 2: Check for username in params
        elif 'username' in params:
            username = params['username']
            print(f"[DEBUG] Looking up user by username: {username}")
            
            cursor = self.db.connection.cursor()
            cursor.execute("SELECT id FROM users WHERE username = ?", (username,))
            user = cursor.fetchone()
            
            if user:
                user_id = user[0]
                print(f"[DEBUG] Found user_id: {user_id}")
            else:
                print(f"[DEBUG] User not found")
                return self.create_response("error", "User not found")
        
        # Priority 3: Check for direct user_id parameter
        elif 'user_id' in params:
            user_id = params['user_id']
            print(f"[DEBUG] Using direct user_id from params: {user_id}")
        
        else:
            print(f"[DEBUG] No authentication found")
            return self.create_response("error", "Authentication required")
        
        print(f"[DEBUG] Fetching student data for user_id: {user_id}")
        result = self.db.get_student_data(user_id)
        return result
        
    def handle_submit_request(self, params, authenticated_user_id=None):
        """Submit a new request/complaint"""
        print(f"[DEBUG] SUBMIT_REQUEST called with params: {params}")
        print(f"[DEBUG] authenticated_user_id parameter: {authenticated_user_id}")
        
        user_id = None
        
        # Priority 1: Use authenticated_user_id from session (if available)
        if authenticated_user_id:
            user_id = authenticated_user_id
            print(f"[DEBUG] Using authenticated_user_id from session: {user_id}")
        
        # Priority 2: Check for username in params
        elif 'username' in params:
            username = params['username']
            print(f"[DEBUG] Looking up user by username: {username}")
            
            cursor = self.db.connection.cursor()
            cursor.execute("SELECT id FROM users WHERE username = ?", (username,))
            user = cursor.fetchone()
            
            if user:
                user_id = user[0]
                print(f"[DEBUG] Found user_id: {user_id}")
            else:
                print(f"[DEBUG] User not found")
                return self.create_response("error", "User not found")
        
        # Priority 3: Check for direct user_id parameter
        elif 'user_id' in params:
            user_id = params['user_id']
            print(f"[DEBUG] Using direct user_id from params: {user_id}")
        
        else:
            print(f"[DEBUG] No authentication found")
            return self.create_response("error", "Authentication required")
        
        # Validate request parameters
        required = ['request_type', 'title', 'description']
        valid, message = self.security.validate_input(params, required)
        
        if not valid:
            return self.create_response("error", message)
        
        # Sanitize inputs
        request_type = self.security.sanitize_input(params['request_type'])
        title = self.security.sanitize_input(params['title'])
        description = self.security.sanitize_input(params['description'])
        
        print(f"[DEBUG] Submitting request for user_id: {user_id}")
        print(f"[DEBUG] Request type: {request_type}, Title: {title}")
        
        result = self.db.submit_request(user_id, request_type, title, description)
        return self.create_response("success", "Request submitted successfully", {
            "request_id": result['request_id']
        })
        
    def handle_get_requests(self, params, authenticated_user_id=None):
        """Get all requests for the user"""
        print(f"[DEBUG] GET_REQUESTS called with params: {params}")
        print(f"[DEBUG] authenticated_user_id parameter: {authenticated_user_id}")
        
        user_id = None
        
        # Priority 1: Use authenticated_user_id from session (if available)
        if authenticated_user_id:
            user_id = authenticated_user_id
            print(f"[DEBUG] Using authenticated_user_id from session: {user_id}")
        
        # Priority 2: Check for username in params
        elif 'username' in params:
            username = params['username']
            print(f"[DEBUG] Looking up user by username: {username}")
            
            cursor = self.db.connection.cursor()
            cursor.execute("SELECT id FROM users WHERE username = ?", (username,))
            user = cursor.fetchone()
            
            if user:
                user_id = user[0]
                print(f"[DEBUG] Found user_id: {user_id}")
            else:
                print(f"[DEBUG] User not found")
                return self.create_response("error", "User not found")
        
        # Priority 3: Check for direct user_id parameter
        elif 'user_id' in params:
            user_id = params['user_id']
            print(f"[DEBUG] Using direct user_id from params: {user_id}")
        
        else:
            print(f"[DEBUG] No authentication found")
            return self.create_response("error", "Authentication required")
        
        print(f"[DEBUG] Fetching requests for user_id: {user_id}")
        result = self.db.get_user_requests(user_id)
        return result
    
    def handle_help(self, params):
        """Return available commands"""
        commands_info = {
            'REGISTER': 'Register new user - params: username, password, email, [student_data]',
            'LOGIN': 'Login user - params: username, password',
            'GET_DATA': 'Get student data (requires login)',
            'SUBMIT_REQUEST': 'Submit request - params: request_type, title, description',
            'GET_REQUESTS': 'Get user requests (requires login)',
            'EXIT': 'Disconnect from server'
        }
        return self.create_response("success", "Available commands", commands_info)
    
    def handle_exit(self, params, user_id=None):
        """Handle client exit"""
        return self.create_response("success", "Goodbye!")
    
    def create_response(self, status, message, data=None):
        """Create standardized response"""
        response = {
            "status": status,
            "message": message,
            "timestamp": datetime.now().isoformat()
        }
        if data:
            response["data"] = data
        return response
    
    def stop(self):
        """Stop the server"""
        self.running = False
        for client, _ in self.clients:
            try:
                client.close()
            except:
                pass
        
        if self.server_socket:
            self.server_socket.close()
        
        self.db.close()
        print("[*] Server stopped")

if __name__ == "__main__":
    # Parse command line arguments
    host = '0.0.0.0'
    port = 12345
    
    if len(sys.argv) > 1:
        host = sys.argv[1]
    if len(sys.argv) > 2:
        port = int(sys.argv[2])
    
    server = StudentSocketServer(host, port)
    
    try:
        server.start()
    except KeyboardInterrupt:
        print("\n[*] Shutting down server...")
        server.stop()

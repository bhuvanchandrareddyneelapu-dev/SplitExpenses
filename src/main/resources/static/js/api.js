const API = {
    getToken() {
        return localStorage.getItem("jwt_token");
    },
    setToken(token) {
        localStorage.setItem("jwt_token", token);
    },
    clearToken() {
        localStorage.removeItem("jwt_token");
    },
    async request(url, method = "GET", body = null, requireAuth = true) {
        const headers = {
            "Content-Type": "application/json"
        };
        if (requireAuth) {
            const token = this.getToken();
            if (!token) {
                window.location.href = "login.html";
                return;
            }
            headers["Authorization"] = `Bearer ${token}`;
        }
        
        const config = {
            method,
            headers
        };
        if (body) {
            config.body = JSON.stringify(body);
        }
        
        const response = await fetch(url, config);
        
        if (response.status === 401) {
            this.clearToken();
            window.location.href = "login.html";
            return;
        }
        
        // Handle empty or text responses
        const text = await response.text();
        let data = null;
        try {
            data = text ? JSON.parse(text) : null;
        } catch (e) {
            data = { message: text };
        }
        
        if (!response.ok) {
            const message = (data && data.message) || `Request failed with status ${response.status}`;
            throw new Error(message);
        }
        
        return data;
    },
    get(url, requireAuth = true) {
        return this.request(url, "GET", null, requireAuth);
    },
    post(url, body, requireAuth = true) {
        return this.request(url, "POST", body, requireAuth);
    },
    put(url, body, requireAuth = true) {
        return this.request(url, "PUT", body, requireAuth);
    },
    delete(url, requireAuth = true) {
        return this.request(url, "DELETE", null, requireAuth);
    },
    async uploadFile(url, file, requireAuth = true) {
        const formData = new FormData();
        formData.append('file', file);
        
        const headers = {};
        if (requireAuth) {
            const token = this.getToken();
            if (!token) {
                window.location.href = "login.html";
                return;
            }
            headers["Authorization"] = `Bearer ${token}`;
        }
        
        const response = await fetch(url, {
            method: 'POST',
            headers,
            body: formData
        });
        
        if (response.status === 401) {
            this.clearToken();
            window.location.href = "login.html";
            return;
        }
        
        const text = await response.text();
        if (!response.ok) {
            throw new Error(text || `Request failed with status ${response.status}`);
        }
        return text;
    }
};

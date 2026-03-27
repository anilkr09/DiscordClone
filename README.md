# DiscordClone

🚀 Discord Clone – Real-Time Chat Application

A full-stack Discord-like real-time chat application  
---







# ✨ Features
🔐 Authentication & Authorization

JWT-based authentication

Secure login & registration

Role-based access control

💬 Real-Time Messaging

One-to-one (DM) messaging

Channel-based group chats

real-time online/offline status

reatl-time Send / accept / reject friend requests

real-tiem Remove friends

Real-time friend status updates
Real-time create/remove  channel



---
## Demo
<img width="1464" height="855" alt="image" src="https://github.com/user-attachments/assets/05fe74fe-ee37-45e4-88c2-01f301e01575" />

<img width="1415" height="860" alt="image" src="https://github.com/user-attachments/assets/ece67323-61f6-4b86-be31-9d4b886c2692" />
<img width="1415" height="860" alt="image" src="https://github.com/user-attachments/assets/0517a2bb-e8e9-4b72-bb57-62869506c76f" />



## Architecture
- **Backend:** Java 17, Spring Boot, Spring Security, JPA, WebSocket, Redis, PostgreSQL
- **Frontend:** React, TypeScript, Vite, MUI, React Query, WebSocket

---

## Backend Setup

### Prerequisites
- Java 17+
- Gradle
- PostgreSQL (default: `localhost:5432`, DB: `discord_clone`)

### Running Locally
1. Clone the repository:
   ```bash
   git clone <repo-url>
   cd DiscordClone
   ```
2. Configure your database and message broker in `src/main/resources/application.properties` if needed.
3. Build and run the backend:
   ```bash
   ./gradlew bootRun
   ```
   The backend will start on [http://localhost:8082](http://localhost:8082).

---

## Frontend Setup

See [frontend/README.md](frontend/README.md) for full details.

### Quick Start
1. Navigate to the frontend directory:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the development server:
   ```bash
   npm run dev
   ```
   The frontend will start on [http://localhost:5173](http://localhost:5173) (or as configured by Vite).

---


---

## Environment Variables
- Backend environment variables can be set in `application.properties` or overridden in Docker Compose.
- Frontend environment variables can be set in `.env` files in the `frontend` directory.

---

## Testing

### Backend
- Run tests with:
  ```bash
  ./gradlew test
  ```
- Tests are located in `src/test/java/com/discordclone/`

### Frontend
- See [frontend/README.md](frontend/README.md) for frontend testing instructions.

---

## License

This project is licensed under the MIT License. 

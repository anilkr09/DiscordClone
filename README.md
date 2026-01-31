# DiscordClone

🚀 Discord Clone – Real-Time Chat Application

A full-stack Discord-like real-time chat application featuring user authentication, friend management, group chats, WebSocket-based messaging, and Redis caching. Built with Spring Boot on the backend and React + TypeScript on the frontend,
---







# ✨ Features
🔐 Authentication & Authorization

JWT-based authentication

Secure login & registration

Role-based access control

💬 Real-Time Messaging

One-to-one (DM) messaging

Channel-based group chats

WebSocket (STOMP) real-time communication

online/offline status

👥 Friends System

Send / accept / reject friend requests

Remove friends

Real-time friend status updates

🧠 Performance & Scalability

Redis caching for frequently accessed data


🛠 Developer Friendly

Clean layered architecture

Dockerized setup

Unit & integration tests

---
## Demo
<img width="1464" height="855" alt="image" src="https://github.com/user-attachments/assets/05fe74fe-ee37-45e4-88c2-01f301e01575" />

<img width="1464" height="855" alt="image" src="https://github.com/user-attachments/assets/95723d2d-981f-49c0-b60f-a5ec1ccb12e3" />
<img width="1464" height="855" alt="image" src="https://github.com/user-attachments/assets/1854b273-cc88-4e13-a6d2-5e4d09e0e892" />



## Architecture
- **Backend:** Java 17, Spring Boot, Spring Security, JPA, WebSocket, Redis, PostgreSQL
- **Frontend:** React, TypeScript, Vite, MUI, React Query, WebSocket
- **DevOps:** Docker, Docker Compose, Nginx (for frontend static serving), Jenkins agent (optional)

---

## Backend Setup

### Prerequisites
- Java 17+
- Gradle
- PostgreSQL (default: `localhost:5432`, DB: `discord_clone`)
- Redis (default: `localhost:6379`)

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

## Running with Docker

### Prerequisites
- Docker
- Docker Compose

### Steps
1. Build and start all services:
   ```bash
   docker-compose  up -d
   ```
2. The backend will be available at [http://localhost:8082](http://localhost:8082), and the frontend at [http://localhost:4173](http://localhost:4173).

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

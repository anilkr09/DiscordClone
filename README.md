# DiscordClone

A full-stack Discord-like chat application with a Spring Boot backend and a React + TypeScript + Vite frontend. This project supports real-time messaging, user management, server (guild) management, and more.

---

## Table of Contents
- [Features](#features)
- [Architecture](#architecture)
- [Backend Setup](#backend-setup)
- [Frontend Setup](#frontend-setup)
- [Running with Docker](#running-with-docker)
- [Environment Variables](#environment-variables)
- [Testing](#testing)
- [License](#license)

---

## Features
- Real-time chat with WebSocket support
- User authentication with JWT
- Server (guild) and member management
- Role-based access control (Owner, Admin, Member)
- RESTful API
- PostgreSQL and Redis integration
- Kafka for event streaming
- Dockerized for easy deployment

---

## Architecture
- **Backend:** Java 17, Spring Boot, Spring Security, JPA, WebSocket, Kafka, Redis, PostgreSQL
- **Frontend:** React, TypeScript, Vite, MUI, React Query, WebSocket
- **DevOps:** Docker, Docker Compose, Nginx (for frontend static serving), Jenkins agent (optional)

---

## Backend Setup

### Prerequisites
- Java 17+
- Gradle
- PostgreSQL (default: `localhost:5432`, DB: `discord_clone`)
- Redis (default: `localhost:6379`)
- Kafka (default: `localhost:9092`)

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
   docker-compose -f updated-docker-compose.yml up --build
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

export interface User {
    userId: string;               // Unique user identifier
    username: string;             // Display name
    profilePicture?: string | null; // Optional profile picture (can be null)
    status?: "online" | "idle" | "offline" | "typing"; // User status (optional)
}
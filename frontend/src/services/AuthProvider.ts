// import React, { createContext, useContext, useEffect, useState, useCallback, ReactNode } from "react";

// // Define the authentication context type
// interface AuthContextType {
//   isLoggedIn: boolean;
//   jwt: string | null;
//   username: string | null;
//   setJwt: (token: string | null) => void;
//   setUsername: (name: string | null) => void;
// }

// // Create the context
// const AuthContext = createContext<AuthContextType | undefined>(undefined);

// interface AuthProviderProps {
//   children: ReactNode;
// }

// export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
//   const [jwt, setJwtState] = useState<string | null>(() => localStorage.getItem("accessToken"));
//   const [username, setUsernameState] = useState<string | null>(() => localStorage.getItem("username"));
// export const AuthProvider = ({ children }: AuthProviderProps): JSX.Element => {
//   // Function to update JWT and sync it with localStorage
//   const setJwt = useCallback((token: string | null) => {
//     setJwtState(token);
//     if (token) {
//       localStorage.setItem("accessToken", token);
//     } else {
//       localStorage.removeItem("accessToken");
//     }
//   }, []);

//   // Function to update Username and sync it with localStorage
//   const setUsername = useCallback((name: string | null) => {
//     setUsernameState(name);
//     if (name) {
//       localStorage.setItem("username", name);
//     } else {
//       localStorage.removeItem("username");
//     }
//   }, []);

//   useEffect(() => {
//     // Sync state with localStorage when storage changes externally
//     const handleStorageChange = () => {
//       setJwtState(localStorage.getItem("accessToken"));
//       setUsernameState(localStorage.getItem("username"));
//     };

//     window.addEventListener("storage", handleStorageChange);
//     return () => window.removeEventListener("storage", handleStorageChange);
//   }, []);

//   // Context value
//   const authContextValue: AuthContextType = {
//     isLoggedIn: !!jwt,
//     jwt,
//     username,
//     setJwt,
//     setUsername
//   };

//   return <AuthContext.Provider value={authContextValue}> {children} </AuthContext.Provider>;
// };

// // Custom hook to use authentication context
// export const useAuth = (): AuthContextType => {
//   const context = useContext(AuthContext);
//   if (!context) {
//     throw new Error("useAuth must be used within an AuthProvider");
//   }
//   return context;
// };

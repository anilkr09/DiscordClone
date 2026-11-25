/** Where the signed-in user's tokens live. */
export const session = {
  token: (): string | null => localStorage.getItem("accessToken"),

  /** The access token is no longer accepted: forget it and ask the user to sign in again. */
  expire(): void {
    for (const key of ["accessToken", "refreshToken", "username", "id", "currentUser"]) {
      localStorage.removeItem(key);
    }
    window.location.assign("/login");
  },
};

import { Component, ReactNode } from "react";
import { Box, Button, Typography } from "@mui/material";
import { useLocation } from "react-router-dom";

class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error: unknown) {
    console.error("A view failed to render", error);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <Box role="alert" sx={{ flex: 1, p: 4, textAlign: "center" }}>
        <Typography sx={{ mb: 2 }}>Something went wrong showing this page.</Typography>
        <Button variant="contained" onClick={() => this.setState({ failed: false })}>
          Try again
        </Button>
      </Box>
    );
  }
}

/** Keeps a failing view from taking down the rest of the app; navigating elsewhere resets it. */
export function RouteBoundary({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  return <ErrorBoundary key={pathname}>{children}</ErrorBoundary>;
}

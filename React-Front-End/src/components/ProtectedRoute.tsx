
import { useEffect, useState, type ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { ApiError, getCurrentSession } from "../services/quoteApi";

interface ProtectedRouteProps {
  children: ReactNode;
}

export default function ProtectedRoute({ children }: ProtectedRouteProps) {
const [status, setStatus] = useState<"loading" | "authenticated" | "unauthenticated" | "error">("loading");

  useEffect(() => {
    let active = true;

    async function checkSession() {
      try {
        await getCurrentSession();

        if (active) setStatus("authenticated");
      } catch (error) {
        if (active) {
          setStatus(
            error instanceof ApiError && (error.status === 401 || error.status === 403)
              ? "unauthenticated"
              : "error"
          );
        }
      }
    }

    checkSession();

    return () => {
      active = false;
    };
  }, []);

  if (status === "loading") {
    return <p role="status">Checking your session...</p>;
  }

  if (status === "error") {
    return <p role="alert">Could not verify your session. Please try again later.</p>;
  }

  if (status === "unauthenticated") {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

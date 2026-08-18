import { useKeycloak } from "@react-keycloak/web";
import { Navigate } from "react-router-dom";
export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { initialized, keycloak } = useKeycloak();
  if (!initialized)
    return <main className="center">Starting secure session…</main>;
  return keycloak.authenticated ? (
    <>{children}</>
  ) : (
    <Navigate to="/login" replace />
  );
}

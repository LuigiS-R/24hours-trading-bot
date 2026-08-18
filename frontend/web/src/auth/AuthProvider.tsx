import { ReactKeycloakProvider } from "@react-keycloak/web";
import { keycloak } from "./keycloak";
export function AuthProvider({ children }: { children: React.ReactNode }) {
  return (
    <ReactKeycloakProvider
      authClient={keycloak}
      initOptions={{
        onLoad: "check-sso",
        pkceMethod: "S256",
        checkLoginIframe: false,
      }}
    >
      {children}
    </ReactKeycloakProvider>
  );
}

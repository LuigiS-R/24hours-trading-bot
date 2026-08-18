import Keycloak from "keycloak-js";
const required = [
  "VITE_KEYCLOAK_URL",
  "VITE_KEYCLOAK_REALM",
  "VITE_KEYCLOAK_CLIENT_ID",
] as const;
export const configError = required.some((key) => !import.meta.env[key])
  ? "Keycloak configuration is incomplete."
  : undefined;
export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || "",
  realm: import.meta.env.VITE_KEYCLOAK_REALM || "",
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || "",
});

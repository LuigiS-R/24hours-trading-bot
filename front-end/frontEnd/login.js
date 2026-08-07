const keycloak = new Keycloak({
  url: 'http://localhost:8085',
  realm: 'trading-bot',
  clientId: 'trading-bot-frontend-client'
});

const registrationRedirectUri = 'http://localhost:5500/login.html';
const loginRedirectUri = 'http://localhost:5500/coming-soon.html';

const keycloakReady = keycloak.init({
  checkLoginIframe: false,
  pkceMethod: 'S256'
}).catch((error) => {
  console.error('Keycloak initialization failed', error);
  throw error;
});

const authCard = document.getElementById('authCard');
const authForm = document.getElementById('authForm');
const authToggle = document.getElementById('authToggle');

// Triggers the redirect to the Keycloak sign-up form
const handleSignUp = async () => {
  await keycloakReady;
  keycloak.register({
    redirectUri: registrationRedirectUri
  });
};

const handleLogin = async () => {
  await keycloakReady;
  keycloak.login({
    redirectUri: loginRedirectUri
  });
};

authForm.addEventListener('submit', (event) => {
  event.preventDefault();
  const mode = authCard.dataset.mode;

  if (mode === 'signup') {
    handleSignUp().catch((error) => {
      console.error('Sign-up redirect failed', error);
    });
    return;
  }

  handleLogin();
});

authToggle.addEventListener('click', (event) => {
  event.preventDefault();
  const isLoginMode = authCard.dataset.mode === 'login';

  if (isLoginMode) {
    authCard.dataset.mode = 'signup';
    authToggle.textContent = 'Login';
    handleSignUp().catch((error) => {
      console.error('Sign-up redirect failed', error);
    });
    return;
  }

  authCard.dataset.mode = 'login';
  authToggle.textContent = 'Sign Up';
});

handleLogin().catch((error) => {
  console.error('Login redirect failed', error);
});
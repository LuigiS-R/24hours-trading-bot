import React from 'react'; import ReactDOM from 'react-dom/client'; import App from './App'; import { AuthProvider } from './auth/AuthProvider'; import { configError } from './auth/keycloak'; import './styles/global.css';

function ConfigurationRequired() { return <main className="login"><section className="panel"><span className="paper">SETUP REQUIRED</span><h1>Tradify needs configuration</h1><p className="muted">This build is missing its public Keycloak settings. Build the app with the values from <code>.env.example</code>, then serve the <code>dist</code> folder through your web server.</p></section></main>; }

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode>{configError ? <ConfigurationRequired/> : <AuthProvider><App/></AuthProvider>}</React.StrictMode>);

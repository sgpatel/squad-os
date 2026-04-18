import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';

// CSS load order matters — tokens first, theme overrides next, base components last.
// Each theme override is gated by `:root[data-theme="..."]` so they coexist safely.
import './styles/tokens.css';
import './styles/themes/lumen.css';
import './styles/themes/crayon.css';
import './styles/themes/atlas.css';
import './styles/themes/studio.css';
import './styles/themes/graphite.css';
import './styles/base.css';
import './styles/app.css';

const rootEl = document.getElementById('root');
if (!rootEl) throw new Error('Missing #root element in index.html');

createRoot(rootEl).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>
);

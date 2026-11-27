// Made by ../build.ts: every component, registered with the page's React (swaswa-react's runtime).
import './index.css';
import * as m0 from './components/ui/alert';
import * as m1 from './components/ui/badge';
import * as m2 from './components/ui/button';
import * as m3 from './components/ui/card';
import * as m4 from './components/ui/checkbox';
import * as m5 from './components/ui/dialog';
import * as m6 from './components/ui/input';
import * as m7 from './components/ui/label';
import * as m8 from './components/ui/progress';
import * as m9 from './components/ui/separator';
import * as m10 from './components/ui/skeleton';
import * as m11 from './components/ui/switch';
import * as m12 from './components/ui/tabs';
import * as m13 from './components/ui/textarea';

const { register } = (window as any).__swaswaReact;
for (const m of [m0, m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11, m12, m13] as Record<string, unknown>[]) {
  for (const [name, value] of Object.entries(m)) {
    if (/^[A-Z]/.test(name) && !name.endsWith('Variants') && (typeof value === 'function' || typeof value === 'object')) register(name, value);
  }
}

// shadcn's dark theme is a class (.dark): it follows the page's colour scheme (Java's theme: light, dark, the system's)
const root = document.documentElement;
const system = matchMedia('(prefers-color-scheme: dark)');
const follow = () => {
  const scheme = root.style.colorScheme;
  root.classList.toggle('dark', scheme === 'dark' || (scheme !== 'light' && system.matches));
};
follow();
system.addEventListener('change', follow);
new MutationObserver(follow).observe(root, { attributes: true, attributeFilter: ['style'] });

// Made by ../build.ts: every component, registered with the page's React (swaswa-react's runtime).
import './index.css';
import * as m0 from './components/ui/accordion';
import * as m1 from './components/ui/alert-dialog';
import * as m2 from './components/ui/alert';
import * as m3 from './components/ui/aspect-ratio';
import * as m4 from './components/ui/attachment';
import * as m5 from './components/ui/avatar';
import * as m6 from './components/ui/badge';
import * as m7 from './components/ui/breadcrumb';
import * as m8 from './components/ui/bubble';
import * as m9 from './components/ui/button-group';
import * as m10 from './components/ui/button';
import * as m11 from './components/ui/calendar';
import * as m12 from './components/ui/card';
import * as m13 from './components/ui/carousel';
import * as m14 from './components/ui/chart';
import * as m15 from './components/ui/checkbox';
import * as m16 from './components/ui/code-block';
import * as m17 from './components/ui/collapsible';
import * as m18 from './components/ui/combobox';
import * as m19 from './components/ui/command';
import * as m20 from './components/ui/context-menu';
import * as m21 from './components/ui/dialog';
import * as m22 from './components/ui/direction';
import * as m23 from './components/ui/drawer';
import * as m24 from './components/ui/dropdown-menu';
import * as m25 from './components/ui/empty';
import * as m26 from './components/ui/field';
import * as m27 from './components/ui/hover-card';
import * as m28 from './components/ui/input-group';
import * as m29 from './components/ui/input-otp';
import * as m30 from './components/ui/input';
import * as m31 from './components/ui/item';
import * as m32 from './components/ui/kbd';
import * as m33 from './components/ui/label';
import * as m34 from './components/ui/marker';
import * as m35 from './components/ui/menubar';
import * as m36 from './components/ui/message-scroller';
import * as m37 from './components/ui/message';
import * as m38 from './components/ui/native-select';
import * as m39 from './components/ui/navigation-menu';
import * as m40 from './components/ui/pagination';
import * as m41 from './components/ui/popover';
import * as m42 from './components/ui/progress';
import * as m43 from './components/ui/questionnaire';
import * as m44 from './components/ui/radio-group';
import * as m45 from './components/ui/resizable';
import * as m46 from './components/ui/scroll-area';
import * as m47 from './components/ui/select';
import * as m48 from './components/ui/separator';
import * as m49 from './components/ui/sheet';
import * as m50 from './components/ui/sidebar';
import * as m51 from './components/ui/skeleton';
import * as m52 from './components/ui/slider';
import * as m53 from './components/ui/spinner';
import * as m54 from './components/ui/switch';
import * as m55 from './components/ui/table';
import * as m56 from './components/ui/tabs';
import * as m57 from './components/ui/textarea';
import * as m58 from './components/ui/toast';
import * as m59 from './components/ui/toggle-group';
import * as m60 from './components/ui/toggle';
import * as m61 from './components/ui/tooltip';
import * as m62 from './components/ui/trace';

const { register } = (window as any).__swaswaReact;
for (const m of [m0, m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11, m12, m13, m14, m15, m16, m17, m18, m19, m20, m21, m22, m23, m24, m25, m26, m27, m28, m29, m30, m31, m32, m33, m34, m35, m36, m37, m38, m39, m40, m41, m42, m43, m44, m45, m46, m47, m48, m49, m50, m51, m52, m53, m54, m55, m56, m57, m58, m59, m60, m61, m62] as Record<string, unknown>[]) {
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

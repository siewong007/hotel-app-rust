import { createRouter } from '@tanstack/react-router';
import { routeTree } from '../routeTree.gen';
import { parsePlainSearch, stringifyPlainSearch } from './compat';

export const router = createRouter({
  routeTree,
  defaultPreload: 'intent',
  parseSearch: parsePlainSearch,
  stringifySearch: stringifyPlainSearch,
});

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}

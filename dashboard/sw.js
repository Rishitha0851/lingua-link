// Lingua Link — Service Worker for PWA Offline Caching
const CACHE_NAME = 'lingua-link-v1';
const STATIC_ASSETS = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './manifest.json',
  './phrasebook.json',
  './api/phrasebook',
  './icon-192.png',
  './icon-512.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log('[SW] Pre-caching static assets & phrasebook...');
      return cache.addAll(STATIC_ASSETS).catch((err) => {
        console.warn('[SW] Cache addAll warning:', err);
      });
    }).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // Network-first for translation and health APIs
  if (url.pathname.startsWith('/api/translate') || url.pathname.startsWith('/api/health')) {
    event.respondWith(
      fetch(event.request).catch(() => {
        // Return offline JSON message
        if (url.pathname.startsWith('/api/health')) {
          return new Response(JSON.stringify({
            status: 'healthy',
            internet_online: false,
            cached: true
          }), { headers: { 'Content-Type': 'application/json' } });
        }
        return new Response(JSON.stringify({
          original: '',
          translated: 'Offline: Translation unavailable for unlisted sentences without internet. Please use the Phrasebook or known phrases.',
          status: 'offline_cached'
        }), { headers: { 'Content-Type': 'application/json' } });
      })
    );
    return;
  }

  // Cache-first for all other static assets and phrasebook
  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      if (cachedResponse) {
        // Return cached, refresh in background
        fetch(event.request).then((networkResponse) => {
          if (networkResponse && networkResponse.status === 200) {
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, networkResponse));
          }
        }).catch(() => {});
        return cachedResponse;
      }
      return fetch(event.request).then((networkResponse) => {
        if (!networkResponse || networkResponse.status !== 200 || networkResponse.type !== 'basic') {
          return networkResponse;
        }
        const responseToCache = networkResponse.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, responseToCache));
        return networkResponse;
      }).catch(() => {
        if (event.request.mode === 'navigate') {
          return caches.match('/index.html');
        }
      });
    })
  );
});

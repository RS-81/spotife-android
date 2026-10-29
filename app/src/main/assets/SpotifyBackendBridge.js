(function () {
    'use strict';

    if (window.__spotifyBridgeInstalled) return;
    window.__spotifyBridgeInstalled = true;

    const ANDROID_BRIDGE = 'AndroidSpotifyBridge';
    let capturedBearerToken = null;
    let capturedClientToken = null;
    let activeDeviceId = null;

    function notifyAndroid(method, payload) {
        if (window[ANDROID_BRIDGE] && typeof window[ANDROID_BRIDGE][method] === 'function') {
            try {
                window[ANDROID_BRIDGE][method](
                    typeof payload === 'string' ? payload : JSON.stringify(payload)
                );
            } catch (err) {
                console.error('[SpotifyBridge] Failed calling ' + method, err);
            }
        }
    }

    function extractHeaders(input, init) {
        const headers = {};
        const collect = (h) => {
            if (!h) return;
            if (typeof Headers !== 'undefined' && h instanceof Headers) {
                h.forEach((v, k) => { headers[k.toLowerCase()] = v; });
            } else if (Array.isArray(h)) {
                h.forEach(([k, v]) => { headers[String(k).toLowerCase()] = v; });
            } else if (typeof h === 'object') {
                Object.keys(h).forEach((k) => { headers[k.toLowerCase()] = h[k]; });
            }
        };

        if (typeof Request !== 'undefined' && input instanceof Request) {
            collect(input.headers);
        }
        if (init && init.headers) {
            collect(init.headers);
        }
        return headers;
    }

    const originalFetch = window.fetch.bind(window);
    window.fetch = async function (input, init) {
        const url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
        const headers = extractHeaders(input, init);

        const authHeader = headers['authorization'];
        if (authHeader && authHeader.startsWith('Bearer ')) {
            const token = authHeader.substring(7).trim();
            if (token && token !== capturedBearerToken) {
                capturedBearerToken = token;
                notifyAndroid('onTokenCaptured', token);
            }
        }

        if (headers['client-token']) {
            capturedClientToken = headers['client-token'];
        }

        const deviceMatch = url.match(/\/connect-state\/v1\/devices\/([a-zA-Z0-9_-]+)/);
        if (deviceMatch && deviceMatch[1]) {
            activeDeviceId = deviceMatch[1];
        }

        const response = await originalFetch(input, init);

        try {
            if (url.includes('/pathfinder/v1/query') || url.includes('/pathfinder/v2/query')) {
                const clone = response.clone();
                clone.json().then((jsonBody) => {
                    notifyAndroid('onPathfinderResponse', {
                        url: url,
                        data: jsonBody
                    });
                }).catch(() => {});
            } else if (url.includes('api.spotify.com/v1/me/player')) {
                const clone = response.clone();
                clone.json().then((playerJson) => {
                    notifyAndroid('onPlayerApiState', playerJson);
                }).catch(() => {});
            }
        } catch (err) {}

        return response;
    };

    function parseTimeToMs(timeStr) {
        if (!timeStr) return 0;
        const parts = timeStr.trim().split(':').map(Number);
        if (parts.some(isNaN)) return 0;
        if (parts.length === 2) {
            return (parts[0] * 60 + parts[1]) * 1000;
        } else if (parts.length === 3) {
            return (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000;
        }
        return 0;
    }

    function scrapeCurrentDomState() {
        const titleEl = document.querySelector('[data-testid="context-item-info-title"]');
        const artistEl = document.querySelector('[data-testid="context-item-info-subtitles"]');
        const coverEl = document.querySelector('[data-testid="cover-art-image"]');
        const playBtn = document.querySelector('[data-testid="control-button-playpause"]');
        const posEl = document.querySelector('[data-testid="playback-position"]');
        const durEl = document.querySelector('[data-testid="playback-duration"]');

        const ariaLabel = playBtn ? (playBtn.getAttribute('aria-label') || '') : '';
        const isPlaying = ariaLabel.toLowerCase().includes('pause');

        return {
            title: titleEl ? (titleEl.textContent || '').trim() : '',
            artist: artistEl ? (artistEl.textContent || '').trim() : '',
            artworkUrl: coverEl ? (coverEl.getAttribute('src') || '') : '',
            isPlaying: isPlaying,
            positionMs: parseTimeToMs(posEl ? posEl.textContent : '0:00'),
            durationMs: parseTimeToMs(durEl ? durEl.textContent : '0:00')
        };
    }

    setInterval(() => {
        const state = scrapeCurrentDomState();
        if (state.title || state.durationMs > 0) {
            notifyAndroid('onPlayerStateChanged', state);
        }
    }, 1000);

    async function callSpotifyWebApi(endpoint, method, bodyObj) {
        if (!capturedBearerToken) return null;
        const headers = {
            'Authorization': 'Bearer ' + capturedBearerToken,
            'Content-Type': 'application/json'
        };
        if (capturedClientToken) {
            headers['client-token'] = capturedClientToken;
        }

        const options = { method: method, headers: headers };
        if (bodyObj !== undefined && bodyObj !== null) {
            options.body = JSON.stringify(bodyObj);
        }

        return originalFetch('https://api.spotify.com/v1' + endpoint, options);
    }

    window.SpotifyBridgeController = {
        playUri: async function (spotifyUri) {
            if (!spotifyUri) return;
            const deviceQuery = activeDeviceId ? ('?device_id=' + encodeURIComponent(activeDeviceId)) : '';
            const isContext =
                spotifyUri.includes(':playlist:') ||
                spotifyUri.includes(':album:') ||
                spotifyUri.includes(':artist:');

            const payload = isContext
                ? { context_uri: spotifyUri }
                : { uris: [spotifyUri] };

            const res = await callSpotifyWebApi('/me/player/play' + deviceQuery, 'PUT', payload);

            if (!res || !res.ok) {
                const parts = spotifyUri.split(':');
                if (parts.length >= 3) {
                    const path = '/' + parts[1] + '/' + parts[2];
                    window.history.pushState({}, '', path);
                    window.dispatchEvent(new PopStateEvent('popstate'));
                    setTimeout(() => {
                        const primaryPlay = document.querySelector('[data-testid="play-button"], [data-testid="action-bar-row"] button');
                        if (primaryPlay) primaryPlay.click();
                    }, 800);
                }
            }
        },

        togglePlayPause: function () {
            const btn = document.querySelector('[data-testid="control-button-playpause"]');
            if (btn) {
                btn.click();
            } else {
                const state = scrapeCurrentDomState();
                const endpoint = state.isPlaying ? '/me/player/pause' : '/me/player/play';
                callSpotifyWebApi(endpoint, 'PUT');
            }
        },

        skipNext: function () {
            const nextBtn = document.querySelector('[data-testid="control-button-skip-forward"]');
            if (nextBtn) {
                nextBtn.click();
            } else {
                callSpotifyWebApi('/me/player/next', 'POST');
            }
        },

        seekTo: function (positionMs) {
            const targetMs = Math.max(0, Math.floor(Number(positionMs) || 0));
            const progressBar = document.querySelector('[data-testid="playback-progressbar"] input[type="range"]');
            if (progressBar) {
                const nativeInputValueSetter = Object.getOwnPropertyDescriptor(
                    window.HTMLInputElement.prototype,
                    'value'
                ).set;
                nativeInputValueSetter.call(progressBar, targetMs);
                progressBar.dispatchEvent(new Event('input', { bubbles: true }));
                progressBar.dispatchEvent(new Event('change', { bubbles: true }));
            } else {
                callSpotifyWebApi('/me/player/seek?position_ms=' + targetMs, 'PUT');
            }
        },

        searchCatalog: async function (query) {
            if (!query || !query.trim()) return;
            if (capturedBearerToken) {
                const encoded = encodeURIComponent(query.trim());
                const res = await callSpotifyWebApi(
                    '/search?q=' + encoded + '&type=track,playlist,album&limit=20',
                    'GET'
                );
                if (res && res.ok) {
                    const data = await res.json();
                    notifyAndroid('onSearchResults', data);
                    return;
                }
            }
            const searchPath = '/search/' + encodeURIComponent(query.trim());
            window.history.pushState({}, '', searchPath);
            window.dispatchEvent(new PopStateEvent('popstate'));
        }
    };
})();

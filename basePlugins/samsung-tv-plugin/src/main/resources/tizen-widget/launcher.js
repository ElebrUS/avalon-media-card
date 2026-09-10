(function () {
    var BAKED_SERVER_URL = "%%AVALON_SERVER_URL%%";
    var STORAGE_KEY = "avalon_samsung_server_url";

    function isTizen() {
        return typeof tizen !== "undefined";
    }

    function isPackagedWidget() {
        var protocol = window.location.protocol || "";
        var host = window.location.hostname || "";
        return protocol === "file:" || protocol === "app:" || host === "" || isTizen();
    }

    function registerRemoteKeys() {
        if (!isTizen() || !tizen.tvinputdevice) {
            return;
        }
        var keys = [
            "MediaPlayPause",
            "MediaPlay",
            "MediaPause",
            "MediaStop",
            "MediaFastForward",
            "MediaRewind",
            "MediaTrackNext",
            "MediaTrackPrevious",
            "ColorF0Red",
            "ColorF1Green",
            "ColorF2Yellow",
            "ColorF3Blue",
            "ChannelUp",
            "ChannelDown",
            "Info",
            "Caption"
        ];
        for (var i = 0; i < keys.length; i++) {
            try {
                tizen.tvinputdevice.registerKey(keys[i]);
            } catch (e) {}
        }
        document.addEventListener("keydown", function (event) {
            if (event.keyCode === 10009) {
                try {
                    tizen.application.getCurrentApplication().exit();
                } catch (err) {}
            }
        });
    }

    function registerPreview(serverUrl) {
        if (typeof webapis === "undefined" || !webapis.preview || !webapis.preview.setPreviewData) {
            return;
        }
        var previewUrl = serverUrl.replace(/\/+$/, "") + "/widgets/preview.json";
        fetchJson(previewUrl, function (data) {
            try {
                webapis.preview.setPreviewData(JSON.stringify(data));
            } catch (e) {}
        });
    }

    function fetchJson(url, onSuccess) {
        if (typeof fetch === "function") {
            fetch(url).then(function (response) {
                return response.json();
            }).then(onSuccess).catch(function () {});
            return;
        }
        try {
            var xhr = new XMLHttpRequest();
            xhr.open("GET", url, true);
            xhr.onreadystatechange = function () {
                if (xhr.readyState === 4 && xhr.status >= 200 && xhr.status < 300) {
                    try {
                        onSuccess(JSON.parse(xhr.responseText));
                    } catch (e) {}
                }
            };
            xhr.send(null);
        } catch (e) {}
    }

    function openAvalon(serverUrl) {
        var base = (serverUrl || "").replace(/\/+$/, "");
        if (!base) {
            return false;
        }
        try {
            window.localStorage.setItem(STORAGE_KEY, base);
        } catch (e) {}
        registerRemoteKeys();
        registerPreview(base);
        window.location.replace(base + "/?ui=tv");
        return true;
    }

    function sameOriginAvalon() {
        var protocol = window.location.protocol || "";
        if (protocol !== "http:" && protocol !== "https:") {
            return false;
        }
        registerRemoteKeys();
        registerPreview(window.location.origin);
        window.location.replace("/?ui=tv");
        return true;
    }

    function readStoredUrl() {
        try {
            return window.localStorage.getItem(STORAGE_KEY) || "";
        } catch (e) {
            return "";
        }
    }

    function showSetup(initialUrl) {
        var setup = document.getElementById("setup");
        var input = document.getElementById("server-url");
        var button = document.getElementById("connect-btn");
        if (!setup || !input || !button) {
            return;
        }
        setup.className = "setup";
        if (initialUrl) {
            input.value = initialUrl;
        }
        button.onclick = function () {
            openAvalon(input.value);
        };
        input.addEventListener("keydown", function (event) {
            if (event.keyCode === 13) {
                openAvalon(input.value);
            }
        });
        try {
            input.focus();
        } catch (e) {}
    }

    function boot() {
        registerRemoteKeys();
        if (sameOriginAvalon()) {
            return;
        }
        var stored = readStoredUrl();
        var candidate = stored || BAKED_SERVER_URL;
        if (candidate && !isPackagedWidget()) {
            openAvalon(candidate);
            return;
        }
        if (candidate && stored) {
            openAvalon(candidate);
            return;
        }
        showSetup(candidate);
    }

    if (document.readyState === "complete" || document.readyState === "interactive") {
        boot();
    } else {
        document.addEventListener("DOMContentLoaded", boot);
    }
})();
